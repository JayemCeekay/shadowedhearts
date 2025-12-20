package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.ai.PokemonBrain;
import com.mojang.serialization.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PokemonBrain.class, remap = false)
public class MixinPokemonBrain {

    @Inject(method = "applyBrain", at = @At("RETURN"))
    private void shadowedhearts$installTossOrderOnPokemonBrain(PokemonEntity entity, Pokemon pokemon, Dynamic<?> dynamic, CallbackInfo ci) {
        // Ensure our TossOrder behavior is present even when Cobblemon applies custom behaviour configs
    }

}

