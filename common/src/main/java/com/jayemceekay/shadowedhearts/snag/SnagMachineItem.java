package com.jayemceekay.shadowedhearts.snag;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
/**
 * Unique wearable/held item that toggles an "armed" state for snagging.
 */
public class SnagMachineItem extends Item {
    private final int capacity;
    private final int costOnUse;

    public SnagMachineItem(Properties p, int capacity, int costOnUse) {
        super(p.stacksTo(1));
        this.capacity = Math.max(0, capacity);
        this.costOnUse = Math.max(0, costOnUse);
    }

    public String getAttributePath() {
        return "hand";
    }

    public String getDefaultSlot() {
        return "hand";
    }

    public int capacity() { return capacity; }
    public int costOnUse() { return costOnUse; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(held);
        // Initialize energy store
        SnagEnergy.ensureInitialized(held, capacity);
        PlayerSnagData cap = SnagCaps.get(player);
        if (!cap.hasSnagMachine()) return InteractionResultHolder.fail(held);
        if (cap.cooldown() > 0) return InteractionResultHolder.fail(held);
        // Gate: only usable in trainer battles; arming is handled via C2S packet and energy is consumed server-side there.
        if (!SnagBattleUtil.isInTrainerBattle(player)) {
            return InteractionResultHolder.fail(held);
        }
        // No toggle here; UI/action should send SnagArmC2S. Item use is a no-op success in battle to avoid desync.
        return InteractionResultHolder.sidedSuccess(held, false);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, Level level, @NotNull net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        // ensure energy component exists
        if (!level.isClientSide) {
            SnagEnergy.ensureInitialized(stack, capacity);
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int cur = SnagEnergy.get(stack);
        tooltip.add(Component.translatable("tooltip.shadowedhearts.snag_machine.energy", cur, capacity));
        tooltip.add(Component.translatable("tooltip.shadowedhearts.snag_machine.equippable.hand").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }


}
