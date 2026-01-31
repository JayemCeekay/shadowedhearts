package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.SHAspects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "us.timinc.mc.cobblemon.timcore.mixin.helper.CancelThrownPokeballInBattleHelper", remap = false)
public abstract class MixinCancelThrownPokeballInBattleHelper {

    @Inject(method = "checkForCancel", at = @At("HEAD"), cancellable = true)
    private static void onCheckForCancel(EmptyPokeBallEntity pokeBallEntity, PokemonEntity pokemonEntity, CallbackInfoReturnable<Boolean> cir) {
        if (pokemonEntity.getAspects().contains(SHAspects.SHADOW)) {
            cir.setReturnValue(false);
        }
    }
}
