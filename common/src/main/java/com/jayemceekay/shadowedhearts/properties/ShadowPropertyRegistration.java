package com.jayemceekay.shadowedhearts.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;

/**
 * Registers the ShadowChanceProperty parser with Cobblemon using the public API (no reflection).
 */
public final class ShadowPropertyRegistration {
    private ShadowPropertyRegistration() {}

    public static void register() {
        try {
            // Directly register our type implementation
            CustomPokemonProperty.Companion.register(new ShadowChancePropertyType());
        } catch (Throwable ignored) {
            // Avoid crashing if the API is unavailable in some environments.
        }
    }
}
