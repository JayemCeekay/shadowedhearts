package com.jayemceekay.shadowedhearts.content.items;

import com.jayemceekay.shadowedhearts.common.aura.AuraReaderCharge;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Require SHIFT + right-click (from main hand) to equip
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            boolean mainHand = hand == InteractionHand.MAIN_HAND;
            if (player.isShiftKeyDown() && mainHand) {
                // Attempt to equip to the vanilla HEAD slot (Accessories integration will also respect this)
                var slot = getEquipmentSlot();
                ItemStack currentlyEquipped = player.getItemBySlot(slot);
                if (currentlyEquipped.isEmpty()) {
                    // Move one item into the head slot
                    player.setItemSlot(slot, stack.copyWithCount(1));
                    stack.shrink(1);
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else {
                    // Already wearing something in head; fail to prevent accidental swaps
                    return InteractionResultHolder.fail(stack);
                }
            } else {
                // Upgrades screen temporarily disabled
                return InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int charge = AuraReaderCharge.get(stack);
        tooltip.add(Component.translatable("item.shadowedhearts.aura_reader.tooltip"));
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().expandedAuraReaderEnabled()) {
            tooltip.add(Component.translatable("tooltip.shadowedhearts.aura_reader.charge", charge, MAX_CHARGE));
        }
        super.appendHoverText(stack, context, tooltip, type);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
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
