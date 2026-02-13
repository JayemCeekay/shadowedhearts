package com.jayemceekay.shadowedhearts.api.pokeball.catching.effects

import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import net.minecraft.world.entity.LivingEntity

class DarkBallCaptureEffect : CaptureEffect {
    override fun apply(thrower: LivingEntity, pokemon: Pokemon) {
        PokemonAspectUtil.setShadowAspect(pokemon, true)
    }
}
