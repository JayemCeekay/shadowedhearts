package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

public class EVBufferProperty implements CustomPokemonProperty {
    private final int[] values;

    public EVBufferProperty(int[] values) {
        if (values.length != 6) {
            throw new IllegalArgumentException("EV Buffer must have 6 values (HP, Atk, Def, SpA, SpD, Spe)");
        }
        this.values = values;
    }

    public int[] getValues() {
        return values;
    }

    @Override
    public String asString() {
        return "sh_ev_buf=" + Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    public void apply(Pokemon pokemon) {
        ShadowAspectUtil.setEVBufferProperty(pokemon, values);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof EVBufferProperty && Arrays.equals(((EVBufferProperty) p).values, this.values));
    }
}
