package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Custom Cobblemon Pokemon property that applies the Shadowed Hearts shadow aspect
 * with a configurable probability. Usage in a properties string:
 *   sh_shadow_chance=25       // 25% chance
 *   sh_shadow_chance=0.25%    // 0.25% chance
 *   sh_shadow_chance=0.25     // 25% chance (legacy 0..1 decimal format)
 *
 * Each parsed property rolls once per Pokemon object, including failed rolls.
 * Repeated native callbacks reuse that result; shared templates still roll for each new Pokemon.
 */
public final class ShadowChanceProperty implements CustomPokemonProperty {

    private final double percent; // 0..100 inclusive
    private final DoubleSupplier random;
    // Do not retain spawned Pokemon for the lifetime of a shared template, or persist
    // a permanent opt-out marker that would prevent a later, freshly parsed instruction.
    private final Map<Pokemon, Boolean> outcomes = Collections.synchronizedMap(new WeakHashMap<>());

    /** Retains the existing constructor signature for integrations. */
    public ShadowChanceProperty(int percent) {
        this((double) percent);
    }

    public ShadowChanceProperty(double percent) {
        this(percent, () -> ThreadLocalRandom.current().nextDouble());
    }

    ShadowChanceProperty(double percent, DoubleSupplier random) {
        if (!Double.isFinite(percent)) throw new IllegalArgumentException("Shadow chance must be finite");
        this.percent = Math.max(0.0, Math.min(100.0, percent));
        this.random = Objects.requireNonNull(random, "random");
    }

    public double getPercent() {
        return percent;
    }

    @Override
    public void apply(Pokemon pokemon) {
        if (pokemon == null) return;
        boolean shadow = outcomes.computeIfAbsent(pokemon,
                ignored -> percent > 0 && (percent >= 100 || random.getAsDouble() * 100.0 < percent));
        if (shadow) {
            // Keep successful application idempotent, rather than skipping it: native
            // entity creation can assign authored moves between property callbacks.
            ShadowService.setShadow(pokemon, null, true, true);
        }
    }

    @Override
    public void apply(PokemonEntity pokemonEntity) {
        if (pokemonEntity != null) apply(pokemonEntity.getPokemon());
    }

    @Override
    public String asString() {
        // An explicit suffix prevents sub-percent values from being read back as ratios.
        return "sh_shadow_chance=" + BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString() + "%";
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return false;
    }

}
