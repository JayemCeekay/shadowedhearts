package com.jayemceekay.shadowedhearts.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.ConfigMigrationBootstrap;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorConfigBinding;
import com.jayemceekay.shadowedhearts.neoforge.net.ShadowedHeartsNeoForgeNetworkRegistrar;
import com.jayemceekay.shadowedhearts.neoforge.worldgen.ShadowedHeartsBiomeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;

@Mod(Shadowedhearts.MOD_ID)
public final class ShadowedheartsNeoForge {

    public ShadowedheartsNeoForge(IEventBus modEventBus, ModContainer container) {
        ConfigMigrationBootstrap.initialize(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()));
        // Both gameplay files are authoritative and synced, irrespective of their filenames.
        container.registerConfig(Type.SERVER, ModConfig.SPEC, "shadowedhearts/common.toml");
        container.registerConfig(Type.SERVER, ServerConfig.SPEC, "shadowedhearts/server.toml");

        modEventBus.addListener(ShadowedheartsNeoForge::onConfigLoading);
        modEventBus.addListener(ShadowedheartsNeoForge::onConfigReloading);
        modEventBus.addListener(ShadowedheartsNeoForge::onConfigUnloading);
        modEventBus.addListener(ShadowedHeartsBiomeModifiers::register);
        modEventBus.addListener(ShadowedHeartsNeoForgeNetworkRegistrar::registerMessages);

        Shadowedhearts.featureAdder = ShadowedHeartsBiomeModifiers::add;

        // Run our common setup.
        Shadowedhearts.init();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ShadowedheartsNeoForge::onStartTracking);
    }

    private static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.jayemceekay.shadowedhearts.network.AuraServerSync.onStartTracking(event.getTarget(), player);
        }
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService.loaded(event.getConfig().getSpec(), event.getConfig()::getFullPath);
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            bindTrainerConfig(event.getConfig());
            ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
            Shadowedhearts.injectShowdownConfig();
        } else if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            ServerConfig.INSTANCE.load();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService.loaded(event.getConfig().getSpec(), event.getConfig()::getFullPath);
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            bindTrainerConfig(event.getConfig());
            ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
            Shadowedhearts.injectShowdownConfig();
        } else if (event.getConfig().getSpec() == ServerConfig.SPEC) {
            ServerConfig.INSTANCE.load();
        }
    }


    public static void onConfigUnloading(ModConfigEvent.Unloading event) {
        com.jayemceekay.shadowedhearts.config.preset.ConfigSnapshotService.unloaded(event.getConfig().getSpec());
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            TrainerEditorConfigBinding.bind(null);
            com.jayemceekay.shadowedhearts.config.preset.RulesPresetService.bind(null);
            HeartGaugeConfig.reset();
        }
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
