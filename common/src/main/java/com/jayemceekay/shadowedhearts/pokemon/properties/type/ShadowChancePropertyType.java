package com.jayemceekay.shadowedhearts.pokemon.properties.type;

import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Direct CustomPokemonPropertyType implementation for ShadowChanceProperty.
 * No reflection; uses Cobblemon's public API.
 */
public final class ShadowChancePropertyType implements CustomPokemonPropertyType<com.jayemceekay.shadowedhearts.pokemon.properties.ShadowChanceProperty> {

    private static final Set<String> KEYS = Set.of(
            "shadow_chance",
            "force_shadow_chance",
            "shadowedhearts:shadow_chance"
    );

    @Override
    public Iterable<String> getKeys() {
        return KEYS;
    }

    @Override
    public boolean getNeedsKey() {
        return true;
    }

    @Override
    public com.jayemceekay.shadowedhearts.pokemon.properties.ShadowChanceProperty fromString(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty()) return null;
        try {
            int percent;
            if (s.endsWith("%")) s = s.substring(0, s.length() - 1).trim();
            if (s.contains(".")) {
                double d = Double.parseDouble(s);
                if (d <= 1.0) percent = (int) Math.round(d * 100.0);
                else percent = (int) Math.round(d);
            } else {
                percent = Integer.parseInt(s);
            }
            percent = Math.max(0, Math.min(100, percent));
            return new com.jayemceekay.shadowedhearts.pokemon.properties.ShadowChanceProperty(percent);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public Collection<String> examples() {
        // Examples of literal values (not including the key), used for command tab completion
        return List.of("25", "25%", "0.25");
    }
}
