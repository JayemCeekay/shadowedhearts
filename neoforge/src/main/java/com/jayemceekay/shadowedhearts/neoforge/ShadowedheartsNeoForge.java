package com.jayemceekay.shadowedhearts.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ConfigLifecycle;
import com.jayemceekay.shadowedhearts.config.ConfigMigrationBootstrap;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
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
            com.jayemceekay.shadowedhearts.network.aura.AuraServerSync.onStartTracking(event.getTarget(), player);
        }
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        ConfigLifecycle.loadGameplay(event.getConfig().getSpec(), event.getConfig()::getFullPath);
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        ConfigLifecycle.loadGameplay(event.getConfig().getSpec(), event.getConfig()::getFullPath);
    }


    public static void onConfigUnloading(ModConfigEvent.Unloading event) {
        ConfigLifecycle.unloadGameplay(event.getConfig().getSpec());
    }
}
