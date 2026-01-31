package com.jayemceekay.shadowedhearts.items;

import com.jayemceekay.shadowedhearts.core.ModItemComponents;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.snag.PlayerSnagData;
import com.jayemceekay.shadowedhearts.snag.SnagBattleUtil;
import com.jayemceekay.shadowedhearts.snag.SnagCaps;
import com.jayemceekay.shadowedhearts.snag.SnagEnergy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Unique wearable/held item that toggles an "armed" state for snagging.
 */
public class SnagMachineItem extends Item implements Equipable {
    private final IntSupplier capacity;

    public SnagMachineItem(Properties p, IntSupplier capacity) {
        super(p.stacksTo(1));
        this.capacity = capacity;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.OFFHAND;
    }

    public String getAttributePath() {
        return "hand";
    }

    public String getDefaultSlot() {
        return "hand";
    }

    public int capacity() { return capacity.getAsInt(); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        
        // If we are on client and Accessories mod might want to handle this, 
        // we might want to return pass to allow other things to handle it.
        // But Equipable usually handles it if we return success/consume.
        
        if (level.isClientSide) return InteractionResultHolder.success(held);
        // Initialize energy store
        SnagEnergy.ensureInitialized(held, capacity.getAsInt());
        
        // Check if it's already equipped in an accessory slot or offhand.
        // If not, let the Equipable system (or Accessories mod) handle it.
        if (!isEquipped(player, held)) {
             // Prevent equipping to offhand if already equipped in accessory slot
             if (SnagAccessoryBridgeHolder.INSTANCE.isEquipped(player)) {
                 return InteractionResultHolder.fail(held);
             }

             // In 1.21, Equipable items might need to return pass to allow the right-click to equip them
             // if they don't have a specific use action.
             // However, SnagMachineItem HAS a use action (arming in battle).
             if (!SnagBattleUtil.isInTrainerBattle(player)) {
                 return InteractionResultHolder.pass(held);
             }
        }

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

    private boolean isEquipped(Player player, ItemStack stack) {
        return player.getOffhandItem() == stack || player.getMainHandItem() == stack || SnagAccessoryBridgeHolder.INSTANCE.getEquippedStack(player) == stack;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, Level level, @NotNull net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        // ensure energy component exists
        if (!level.isClientSide) {
            SnagEnergy.ensureInitialized(stack, capacity.getAsInt());
            if (!stack.has(ModItemComponents.SNAG_FAIL_ATTEMPTS.get())) {
                stack.set(ModItemComponents.SNAG_FAIL_ATTEMPTS.get(), 0);
            }
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int cur = SnagEnergy.get(stack);
        tooltip.add(Component.translatable("tooltip.shadowedhearts.snag_machine.energy", cur, capacity.getAsInt()));
        tooltip.add(Component.translatable("tooltip.shadowedhearts.snag_machine.equippable.hand").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }


}
