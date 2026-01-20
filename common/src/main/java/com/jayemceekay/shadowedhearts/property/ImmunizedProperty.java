package com.jayemceekay.shadowedhearts.property;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;

public class ImmunizedProperty implements CustomPokemonProperty {
    private final boolean value;

    public ImmunizedProperty(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public String asString() {
        return "shadowedhearts:immunized=" + value;
    }

    @Override
    public void apply(Pokemon pokemon) {
        PokemonAspectUtil.setImmunizedProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof ImmunizedProperty && ((ImmunizedProperty) p).value == this.value);
    }
}
