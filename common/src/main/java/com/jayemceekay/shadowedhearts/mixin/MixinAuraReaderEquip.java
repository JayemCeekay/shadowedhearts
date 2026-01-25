package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MixinAuraReaderEquip {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$preventAuraReaderEquip(net.minecraft.world.level.Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.is(ModItems.AURA_READER.get())) {
            if (SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(player)) {
                // If it is already equipped in an accessory slot, check if it's NOT the head slot.
                // SnagAccessoryBridge.isAuraReaderEquipped checks both.
                // We want to prevent equipping if it's ALREADY in an accessory slot.
                // But wait, if it's in the head slot, isAuraReaderEquipped returns true.
                // We need to check if it's in the ACCESSORY slots specifically.
                
                // Let's use a more specific check.
                if (isEquippedInAccessorySlot(player)) {
                    cir.setReturnValue(InteractionResultHolder.fail(stack));
                }
            }
        }
    }

    private boolean isEquippedInAccessorySlot(Player player) {
        // We can check the bridge for accessory-only check or just reimplement here if needed, 
        // but better to add a method to the bridge.
        return SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(player) && 
               !player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ModItems.AURA_READER.get());
    }
}
