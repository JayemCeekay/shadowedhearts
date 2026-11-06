package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;

public class HeartGaugeProperty implements CustomPokemonProperty {
    private final int value;

    public HeartGaugeProperty(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String asString() {
        return "sh_heartgauge=" + value;
    }

    @Override
    public void apply(Pokemon pokemon) {
        ShadowPokemonProperties.setHeartGaugeProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof HeartGaugeProperty && ((HeartGaugeProperty) p).value == this.value);
    }
}
