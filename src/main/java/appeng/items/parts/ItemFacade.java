/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.items.parts;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.AEApi;
import appeng.api.exceptions.MissingDefinitionException;
import appeng.api.parts.IAlphaPassItem;
import appeng.api.util.AEPartLocation;
import appeng.core.AELog;
import appeng.core.FacadeConfig;
import appeng.facade.FacadePart;
import appeng.facade.IFacadeItem;
import appeng.items.AEBaseItem;

public class ItemFacade extends AEBaseItem implements IFacadeItem, IAlphaPassItem {

    private static final String TAG_ITEM_ID = "item";

    private List<ItemStack> subTypes = null;

    public ItemFacade(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context) {
        return AEApi.instance().partHelper().placeBus(stack, context.getPos(), context.getFace(), context.getPlayer(),
                context.getHand(), context.getWorld());
    }

    @Override
    public ITextComponent getDisplayName(ItemStack is) {
        try {
            final ItemStack in = this.getTextureItem(is);
            if (!in.isEmpty()) {
                return super.getDisplayName(is).deepCopy().appendText(" - ").appendSibling(in.getDisplayName());
            }
        } catch (final Throwable ignored) {

        }

        return super.getDisplayName(is);
    }

    @Override
    public void fillItemGroup(ItemGroup group, NonNullList<ItemStack> items) {
        this.calculateSubTypes();
        items.addAll(this.subTypes);
    }

    private void calculateSubTypes() {
        if (this.subTypes == null) {
            this.subTypes = new ArrayList<>(1000);
            for (final Block b : ForgeRegistries.BLOCKS) {
                try {
                    final Item item = Item.getItemFromBlock(b);
                    if (item == Items.AIR) {
                        continue;
                    }

                    Item blockItem = b.asItem();
                    if (blockItem != Items.AIR && blockItem.getGroup() != null) {
                        final NonNullList<ItemStack> tmpList = NonNullList.create();
                        b.fillItemGroup(blockItem.getGroup(), tmpList);
                        for (final ItemStack l : tmpList) {
                            final ItemStack facade = this.createFacadeForItem(l, false);
                            if (!facade.isEmpty()) {
                                this.subTypes.add(facade);
                            }
                        }
                    }
                } catch (final Throwable t) {
                    // just absorb..
                }
            }
        }
    }

    public ItemStack createFacadeForItem(final ItemStack itemStack, final boolean returnItem) {
        if (itemStack.isEmpty() || itemStack.hasTag() || !(itemStack.getItem() instanceof BlockItem)) {
            return ItemStack.EMPTY;
        }

        BlockItem blockItem = (BlockItem) itemStack.getItem();
        Block block = blockItem.getBlock();
        if (block == Blocks.AIR) {
            return ItemStack.EMPTY;
        }

        // We only support the default state for facades. Sorry.
        BlockState blockState = block.getDefaultState();

        final boolean areTileEntitiesEnabled = false; // FacadeConfig.instance().allowTileEntityFacades();
        final boolean isWhiteListed = true; // FIXME FacadeConfig.instance().isWhiteListed(block);
        final boolean isModel = blockState.getRenderType() == BlockRenderType.MODEL;

        final BlockState defaultState = block.getDefaultState();
        final boolean isTileEntity = block.hasTileEntity(defaultState);
        final boolean isFullCube = true; // FIXME defaultState.isFullCube( defaultState );

        final boolean isTileEntityAllowed = !isTileEntity || (areTileEntitiesEnabled && isWhiteListed);
        final boolean isBlockAllowed = isFullCube || isWhiteListed;

        if (isModel && isTileEntityAllowed && isBlockAllowed) {
            if (returnItem) {
                return itemStack;
            }

            final ItemStack is = new ItemStack(this);
            final CompoundNBT data = new CompoundNBT();
            data.putString(TAG_ITEM_ID, itemStack.getItem().getRegistryName().toString());
            is.setTag(data);
            return is;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public FacadePart createPartFromItemStack(final ItemStack is, final AEPartLocation side) {
        final ItemStack in = this.getTextureItem(is);
        if (!in.isEmpty()) {
            return new FacadePart(is, side);
        }
        return null;
    }

    @Override
    public ItemStack getTextureItem(ItemStack is) {

        CompoundNBT nbt = is.getTag();

        if (nbt == null) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId;

        // Handle legacy facades
        if (nbt.contains("x")) {
            int[] data = nbt.getIntArray("x");
            if (data.length != 2) {
                return ItemStack.EMPTY;
            }

            Item item = Registry.ITEM.getByValue(data[0]);
            if (item == null) {
                return ItemStack.EMPTY;
            }

            itemId = item.getRegistryName();
        } else {
            // First item is numeric item id
            itemId = new ResourceLocation(nbt.getString(TAG_ITEM_ID));
        }

        Item baseItem = ForgeRegistries.ITEMS.getValue(itemId);

        if (baseItem == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(baseItem, 1);
    }

    @Override
    public BlockState getTextureBlockState(ItemStack is) {

        ItemStack baseItemStack = this.getTextureItem(is);

        if (baseItemStack.isEmpty()) {
            return Blocks.GLASS.getDefaultState();
        }

        Block block = Block.getBlockFromItem(baseItemStack.getItem());

        if (block == Blocks.AIR) {
            return Blocks.GLASS.getDefaultState();
        }

        return block.getDefaultState();
    }

    public List<ItemStack> getFacades() {
        this.calculateSubTypes();
        return this.subTypes;
    }

    public ItemStack getCreativeTabIcon() {
        this.calculateSubTypes();
        if (this.subTypes.isEmpty()) {
            return new ItemStack(Items.CAKE);
        }
        return this.subTypes.get(0);
    }

    public ItemStack createFromID(final int id) {
        ItemStack facadeStack = AEApi.instance().definitions().items().facade().maybeStack(1).orElseThrow(
                () -> new MissingDefinitionException("Tried to create a facade, while facades are being deactivated."));

        // Convert back to a registry name...
        Item item = Registry.ITEM.getByValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        final CompoundNBT facadeTag = new CompoundNBT();
        facadeTag.putString(TAG_ITEM_ID, item.getRegistryName().toString());
        facadeStack.setTag(facadeTag);

        return facadeStack;
    }

    @Override
    public boolean useAlphaPass(final ItemStack is) {
        BlockState blockState = this.getTextureBlockState(is);

        if (blockState == null) {
            return false;
        }

        return RenderTypeLookup.canRenderInLayer(blockState, RenderType.getTranslucent())
                || RenderTypeLookup.canRenderInLayer(blockState, RenderType.getTranslucentNoCrumbling());
    }
}
