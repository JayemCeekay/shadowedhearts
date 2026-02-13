package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import org.jetbrains.annotations.NotNull;

public class ImmunizedProperty implements CustomPokemonProperty {
    private final boolean value;

    public ImmunizedProperty(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public @NotNull String asString() {
        return "sh_immunized=" + value;
    }

    @Override
    public void apply(@NotNull Pokemon pokemon) {
        ShadowAspectUtil.setImmunizedProperty(pokemon, value);
    }

    @Override
    public boolean matches(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .anyMatch(p -> p instanceof ImmunizedProperty && ((ImmunizedProperty) p).value == this.value);
    }
}
