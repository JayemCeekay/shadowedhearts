package com.jayemceekay.shadowedhearts.config;

public interface IClientConfig extends IModConfig {
    default boolean enableShadowAura() { return true; }
    default boolean debugShadowAuraEmitters() { return false; }
    default boolean auraScannerEnabled() { return true; }
    default float auraReaderYOffset() { return -0.15f; }

    // Aura Reader HUD layout and accessibility options
    default float auraReaderHudScale() { return 1.0f; }
    default float auraReaderCompassWidth() { return 0.42f; }
    default int auraReaderCompassVerticalOffset() { return 8; }
    default int auraReaderCompassVisibleArc() { return 90; }
    default float auraReaderReticleScale() { return 1.0f; }
    default float auraReaderHudOpacity() { return 0.9f; }
    default boolean auraReaderShowCardinalLabels() { return false; }
    default boolean auraReaderClusterMarkers() { return true; }
    default int auraReaderMaxMarkers() { return 12; }
    default String auraReaderChargeDisplay() { return "conditional"; }
    default String auraReaderLayoutPreset() { return "default"; }
    default int auraReaderBottomSafeMargin() { return 104; }
    default int auraReaderPartyOverlayMargin() { return 12; }
    default int auraReaderBossBarMargin() { return 8; }
    default int auraReaderRightOverlayMargin() { return 8; }
    default boolean auraReaderReducedMotion() { return false; }
    default float auraReaderStaticIntensity() { return 1.0f; }

    ISoundConfig soundConfig();
    /**
     * If true, temperatures in HUD are displayed in Fahrenheit instead of Celsius.
     */
    default boolean useFahrenheitDisplay() { return false; }

    // ── Snag Ball VFX options ────────────────────────────────────────────

    /** Multiplier for particle counts in snag ball VFX (0.0 = none, 1.0 = default, 2.0 = double). */
    default float snagParticleMultiplier() { return 1.0f; }

    /** Whether the bloom/glow post-processing pass is enabled for snag ball VFX. */
    default boolean snagBloomEnabled() { return true; }

    /** Intensity of the bloom effect (0.0–2.0). */
    default float snagBloomIntensity() { return 0.8f; }

    /** Whether lens flare is rendered at convergence. */
    default boolean snagLensFlareEnabled() { return true; }

    /** Whether the dissolve shader is applied to the Pokémon during absorption. */
    default boolean snagDissolveEnabled() { return true; }

    /** Whether shake-phase VFX (seam glow, ground pulses, sparks) are enabled. */
    default boolean snagShakeVfxEnabled() { return true; }

    /** Reduced motion mode: disables fast-moving particles, flashes, and screen-shake. */
    default boolean snagReducedMotion() { return false; }

    /** Low, medium, or high budget for the analytical Dark Ball volume and shell. */
    default String darkBallVfxQuality() { return "medium"; }

    /**
     * Manual Dark Ball body-surfel budget. Zero keeps the selected quality
     * preset's budget; positive values override only the surfel count.
     */
    default int darkBallSurfelBudget() { return 0; }

    /**
     * Diagnostic override that keeps the captured texture/depth mask as the
     * only Dark Ball body presentation and bypasses surfel preparation.
     */
    default boolean darkBallForceExactMaskOnly() { return false; }

    /**
     * Diagnostic heatmap that colors live Dark Ball spikes and indentations
     * from their signed, normalized surfel displacement amplitude.
     */
    default boolean darkBallVisualizeSpikeIndentAmplitude() { return false; }

    /**
     * Strength of the stable silhouette-distance body sheen. Zero disables it;
     * the renderer hard-caps the contribution at 0.06.
     */
    default float darkBallBodyContourSheenStrength() { return 0.045f; }

    /**
     * Strength of the guarded body depth-normal highlight on the black-side
     * silhouette shoulder. Zero disables it; the shader hard-caps the result.
     */
    default float darkBallBodyDepthSpecularStrength() { return 0.09f; }

}
