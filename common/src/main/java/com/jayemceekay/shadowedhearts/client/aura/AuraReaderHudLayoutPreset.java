package com.jayemceekay.shadowedhearts.client.aura;

import java.util.Locale;

/**
 * High-level Aura Reader HUD layout profiles.
 */
public enum AuraReaderHudLayoutPreset {
    DEFAULT,
    COMPACT,
    MINIMAL,
    ULTRAWIDE,
    STREAMER;

    /**
     * Resolves a serialized preset name without making callers depend on enum
     * casing. Unknown or empty names retain the default layout.
     */
    public static AuraReaderHudLayoutPreset fromName(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }

        String normalized = name.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }
}
