package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.ShadowAuraStyle;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

/**
 * Client-only config for visual toggles using ModConfigSpec.
 */
public final class ClientConfig implements IClientConfig, ISoundConfig {
    public enum DarkBallQuality {
        LOW, MEDIUM, HIGH
    }

    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        SPEC = builder.build();
    }

    private static final class Data {
        public ModConfigSpec.ConfigValue<String> shadowAuraStyle;
        public ModConfigSpec.BooleanValue debugShadowAuraEmitters;
        public ModConfigSpec.IntValue darkBallSurfelBudget;
        public ModConfigSpec.BooleanValue darkBallForceExactMaskOnly;
        public ModConfigSpec.BooleanValue
                darkBallVisualizeSpikeIndentAmplitude;
        public ModConfigSpec.DoubleValue darkBallBodyContourSheenStrength;
        public ModConfigSpec.DoubleValue darkBallBodyDepthSpecularStrength;
        public ModConfigSpec.EnumValue<ClientEffectPolicy.Preset> accessibilityPreset;
        public ModConfigSpec.EnumValue<ClientEffectPolicy.EffectOverride> scanPulseEffects;
        public ModConfigSpec.EnumValue<ClientEffectPolicy.EffectOverride> darkBallEffects;
        public ModConfigSpec.EnumValue<ClientEffectPolicy.EffectOverride> moonwoundEffects;
        public ModConfigSpec.EnumValue<ClientEffectPolicy.EffectOverride> auraEffects;
        public ModConfigSpec.EnumValue<ClientEffectPolicy.EffectOverride> ambientEffects;
        public ModConfigSpec.BooleanValue enableShadowAura;
        public ModConfigSpec.EnumValue<AuraPixelationStyle> colosseumAuraPixelation;
        public ModConfigSpec.EnumValue<AuraPixelationStyle> xdAuraPixelation;
        public ModConfigSpec.DoubleValue auraReaderYOffset;

        // Aura Reader HUD
        public ModConfigSpec.DoubleValue auraReaderHudScale;
        public ModConfigSpec.DoubleValue auraReaderCompassWidth;
        public ModConfigSpec.IntValue auraReaderTopInset;
        public ModConfigSpec.IntValue auraReaderCompassVisibleArc;
        public ModConfigSpec.DoubleValue auraReaderReticleScale;
        public ModConfigSpec.DoubleValue auraReaderHudOpacity;
        public ModConfigSpec.BooleanValue auraReaderClusterMarkers;
        public ModConfigSpec.IntValue auraReaderBottomSafeMargin;
        public ModConfigSpec.IntValue auraReaderPartyOverlayMargin;
        public ModConfigSpec.IntValue auraReaderRightOverlayMargin;
        public ModConfigSpec.BooleanValue auraReaderReducedMotion;
        public ModConfigSpec.DoubleValue auraReaderStaticIntensity;

        // Moonwound objectives
        public ModConfigSpec.EnumValue<MoonwoundObjectiveDisplay> moonwoundObjectiveDisplay;
        public ModConfigSpec.EnumValue<MoonwoundObjectivePlacement> moonwoundObjectivePlacement;
        public ModConfigSpec.IntValue moonwoundObjectiveOffsetX;
        public ModConfigSpec.IntValue moonwoundObjectiveOffsetY;

        public ModConfigSpec.EnumValue<MoonwoundPortalStyle> moonwoundPortalStyle;

        public ModConfigSpec.DoubleValue shadowAuraInitialBurstVolume;
        public ModConfigSpec.DoubleValue shadowAuraLoopVolume;
        public ModConfigSpec.DoubleValue auraScannerBeepVolume;
        public ModConfigSpec.DoubleValue relicShrineLoopVolume;
        public ModConfigSpec.DoubleValue moonwoundAmbientLoopVolume;
        public ModConfigSpec.DoubleValue moonwoundOpeningVolume;
        public ModConfigSpec.DoubleValue voidShadowTransformVolume;
        public ModConfigSpec.DoubleValue shadowSignalSearchVolume;
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
        public ModConfigSpec.EnumValue<DarkBallQuality> darkBallVfxQuality;

        private void build(ModConfigSpec.Builder builder) {
            builder.translation("shadowedhearts.configuration.client.accessibility").push("accessibility");
            accessibilityPreset = builder
                    .comment("DEFAULT preserves existing effects. REDUCED_MOTION and REDUCED_FLASHES use quieter presentation; MINIMAL removes optional effects while keeping capture and objective feedback. Family overrides take precedence over this preset. Existing individual disable/reduction options still apply.")
                    .translation("shadowedhearts.configuration.client.accessibility.preset")
                    .defineEnum("preset", ClientEffectPolicy.Preset.DEFAULT);
            scanPulseEffects = effectOverride(builder, "scanPulse",
                    "World scan wave: FULL shows it; REDUCED and OFF retain only the existing HUD/sound feedback. Scanning results and range are unchanged.");
            darkBallEffects = effectOverride(builder, "darkBall",
                    "Dark Ball captures: FULL uses the custom effect; REDUCED and OFF use the native capture beam/model. Applies when each capture starts; captures already in progress finish their current presentation.");
            moonwoundEffects = effectOverride(builder, "moonwound",
                    "Moonwound: REDUCED keeps a stationary portal without flowing surface miasma; OFF hides portal effects. Objective HUD/chat and event state are unchanged.");
            auraEffects = effectOverride(builder, "aura",
                    "Shadow aura: REDUCED lowers density/detail; OFF hides world and menu aura visuals. The existing enableShadowAura switch still applies.");
            ambientEffects = effectOverride(builder, "ambient",
                    "Ambient details: REDUCED lowers ball trail/mote density and uses the core's static block texture; OFF hides these optional details. Does not hide blocks or Pokemon.");
            builder.pop();

            enableShadowAura = builder
                    .comment("Master toggle for client-side Shadow aura rendering.")
                    .translation("shadowedhearts.configuration.client.enableShadowAura")
                    .define("enableShadowAura", true);

            builder.translation("shadowedhearts.configuration.client.shadowAura").push("shadowAura");
            colosseumAuraPixelation = builder
                    .comment("Colosseum aura appearance in the world and menu previews: SMOOTH keeps the current appearance, PIXELATED adds a pixel grid, and CHUNKY uses larger texels. This is a local visual preference.")
                    .translation("shadowedhearts.configuration.client.shadowAura.colosseumPixelation")
                    .defineEnum("colosseumPixelation", AuraPixelationStyle.SMOOTH);
            xdAuraPixelation = builder
                    .comment("XD Gale of Darkness aura appearance in the world and menu previews: SMOOTH keeps the current appearance, PIXELATED adds a pixel grid, and CHUNKY uses larger texels. This is a local visual preference.")
                    .translation("shadowedhearts.configuration.client.shadowAura.xdPixelation")
                    .defineEnum("xdPixelation", AuraPixelationStyle.SMOOTH);
            builder.pop();

            shadowAuraStyle = builder
                    .comment(
                            "Default Shadow aura presentation. A Pokemon with an explicit aura-style aspect overrides this value.",
                            "Allowed values: signature, colosseum, xd_faithful.")
                    .define(
                            "shadowAuraStyle",
                            ShadowAuraStyle.DEFAULT.serializedName(),
                            ShadowAuraStyle::isValidSerializedName);

            debugShadowAuraEmitters = builder
                    .comment("Debug: render tiny red marker spheres at Shadow aura emitter anchor positions.")
                    .define("debugShadowAuraEmitters", false);
            

            auraReaderYOffset = builder
                    .comment("The Y offset for the Aura Reader model.")
                    .translation("shadowedhearts.configuration.client.auraReaderYOffset")
                    .defineInRange("auraReaderYOffset", -0.15, -10.0, 10.0);

            builder.translation("shadowedhearts.configuration.client.auraReaderHud").push("auraReaderHud");
            auraReaderHudScale = builder
                    .comment("Scale applied to Aura Reader navigation HUD elements. Scanner rings and connected info frames use the separate reticle scale.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.hudScale")
                    .defineInRange("hudScale", 1.0, 0.5, 2.0);
            auraReaderCompassWidth = builder
                    .comment("Compass rail width as a fraction of the scaled GUI width.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.compassWidth")
                    .defineInRange("compassWidth", 0.42, 0.25, 0.85);
            auraReaderTopInset = builder
                    .comment("Top inset, in scaled GUI pixels, for the compass group. Negative values place it at the top edge.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.topInset")
                    .defineInRange("topInset", 16, -64, 224);
            auraReaderCompassVisibleArc = builder
                    .comment("Horizontal field of view, in degrees, represented by the compass rail.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.compassVisibleArc")
                    .defineInRange("compassVisibleArc", 90, 45, 180);
            auraReaderReticleScale = builder
                    .comment("Scale applied to the central Aura Reader scanner and connected info frames. 1.0 matches the Pokédex scanner's native 116 GUI-pixel outer ring, independently of navigation HUD scale.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.reticleScale")
                    .defineInRange("reticleScale", 1.0, 0.5, 1.75);
            auraReaderHudOpacity = builder
                    .comment("Opacity applied to Aura Reader HUD frames, markers, and text.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.hudOpacity")
                    .defineInRange("hudOpacity", 0.9, 0.2, 1.0);
            auraReaderClusterMarkers = builder
                    .comment("Cluster nearby low-priority compass markers to reduce visual clutter.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.clusterMarkers")
                    .define("clusterMarkers", true);
            auraReaderBottomSafeMargin = builder
                    .comment("Reserved bottom safe area, in scaled GUI pixels, for scanner information panels and connector arms. The feedback card stays centered above the reticle where space permits, may overlap the upper ring to clear the compass, and fits the viewport independently of safe margins.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.bottomSafeMargin")
                    .defineInRange("bottomSafeMargin", 104, 48, 180);
            auraReaderPartyOverlayMargin = builder
                    .comment("Extra clearance, in scaled GUI pixels, around the Cobblemon party overlay.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.partyOverlayMargin")
                    .defineInRange("partyOverlayMargin", 12, 0, 96);
            auraReaderRightOverlayMargin = builder
                    .comment("Extra clearance, in scaled GUI pixels, from right-side overlays such as status effects or minimaps.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.rightOverlayMargin")
                    .defineInRange("rightOverlayMargin", 8, 0, 160);
            auraReaderReducedMotion = builder
                    .comment("Reduce Aura Reader HUD movement, flashing, and animated static while retaining state cues.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.reducedMotion")
                    .define("reducedMotion", false);
            auraReaderStaticIntensity = builder
                    .comment("Intensity of Aura Reader interference and static effects; 0 disables them.")
                    .translation("shadowedhearts.configuration.client.auraReaderHud.staticIntensity")
                    .defineInRange("staticIntensity", 1.0, 0.0, 1.0);
            builder.pop();

            builder.translation("shadowedhearts.configuration.client.moonwoundObjective").push("moonwoundObjective");
            moonwoundObjectiveDisplay = builder
                    .comment("Show Moonwound objectives in an Aura Reader-style HUD infobox, send updates to chat, or hide them. This only changes your display.")
                    .translation("shadowedhearts.configuration.client.moonwoundObjective.display")
                    .defineEnum("display", MoonwoundObjectiveDisplay.HUD);
            moonwoundObjectivePlacement = builder
                    .comment("Preferred position for the Moonwound infobox. AUTO prefers the right side below notifications. All positions shift to clear known HUD elements and stay on screen. Only applies to HUD mode.")
                    .translation("shadowedhearts.configuration.client.moonwoundObjective.placement")
                    .defineEnum("placement", MoonwoundObjectivePlacement.AUTO);
            moonwoundObjectiveOffsetX = builder
                    .comment("Horizontal adjustment from the chosen HUD position, in scaled GUI pixels. Positive moves right; negative moves left.")
                    .translation("shadowedhearts.configuration.client.moonwoundObjective.offsetX")
                    .defineInRange("offsetX", 0, -2048, 2048);
            moonwoundObjectiveOffsetY = builder
                    .comment("Vertical adjustment from the chosen HUD position, in scaled GUI pixels. Positive moves down; negative moves up.")
                    .translation("shadowedhearts.configuration.client.moonwoundObjective.offsetY")
                    .defineInRange("offsetY", 0, -2048, 2048);
            builder.pop();

            builder.translation("shadowedhearts.configuration.client.moonwoundPortal").push("moonwoundPortal");
            moonwoundPortalStyle = builder
                    .comment("Moonwound portal and miasma appearance: PIXELATED is the default. SMOOTH keeps full detail; CHUNKY adds stronger pixelation. Texels scale continuously with the portal and soften in the distance.")
                    .translation("shadowedhearts.configuration.client.moonwoundPortal.style")
                    .defineEnum("style", MoonwoundPortalStyle.PIXELATED);
            builder.pop();

            builder.translation("shadowedhearts.configuration.client.sounds").push("sounds");
            shadowAuraInitialBurstVolume = builder
                    .comment("The volume of the Shadow Aura initial burst sound.")
                    .translation("shadowedhearts.configuration.client.sounds.shadowAuraInitialBurstVolume")
                    .defineInRange("shadowAuraInitialBurstVolume", 3.0, 0.0, 10.0);
            shadowAuraLoopVolume = builder
                    .comment("The volume of the Shadow Aura loop sound.")
                    .translation("shadowedhearts.configuration.client.sounds.shadowAuraLoopVolume")
                    .defineInRange("shadowAuraLoopVolume", 1.0, 0.0, 10.0);
            auraScannerBeepVolume = builder
                    .comment("The volume of the Aura Scanner beep sound.")
                    .translation("shadowedhearts.configuration.client.sounds.auraScannerBeepVolume")
                    .defineInRange("auraScannerBeepVolume", 1.0, 0.0, 10.0);
            relicShrineLoopVolume = builder
                    .comment("The volume of the Relic Shrine loop sound.")
                    .translation("shadowedhearts.configuration.client.sounds.relicShrineLoopVolume")
                    .defineInRange("relicShrineLoopVolume", 1.0, 0.0, 10.0);
            moonwoundAmbientLoopVolume = builder
                    .comment("The volume of active Moonwound ambience. Also controlled by Minecraft's Ambient/Environment sound volume.")
                    .translation("shadowedhearts.configuration.client.sounds.moonwoundAmbientLoopVolume")
                    .defineInRange("moonwoundAmbientLoopVolume", 1.0, 0.0, 10.0);
            moonwoundOpeningVolume = builder
                    .comment("The volume of the Moonwound opening sound. Also controlled by Minecraft's Ambient/Environment sound volume.")
                    .translation("shadowedhearts.configuration.client.sounds.moonwoundOpeningVolume")
                    .defineInRange("moonwoundOpeningVolume", 1.0, 0.0, 10.0);
            voidShadowTransformVolume = builder
                    .comment("The volume of the Void Shadow transformation sound. Also controlled by Minecraft's Friendly Creatures sound volume.")
                    .translation("shadowedhearts.configuration.client.sounds.voidShadowTransformVolume")
                    .defineInRange("voidShadowTransformVolume", 1.0, 0.0, 10.0);
            shadowSignalSearchVolume = builder
                    .comment("The volume of the Shadow Signal Data search loop. Also controlled by Minecraft's Players sound volume.")
                    .translation("shadowedhearts.configuration.client.sounds.shadowSignalSearchVolume")
                    .defineInRange("shadowSignalSearchVolume", 1.0, 0.0, 10.0);
            auraReaderEquipVolume = builder
                    .comment("The volume of the Aura Reader equip sound.")
                    .translation("shadowedhearts.configuration.client.sounds.auraReaderEquipVolume")
                    .defineInRange("auraReaderEquipVolume", 1.0, 0.0, 10.0);
            auraReaderUnequipVolume = builder
                    .comment("The volume of the Aura Reader unequip sound.")
                    .translation("shadowedhearts.configuration.client.sounds.auraReaderUnequipVolume")
                    .defineInRange("auraReaderUnequipVolume", 1.0, 0.0, 10.0);
            builder.pop();

            builder.translation("shadowedhearts.configuration.client.snagVfx").push("snagVfx");
            snagParticleMultiplier = builder
                    .comment("Multiplier for snag capture spark counts (0.0 = no sparks, 1.0 = default, 2.0 = double).")
                    .translation("shadowedhearts.configuration.client.snagVfx.snagParticleMultiplier")
                    .defineInRange("snagParticleMultiplier", 1.0, 0.0, 3.0);
            snagBloomEnabled = builder
                    .comment("Whether the bloom/glow post-processing pass is enabled for snag ball VFX.")
                    .translation("shadowedhearts.configuration.client.snagVfx.snagBloomEnabled")
                    .define("snagBloomEnabled", true);
            snagBloomIntensity = builder
                    .comment("Intensity of the bloom effect (0.0-2.0).")
                    .translation("shadowedhearts.configuration.client.snagVfx.snagBloomIntensity")
                    .defineInRange("snagBloomIntensity", 0.8, 0.0, 2.0);
            snagLensFlareEnabled = builder
                    .comment("Whether lens flare is rendered at beam convergence.")
                    .translation("shadowedhearts.configuration.client.snagVfx.snagLensFlareEnabled")
                    .define("snagLensFlareEnabled", true);
            snagDissolveEnabled = builder
                    .comment("Whether the dissolve shader is applied to the Pok\u00e9mon during absorption.")
                    .translation("shadowedhearts.configuration.client.snagVfx.snagDissolveEnabled")
                    .define("snagDissolveEnabled", true);
            snagShakeVfxEnabled = builder
                    .comment("Whether shake-phase VFX (seam glow, ground pulses, sparks) are enabled.")
                    .translation("shadowedhearts.configuration.client.snagVfx.snagShakeVfxEnabled")
                    .define("snagShakeVfxEnabled", true);
            snagReducedMotion = builder
                    .comment("Reduce snag capture flashes, rotating glare and transient pulses. Accessibility presets also enable this and reduce bloom, lens flare and sparks.")
                    .translation("shadowedhearts.configuration.client.snagVfx.snagReducedMotion")
                    .define("snagReducedMotion", false);
            builder.pop();

            builder.translation("shadowedhearts.configuration.client.darkBallVfx").push("darkBallVfx");
            darkBallVfxQuality = builder
                    .comment("Anime-style Dark Ball capture quality: low, medium, or high.")
                    .translation("shadowedhearts.configuration.client.darkBallVfx.quality")
                    .defineEnum("quality", DarkBallQuality.MEDIUM);
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

        private ModConfigSpec.EnumValue<ClientEffectPolicy.EffectOverride> effectOverride(
                ModConfigSpec.Builder builder, String name, String description) {
            return builder.comment("INHERIT follows the accessibility preset. " + description)
                    .translation("shadowedhearts.configuration.client.accessibility." + name)
                    .defineEnum(name, ClientEffectPolicy.EffectOverride.INHERIT);
        }
    }

    @Override
    public ClientEffectPolicy.Preset accessibilityPreset() {
        return isLoaded() ? DATA.accessibilityPreset.get() : IClientConfig.super.accessibilityPreset();
    }

    @Override
    public ClientEffectPolicy.EffectOverride scanPulseEffects() {
        return isLoaded() ? DATA.scanPulseEffects.get() : IClientConfig.super.scanPulseEffects();
    }

    @Override
    public ClientEffectPolicy.EffectOverride darkBallEffects() {
        return isLoaded() ? DATA.darkBallEffects.get() : IClientConfig.super.darkBallEffects();
    }

    @Override
    public ClientEffectPolicy.EffectOverride moonwoundEffects() {
        return isLoaded() ? DATA.moonwoundEffects.get() : IClientConfig.super.moonwoundEffects();
    }

    @Override
    public ClientEffectPolicy.EffectOverride auraEffects() {
        return isLoaded() ? DATA.auraEffects.get() : IClientConfig.super.auraEffects();
    }

    @Override
    public ClientEffectPolicy.EffectOverride ambientEffects() {
        return isLoaded() ? DATA.ambientEffects.get() : IClientConfig.super.ambientEffects();
    }

    @Override
    public boolean enableShadowAura() {
        if (!isLoaded()) return IClientConfig.super.enableShadowAura();
        return DATA.enableShadowAura.get();
    }

    @Override
    public AuraPixelationStyle colosseumAuraPixelation() {
        if (!isLoaded()) return IClientConfig.super.colosseumAuraPixelation();
        return DATA.colosseumAuraPixelation.get();
    }

    @Override
    public AuraPixelationStyle xdAuraPixelation() {
        if (!isLoaded()) return IClientConfig.super.xdAuraPixelation();
        return DATA.xdAuraPixelation.get();
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
    public int auraReaderTopInset() {
        if (!isLoaded()) return IClientConfig.super.auraReaderTopInset();
        return DATA.auraReaderTopInset.get();
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
    public boolean auraReaderClusterMarkers() {
        if (!isLoaded()) return IClientConfig.super.auraReaderClusterMarkers();
        return DATA.auraReaderClusterMarkers.get();
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
    public int auraReaderRightOverlayMargin() {
        if (!isLoaded()) return IClientConfig.super.auraReaderRightOverlayMargin();
        return DATA.auraReaderRightOverlayMargin.get();
    }

    @Override
    public boolean auraReaderReducedMotion() {
        if (!isLoaded()) return IClientConfig.super.auraReaderReducedMotion();
        return ClientEffectPolicy.reduceHudMotion(accessibilityPreset(), DATA.auraReaderReducedMotion.get());
    }

    @Override
    public float auraReaderStaticIntensity() {
        if (!isLoaded()) return IClientConfig.super.auraReaderStaticIntensity();
        return ClientEffectPolicy.staticIntensity(accessibilityPreset(), DATA.auraReaderStaticIntensity.get().floatValue());
    }

    @Override
    public MoonwoundObjectiveDisplay moonwoundObjectiveDisplay() {
        if (!isLoaded()) return IClientConfig.super.moonwoundObjectiveDisplay();
        return DATA.moonwoundObjectiveDisplay.get();
    }

    @Override
    public MoonwoundObjectivePlacement moonwoundObjectivePlacement() {
        if (!isLoaded()) return IClientConfig.super.moonwoundObjectivePlacement();
        return DATA.moonwoundObjectivePlacement.get();
    }

    @Override
    public int moonwoundObjectiveOffsetX() {
        if (!isLoaded()) return IClientConfig.super.moonwoundObjectiveOffsetX();
        return DATA.moonwoundObjectiveOffsetX.get();
    }

    @Override
    public int moonwoundObjectiveOffsetY() {
        if (!isLoaded()) return IClientConfig.super.moonwoundObjectiveOffsetY();
        return DATA.moonwoundObjectiveOffsetY.get();
    }

    @Override
    public MoonwoundPortalStyle moonwoundPortalStyle() {
        if (!isLoaded()) return IClientConfig.super.moonwoundPortalStyle();
        return DATA.moonwoundPortalStyle.get();
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
    public float moonwoundAmbientLoopVolume() {
        if (!isLoaded()) return ISoundConfig.super.moonwoundAmbientLoopVolume();
        return DATA.moonwoundAmbientLoopVolume.get().floatValue();
    }

    @Override
    public float moonwoundOpeningVolume() {
        if (!isLoaded()) return ISoundConfig.super.moonwoundOpeningVolume();
        return DATA.moonwoundOpeningVolume.get().floatValue();
    }

    @Override
    public float voidShadowTransformVolume() {
        if (!isLoaded()) return ISoundConfig.super.voidShadowTransformVolume();
        return DATA.voidShadowTransformVolume.get().floatValue();
    }

    @Override
    public float shadowSignalSearchVolume() {
        if (!isLoaded()) return ISoundConfig.super.shadowSignalSearchVolume();
        return DATA.shadowSignalSearchVolume.get().floatValue();
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
        return ClientEffectPolicy.snagParticles(accessibilityPreset(), DATA.snagParticleMultiplier.get().floatValue());
    }

    @Override
    public boolean snagBloomEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagBloomEnabled();
        return ClientEffectPolicy.allowSnagFlashes(accessibilityPreset(), DATA.snagBloomEnabled.get());
    }

    @Override
    public float snagBloomIntensity() {
        if (!isLoaded()) return IClientConfig.super.snagBloomIntensity();
        return DATA.snagBloomIntensity.get().floatValue();
    }

    @Override
    public boolean snagLensFlareEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagLensFlareEnabled();
        return ClientEffectPolicy.allowSnagFlashes(accessibilityPreset(), DATA.snagLensFlareEnabled.get());
    }

    @Override
    public boolean snagDissolveEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagDissolveEnabled();
        return ClientEffectPolicy.allowSnagFlashes(accessibilityPreset(), DATA.snagDissolveEnabled.get());
    }

    @Override
    public boolean snagShakeVfxEnabled() {
        if (!isLoaded()) return IClientConfig.super.snagShakeVfxEnabled();
        return ClientEffectPolicy.allowSnagFlashes(accessibilityPreset(), DATA.snagShakeVfxEnabled.get());
    }

    @Override
    public boolean snagReducedMotion() {
        if (!isLoaded()) return IClientConfig.super.snagReducedMotion();
        return ClientEffectPolicy.reduceSnagMotion(accessibilityPreset(), DATA.snagReducedMotion.get());
    }

    @Override
    public String darkBallVfxQuality() {
        if (!isLoaded()) return IClientConfig.super.darkBallVfxQuality();
        return DATA.darkBallVfxQuality.get().name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    @Override
    public ShadowAuraStyle shadowAuraStyle() {
        if (!isLoaded()) return IClientConfig.super.shadowAuraStyle();
        return ShadowAuraStyle.fromSerializedName(DATA.shadowAuraStyle.get());
    }

    @Override
    public boolean debugShadowAuraEmitters() {
        if (!isLoaded()) return IClientConfig.super.debugShadowAuraEmitters();
        return DATA.debugShadowAuraEmitters.get();
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
    public void load() {
        loaded = true;
        Shadowedhearts.LOGGER.info("ClientConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded && SPEC.isLoaded();
    }
}
