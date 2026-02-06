package com.jayemceekay.shadowedhearts.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.config.SnagConfig;
import com.jayemceekay.shadowedhearts.neoforge.worldgen.ShadowedHeartsBiomeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.config.ModConfigEvent;

@Mod(Shadowedhearts.MOD_ID)
public final class ShadowedheartsNeoForge {

    public ShadowedheartsNeoForge(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(Type.SERVER, ModConfig.SPEC, "shadowedhearts/common.toml");
        container.registerConfig(Type.SERVER, SnagConfig.SPEC, "shadowedhearts/snag.toml");

        modEventBus.addListener(ShadowedheartsNeoForge::onConfigLoading);
        modEventBus.addListener(ShadowedheartsNeoForge::onConfigReloading);
        modEventBus.addListener(ShadowedHeartsBiomeModifiers::register);

        Shadowedhearts.featureAdder = ShadowedHeartsBiomeModifiers::add;

        // Run our common setup.
        Shadowedhearts.init();
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
            Shadowedhearts.injectShowdownConfig();
        } else if (event.getConfig().getSpec() == SnagConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getSnagConfig().load();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
            Shadowedhearts.injectShowdownConfig();
        } else if (event.getConfig().getSpec() == SnagConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getSnagConfig().load();
        }
    }


}
