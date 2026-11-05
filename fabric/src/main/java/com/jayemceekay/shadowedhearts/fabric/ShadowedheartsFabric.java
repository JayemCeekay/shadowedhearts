package com.jayemceekay.shadowedhearts.fabric;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.ConfigMigrationBootstrap;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorConfigBinding;
import dev.architectury.platform.Platform;
import fuzs.forgeconfigapiport.fabric.impl.config.ForgeConfigApiPortConfig;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.neoforged.fml.config.ModConfig.Type;

public final class ShadowedheartsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        Shadowedhearts.featureAdder = (feature, step, validTag) -> {
            BiomeModifications.addFeature(
                    context -> validTag == null || context.hasTag(validTag),
                    step,
                    feature
            );
        };

        ConfigMigrationBootstrap.initialize(Platform.getConfigFolder(), ForgeConfigApiPortConfig.getDefaultConfigsDirectory());
        // Both gameplay files are authoritative and synced, irrespective of their filenames.
        NeoForgeConfigRegistry.INSTANCE.register(Shadowedhearts.MOD_ID, Type.SERVER, ModConfig.SPEC, "shadowedhearts/common.toml");
        NeoForgeConfigRegistry.INSTANCE.register(Shadowedhearts.MOD_ID, Type.SERVER, ServerConfig.SPEC, "shadowedhearts/server.toml");

        NeoForgeModConfigEvents.loading(Shadowedhearts.MOD_ID).register(config -> {
            com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService.loaded(config.getSpec(), config::getFullPath);
            if (config.getSpec() == ModConfig.SPEC) {
                bindTrainerConfig(config);
                ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
                Shadowedhearts.injectShowdownConfig();
            } else if (config.getSpec() == ServerConfig.SPEC) {
                ServerConfig.INSTANCE.load();
            }
        });

        NeoForgeModConfigEvents.reloading(Shadowedhearts.MOD_ID).register(config -> {
            com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService.loaded(config.getSpec(), config::getFullPath);
            if (config.getSpec() == ModConfig.SPEC) {
                bindTrainerConfig(config);
                ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
                Shadowedhearts.injectShowdownConfig();
            } else if (config.getSpec() == ServerConfig.SPEC) {
                ServerConfig.INSTANCE.load();
            }
        });

        NeoForgeModConfigEvents.unloading(Shadowedhearts.MOD_ID).register(config -> {
            com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService.unloaded(config.getSpec());
            if (config.getSpec() == ModConfig.SPEC) {
                TrainerEditorConfigBinding.bind(null);
                com.jayemceekay.shadowedhearts.config.preset.RulesPresetService.bind(null);
                HeartGaugeConfig.reset();
            }
        });

        // Run our common setup.
        Shadowedhearts.init();
        FabricBrewingRecipes.register();
        // Register S2C network payloads and client handlers using Cobblemon's system
        com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager.registerMessages();
        // Register C2S codecs and server handlers
        com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager.registerServerHandlers();
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(
                com.jayemceekay.shadowedhearts.network.AuraServerSync::onStartTracking);
    }

    private static void bindTrainerConfig(net.neoforged.fml.config.ModConfig config) {
        try {
            TrainerEditorConfigBinding.bind(config.getFullPath());
            com.jayemceekay.shadowedhearts.config.preset.RulesPresetService.bind(config.getFullPath());
            HeartGaugeConfig.setLegacyFallbackAllowed(true);
        } catch (IllegalStateException nonFileConfig) {
            // Remote server config snapshots have no local backing file.
            TrainerEditorConfigBinding.bind(null);
            com.jayemceekay.shadowedhearts.config.preset.RulesPresetService.bind(null);
            HeartGaugeConfig.setLegacyFallbackAllowed(false);
        }
    }
}
