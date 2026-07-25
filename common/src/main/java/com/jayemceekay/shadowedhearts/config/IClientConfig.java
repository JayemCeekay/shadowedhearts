package com.jayemceekay.shadowedhearts.config;

public interface IClientConfig extends IModConfig {
    default boolean enableShadowAura() { return true; }
    default boolean debugShadowAuraEmitters() { return false; }
    default boolean auraScannerEnabled() { return true; }
    default float auraReaderYOffset() { return -0.15f; }
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
}
