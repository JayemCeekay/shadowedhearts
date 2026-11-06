package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;

public class XPBufferProperty implements CustomPokemonProperty {
    private final int value;

    public XPBufferProperty(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String asString() {
        return "sh_xp_buf=" + value;
    }

    @Override
    public void apply(Pokemon pokemon) {
        ShadowPokemonProperties.setXPBufferProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof XPBufferProperty && ((XPBufferProperty) p).value == this.value);
    }
}
