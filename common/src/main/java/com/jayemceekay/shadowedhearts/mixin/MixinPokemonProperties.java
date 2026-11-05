package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGaugeInitialization;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PokemonProperties.class, remap = false)
public abstract class MixinPokemonProperties {
    @WrapMethod(method = "apply(Lcom/cobblemon/mod/common/pokemon/Pokemon;)V")
    private void shadowedhearts$finishStoredGauge(Pokemon pokemon, Operation<Void> original) {
        ShadowHeartGaugeInitialization.applyProperties(pokemon, () -> original.call(pokemon));
    }

    @WrapMethod(method = "apply(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;)V")
    private void shadowedhearts$finishEntityGauge(PokemonEntity entity, Operation<Void> original) {
        ShadowHeartGaugeInitialization.applyProperties(entity.getPokemon(), () -> original.call(entity));
    }

    @WrapMethod(method = "applyCustomProperties")
    private void shadowedhearts$finishCustomGauge(PokemonEntity entity, Operation<Void> original) {
        ShadowHeartGaugeInitialization.applyProperties(entity.getPokemon(), () -> original.call(entity));
    }
}
