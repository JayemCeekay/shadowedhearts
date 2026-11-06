package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;

public class ExposureProperty implements CustomPokemonProperty {
    private final double value;

    public ExposureProperty(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String asString() {
        return "sh_exposure=" + value;
    }

    @Override
    public void apply(Pokemon pokemon) {
        ShadowPokemonProperties.setExposureProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof ExposureProperty && ((ExposureProperty) p).value == this.value);
    }
}
