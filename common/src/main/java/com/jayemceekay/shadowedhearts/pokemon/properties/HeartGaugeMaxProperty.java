package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;

public class HeartGaugeMaxProperty implements CustomPokemonProperty {
    private final int value;

    public HeartGaugeMaxProperty(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String asString() {
        return "sh_heartgaugemax=" + value;
    }

    @Override
    public void apply(Pokemon pokemon) {
        ShadowAspectUtil.setHeartGaugeMaxProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof HeartGaugeMaxProperty && ((HeartGaugeMaxProperty) p).value == this.value);
    }
}
