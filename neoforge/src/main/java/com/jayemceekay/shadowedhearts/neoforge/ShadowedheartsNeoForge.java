package com.jayemceekay.shadowedhearts.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.config.SnagConfig;
import com.jayemceekay.shadowedhearts.config.TrainerSpawnConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.config.ModConfigEvent;

@Mod(Shadowedhearts.MOD_ID)
public final class ShadowedheartsNeoForge {

    public ShadowedheartsNeoForge(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(Type.COMMON, ModConfig.SPEC, "shadowedhearts/common.toml");
        container.registerConfig(Type.COMMON, SnagConfig.SPEC, "shadowedhearts/snag.toml");
        container.registerConfig(Type.COMMON, TrainerSpawnConfig.SPEC, "shadowedhearts/trainer_spawn.toml");

        modEventBus.addListener(ShadowedheartsNeoForge::onConfigLoading);
        modEventBus.addListener(ShadowedheartsNeoForge::onConfigReloading);
       // NeoForge.EVENT_BUS.addListener(this::onRegisterBrewing);

        // Run our common setup.
        Shadowedhearts.init();
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
        } else if (event.getConfig().getSpec() == SnagConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getSnagConfig().load();
        } else if (event.getConfig().getSpec() == TrainerSpawnConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getTrainerSpawnConfig().load();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getShadowConfig().load();
        } else if (event.getConfig().getSpec() == SnagConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getSnagConfig().load();
        } else if (event.getConfig().getSpec() == TrainerSpawnConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getTrainerSpawnConfig().load();
        }
    }

    /*private void onRegisterBrewing(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addContainerRecipe(CobblemonItems.BERRY_JUICE, CobblemonItems.POTION, ModItems.JOY_SCENT.get());
        event.getBuilder().addContainerRecipe(CobblemonItems.BERRY_JUICE, CobblemonItems.SUPER_POTION, ModItems.EXCITE_SCENT.get());
        event.getBuilder().addContainerRecipe(CobblemonItems.BERRY_JUICE, CobblemonItems.HYPER_POTION, ModItems.VIVID_SCENT.get());
    }*/
}
