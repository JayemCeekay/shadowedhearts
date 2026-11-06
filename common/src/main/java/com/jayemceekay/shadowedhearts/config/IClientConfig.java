package com.jayemceekay.shadowedhearts.config;

public interface IClientConfig extends IModConfig {
    default ClientEffectPolicy.Preset accessibilityPreset() { return ClientEffectPolicy.Preset.DEFAULT; }
    default ClientEffectPolicy.EffectOverride scanPulseEffects() { return ClientEffectPolicy.EffectOverride.INHERIT; }
    default ClientEffectPolicy.EffectOverride darkBallEffects() { return ClientEffectPolicy.EffectOverride.INHERIT; }
    default ClientEffectPolicy.EffectOverride moonwoundEffects() { return ClientEffectPolicy.EffectOverride.INHERIT; }
    default ClientEffectPolicy.EffectOverride auraEffects() { return ClientEffectPolicy.EffectOverride.INHERIT; }
    default ClientEffectPolicy.EffectOverride ambientEffects() { return ClientEffectPolicy.EffectOverride.INHERIT; }
    default boolean enableShadowAura() { return true; }
    default AuraRenderQuality auraRenderQuality() { return AuraRenderQuality.QUALITY; }
    default AuraPixelationStyle signatureAuraPixelation() { return AuraPixelationStyle.PIXELATED; }
    default AuraPixelationStyle colosseumAuraPixelation() { return AuraPixelationStyle.SMOOTH; }
    default AuraPixelationStyle xdAuraPixelation() { return AuraPixelationStyle.SMOOTH; }
    default float auraReaderYOffset() { return -0.15f; }

    // Aura Reader HUD presentation and accessibility options
    default float auraReaderHudScale() { return 1.0f; }
    default float auraReaderCompassWidth() { return 0.42f; }
    default int auraReaderTopInset() { return 16; }
    default int auraReaderCompassVisibleArc() { return 90; }
    default float auraReaderReticleScale() { return 1.0f; }
    default float auraReaderHudOpacity() { return 0.9f; }
    default boolean auraReaderClusterMarkers() { return true; }
    default int auraReaderBottomSafeMargin() { return 104; }
    default int auraReaderPartyOverlayMargin() { return 12; }
    default int auraReaderRightOverlayMargin() { return 8; }
    default boolean auraReaderReducedMotion() { return false; }
    default float auraReaderStaticIntensity() { return 1.0f; }

    // Moonwound objective presentation; offsets use scaled GUI pixels.
    default MoonwoundObjectiveDisplay moonwoundObjectiveDisplay() { return MoonwoundObjectiveDisplay.HUD; }
    default MoonwoundObjectivePlacement moonwoundObjectivePlacement() { return MoonwoundObjectivePlacement.AUTO; }
    default int moonwoundObjectiveOffsetX() { return 0; }
    default int moonwoundObjectiveOffsetY() { return 0; }

    // Moonwound portal and miasma presentation; texels scale with distance, field of view and resolution.
    default MoonwoundPortalStyle moonwoundPortalStyle() { return MoonwoundPortalStyle.PIXELATED; }

    ISoundConfig soundConfig();

    // ── Snag Ball VFX options ────────────────────────────────────────────

    /** Multiplier for snag capture spark counts (0.0 = no sparks, 1.0 = default, 2.0 = double). */
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

    /** Disable the rotating glare spikes around the absorption cloud during snag captures. */
    default boolean snagReducedMotion() { return false; }

    /** Low, medium, or high quality for the anime-style Dark Ball capture. */
    default String darkBallVfxQuality() { return "medium"; }


}
