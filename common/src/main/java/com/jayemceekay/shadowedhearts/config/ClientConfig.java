package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only config for visual toggles using ModConfigSpec.
 */
public final class ClientConfig implements IClientConfig, ISoundConfig {
    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        SPEC = builder.build();
    }

    private static final class Data {
        public ModConfigSpec.BooleanValue enableShadowAura;
        public ModConfigSpec.BooleanValue debugShadowAuraEmitters;
        public ModConfigSpec.BooleanValue auraScannerEnabled;
        public ModConfigSpec.BooleanValue useFahrenheitDisplay;
        public ModConfigSpec.DoubleValue auraReaderYOffset;

        // Aura Reader HUD
        public ModConfigSpec.DoubleValue auraReaderHudScale;
        public ModConfigSpec.DoubleValue auraReaderCompassWidth;
        public ModConfigSpec.IntValue auraReaderCompassVerticalOffset;
        public ModConfigSpec.IntValue auraReaderCompassVisibleArc;
        public ModConfigSpec.DoubleValue auraReaderReticleScale;
        public ModConfigSpec.DoubleValue auraReaderHudOpacity;
        public ModConfigSpec.BooleanValue auraReaderShowCardinalLabels;
        public ModConfigSpec.BooleanValue auraReaderClusterMarkers;
        public ModConfigSpec.IntValue auraReaderMaxMarkers;
        public ModConfigSpec.ConfigValue<String> auraReaderChargeDisplay;
        public ModConfigSpec.ConfigValue<String> auraReaderLayoutPreset;
        public ModConfigSpec.IntValue auraReaderBottomSafeMargin;
        public ModConfigSpec.IntValue auraReaderPartyOverlayMargin;
        public ModConfigSpec.IntValue auraReaderBossBarMargin;
        public ModConfigSpec.IntValue auraReaderRightOverlayMargin;
        public ModConfigSpec.BooleanValue auraReaderReducedMotion;
        public ModConfigSpec.DoubleValue auraReaderStaticIntensity;

        public ModConfigSpec.DoubleValue shadowAuraInitialBurstVolume;
        public ModConfigSpec.DoubleValue shadowAuraLoopVolume;
        public ModConfigSpec.DoubleValue auraScannerBeepVolume;
        public ModConfigSpec.DoubleValue relicShrineLoopVolume;
        public ModConfigSpec.DoubleValue auraReaderEquipVolume;
        public ModConfigSpec.DoubleValue auraReaderUnequipVolume;

        // Snag Ball VFX
        public ModConfigSpec.DoubleValue snagParticleMultiplier;
        public ModConfigSpec.BooleanValue snagBloomEnabled;
        public ModConfigSpec.DoubleValue snagBloomIntensity;
        public ModConfigSpec.BooleanValue snagLensFlareEnabled;
        public ModConfigSpec.BooleanValue snagDissolveEnabled;
        public ModConfigSpec.BooleanValue snagShakeVfxEnabled;
        public ModConfigSpec.BooleanValue snagReducedMotion;
        public ModConfigSpec.ConfigValue<String> darkBallVfxQuality;
        public ModConfigSpec.IntValue darkBallSurfelBudget;
        public ModConfigSpec.BooleanValue darkBallForceExactMaskOnly;
        public ModConfigSpec.BooleanValue
                darkBallVisualizeSpikeIndentAmplitude;
        public ModConfigSpec.DoubleValue darkBallBodyContourSheenStrength;
        public ModConfigSpec.DoubleValue darkBallBodyDepthSpecularStrength;

        private void build(ModConfigSpec.Builder builder) {
            enableShadowAura = builder
                    .comment("Master toggle for client-side Shadow aura rendering.")
                    .define("enableShadowAura", true);

            debugShadowAuraEmitters = builder
                    .comment("Debug: render tiny red marker spheres at Shadow aura emitter anchor positions.")
                    .define("debugShadowAuraEmitters", false);
            

            auraScannerEnabled = builder
                    .comment("Whether the Aura Scanner HUD is enabled.")
                    .define("auraScannerEnabled", true);

            useFahrenheitDisplay = builder
                    .comment("Display temperatures in Fahrenheit instead of Celsius in the Aura Scanner HUD.")
                    .define("useFahrenheitDisplay", false);

            auraReaderYOffset = builder
                    .comment("The Y offset for the Aura Reader model.")
                    .defineInRange("auraReaderYOffset", -0.15, -10.0, 10.0);

            builder.push("auraReaderHud");
            auraReaderHudScale = builder
                    .comment("Global scale applied to every Aura Reader HUD element.")
                    .defineInRange("hudScale", 1.0, 0.5, 2.0);
            auraReaderCompassWidth = builder
                    .comment("Compass rail width as a fraction of the scaled GUI width.")
                    .defineInRange("compassWidth", 0.42, 0.25, 0.85);
            auraReaderCompassVerticalOffset = builder
                    .comment("Vertical offset, in scaled GUI pixels, for the compass group.")
                    .defineInRange("compassVerticalOffset", 8, -64, 128);
            auraReaderCompassVisibleArc = builder
                    .comment("Horizontal field of view, in degrees, represented by the compass rail.")
                    .defineInRange("compassVisibleArc", 90, 45, 180);
            auraReaderReticleScale = builder
                    .comment("Scale applied to the central Aura Reader reticle.")
                    .defineInRange("reticleScale", 1.0, 0.5, 1.75);
            auraReaderHudOpacity = builder
                    .comment("Opacity applied to Aura Reader HUD frames, markers, and text.")
                    .defineInRange("hudOpacity", 0.9, 0.2, 1.0);
            auraReaderShowCardinalLabels = builder
                    .comment("Show cardinal direction labels on the compass rail.")
                    .define("showCardinalLabels", false);
            auraReaderClusterMarkers = builder
                    .comment("Cluster nearby low-priority compass markers to reduce visual clutter.")
                    .define("clusterMarkers", true);
            auraReaderMaxMarkers = builder
                    .comment("Maximum number of Aura Reader markers presented at once.")
                    .defineInRange("maxMarkers", 12, 1, 48);
            auraReaderChargeDisplay = builder
                    .comment("Charge detail visibility: always, conditional, or hidden.")
                    .define("chargeDisplay", "conditional", value -> value instanceof String text
                            && (text.equalsIgnoreCase("always")
                            || text.equalsIgnoreCase("conditional")
                            || text.equalsIgnoreCase("hidden")));
            auraReaderLayoutPreset = builder
                    .comment("HUD layout preset: default, compact, minimal, ultrawide, or streamer.")
                    .define("layoutPreset", "default", value -> value instanceof String text
                            && (text.equalsIgnoreCase("default")
                            || text.equalsIgnoreCase("compact")
                            || text.equalsIgnoreCase("minimal")
                            || text.equalsIgnoreCase("ultrawide")
                            || text.equalsIgnoreCase("streamer")));
            auraReaderBottomSafeMargin = builder
                    .comment("Reserved bottom safe area, in scaled GUI pixels, for vanilla and mod HUD elements.")
                    .defineInRange("bottomSafeMargin", 104, 48, 180);
            auraReaderPartyOverlayMargin = builder
                    .comment("Extra clearance, in scaled GUI pixels, around the Cobblemon party overlay.")
                    .defineInRange("partyOverlayMargin", 12, 0, 96);
            auraReaderBossBarMargin = builder
                    .comment("Extra clearance, in scaled GUI pixels, below the boss bar area.")
                    .defineInRange("bossBarMargin", 8, 0, 96);
            auraReaderRightOverlayMargin = builder
                    .comment("Extra clearance, in scaled GUI pixels, from right-side overlays such as status effects or minimaps.")
                    .defineInRange("rightOverlayMargin", 8, 0, 160);
            auraReaderReducedMotion = builder
                    .comment("Reduce Aura Reader HUD movement, flashing, and animated static while retaining state cues.")
                    .define("reducedMotion", false);
            auraReaderStaticIntensity = builder
                    .comment("Intensity of Aura Reader interference and static effects; 0 disables them.")
                    .defineInRange("staticIntensity", 1.0, 0.0, 1.0);
            builder.pop();

            builder.push("sounds");
            shadowAuraInitialBurstVolume = builder
                    .comment("The volume of the Shadow Aura initial burst sound.")
                    .defineInRange("shadowAuraInitialBurstVolume", 3.0, 0.0, 10.0);
            shadowAuraLoopVolume = builder
                    .comment("The volume of the Shadow Aura loop sound.")
                    .defineInRange("shadowAuraLoopVolume", 1.0, 0.0, 10.0);
            auraScannerBeepVolume = builder
                    .comment("The volume of the Aura Scanner beep sound.")
                    .defineInRange("auraScannerBeepVolume", 1.0, 0.0, 10.0);
            relicShrineLoopVolume = builder
                    .comment("The volume of the Relic Shrine loop sound.")
                    .defineInRange("relicShrineLoopVolume", 1.0, 0.0, 10.0);
            auraReaderEquipVolume = builder
                    .comment("The volume of the Aura Reader equip sound.")
                    .defineInRange("auraReaderEquipVolume", 1.0, 0.0, 10.0);
            auraReaderUnequipVolume = builder
                    .comment("The volume of the Aura Reader unequip sound.")
                    .defineInRange("auraReaderUnequipVolume", 1.0, 0.0, 10.0);
            builder.pop();

            builder.push("snagVfx");
            snagParticleMultiplier = builder
                    .comment("Multiplier for particle counts in snag ball VFX (0.0 = none, 1.0 = default, 2.0 = double).")
                    .defineInRange("snagParticleMultiplier", 1.0, 0.0, 3.0);
            snagBloomEnabled = builder
                    .comment("Whether the bloom/glow post-processing pass is enabled for snag ball VFX.")
                    .define("snagBloomEnabled", true);
            snagBloomIntensity = builder
                    .comment("Intensity of the bloom effect (0.0-2.0).")
                    .defineInRange("snagBloomIntensity", 0.8, 0.0, 2.0);
            snagLensFlareEnabled = builder
                    .comment("Whether lens flare is rendered at beam convergence.")
                    .define("snagLensFlareEnabled", true);
            snagDissolveEnabled = builder
                    .comment("Whether the dissolve shader is applied to the Pok\u00e9mon during absorption.")
                    .define("snagDissolveEnabled", true);
            snagShakeVfxEnabled = builder
                    .comment("Whether shake-phase VFX (seam glow, ground pulses, sparks) are enabled.")
                    .define("snagShakeVfxEnabled", true);
            snagReducedMotion = builder
                    .comment("Reduced motion mode: disables fast-moving particles, flashes, and screen effects.")
                    .define("snagReducedMotion", false);
            builder.pop();

            builder.push("darkBallVfx");
            darkBallVfxQuality = builder
                    .comment("Dark Ball volume quality: low, medium, or high.")
                    .define("quality", "medium", value -> value instanceof String text
                            && (text.equalsIgnoreCase("low")
                            || text.equalsIgnoreCase("medium")
                            || text.equalsIgnoreCase("high")));
            darkBallSurfelBudget = builder
                    .comment(
                            "Manual Dark Ball body-surfel budget. "
                                    + "0 uses the quality preset "
                                    + "(low=1350, medium=2200, high=3200).",
                            "Higher values can tighten coverage but increase "
                                    + "asynchronous preparation, VBO size, "
                                    + "vertex work, and fragment overlap. "
                                    + "Applied to newly started captures.")
                    .defineInRange(
                            "surfelBudget",
                            0,
                            0,
                            16000);
            darkBallForceExactMaskOnly = builder
                    .comment(
                            "Diagnostic: bypass Dark Ball surfel preparation "
                                    + "and keep the captured texture/depth mask "
                                    + "as the only body presentation. This "
                                    + "intentionally reproduces the clean "
                                    + "exact-mask safety path for comparison.")
                    .define("forceExactMaskOnly", false);
            darkBallVisualizeSpikeIndentAmplitude = builder
                    .comment(
                            "Diagnostic: replace the Dark Ball body material "
                                    + "with a signed deformation-amplitude "
                                    + "heatmap. Outward spikes are red/orange, "
                                    + "inward indentations are cyan/blue, and "
                                    + "neutral surfels are dark.")
                    .define("visualizeSpikeIndentAmplitude", false);
            darkBallBodyContourSheenStrength = builder
                    .comment(
                            "Strength of the stable Dark Ball body contour "
                                    + "sheen. This uses the final silhouette "
                                    + "distance field rather than reconstructed "
                                    + "surfel depth normals, so it does not "
                                    + "produce moving prism-like facets. "
                                    + "Set to 0 to disable it.")
                    .defineInRange(
                            "bodyContourSheenStrength",
                            0.045,
                            0.0,
                            0.06);
            darkBallBodyDepthSpecularStrength = builder
                    .comment(
                            "Strength of the guarded Dark Ball body depth "
                                    + "highlight. It uses a strict four-neighbor "
                                    + "depth normal only on the inner silhouette "
                                    + "shoulder and omits Fresnel lighting to "
                                    + "avoid moving prism-like facets. Set to 0 "
                                    + "to disable it.")
                    .defineInRange(
                            "bodyDepthSpecularStrength",
                            0.09,
                            0.0,
                            0.12);
            builder.pop();
        }
    }

    @Override
    public boolean enableShadowAura() {
        if (!isLoaded()) return IClientConfig.super.enableShadowAura();
        return DATA.enableShadowAura.get();
    }

    @Override
    public boolean debugShadowAuraEmitters() {
        if (!isLoaded()) return IClientConfig.super.debugShadowAuraEmitters();
        return DATA.debugShadowAuraEmitters.get();
    }

    @Override
    public boolean auraScannerEnabled() {
        if (!isLoaded()) return IClientConfig.super.auraScannerEnabled();
        return DATA.auraScannerEnabled.get();
    }

    @Override
    public float auraReaderYOffset() {
        if (!isLoaded()) return IClientConfig.super.auraReaderYOffset();
        return DATA.auraReaderYOffset.get().floatValue();
    }

    @Override
    public float auraReaderHudScale() {
        if (!isLoaded()) return IClientConfig.super.auraReaderHudScale();
        return DATA.auraReaderHudScale.get().floatValue();
    }

    @Override
    public float auraReaderCompassWidth() {
        if (!isLoaded()) return IClientConfig.super.auraReaderCompassWidth();
        return DATA.auraReaderCompassWidth.get().floatValue();
    }

    @Override
    public int auraReaderCompassVerticalOffset() {
        if (!isLoaded()) return IClientConfig.super.auraReaderCompassVerticalOffset();
        return DATA.auraReaderCompassVerticalOffset.get();
    }

    @Override
    public int auraReaderCompassVisibleArc() {
        if (!isLoaded()) return IClientConfig.super.auraReaderCompassVisibleArc();
        return DATA.auraReaderCompassVisibleArc.get();
    }

    @Override
    public float auraReaderReticleScale() {
        if (!isLoaded()) return IClientConfig.super.auraReaderReticleScale();
        return DATA.auraReaderReticleScale.get().floatValue();
    }

    @Override
    public float auraReaderHudOpacity() {
        if (!isLoaded()) return IClientConfig.super.auraReaderHudOpacity();
        return DATA.auraReaderHudOpacity.get().floatValue();
    }

    @Override
    public boolean auraReaderShowCardinalLabels() {
        if (!isLoaded()) return IClientConfig.super.auraReaderShowCardinalLabels();
        return DATA.auraReaderShowCardinalLabels.get();
    }

    @Override
    public boolean auraReaderClusterMarkers() {
        if (!isLoaded()) return IClientConfig.super.auraReaderClusterMarkers();
        return DATA.auraReaderClusterMarkers.get();
    }

    @Override
    public int auraReaderMaxMarkers() {
        if (!isLoaded()) return IClientConfig.super.auraReaderMaxMarkers();
        return DATA.auraReaderMaxMarkers.get();
    }

    @Override
    public String auraReaderChargeDisplay() {
        if (!isLoaded()) return IClientConfig.super.auraReaderChargeDisplay();
        return DATA.auraReaderChargeDisplay.get().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String auraReaderLayoutPreset() {
        if (!isLoaded()) return IClientConfig.super.auraReaderLayoutPreset();
        return DATA.auraReaderLayoutPreset.get().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public int auraReaderBottomSafeMargin() {
        if (!isLoaded()) return IClientConfig.super.auraReaderBottomSafeMargin();
        return DATA.auraReaderBottomSafeMargin.get();
    }

    @Override
    public int auraReaderPartyOverlayMargin() {
        if (!isLoaded()) return IClientConfig.super.auraReaderPartyOverlayMargin();
        return DATA.auraReaderPartyOverlayMargin.get();
    }

    @Override
    public int auraReaderBossBarMargin() {
        if (!isLoaded()) return IClientConfig.super.auraReaderBossBarMargin();
        return DATA.auraReaderBossBarMargin.get();
    }

    @Override
    public int auraReaderRightOverlayMargin() {
        if (!isLoaded()) return IClientConfig.super.auraReaderRightOverlayMargin();
        return DATA.auraReaderRightOverlayMargin.get();
    }

    @Override
    public boolean auraReaderReducedMotion() {
        if (!isLoaded()) return IClientConfig.super.auraReaderReducedMotion();
        return DATA.auraReaderReducedMotion.get();
    }

    @Override
    public float auraReaderStaticIntensity() {
        if (!isLoaded()) return IClientConfig.super.auraReaderStaticIntensity();
        return DATA.auraReaderStaticIntensity.get().floatValue();
    }

    @Override
    public boolean useFahrenheitDisplay() {
        if (!isLoaded()) return IClientConfig.super.useFahrenheitDisplay();
        return DATA.useFahrenheitDisplay.get();
    }

    @Override
    public ISoundConfig soundConfig() {
        return this;
    }

    @Override
    public float shadowAuraInitialBurstVolume() {
        if (!isLoaded()) return ISoundConfig.super.shadowAuraInitialBurstVolume();
        return DATA.shadowAuraInitialBurstVolume.get().floatValue();
    }

    @Override
    public float shadowAuraLoopVolume() {
        if (!isLoaded()) return ISoundConfig.super.shadowAuraLoopVolume();
        return DATA.shadowAuraLoopVolume.get().floatValue();
    }

    @Override
    public float auraScannerBeepVolume() {
        if (!isLoaded()) return ISoundConfig.super.auraScannerBeepVolume();
        return DATA.auraScannerBeepVolume.get().floatValue();
    }

    @Override
    public float relicShrineLoopVolume() {
        if (!isLoaded()) return ISoundConfig.super.relicShrineLoopVolume();
        return DATA.relicShrineLoopVolume.get().floatValue();
    }

    @Override
    public float auraReaderEquipVolume() {
        if (!isLoaded()) return ISoundConfig.super.auraReaderEquipVolume();
        return DATA.auraReaderEquipVolume.get().floatValue();
    }

    @Override
    public float auraReaderUnequipVolume() {
        if (!isLoaded()) return ISoundConfig.super.auraReaderUnequipVolume();
        return DATA.auraReaderUnequipVolume.get().floatValue();
    }

    // ── Snag Ball VFX ────────────────────────────────────────────────────

    @Override
    public float snagParticleMultiplier() {
        if (!isLoaded()) return IClientConfig.super.snagParticleMultiplier();
        return DATA.snagParticleMultiplier.get().floatValue();
    }

    @Override
    public boolean snagBloomEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagBloomEnabled();
        return DATA.snagBloomEnabled.get();
    }

    @Override
    public float snagBloomIntensity() {
        if (!isLoaded()) return IClientConfig.super.snagBloomIntensity();
        return DATA.snagBloomIntensity.get().floatValue();
    }

    @Override
    public boolean snagLensFlareEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagLensFlareEnabled();
        return DATA.snagLensFlareEnabled.get();
    }

    @Override
    public boolean snagDissolveEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagDissolveEnabled();
        return DATA.snagDissolveEnabled.get();
    }

    @Override
    public boolean snagShakeVfxEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagShakeVfxEnabled();
        return DATA.snagShakeVfxEnabled.get();
    }

    @Override
    public boolean snagReducedMotion() {
        if (!isLoaded()) return IClientConfig.super.snagReducedMotion();
        return DATA.snagReducedMotion.get();
    }

    @Override
    public String darkBallVfxQuality() {
        if (!isLoaded()) return IClientConfig.super.darkBallVfxQuality();
        return DATA.darkBallVfxQuality.get();
    }

    @Override
    public int darkBallSurfelBudget() {
        if (!isLoaded()) return IClientConfig.super.darkBallSurfelBudget();
        return DATA.darkBallSurfelBudget.get();
    }

    @Override
    public boolean darkBallForceExactMaskOnly() {
        if (!isLoaded()) {
            return IClientConfig.super.darkBallForceExactMaskOnly();
        }
        return DATA.darkBallForceExactMaskOnly.get();
    }

    @Override
    public boolean darkBallVisualizeSpikeIndentAmplitude() {
        if (!isLoaded()) {
            return IClientConfig.super
                    .darkBallVisualizeSpikeIndentAmplitude();
        }
        return DATA.darkBallVisualizeSpikeIndentAmplitude.get();
    }

    @Override
    public float darkBallBodyContourSheenStrength() {
        if (!isLoaded()) {
            return IClientConfig.super.darkBallBodyContourSheenStrength();
        }
        return DATA.darkBallBodyContourSheenStrength.get().floatValue();
    }

    @Override
    public float darkBallBodyDepthSpecularStrength() {
        if (!isLoaded()) {
            return IClientConfig.super.darkBallBodyDepthSpecularStrength();
        }
        return DATA.darkBallBodyDepthSpecularStrength.get().floatValue();
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    @Override
    public void load() {
        loaded = true;
        Shadowedhearts.LOGGER.info("ClientConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
