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

package appeng.items.tools.powered.powersink;


import java.text.MessageFormat;
import java.util.List;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.core.localization.GuiText;
import appeng.items.AEBaseItem;
import appeng.util.Platform;


public abstract class AEBasePoweredItem extends AEBaseItem implements IAEItemPowerStorage
{
	private static final String CURRENT_POWER_NBT_KEY = "internalCurrentPower";
	private static final String MAX_POWER_NBT_KEY = "internalMaxPower";
	private final double powerCapacity;

	public AEBasePoweredItem( final double powerCapacity )
	{
		this.setMaxStackSize( 1 );
		this.setMaxDamage( 32 );
		this.hasSubtypes = false;
		this.setFull3D();

		this.powerCapacity = powerCapacity;
	}

	@OnlyIn( Dist.CLIENT )
	@Override
	public void addInformation(final ItemStack stack, final World world, final List<ITextComponent> lines, final ITooltipFlag advancedTooltips )
	{
		final CompoundNBT tag = stack.getTag();
		double internalCurrentPower = 0;
		final double internalMaxPower = this.getAEMaxPower( stack );

		if( tag != null )
		{
			internalCurrentPower = tag.getDouble( CURRENT_POWER_NBT_KEY );
		}

		final double percent = internalCurrentPower / internalMaxPower;

		lines.add( GuiText.StoredEnergy.getLocal() + ':' + MessageFormat.format( " {0,number,#} ", internalCurrentPower ) + Platform
				.gui_localize( PowerUnits.AE.unlocalizedName ) + " - " + MessageFormat.format( " {0,number,#.##%} ", percent ) );
	}

	@Override
	public boolean isDamageable()
	{
		return true;
	}

	@Override
	protected void getCheckedSubItems( final CreativeTabs creativeTab, final NonNullList<ItemStack> itemStacks )
	{
		super.getCheckedSubItems( creativeTab, itemStacks );

		final ItemStack charged = new ItemStack( this, 1 );
        final CompoundNBT tag = charged.getOrCreateTag();
		tag.putDouble(CURRENT_POWER_NBT_KEY, this.getAEMaxPower( charged ));
		tag.putDouble(MAX_POWER_NBT_KEY, this.getAEMaxPower( charged ));

		itemStacks.add( charged );
	}

	@Override
	public boolean isRepairable()
	{
		return false;
	}

	@Override
	public double getDurabilityForDisplay( final ItemStack is )
	{
		return 1 - this.getAECurrentPower( is ) / this.getAEMaxPower( is );
	}

	@Override
	public boolean isDamaged( final ItemStack stack )
	{
		return true;
	}

	@Override
	public void setDamage( final ItemStack stack, final int damage )
	{

	}

	@Override
	public double injectAEPower( final ItemStack is, final double amount, Actionable mode )
	{
		final double maxStorage = this.getAEMaxPower( is );
		final double currentStorage = this.getAECurrentPower( is );
		final double required = maxStorage - currentStorage;
		final double overflow = amount - required;

		if( mode == Actionable.MODULATE )
		{
            final CompoundNBT data = is.getOrCreateTag();
			final double toAdd = Math.min( amount, required );

			data.putDouble(CURRENT_POWER_NBT_KEY, currentStorage + toAdd);
		}

		return Math.max( 0, overflow );
	}

	@Override
	public double extractAEPower( final ItemStack is, final double amount, Actionable mode )
	{
		final double currentStorage = this.getAECurrentPower( is );
		final double fulfillable = Math.min( amount, currentStorage );

		if( mode == Actionable.MODULATE )
		{
            final CompoundNBT data = is.getOrCreateTag();

			data.putDouble(CURRENT_POWER_NBT_KEY, currentStorage - fulfillable);
		}

		return fulfillable;
	}

	@Override
	public double getAEMaxPower( final ItemStack is )
	{
		return this.powerCapacity;
	}

	@Override
	public double getAECurrentPower( final ItemStack is )
	{
        final CompoundNBT data = is.getOrCreateTag();

		return data.getDouble( CURRENT_POWER_NBT_KEY );
	}

	@Override
	public AccessRestriction getPowerFlow( final ItemStack is )
	{
		return AccessRestriction.WRITE;
	}

	@Override
	public ICapabilityProvider initCapabilities( ItemStack stack, CompoundNBT nbt )
	{
		return new PoweredItemCapabilities( stack, this );
	}
}
