package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.type.ShadowChancePropertyType;

import java.util.List;

/**
 * Registers the ShadowChanceProperty parser with Cobblemon using the public API (no reflection).
 */
public final class PropertyRegistration {
    private PropertyRegistration() {
    }

    public static void register() {
        try {
            // Directly register our type implementation
            CustomPokemonProperty.Companion.register(new ShadowChancePropertyType());

            CustomPokemonProperty.Companion.register("sh_exposure", true, (val) -> {
                if (val == null) return null;
                return new ExposureProperty(Double.parseDouble(val));
            }, () -> List.of("0.0", "10.0", "50.0"));

            CustomPokemonProperty.Companion.register("sh_immunized", true, (val) -> {
                if (val == null) return null;
                return new ImmunizedProperty(Boolean.parseBoolean(val));
            }, () -> List.of("true", "false"));

            CustomPokemonProperty.Companion.register("sh_heartgauge", true, (val) -> {
                if (val == null) return null;
                return new HeartGaugeProperty(Integer.parseInt(val));
            }, () -> List.of("0", "50", "100"));

            CustomPokemonProperty.Companion.register("sh_xp_buf", true, (val) -> {
                if (val == null) return null;
                return new XPBufferProperty(Integer.parseInt(val));
            }, () -> List.of("0", "1000", "5000"));

            CustomPokemonProperty.Companion.register("sh_ev_buf", true, (val) -> {
                if (val == null) return null;
                String[] parts = val.split(",");
                int[] evs = new int[6];
                for (int i = 0; i < Math.min(parts.length, 6); i++) {
                    evs[i] = Integer.parseInt(parts[i]);
                }
                return new EVBufferProperty(evs);
            }, () -> List.of("0,0,0,0,0,0", "252,252,4,0,0,0"));

            CustomPokemonProperty.Companion.register("sh_scent_cooldown", true, (val) -> {
                if (val == null) return null;
                return new ScentCooldownProperty(Long.parseLong(val));
            }, () -> List.of("0"));

            CustomPokemonProperty.Companion.register("sh_shadow", true, (val) -> new ShadowProperty(), () -> List.of());

        } catch (Throwable ignored) {
            ignored.printStackTrace();
            // Avoid crashing if the API is unavailable in some environments.
        }
    }
}
