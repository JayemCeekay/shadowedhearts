package com.jayemceekay.shadowedhearts.content.items;

import com.jayemceekay.shadowedhearts.common.shadow.restrictions.HeartGaugeUnlockRules;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionSettings;

/** Exact revival and full-payment calculations, without changing Heart Gauge capacity. */
public final class BitterReawakeningRules {
    private BitterReawakeningRules() { }

    public static int heartCost(int maximum) {
        if (maximum <= 0) throw new IllegalArgumentException("Heart Gauge capacity must be positive");
        return (int) ((maximum + 9L) / 10L);
    }

    public static boolean canPay(int current, int maximum) {
        return maximum > 0 && current >= 0 && current <= maximum
                && (long) current + heartCost(maximum) <= maximum;
    }

    public static int afterPayment(int current, int maximum) {
        if (!canPay(current, maximum)) throw new IllegalArgumentException("The complete Heart Gauge cost cannot fit");
        return current + heartCost(maximum);
    }

    public static int revivedHealth(int maximumHealth) {
        if (maximumHealth <= 0) throw new IllegalArgumentException("Maximum HP must be positive");
        return (int) Math.max(1L, (maximumHealth * 3L + 3L) / 4L);
    }

    /** Uses the same gauge comparison and server policy as move visibility and battle validation. */
    public static int allowedNonShadowMoves(int meter, int maximum, ShadowRestrictionSettings settings) {
        if (maximum <= 0) throw new IllegalArgumentException("Heart Gauge capacity must be positive");
        return HeartGaugeUnlockRules.allowedNonShadowMoves(meter, maximum, settings.restrictNonShadowMoves(),
                settings.firstMoveUnlockPercent(), settings.secondMoveUnlockPercent(), settings.thirdMoveUnlockPercent(),
                settings.exactThresholds());
    }
}
