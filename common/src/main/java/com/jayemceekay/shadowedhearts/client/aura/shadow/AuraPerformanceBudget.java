package com.jayemceekay.shadowedhearts.client.aura.shadow;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.ShadowAuraStyleProfile;
import net.minecraft.util.Mth;

import static com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraModelGeometry.MAX_BONE_ANCHORS;

/**
 * Pure adaptive quality and presentation budgets shared by world and preview simulation.
 * Callers supply source counts, distance, and the current mode; no runtime state is retained here.
 */
public final class AuraPerformanceBudget {
    static final int MAX_PUFFS = 15000;
    private static final int MIN_BONE_EMITTERS_PER_TICK = 54;
    private static final int MAX_BONE_EMITTERS_PER_TICK = 120;
    private static final float BONE_EMITTER_ACTIVE_FRACTION = 0.32f;
    private static final int MIN_ADAPTIVE_BONE_ANCHORS = 72;
    private static final float MIN_AURA_QUALITY = 0.22f;
    private static final float QUALITY_NEAR_DISTANCE = 9.0f;
    private static final float QUALITY_FAR_DISTANCE = 38.0f;
    private static final int MIN_ADAPTIVE_PUFFS = 8500;
    private static final int PUFF_LIMIT_PER_EXTRA_SOURCE_DROP = 1400;

    public static final AuraPerformanceBudget QUALITY = new AuraPerformanceBudget(false);
    public static final AuraPerformanceBudget PERFORMANCE = new AuraPerformanceBudget(true);

    private final boolean performance;

    private AuraPerformanceBudget(boolean performance) {
        this.performance = performance;
    }

    public static AuraPerformanceBudget forMode(boolean performance) {
        return performance ? PERFORMANCE : QUALITY;
    }

    /** Accessibility scaling is applied by the caller after this base quality floor. */
    static float auraQuality(float modelSize, double distance, int activeSources) {
        float near = Math.max(QUALITY_NEAR_DISTANCE, modelSize * 3.0f);
        float far = Math.max(QUALITY_FAR_DISTANCE, modelSize * 10.0f);
        float distanceFade = 1.0f - smoothstep(near, far, (float) distance);
        float distanceQuality = MIN_AURA_QUALITY + (1.0f - MIN_AURA_QUALITY) * distanceFade;
        float pressureQuality = sourcePressureQuality(activeSources);
        return Mth.clamp(distanceQuality * pressureQuality, MIN_AURA_QUALITY, 1.0f);
    }

    static float sourcePressureQuality(int activeSources) {
        return Mth.clamp(1.0f - Math.max(0, activeSources - 1) * 0.08f, 0.58f, 1.0f);
    }

    static int boneEmissionInterval(float quality) {
        if (quality >= 0.78f) {
            return 1;
        }
        if (quality >= 0.52f) {
            return 2;
        }
        if (quality >= 0.34f) {
            return 3;
        }
        return 4;
    }

    int adaptiveAnchorBudget(int baseBudget, float quality) {
        float scale = 0.38f + quality * 0.62f;
        int fullCount = Mth.clamp(Math.round(baseBudget * scale), MIN_ADAPTIVE_BONE_ANCHORS, MAX_BONE_ANCHORS);
        return captureAnchors(fullCount, MIN_ADAPTIVE_BONE_ANCHORS);
    }

    static float adaptiveEmitterScale(float quality) {
        return Mth.clamp(0.30f + quality * 0.70f, 0.30f, 1.0f);
    }

    static int boneEmitterTargetCount(int anchorCount,
                                      float quality,
                                      ShadowAuraStyleProfile profile) {
        float emitterScale = adaptiveEmitterScale(quality)
                * profile.emission().rateScale()
                * profile.emission().boneBudgetScale();
        int minEmitters = Math.max(12, Math.round(MIN_BONE_EMITTERS_PER_TICK * emitterScale));
        int maxEmitters = Math.max(minEmitters, Math.round(MAX_BONE_EMITTERS_PER_TICK * emitterScale));
        int scaled = Math.round(anchorCount * BONE_EMITTER_ACTIVE_FRACTION * emitterScale);
        return Math.min(anchorCount, Mth.clamp(scaled, minEmitters, maxEmitters));
    }

    /** Uses the caller's total source count, independently of the recent-source quality window. */
    static int adaptivePuffLimit(int activeSources) {
        activeSources = Math.max(1, activeSources);
        int scaledLimit = MAX_PUFFS - Math.max(0, activeSources - 1) * PUFF_LIMIT_PER_EXTRA_SOURCE_DROP;
        return Mth.clamp(scaledLimit, MIN_ADAPTIVE_PUFFS, MAX_PUFFS);
    }

    static float puffRenderQuality(double distance) {
        return Mth.clamp(1.0f - smoothstep(18.0f, 48.0f, (float) distance) * 0.62f, 0.38f, 1.0f);
    }

    public int captureAnchors(int fullCount, int minimum) {
        return performance ? Math.min(fullCount, Math.max(minimum, Math.round(fullCount * .75f))) : fullCount;
    }

    /** Primary body, appendage and tip emitters retain their existing budgets. */
    public int signatureFillCount(int fullCount) {
        return scaledCount(fullCount, .50f);
    }

    public int signatureFallbackCount(int fullCount) {
        return scaledCount(fullCount, .75f);
    }

    public float signatureDetailScale() {
        return performance ? .50f : 1f;
    }

    public double colosseumSmokeRate(double fullRate) {
        return performance ? fullRate * .70 : fullRate;
    }

    public double colosseumInfillRate(double fullRate) {
        return performance ? fullRate * .50 : fullRate;
    }

    public int xdCellTarget(int fullCount) {
        return scaledCount(fullCount, .60f);
    }

    public int xdHazeTarget(int fullCount) {
        return scaledCount(fullCount, .75f);
    }

    public double xdHazeRate(double fullRate) {
        return performance ? fullRate * .75 : fullRate;
    }

    public double accentRate(double fullRate) {
        return performance ? fullRate * .50 : fullRate;
    }

    private int scaledCount(int fullCount, float scale) {
        return performance && fullCount > 0 ? Math.max(1, Math.round(fullCount * scale)) : fullCount;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
