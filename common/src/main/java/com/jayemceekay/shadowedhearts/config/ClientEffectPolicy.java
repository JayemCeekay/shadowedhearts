package com.jayemceekay.shadowedhearts.config;

/** Pure presentation policy. It never changes server state, capture duration or detection results. */
public final class ClientEffectPolicy {
    public enum Preset { DEFAULT, REDUCED_MOTION, REDUCED_FLASHES, MINIMAL }
    public enum EffectOverride { INHERIT, FULL, REDUCED, OFF }
    public enum Family { SCAN_PULSE, DARK_BALL, MOONWOUND, AURA, AMBIENT }
    public enum Level { FULL, REDUCED, OFF }

    private ClientEffectPolicy() { }

    public static Level resolve(Preset preset, Family family, EffectOverride override) {
        // Tolerate older config adapters that do not yet provide the new optional preferences.
        if (preset == null) preset = Preset.DEFAULT;
        if (override == null) override = EffectOverride.INHERIT;
        if (override != EffectOverride.INHERIT) return Level.valueOf(override.name());
        return switch (preset) {
            case DEFAULT -> Level.FULL;
            case REDUCED_MOTION -> family == Family.AURA || family == Family.AMBIENT
                    ? Level.OFF : Level.REDUCED;
            case REDUCED_FLASHES -> Level.REDUCED;
            // Keep a stationary Moonwound landmark even in the minimal preset.
            case MINIMAL -> family == Family.MOONWOUND ? Level.REDUCED : Level.OFF;
        };
    }

    public static boolean reduceHudMotion(Preset preset, boolean existingPreference) {
        return existingPreference || preset != Preset.DEFAULT;
    }

    public static float staticIntensity(Preset preset, float existingPreference) {
        return preset == Preset.DEFAULT ? existingPreference : 0;
    }

    public static boolean reduceSnagMotion(Preset preset, boolean existingPreference) {
        return existingPreference || preset != Preset.DEFAULT;
    }

    public static boolean allowSnagFlashes(Preset preset, boolean existingPreference) {
        return existingPreference && preset == Preset.DEFAULT;
    }

    public static float snagParticles(Preset preset, float existingMultiplier) {
        return existingMultiplier * switch (preset) {
            case DEFAULT -> 1f;
            case REDUCED_MOTION, REDUCED_FLASHES -> 0.25f;
            case MINIMAL -> 0f;
        };
    }

    /** Reduced and off use the native capture beam/model, never a hidden model without its replacement. */
    public static boolean useDarkBallReplacement(Level level) { return level == Level.FULL; }

    /** Reduced scan feedback remains in the HUD and the existing sound cue. */
    public static boolean drawScanWave(Level level) { return level == Level.FULL; }

    public static float auraQuality(Level level) {
        return switch (level) { case FULL -> 1f; case REDUCED -> 0.30f; case OFF -> 0f; };
    }

    public static float ambientDensity(Level level) {
        return switch (level) { case FULL -> 1f; case REDUCED -> 0.25f; case OFF -> 0f; };
    }

    public static float moonwoundTime(Level level, float time) { return level == Level.FULL ? time : 0f; }
    public static boolean moonwoundMiasma(Level level) { return level == Level.FULL; }
}
