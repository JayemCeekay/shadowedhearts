package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.item.interactive.HyperTrainingItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowHyperTraining;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve native IV bounds and consumption while rejecting ineligible Shadow targets first. */
@Mixin(value = HyperTrainingItem.class, remap = false)
public abstract class MixinShadowHyperTrainingItem {
    @Inject(method = "canUseOnPokemon", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$filterHyperTraining(ItemStack stack, Pokemon pokemon,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (ShadowHyperTraining.blocks(pokemon)) cir.setReturnValue(false);
    }

    @Inject(method = "applyToPokemon", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$rejectHyperTraining(ServerPlayer player, ItemStack stack, Pokemon pokemon,
                                                   CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (ShadowHyperTraining.reject(player, pokemon)) cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
}
