package com.jayemceekay.shadowedhearts.common.aura;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.Set;

/** Synchronizes explicit selections or UUID-derived inheritance rolls through rendering aspects. */
public final class AuraStyleAspectProvider implements AspectProvider {
    public static final AuraStyleAspectProvider INSTANCE = new AuraStyleAspectProvider();

    private AuraStyleAspectProvider() {}

    @Override
    public Set<String> provide(Pokemon pokemon) {
        return AuraStyleProperties.derivedAspects(pokemon);
    }

    @Override
    public Set<String> provide(PokemonProperties properties) {
        return AuraStyleProperties.derivedAspects(properties);
    }
}
