package com.jayemceekay.shadowedhearts.pokemon.properties.type;

import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType;
import com.jayemceekay.shadowedhearts.pokemon.properties.ShadowChanceProperty;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Direct CustomPokemonPropertyType implementation for ShadowChanceProperty.
 * No reflection; uses Cobblemon's public API.
 */
public final class ShadowChancePropertyType implements CustomPokemonPropertyType<ShadowChanceProperty> {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private static final Set<String> KEYS = Set.of(
            "sh_shadow_chance"
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
    public ShadowChanceProperty fromString(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty()) return null;
        try {
            boolean explicitPercent = s.endsWith("%");
            if (explicitPercent) s = s.substring(0, s.length() - 1).trim();
            if (!DECIMAL.matcher(s).matches()) return null;
            double percent = Double.parseDouble(s);
            if (!Double.isFinite(percent)) return null;
            boolean decimalSyntax = s.contains(".") || s.contains("e") || s.contains("E");
            // Preserve legacy bare ratios, but a '%' suffix always means percentage units.
            if (!explicitPercent && decimalSyntax && percent >= 0 && percent <= 1.0) percent *= 100.0;
            return new ShadowChanceProperty(percent);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public Collection<String> examples() {
        // Examples of literal values (not including the key), used for command tab completion
        return List.of("25", "25%", "0.25", "0.25%");
    }
}
