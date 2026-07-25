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
