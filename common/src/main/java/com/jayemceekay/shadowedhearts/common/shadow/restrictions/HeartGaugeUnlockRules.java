package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

/** Percent thresholds use remaining gauge: a lower value means a more open heart. */
public final class HeartGaugeUnlockRules {
    private HeartGaugeUnlockRules() {}

    public static boolean isHidden(int percent, boolean enabled, int revealPercent) {
        return enabled && clampPercent(percent) > clampPercent(revealPercent);
    }

    public static int allowedNonShadowMoves(int percent, boolean restricted,
                                           int firstPercent, int secondPercent, int thirdPercent) {
        if (!restricted) return 4;
        int remaining = clampPercent(percent);
        // Later stages require the earlier stages too, even if the server orders thresholds differently.
        if (remaining > clampPercent(firstPercent)) return 0;
        if (remaining > clampPercent(secondPercent)) return 1;
        if (remaining > clampPercent(thirdPercent)) return 2;
        return 3;
    }

    /** Exact rules compare the stored meter, never its rounded presentation percentage. */
    public static boolean isHidden(int meter, int maximum, boolean enabled, int revealPercent,
                                   boolean exactThresholds) {
        return enabled && !reached(meter, maximum, revealPercent, exactThresholds);
    }

    public static int allowedNonShadowMoves(int meter, int maximum, boolean restricted,
                                           int firstPercent, int secondPercent, int thirdPercent,
                                           boolean exactThresholds) {
        if (!restricted) return 4;
        if (!reached(meter, maximum, firstPercent, exactThresholds)) return 0;
        if (!reached(meter, maximum, secondPercent, exactThresholds)) return 1;
        if (!reached(meter, maximum, thirdPercent, exactThresholds)) return 2;
        return 3;
    }

    private static boolean reached(int meter, int maximum, int threshold, boolean exactThresholds) {
        int remaining = Math.max(0, Math.min(Math.max(0, maximum), meter));
        int percent = clampPercent(threshold);
        if (!exactThresholds) {
            int rounded = maximum <= 0 ? 0 : Math.round((remaining * 100f) / maximum);
            return rounded <= percent;
        }
        // Zero is an explicit empty-gauge milestone, including XD's final ordinary move.
        if (remaining == 0) return true;
        return (long) remaining * 100L < (long) maximum * percent;
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
