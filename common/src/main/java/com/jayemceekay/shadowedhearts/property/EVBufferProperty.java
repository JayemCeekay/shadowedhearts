package com.jayemceekay.shadowedhearts.property;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;

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
        return "shadowedhearts:ev_buf=" + Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    public void apply(Pokemon pokemon) {
        PokemonAspectUtil.setEVBufferProperty(pokemon, values);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof EVBufferProperty && Arrays.equals(((EVBufferProperty) p).values, this.values));
    }
}
