/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.blockentity;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.model.data.ModelData;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;

import appeng.api.ids.AEComponents;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.block.AEBaseEntityBlock;
import appeng.client.render.model.AEModelData;
import appeng.core.AELog;
import appeng.hooks.VisualStateSaving;
import appeng.hooks.ticking.TickHandler;
import appeng.items.tools.MemoryCardItem;
import appeng.util.IDebugExportable;
import appeng.util.JsonStreamUtil;
import appeng.util.SettingsFrom;
import appeng.util.helpers.ItemComparisonHelper;

public class AEBaseBlockEntity extends BlockEntity
        implements Nameable, ISegmentedInventory, Clearable, IDebugExportable {
    private static final Logger LOG = LoggerFactory.getLogger(AEBaseBlockEntity.class);

    private static final Map<BlockEntityType<?>, Item> REPRESENTATIVE_ITEMS = new HashMap<>();
    @Nullable
    private Component customName;
    private boolean setChangedQueued = false;
    /**
     * For diagnosing issues with the delayed block entity initialization, this tracks how often this BE has been queued
     * for defered initializiation using {@link GridHelper#onFirstTick}.
     */
    private byte queuedForReady = 0;
    /**
     * Tracks how often {@link #onReady()} has been called. This should always be less than {@link #queuedForReady}, and
     * subsequently be equal.
     */
    private byte readyInvoked = 0;

    public AEBaseBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    public static void registerBlockEntityItem(BlockEntityType<?> type, Item wat) {
        REPRESENTATIVE_ITEMS.put(type, wat);
    }

    public boolean notLoaded() {
        return !this.level.hasChunkAt(this.worldPosition);
    }

    public final GlobalPos getGlobalPos() {
        if (level == null) {
            throw new IllegalStateException("Block entity is not in a level");
        }
        return GlobalPos.of(level.dimension(), getBlockPos());
    }

    public BlockEntity getBlockEntity() {
        return this;
    }

    protected Item getItemFromBlockEntity() {
        return REPRESENTATIVE_ITEMS.getOrDefault(getType(), Items.AIR);
    }

    @Override
    public final void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // On the client, this can either be data received as part of an initial chunk update,
        // or as part of a sole block entity data update.
        RegistryAccess registryAccess = null;
        if (registries instanceof RegistryAccess) {
            registryAccess = (RegistryAccess) registries;
        } else if (level != null) {
            registryAccess = level.registryAccess();
        }
        if (tag.contains("#upd", Tag.TAG_BYTE_ARRAY) && tag.size() == 1) {
            var updateData = tag.getByteArray("#upd");
            if (registryAccess == null) {
                LOG.warn("Ignoring  update packet for {} since no registry is available.", this);
            } else if (readUpdateData(
                    new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(updateData), registryAccess))) {
                // Triggers a chunk re-render if the level is already loaded
                if (level != null) {
                    requestModelDataUpdate();
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 0);
                }
            }
            return;
        }

        // Load visual client-side data (used by PonderJS)
        if (tag.contains("visual", Tag.TAG_COMPOUND)) {
            loadVisualState(tag.getCompound("visual"));
        }

        super.loadAdditional(tag, registries);
        loadTag(tag, registries);
    }

    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        if (data.contains("customName")) {
            this.customName = Component.literal(data.getString("customName"));
        } else {
            this.customName = null;
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        // Save visual state first, so that it can never overwrite normal state
        if (VisualStateSaving.isEnabled(level)) {
            var visualTag = new CompoundTag();
            saveVisualState(visualTag);
            data.put("visual", visualTag);
        }

        super.saveAdditional(data, registries);

        if (this.customName != null) {
            data.putString("customName", this.customName.getString());
        }
    }

    /**
     * Deferred initialization when block entities actually start first ticking in a chunk. The block entity needs to
     * override {@link #clearRemoved()} and call {@link #scheduleInit()} to make this work.
     */
    @MustBeInvokedByOverriders
    public void onReady() {
        readyInvoked++;
    }

    protected void scheduleInit() {
        queuedForReady++;
        GridHelper.onFirstTick(this, AEBaseBlockEntity::onReady);
    }

    /**
     * This builds a tag with the actual data that should be sent to the client for update syncs. If the block entity
     * doesn't need update syncs, it returns null.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var data = new CompoundTag();

        var stream = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        this.writeToStream(stream);

        stream.capacity(stream.readableBytes());
        data.putByteArray("#upd", stream.array());
        return data;
    }

    private boolean readUpdateData(RegistryFriendlyByteBuf stream) {
        boolean output = false;

        try {
            output = this.readFromStream(stream);
        } catch (Throwable t) {
            AELog.warn(t);
        }

        return output;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        return false;
    }

    protected void writeToStream(RegistryFriendlyByteBuf data) {
    }

    /**
     * Used to store the state that is synchronized to clients for the visual appearance of this part as NBT. This is
     * only used to store this state for tools such as Create Ponders in Structure NBT. Actual synchronization uses
     * {@link #writeToStream(RegistryFriendlyByteBuf)} and {@link #readFromStream(RegistryFriendlyByteBuf)}. Any data
     * that is saved to the NBT tag in {@link #saveAdditional(CompoundTag, HolderLookup.Provider)} does not need to be
     * saved here again.
     * <p>
     * The data saved should be equivalent to the data sent to the client in {@link #writeToStream}.
     */
    @MustBeInvokedByOverriders
    protected void saveVisualState(CompoundTag data) {
    }

    /**
     * @see #saveVisualState(CompoundTag)
     */
    @MustBeInvokedByOverriders
    protected void loadVisualState(CompoundTag data) {
    }

    /**
     * Mark this block to be updated for clients.
     */
    public void markForClientUpdate() {
        this.requestModelDataUpdate();

        if (this.level != null && !this.isRemoved() && !notLoaded()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void markForUpdate() {
        // Clearing the cached model-data is always harmless regardless of status
        this.requestModelDataUpdate();

        // TODO: Optimize Network Load
        if (this.level != null && !this.isRemoved() && !notLoaded()) {

            boolean alreadyUpdated = false;
            // Let the block update its own state with our internal state changes
            BlockState currentState = getBlockState();
            if (currentState.getBlock() instanceof AEBaseEntityBlock<?> block) {
                BlockState newState = block.getBlockEntityBlockState(currentState, this);
                if (currentState != newState) {
                    AELog.blockUpdate(this.worldPosition, currentState, newState, this);
                    this.level.setBlockAndUpdate(worldPosition, newState);
                    alreadyUpdated = true;
                }
            }

            if (!alreadyUpdated) {
                this.level.sendBlockUpdated(this.worldPosition, currentState, currentState, Block.UPDATE_NEIGHBORS);
            }
        }
    }

    public final BlockOrientation getOrientation() {
        return BlockOrientation.get(getBlockState());
    }

    public Direction getFront() {
        return getOrientation().getSide(RelativeSide.FRONT);
    }

    public Direction getTop() {
        return getOrientation().getSide(RelativeSide.TOP);
    }

    /**
     * Called when the orientation of the block the block-entity is attached to is changed.
     */
    @ApiStatus.OverrideOnly
    protected void onOrientationChanged(BlockOrientation orientation) {
        invalidateCapabilities();
    }

    public final DataComponentMap exportSettings(SettingsFrom mode, @Nullable Player player) {
        var builder = DataComponentMap.builder();
        exportSettings(mode, builder, player);
        return builder.build();
    }

    /**
     * @param mode    source of settings
     * @param builder
     * @param player  The (optional) player, who is exporting the settings
     */
    @MustBeInvokedByOverriders
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            builder.set(DataComponents.CUSTOM_NAME, this.customName);
        } else if (mode == SettingsFrom.MEMORY_CARD) {
            builder.set(AEComponents.EXPORTED_CUSTOM_NAME, this.customName);
        }

        if (mode == SettingsFrom.MEMORY_CARD) {
            MemoryCardItem.exportGenericSettings(this, builder);
            builder.set(AEComponents.EXPORTED_SETTINGS_SOURCE, getItemFromBlockEntity().getDescription());
        }
    }

    /**
     * Depending on the mode, different settings will be accepted.
     *
     * @param input  source of settings
     * @param player The (optional) player, who is importing the settings
     */
    @MustBeInvokedByOverriders
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            this.customName = input.get(DataComponents.CUSTOM_NAME);
        } else if (mode == SettingsFrom.MEMORY_CARD) {
            this.customName = input.get(AEComponents.EXPORTED_CUSTOM_NAME);
        }

        MemoryCardItem.importGenericSettings(this, input, player);
    }

    /**
     * returns the contents of the block entity but not the block itself, to drop into the world.
     * <p>
     * Ensure you also clear the inventories that contribute to additional drops in {@link #clearContent()} when you
     * override this method.
     *
     * @param level level
     * @param pos   block position
     * @param drops drops of block entity
     */
    @MustBeInvokedByOverriders
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
    }

    /**
     * Clears the contents of this block-entity, which would otherwise be dropped by {@link #addAdditionalDrops}.
     */
    @Override
    @MustBeInvokedByOverriders
    public void clearContent() {
    }

    @Override
    public Component getName() {
        return Objects.requireNonNullElse(this.customName, getItemFromBlockEntity().getDescription());
    }

    @Override
    @Nullable
    public Component getCustomName() {
        return this.customName;
    }

    /**
     * Checks if this block entity is remote (we are running on the logical client side).
     */
    public boolean isClientSide() {
        Level level = getLevel();
        return level == null || level.isClientSide();
    }

    public void saveChanges() {
        if (this.level == null) {
            return;
        }

        // Clientside is marked immediately as dirty as there is no queue processing
        // Serverside is only queued once per tick to avoid costly operations
        // TODO: Evaluate if this is still necessary
        if (this.level.isClientSide) {
            this.setChanged();
        } else {
            this.level.blockEntityChanged(this.worldPosition);
            if (!this.setChangedQueued) {
                TickHandler.instance().addCallable(null, this::setChangedAtEndOfTick);
                this.setChangedQueued = true;
            }
        }
    }

    private Object setChangedAtEndOfTick(Level level) {
        this.setChanged();
        this.setChangedQueued = false;
        return null;
    }

    public void setName(String name) {
        this.customName = Component.literal(name);
    }

    @Override
    @Nullable
    @MustBeInvokedByOverriders
    public InternalInventory getSubInventory(ResourceLocation id) {
        return null;
    }

    @Override
    public ModelData getModelData() {
        return AEModelData.create();
    }

    /**
     * Called when a player uses a wrench on this block entity to disassemble it.
     */
    public InteractionResult disassembleWithWrench(Player player, Level level, BlockHitResult hitResult,
            ItemStack wrench) {
        var pos = hitResult.getBlockPos();
        var state = level.getBlockState(pos);
        var block = state.getBlock();

        if (level instanceof ServerLevel serverLevel) {
            // Drops of the block itself (without extra block entity inventory)
            var drops = Block.getDrops(state, serverLevel, pos, this, player, wrench);

            var op = new ItemStack(state.getBlock());
            for (var ol : drops) {
                if (ItemComparisonHelper.isEqualItemType(ol, op)) {
                    var settings = exportSettings(SettingsFrom.DISMANTLE_ITEM, player);
                    ol.applyComponents(settings);
                    break;
                }
            }

            // Add and remove extra block entity inventory
            addAdditionalDrops(level, pos, drops);
            clearContent();

            for (var item : drops) {
                player.getInventory().placeItemBackInInventory(item);
            }
        }

        block.playerWillDestroy(level, pos, state, player);
        level.removeBlock(pos, false);
        block.destroy(level, pos, getBlockState());

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public byte getQueuedForReady() {
        return queuedForReady;
    }

    public byte getReadyInvoked() {
        return readyInvoked;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBlockState(BlockState state) {
        var previousOrientation = BlockOrientation.get(getBlockState());

        super.setBlockState(state);

        // This method is called when the blockstate of an existing block-entity is changed
        // We use this to detect a change to rotation
        var newOrientation = BlockOrientation.get(getBlockState());
        if (previousOrientation != newOrientation) {
            onOrientationChanged(newOrientation);
        }
    }

    @Override
    public void debugExport(JsonWriter writer, HolderLookup.Provider registries, Reference2IntMap<Object> machineIds,
            Reference2IntMap<IGridNode> nodeIds)
            throws IOException {
        var data = new CompoundTag();
        saveAdditional(data, registries);

        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonStreamUtil.writeProperties(Map.of(
                "blockState", BlockState.CODEC.encodeStart(ops, getBlockState()).getOrThrow(),
                "level", level.dimension().location().toString(),
                "pos", getBlockPos(),
                "data", CompoundTag.CODEC.encodeStart(ops, data).getOrThrow()), writer);
    }
}
