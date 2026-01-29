package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.SHAspects;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom Cobblemon Pokemon property that applies the Shadowed Hearts shadow aspect
 * with a configurable probability. Usage in a properties string:
 *   shadow_chance=25        // 25% chance
 *   shadow_chance=25%       // 25% chance (percent sign optional)
 *   shadow_chance=0.25      // 25% chance (0..1 decimal format)
 *
 * Key: "shadow_chance" (aliases: "shadowedhearts:shadow_chance", "force_shadow_chance")
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
            // Merge into existing forcedAspects
            Set<String> merged = new HashSet<>(pokemon.getForcedAspects());
            merged.add(SHAspects.SHADOW);
            pokemon.setForcedAspects(merged);
            // Let Cobblemon recompute effective aspects if needed
            pokemon.updateAspects();
        }
    }

    @Override
    public void apply(PokemonEntity pokemonEntity) {
        if (pokemonEntity != null) apply(pokemonEntity.getPokemon());
    }

    @Override
    public String asString() {
        return "shadow_chance=" + percent;
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        // This property is not intended for reverse-derivation; return false to avoid implying presence.
        // If you want to treat existing shadow mons as matching a 100% chance, adjust accordingly.
        return false;
    }

    private static boolean roll(int percent) {
        // ThreadLocalRandom current is fine for server-side deterministic-enough RNG per creation
        // Value in [0,100)
        int r = ThreadLocalRandom.current().nextInt(100);
        return r < percent;
    }
}
