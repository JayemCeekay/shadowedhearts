package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowService;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom Cobblemon Pokemon property that applies the Shadowed Hearts shadow aspect
 * with a configurable probability. Usage in a properties string:
 *   shadow_chance=25        // 25% chance
 *   shadow_chance=25%       // 25% chance (percent sign optional)
 *   shadow_chance=0.25      // 25% chance (0..1 decimal format)
 *
 * Key: "shadow_chance" (aliases: "sh_shadow_chance", "force_shadow_chance")
 */
public final class ShadowChanceProperty implements CustomPokemonProperty {

    private final int percent; // 0..100 inclusive

    public ShadowChanceProperty(int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        this.percent = percent;
    }

    @Override
    public void apply(Pokemon pokemon) {
        if (percent <= 0) return;
        if (percent >= 100 || roll(percent)) {
            ShadowService.setShadow(pokemon, null, true, true);
        }
    }

    @Override
    public void apply(PokemonEntity pokemonEntity) {
        if (pokemonEntity != null) apply(pokemonEntity.getPokemon());
    }

    @Override
    public String asString() {
        return "sh_shadow_chance=" + percent;
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return false;
    }

    private static boolean roll(int percent) {
        int r = ThreadLocalRandom.current().nextInt(100);
        return r < percent;
    }
}
