package com.jayemceekay.shadowedhearts.common.capture;

/** Persistent capture provenance and bounded arithmetic for Dark Ball rewards. */
public final class DarkBallCaptureRules {
    public static final String NBT_CORRUPTED = "shadowedhearts:dark_ball_corrupted";
    public static final String NBT_LEVEL_BOOSTED = "shadowedhearts:dark_ball_level_boosted";

    private DarkBallCaptureRules() {}

    /** A capture bonus never lowers an already over-cap Pokemon. */
    public static int boostedLevel(int currentLevel, int levelBoost, int levelCap) {
        long increased = (long) currentLevel + Math.max(0, levelBoost);
        return (int) Math.max(currentLevel, Math.min(increased, levelCap));
    }

    /** The caller supplies the species baseline, never an individual gauge override. */
    public static int heartGaugeMaximum(int speciesMaximum, double multiplier) {
        double boundedMultiplier = Double.isFinite(multiplier) ? Math.max(1.0, multiplier) : 1.0;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.max(1, speciesMaximum) * boundedMultiplier));
    }
}
