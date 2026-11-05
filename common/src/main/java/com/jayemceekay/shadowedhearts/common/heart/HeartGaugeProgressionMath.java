package com.jayemceekay.shadowedhearts.common.heart;

/** Bounded scaling for earned purification progress; never turns a reduction into corruption. */
public final class HeartGaugeProgressionMath {
    private HeartGaugeProgressionMath() {}

    public static int scaleReduction(int delta, double multiplier) {
        if (delta >= 0 || !Double.isFinite(multiplier) || multiplier <= 0.0) return 0;
        long reduction = Math.round(-(double) delta * multiplier);
        return -(int) Math.min(Integer.MAX_VALUE, reduction);
    }
}
