package com.jayemceekay.shadowedhearts.client.aura;

import java.util.Objects;

/**
 * Immutable simulation and material parameters for one Shadow aura style.
 *
 * <p>Values are expressed as multipliers over the current signature aura
 * kernel unless their accessor documents an absolute unit. Keeping the
 * profile free of renderer state lets the world and every GUI preview consume
 * the same authored behavior while retaining separate particle pools.
 */
public record ShadowAuraStyleProfile(
        ShadowAuraStyle style,
        EmissionTuning emission,
        PuffTuning broadHaze,
        PuffTuning coreHaze,
        PuffTuning wisp,
        PuffTuning hotFleck,
        BurstTuning burst,
        FilamentTuning filament,
        RenderTuning render
) {
    public ShadowAuraStyleProfile {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(emission, "emission");
        Objects.requireNonNull(broadHaze, "broadHaze");
        Objects.requireNonNull(coreHaze, "coreHaze");
        Objects.requireNonNull(wisp, "wisp");
        Objects.requireNonNull(hotFleck, "hotFleck");
        Objects.requireNonNull(burst, "burst");
        Objects.requireNonNull(filament, "filament");
        Objects.requireNonNull(render, "render");
    }

    /** Spawn budgets and the three body-filling paths in the shared kernel. */
    public record EmissionTuning(
            float rateScale,
            float boneBudgetScale,
            float bodyAnchorFillScale,
            float bodyVolumeFillScale,
            float upperBodyFillScale,
            float fallbackEmitterScale
    ) {
        public EmissionTuning {
            requireNonNegative("rateScale", rateScale);
            requireNonNegative("boneBudgetScale", boneBudgetScale);
            requireNonNegative("bodyAnchorFillScale", bodyAnchorFillScale);
            requireNonNegative("bodyVolumeFillScale", bodyVolumeFillScale);
            requireNonNegative("upperBodyFillScale", upperBodyFillScale);
            requireNonNegative("fallbackEmitterScale", fallbackEmitterScale);
        }
    }

    /** Per-role multipliers applied when an ordinary density puff is spawned. */
    public record PuffTuning(
            float spawnWeight,
            float sizeScale,
            float lifetimeScale,
            float densityScale,
            float driftScale
    ) {
        public PuffTuning {
            requireNonNegative("spawnWeight", spawnWeight);
            requireNonNegative("sizeScale", sizeScale);
            requireNonNegative("lifetimeScale", lifetimeScale);
            requireNonNegative("densityScale", densityScale);
            requireNonNegative("driftScale", driftScale);
        }
    }

    /**
     * Persistent sparse burst clusters. Sizes are fractions of model size;
     * lifetimes are ticks, and spawnChancePerTick is evaluated per source.
     */
    public record BurstTuning(
            boolean enabled,
            float spawnChancePerTick,
            int targetVisibleMin,
            int targetVisibleMax,
            float sizeMinModelScale,
            float sizeMaxModelScale,
            int lifetimeMinTicks,
            int lifetimeMaxTicks,
            int moteCountMin,
            int moteCountMax
    ) {
        public BurstTuning {
            requireUnitInterval("spawnChancePerTick", spawnChancePerTick);
            requireRange("targetVisible", targetVisibleMin, targetVisibleMax);
            requireOrderedNonNegative(
                    "sizeModelScale", sizeMinModelScale, sizeMaxModelScale);
            requireRange("lifetimeTicks", lifetimeMinTicks, lifetimeMaxTicks);
            requireRange("moteCount", moteCountMin, moteCountMax);
            if (enabled && (targetVisibleMin == 0 || lifetimeMinTicks == 0
                    || sizeMinModelScale == 0.0f)) {
                throw new IllegalArgumentException(
                        "Enabled burst tuning requires positive target, size, and lifetime minima");
            }
        }

        public static BurstTuning disabled() {
            return new BurstTuning(false, 0.0f, 0, 0,
                    0.0f, 0.0f, 0, 0, 0, 0);
        }
    }

    /**
     * Coherent plasma detail authored inside a persistent burst cluster.
     * Widths are fractions of model size; haloWidthScale multiplies core width.
     */
    public record FilamentTuning(
            boolean enabled,
            float clusterChance,
            int segmentCountMin,
            int segmentCountMax,
            int branchCountMin,
            int branchCountMax,
            float coreWidthModelScale,
            float haloWidthScale,
            float intensityScale,
            float lifetimeScale,
            float driftFollow
    ) {
        public FilamentTuning {
            requireUnitInterval("clusterChance", clusterChance);
            requireRange("segmentCount", segmentCountMin, segmentCountMax);
            requireRange("branchCount", branchCountMin, branchCountMax);
            requireNonNegative("coreWidthModelScale", coreWidthModelScale);
            requireNonNegative("haloWidthScale", haloWidthScale);
            requireNonNegative("intensityScale", intensityScale);
            requireNonNegative("lifetimeScale", lifetimeScale);
            requireUnitInterval("driftFollow", driftFollow);
            if (enabled && (segmentCountMin == 0 || coreWidthModelScale == 0.0f)) {
                throw new IllegalArgumentException(
                        "Enabled filament tuning requires segments and a positive core width");
            }
        }

        public static FilamentTuning disabled() {
            return new FilamentTuning(false, 0.0f, 0, 0, 0, 0,
                    0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    /**
     * Density-channel and presentation parameters shared by simulation paths.
     * Each style's isolated composite shader remains the sole palette and
     * material authority, avoiding duplicated Java/GLSL color definitions.
     */
    public record RenderTuning(
            float broadChannelScale,
            float heatChannelScale,
            float wispChannelScale,
            float coverageScale,
            float opacityScale,
            float blurRadiusScale,
            float pixelSizeScale
    ) {
        public RenderTuning {
            requireNonNegative("broadChannelScale", broadChannelScale);
            requireNonNegative("heatChannelScale", heatChannelScale);
            requireNonNegative("wispChannelScale", wispChannelScale);
            requireNonNegative("coverageScale", coverageScale);
            requireNonNegative("opacityScale", opacityScale);
            requireNonNegative("blurRadiusScale", blurRadiusScale);
            requireNonNegative("pixelSizeScale", pixelSizeScale);
        }
    }

    private static void requireNonNegative(String name, float value) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireUnitInterval(String name, float value) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    private static void requireRange(String name, int minimum, int maximum) {
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException(
                    name + " must have 0 <= minimum <= maximum");
        }
    }

    private static void requireOrderedNonNegative(String name,
                                                   float minimum,
                                                   float maximum) {
        requireNonNegative(name + " minimum", minimum);
        requireNonNegative(name + " maximum", maximum);
        if (maximum < minimum) {
            throw new IllegalArgumentException(name + " maximum must be >= minimum");
        }
    }
}
