package com.jayemceekay.shadowedhearts.property;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;

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
        return "shadowedhearts:heartgauge=" + value;
    }

    @Override
    public void apply(Pokemon pokemon) {
        PokemonAspectUtil.setHeartGaugeProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof HeartGaugeProperty && ((HeartGaugeProperty) p).value == this.value);
    }
}
