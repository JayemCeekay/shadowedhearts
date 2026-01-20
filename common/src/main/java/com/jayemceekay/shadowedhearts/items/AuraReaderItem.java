package com.jayemceekay.shadowedhearts.items;

import com.jayemceekay.shadowedhearts.aura.AuraReaderCharge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class AuraReaderItem extends Item implements Equipable {
    public static final int MAX_CHARGE = 12000; // 10 minutes at 20/sec? Or maybe less. Let's say 12000 ticks = 10 mins.

    public AuraReaderItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) {
            AuraReaderCharge.ensureInitialized(stack, MAX_CHARGE);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int charge = AuraReaderCharge.get(stack);
        tooltip.add(Component.translatable("item.shadowedhearts.aura_reader.tooltip"));
        tooltip.add(Component.translatable("tooltip.shadowedhearts.aura_reader.charge", charge, MAX_CHARGE));
        super.appendHoverText(stack, context, tooltip, type);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * (float) AuraReaderCharge.get(stack) / (float) MAX_CHARGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x00FFFF; // Cyan
    }
}
