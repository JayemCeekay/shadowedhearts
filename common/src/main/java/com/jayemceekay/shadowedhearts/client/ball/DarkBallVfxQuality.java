package com.jayemceekay.shadowedhearts.client.ball;

import java.util.Locale;

/** Resolution budgets for the anime capture compositor. */
enum DarkBallVfxQuality {
    LOW, MEDIUM, HIGH;

    static DarkBallVfxQuality parse(String value) {
        if (value == null) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MEDIUM;
        }
    }
}
