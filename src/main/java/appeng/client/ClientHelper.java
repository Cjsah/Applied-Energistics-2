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

package appeng.client;


import java.io.IOException;
import java.util.*;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import appeng.api.parts.CableRenderMode;
import appeng.api.util.AEColor;
import appeng.block.AEBaseBlock;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.helpers.IMouseWheelItem;
import appeng.server.ServerHelper;
import appeng.util.Platform;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


public class ClientHelper extends ServerHelper
{
	private final static String KEY_CATEGORY = "key.appliedenergistics2.category";

	private final EnumMap<ActionKey, KeyBinding> bindings = new EnumMap<>( ActionKey.class );

	@Override
	public void preinit()
	{
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientInit);
		MinecraftForge.EVENT_BUS.addListener(this::postPlayerRender);
		MinecraftForge.EVENT_BUS.addListener(this::wheelEvent);
		// Do not register the Fullbright hacks if Optifine is present or if the Forge lighting is disabled
		// FIXME if( !FMLClientHandler.instance().hasOptifine() && ForgeModContainer.forgeLightPipelineEnabled )
		// FIXME {
		// FIXME 	ModelLoaderRegistry.registerLoader( UVLModelLoader.INSTANCE );
		// FIXME }

		// FIXME RenderingRegistry.registerEntityRenderingHandler( EntityTinyTNTPrimed.class, manager -> new RenderTinyTNTPrimed( manager ) );
		// FIXME RenderingRegistry.registerEntityRenderingHandler( EntityFloatingItem.class, manager -> new RenderFloatingItem( manager ) );
	}

	private void clientInit(FMLClientSetupEvent event)
	{
		for( ActionKey key : ActionKey.values() )
		{
			final KeyBinding binding = new KeyBinding( key.getTranslationKey(), key.getDefaultKey(), KEY_CATEGORY );
			ClientRegistry.registerKeyBinding( binding );
			this.bindings.put( key, binding );
		}
	}

	@Override
	public World getWorld()
	{
		if( Platform.isClient() )
		{
			return Minecraft.getInstance().world;
		}
		else
		{
			return super.getWorld();
		}
	}

	@Override
	public void bindTileEntitySpecialRenderer( final Class<? extends TileEntity> tile, final AEBaseBlock blk )
	{

	}

	@Override
	public List<? extends PlayerEntity> getPlayers()
	{
		if( Platform.isClient() )
		{
			return Collections.singletonList(Minecraft.getInstance().player);
		}
		else
		{
			return super.getPlayers();
		}
	}

	@Override
	public void spawnEffect( final EffectType effect, final World world, final double posX, final double posY, final double posZ, final Object o )
	{
		// FIXME if( AEConfig.instance().isEnableEffects() )
		// FIXME {
		// FIXME 	switch( effect )
		// FIXME 	{
		// FIXME 		case Assembler:
		// FIXME 			this.spawnAssembler( world, posX, posY, posZ, o );
		// FIXME 			return;
		// FIXME 		case Vibrant:
		// FIXME 			this.spawnVibrant( world, posX, posY, posZ );
		// FIXME 			return;
		// FIXME 		case Crafting:
		// FIXME 			this.spawnCrafting( world, posX, posY, posZ );
		// FIXME 			return;
		// FIXME 		case Energy:
		// FIXME 			this.spawnEnergy( world, posX, posY, posZ );
		// FIXME 			return;
		// FIXME 		case Lightning:
		// FIXME 			this.spawnLightning( world, posX, posY, posZ );
		// FIXME 			return;
		// FIXME 		case LightningArc:
		// FIXME 			this.spawnLightningArc( world, posX, posY, posZ, (Vec3d) o );
		// FIXME 			return;
		// FIXME 		default:
		// FIXME 	}
		// FIXME }
	}

	@Override
	public boolean shouldAddParticles( final Random r )
	{
		switch (Minecraft.getInstance().gameSettings.particles) {
			default:
			case ALL:
				return true;
			case DECREASED:
				return r.nextBoolean();
			case MINIMAL:
				return false;
		}
	}

	@Override
	public RayTraceResult getRTR()
	{
		return Minecraft.getInstance().objectMouseOver;
	}

	@Override
	public void postInit()
	{
	}

	@Override
	public CableRenderMode getRenderMode()
	{
		if( Platform.isServer() )
		{
			return super.getRenderMode();
		}

		final Minecraft mc = Minecraft.getInstance();
		final PlayerEntity player = mc.player;

		return this.renderModeForPlayer( player );
	}

	@Override
	public void triggerUpdates()
	{
		final Minecraft mc = Minecraft.getInstance();
		if( mc.player == null || mc.world == null )
		{
			return;
		}

		final PlayerEntity player = mc.player;

		final int x = (int) player.getPosX();
		final int y = (int) player.getPosY();
		final int z = (int) player.getPosZ();

		final int range = 16 * 16;

		mc.worldRenderer.markBlockRangeForRenderUpdate( x - range, y - range, z - range, x + range, y + range, z + range );
	}

	private void postPlayerRender( final RenderLivingEvent.Pre p )
	{
//	FIXME	final PlayerColor player = TickHandler.INSTANCE.getPlayerColors().get( p.getEntity().getEntityId() );
//	FIXME	if( player != null )
//	FIXME	{
//	FIXME		final AEColor col = player.myColor;
//	FIXME		final float r = 0xff & ( col.mediumVariant >> 16 );
//	FIXME		final float g = 0xff & ( col.mediumVariant >> 8 );
//	FIXME		final float b = 0xff & ( col.mediumVariant );
//	FIXME		// FIXME: This is most certainly not going to work!
//	FIXME		GlStateManager.color4f( r / 255.0f, g / 255.0f, b / 255.0f, 1.0f );
//	FIXME	}
	}

	private void spawnAssembler( final World world, final double posX, final double posY, final double posZ, final Object o )
	{
		// FIXME final PacketAssemblerAnimation paa = (PacketAssemblerAnimation) o;

		// FIXME final AssemblerFX fx = new AssemblerFX( world, posX, posY, posZ, 0.0D, 0.0D, 0.0D, paa.rate, paa.is );
		// FIXME Minecraft.getInstance().particles.addEffect( fx );
	}

	private void spawnVibrant( final World w, final double x, final double y, final double z )
	{
		if( AppEng.proxy.shouldAddParticles( Platform.getRandom() ) )
		{
			final double d0 = ( Platform.getRandomFloat() - 0.5F ) * 0.26D;
			final double d1 = ( Platform.getRandomFloat() - 0.5F ) * 0.26D;
			final double d2 = ( Platform.getRandomFloat() - 0.5F ) * 0.26D;

			// FIXME final VibrantFX fx = new VibrantFX( w, x + d0, y + d1, z + d2, 0.0D, 0.0D, 0.0D );
			// FIXME Minecraft.getInstance().particles.addEffect( fx );
		}
	}

	private void spawnCrafting( final World w, final double posX, final double posY, final double posZ )
	{
		final float x = (float) ( ( ( Platform.getRandomInt() % 100 ) * 0.01 ) - 0.5 ) * 0.7f;
		final float y = (float) ( ( ( Platform.getRandomInt() % 100 ) * 0.01 ) - 0.5 ) * 0.7f;
		final float z = (float) ( ( ( Platform.getRandomInt() % 100 ) * 0.01 ) - 0.5 ) * 0.7f;

		// FIXME final CraftingFx fx = new CraftingFx( w, posX + x, posY + y, posZ + z, Items.DIAMOND );

		// FIXME fx.setMotionX( -x * 0.2f );
		// FIXME fx.setMotionY( -y * 0.2f );
		// FIXME fx.setMotionZ( -z * 0.2f );

		// FIXME Minecraft.getInstance().particles.addEffect( fx );
	}

	private void spawnEnergy( final World w, final double posX, final double posY, final double posZ )
	{
		final float x = (float) ( ( ( Platform.getRandomInt() % 100 ) * 0.01 ) - 0.5 ) * 0.7f;
		final float y = (float) ( ( ( Platform.getRandomInt() % 100 ) * 0.01 ) - 0.5 ) * 0.7f;
		final float z = (float) ( ( ( Platform.getRandomInt() % 100 ) * 0.01 ) - 0.5 ) * 0.7f;

		// FIXME final EnergyFx fx = new EnergyFx( w, posX + x, posY + y, posZ + z, Items.DIAMOND );

		// FIXME fx.setMotionX( -x * 0.1f );
		// FIXME fx.setMotionY( -y * 0.1f );
		// FIXME fx.setMotionZ( -z * 0.1f );

		// FIXME Minecraft.getInstance().particles.addEffect( fx );
	}

	private void spawnLightning( final World world, final double posX, final double posY, final double posZ )
	{
		// FIXME final LightningFX fx = new LightningFX( world, posX, posY + 0.3f, posZ, 0.0f, 0.0f, 0.0f );
		// FIXME Minecraft.getInstance().particles.addEffect( fx );
	}

	private void spawnLightningArc( final World world, final double posX, final double posY, final double posZ, final Vec3d second )
	{
		// FIXME final LightningFX fx = new LightningArcFX( world, posX, posY, posZ, second.x, second.y, second.z, 0.0f, 0.0f, 0.0f );
		// FIXME Minecraft.getInstance().particles.addEffect( fx );
	}

	private void wheelEvent( final InputEvent.MouseScrollEvent me )
	{
		if( me.getScrollDelta() == 0 )
		{
			return;
		}

		final Minecraft mc = Minecraft.getInstance();
		final PlayerEntity player = mc.player;
		if( player.isCrouching() )
		{
			final boolean mainHand = player.getHeldItem( Hand.MAIN_HAND ).getItem() instanceof IMouseWheelItem;
			final boolean offHand = player.getHeldItem( Hand.OFF_HAND ).getItem() instanceof IMouseWheelItem;

			if( mainHand || offHand )
			{
//	FIXME			try
//	FIXME			{
//	FIXME				NetworkHandler.instance().sendToServer( new PacketValueConfig( "Item", me.getScrollDelta() > 0 ? "WheelUp" : "WheelDown" ) );
//	FIXME				me.setCanceled( true );
//	FIXME			}
//	FIXME			catch( final IOException e )
//	FIXME			{
//	FIXME				AELog.debug( e );
//	FIXME			}
			}
		}
	}

	@SubscribeEvent
	public void onTextureStitch( final TextureStitchEvent.Pre event )
	{
//		 FIXME ParticleTextures.registerSprite( event );
//		 FIXME InscriberTESR.registerTexture( event );
	}

	@Override
	public boolean isActionKey( ActionKey key, InputMappings.Input pressedKey )
	{
		return this.bindings.get( key ).isActiveAndMatches( pressedKey );
	}
}