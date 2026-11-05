package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowModeItemPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also protects direct medicine calls, outside the standard selection callbacks. */
@Mixin(targets = {
        "com.cobblemon.mod.common.item.interactive.PotionItem",
        "com.cobblemon.mod.common.item.interactive.StatusCureItem",
        "com.cobblemon.mod.common.item.interactive.BerryJuiceItem",
        "com.cobblemon.mod.common.item.interactive.ElixirItem",
        "com.cobblemon.mod.common.item.interactive.EnergyRootItem",
        "com.cobblemon.mod.common.item.interactive.HealPowderItem",
        "com.cobblemon.mod.common.item.interactive.RemedyItem",
        "com.cobblemon.mod.common.item.RevivalHerbItem",
        "com.cobblemon.mod.common.item.berry.HealingBerryItem",
        "com.cobblemon.mod.common.item.berry.PortionHealingBerryItem",
        "com.cobblemon.mod.common.item.berry.StatusCuringBerryItem"
}, remap = false)
public abstract class MixinModeNativeMedicine {
    @Inject(method = "applyToPokemon", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$refuse(ServerPlayer player, ItemStack stack, Pokemon pokemon, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (ShadowModeItemPolicy.reject(player, pokemon, stack)) cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
}
