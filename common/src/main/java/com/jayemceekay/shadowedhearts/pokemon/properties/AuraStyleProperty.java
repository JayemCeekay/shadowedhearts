package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleProperties;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleSelection;

import java.util.Objects;

/** One aura appearance override, independent of whether this Pokemon qualifies for an aura. */
public final class AuraStyleProperty implements CustomPokemonProperty {
    public static final String KEY = "sh_aura_style";

    private final AuraStyleSelection value;

    public AuraStyleProperty(AuraStyleSelection value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public AuraStyleSelection getValue() {
        return value;
    }

    @Override
    public String asString() {
        return KEY + "=" + value.serializedName();
    }

    @Override
    public void apply(Pokemon pokemon) {
        AuraStyleProperties.set(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon != null && AuraStyleProperties.get(pokemon) == value;
    }
}
