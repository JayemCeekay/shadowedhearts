package com.jayemceekay.shadowedhearts.fabric;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.config.SnagConfig;
import com.jayemceekay.shadowedhearts.config.TrainerSpawnConfig;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig.Type;

public final class ShadowedheartsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        NeoForgeConfigRegistry.INSTANCE.register(Shadowedhearts.MOD_ID, Type.COMMON, ModConfig.SPEC, "shadowedhearts/common.toml");
        NeoForgeConfigRegistry.INSTANCE.register(Shadowedhearts.MOD_ID, Type.COMMON, SnagConfig.SPEC, "shadowedhearts/snag.toml");
        NeoForgeConfigRegistry.INSTANCE.register(Shadowedhearts.MOD_ID, Type.COMMON, TrainerSpawnConfig.SPEC, "shadowedhearts/trainer_spawn.toml");

        NeoForgeModConfigEvents.loading(Shadowedhearts.MOD_ID).register(config -> {
            if (config.getSpec() == ModConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
            } else if (config.getSpec() == SnagConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getSnagConfig().load();
            } else if (config.getSpec() == TrainerSpawnConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getTrainerSpawnConfig().load();
            }
        });

        NeoForgeModConfigEvents.reloading(Shadowedhearts.MOD_ID).register(config -> {
            if (config.getSpec() == ModConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
            } else if (config.getSpec() == SnagConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getSnagConfig().load();
            } else if (config.getSpec() == TrainerSpawnConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getTrainerSpawnConfig().load();
            }
        });

        // Run our common setup.
        Shadowedhearts.init();
        FabricBrewingRecipes.register();
        // Register S2C network payloads and client handlers using Cobblemon's system
        com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager.registerMessages();
        // Register C2S codecs and server handlers
        com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager.registerServerHandlers();
    }
}
