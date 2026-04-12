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
        public ModConfigSpec.BooleanValue auraScannerEnabled;
        public ModConfigSpec.BooleanValue useFahrenheitDisplay;
        public ModConfigSpec.DoubleValue auraReaderYOffset;

        public ModConfigSpec.DoubleValue shadowAuraInitialBurstVolume;
        public ModConfigSpec.DoubleValue shadowAuraLoopVolume;
        public ModConfigSpec.DoubleValue auraScannerBeepVolume;
        public ModConfigSpec.DoubleValue relicShrineLoopVolume;
        public ModConfigSpec.DoubleValue auraReaderEquipVolume;
        public ModConfigSpec.DoubleValue auraReaderUnequipVolume;

        private void build(ModConfigSpec.Builder builder) {
            enableShadowAura = builder
                    .comment("Master toggle for client-side Shadow aura rendering.")
                    .define("enableShadowAura", true);
            

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
        }
    }

    @Override
    public boolean enableShadowAura() {
        if (!isLoaded()) return IClientConfig.super.enableShadowAura();
        return DATA.enableShadowAura.get();
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
