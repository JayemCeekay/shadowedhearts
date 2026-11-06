package com.jayemceekay.shadowedhearts.client.aura.shadow.style.xd;

import net.minecraft.util.Mth;

/** Pure lifetime curves for XD haze, flakes, cellular births, and legacy bursts. */
public final class XdAuraFade {
    private XdAuraFade() {
    }

    public static float burstEnvelope(float ageTicks, float lifetimeTicks) {
        if (!Float.isFinite(ageTicks)
                || !Float.isFinite(lifetimeTicks)
                || lifetimeTicks <= 0.0f) {
            return 0.0f;
        }
        float t = Mth.clamp(ageTicks / lifetimeTicks, 0.0f, 1.0f);
        float fadeIn = smoothstep(0.0f, 0.24f, t);
        float fadeOut = 1.0f - smoothstep(0.55f, 1.0f, t);
        return fadeIn * fadeOut;
    }

    public static float hazeSize(float baseSize, float ageTicks, float normalizedLife) {
        return baseSize * birthSizeEnvelope(ageTicks)
                * (1 - .18f * smoothstep(.08f, 1, normalizedLife));
    }

    public static float flakeSize(float baseSize, float normalizedLife) {
        return baseSize * (1 - smoothstep(.72f, 1, normalizedLife));
    }

    public static float hazeOpacity(float baseDensity, float ageTicks, float normalizedLife) {
        float appear = birthEnvelope(ageTicks);
        return baseDensity * appear * (1 - smoothstep(.18f, 1, normalizedLife));
    }

    public static float flakeOpacity(float baseDensity, float ageTicks, float normalizedLife) {
        float appear = smoothstep(0, 3, ageTicks);
        return baseDensity * appear * (1 - smoothstep(.18f, 1, normalizedLife));
    }

    /** Shared opacity entrance for XD cellular puffs and their backing haze. */
    public static float birthEnvelope(float ageTicks) {
        // New emissions are ticked before drawing. Match previous-to-current
        // position interpolation so that first draw still starts at zero size.
        return smooth(0.0f, 3.0f, ageTicks - 1.0f);
    }

    /** Earlier size expansion with the same zero start and 150 ms completion. */
    public static float birthSizeEnvelope(float ageTicks) {
        float remaining = 1.0f - clamp((ageTicks - 1.0f) / 3.0f, 0.0f, 1.0f);
        return 1.0f - remaining * remaining * remaining;
    }

    // Birth curves retain the cellular path's finite-value fallback.
    private static float clamp(float value, float low, float high) {
        return Float.isFinite(value) ? Math.max(low, Math.min(high, value)) : low;
    }

    private static float smooth(float low, float high, float value) {
        float t = clamp((value - low) / (high - low), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    // The puff and burst curves keep the renderer's original Mth clamp behavior.
    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
