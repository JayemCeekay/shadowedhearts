package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowService;
import org.jetbrains.annotations.NotNull;

public class ShadowProperty implements CustomPokemonProperty {

    public ShadowProperty() {
    }

    @Override
    public @NotNull String asString() {
        return "sh_shadow";
    }

    @Override
    public void apply(@NotNull Pokemon pokemon) {
        ShadowService.setShadow(pokemon, null, true, true);
    }

    @Override
    public boolean matches(@NotNull Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof ShadowProperty);
    }

    @Override
    public void apply(@NotNull PokemonEntity pokemonEntity) {
        apply(pokemonEntity.getPokemon());
    }

    @Override
    public boolean matches(@NotNull PokemonEntity pokemonEntity) {
        return matches(pokemonEntity.getPokemon());
    }
}
