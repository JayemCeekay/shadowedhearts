package com.jayemceekay.shadowedhearts.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.jayemceekay.shadowedhearts.property.EVBufferProperty;
import com.jayemceekay.shadowedhearts.property.HeartGaugeProperty;
import com.jayemceekay.shadowedhearts.property.ScentCooldownProperty;
import com.jayemceekay.shadowedhearts.property.XPBufferProperty;

import java.util.List;

/**
 * Registers the ShadowChanceProperty parser with Cobblemon using the public API (no reflection).
 */
public final class ShadowPropertyRegistration {
    private ShadowPropertyRegistration() {}

    public static void register() {
        try {
            // Directly register our type implementation
            CustomPokemonProperty.Companion.register(new ShadowChancePropertyType());

            CustomPokemonProperty.Companion.register("shadowedhearts:heartgauge", true, (val) -> {
                if (val == null) return null;
                return new HeartGaugeProperty(Integer.parseInt(val));
            }, () -> List.of("0", "50", "100"));

            CustomPokemonProperty.Companion.register("shadowedhearts:xp_buf", true, (val) -> {
                if (val == null) return null;
                return new XPBufferProperty(Integer.parseInt(val));
            }, () -> List.of("0", "1000", "5000"));

            CustomPokemonProperty.Companion.register("shadowedhearts:ev_buf", true, (val) -> {
                if (val == null) return null;
                String[] parts = val.split(",");
                int[] evs = new int[6];
                for (int i = 0; i < Math.min(parts.length, 6); i++) {
                    evs[i] = Integer.parseInt(parts[i]);
                }
                return new EVBufferProperty(evs);
            }, () -> List.of("0,0,0,0,0,0", "252,252,4,0,0,0"));

            CustomPokemonProperty.Companion.register("shadowedhearts:scent_cooldown", true, (val) -> {
                if (val == null) return null;
                return new ScentCooldownProperty(Long.parseLong(val));
            }, () -> List.of("0"));
        } catch (Throwable ignored) {
            // Avoid crashing if the API is unavailable in some environments.
        }
    }
}
