package com.jayemceekay.shadowedhearts.property;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;

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
        return "shadowedhearts:exposure=" + value;
    }

    @Override
    public void apply(Pokemon pokemon) {
        PokemonAspectUtil.setExposureProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof ExposureProperty && ((ExposureProperty) p).value == this.value);
    }
}
