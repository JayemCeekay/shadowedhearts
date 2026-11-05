package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.helditem.BaseCobblemonHeldItemManager;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cobblemon's native give path only indexes its own namespace, unlike registerRemap. */
@Mixin(value = BaseCobblemonHeldItemManager.class, remap = false)
public abstract class MixinHeldItemTransfer {
    @Inject(method = "nameOf", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$resolveTransferredItemName(String showdownId, CallbackInfoReturnable<Component> cir) {
        Item item = ModItems.heldItemForShowdownId(showdownId);
        if (item != null) {
            cir.setReturnValue(item.getDescription());
        }
    }

    @Inject(method = "give", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$resolveTransferredItem(BattlePokemon pokemon, String showdownId, CallbackInfo ci) {
        Item item = ModItems.heldItemForShowdownId(showdownId);
        if (item != null) {
            pokemon.getEffectedPokemon().swapHeldItem(new ItemStack(item), false, false);
            ci.cancel();
        }
    }
}
