package com.jayemceekay.shadowedhearts.pokemon.properties.type;

import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleSelection;
import com.jayemceekay.shadowedhearts.pokemon.properties.AuraStyleProperty;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/** Strict finite-value parser and command completions for sh_aura_style. */
public final class AuraStylePropertyType implements CustomPokemonPropertyType<AuraStyleProperty> {
    @Override
    public Iterable<String> getKeys() {
        return Set.of(AuraStyleProperty.KEY);
    }

    @Override
    public boolean getNeedsKey() {
        return true;
    }

    @Override
    public AuraStyleProperty fromString(String value) {
        return AuraStyleSelection.parse(value).map(AuraStyleProperty::new).orElse(null);
    }

    @Override
    public Collection<String> examples() {
        return Arrays.stream(AuraStyleSelection.values())
                .map(AuraStyleSelection::serializedName)
                .toList();
    }
}
