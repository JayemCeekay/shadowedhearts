package com.jayemceekay.shadowedhearts.common.aura;

import java.util.Objects;

/** The Heart Gauge information approved for one scan recipient. */
public record AuraHeartGaugeReadout(
        Disclosure disclosure,
        Band band,
        int current,
        int maximum) {
    public AuraHeartGaugeReadout {
        Objects.requireNonNull(disclosure, "disclosure");
        if (disclosure == Disclosure.EXACT && maximum <= 0) {
            disclosure = Disclosure.HIDDEN;
        }
        if (disclosure == Disclosure.EXACT) {
            current = Math.max(0, Math.min(maximum, current));
            band = null;
        } else {
            // Obscured results must not retain the values they conceal, even
            // before packet encoding or after an accidental direct construction.
            current = 0;
            maximum = 0;
            band = disclosure == Disclosure.BANDED
                    ? Objects.requireNonNull(band, "band") : null;
        }
    }

    public static AuraHeartGaugeReadout hidden() {
        return new AuraHeartGaugeReadout(Disclosure.HIDDEN, null, 0, 0);
    }

    public static AuraHeartGaugeReadout banded(int current, int maximum) {
        return maximum <= 0 ? hidden()
                : new AuraHeartGaugeReadout(Disclosure.BANDED, bandFor(current, maximum), 0, 0);
    }

    public static AuraHeartGaugeReadout exact(int current, int maximum) {
        return new AuraHeartGaugeReadout(Disclosure.EXACT, null, current, maximum);
    }

    /** Uses the raw ratio so a tiny positive gauge is never purification-ready. */
    public static Band bandFor(int current, int maximum) {
        int value = Math.max(0, Math.min(Math.max(0, maximum), current));
        if (value == 0) {
            return Band.READY;
        }
        long scaled = (long) value * 100;
        if (scaled >= (long) maximum * 80) {
            return Band.SEALED;
        }
        if (scaled >= (long) maximum * 50) {
            return Band.CLOSED;
        }
        if (scaled >= (long) maximum * 25) {
            return Band.STIRRING;
        }
        return Band.OPENING;
    }

    /** Rounded percent closed; only exact readouts contain a numeric ratio. */
    public int percent() {
        return disclosure == Disclosure.EXACT ? (int) Math.round(100.0 * current / maximum) : 0;
    }

    public enum Disclosure {
        HIDDEN,
        BANDED,
        EXACT
    }

    public enum Band {
        SEALED,
        CLOSED,
        STIRRING,
        OPENING,
        READY
    }
}
