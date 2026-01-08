package com.jayemceekay.shadowedhearts.client.neoforge;


import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
@Mod(value = Shadowedhearts.MOD_ID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ShadowedheartsNeoForgeClient {


    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Client-side common init
        ClientSetupSubscriber.onClientSetup(null);
    }

    public ShadowedheartsNeoForgeClient(ModContainer modContainer) {
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::onClientSetup);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ShadowedHeartsConfigs.getInstance().getClientConfig().getSpec());
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getClientConfig().load();
        }
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            ShadowedHeartsConfigs.getInstance().getClientConfig().load();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            var cam = e.getCamera();
            AuraEmitters.onRender(cam, cam.getPartialTickTime());
            BallEmitters.onRender(cam, cam.getPartialTickTime());
        }
    }


    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) {
            AuraEmitters.onPokemonDespawn(e.getEntity().getId());
            if (e.getEntity() instanceof EmptyPokeBallEntity) {
                BallEmitters.onEntityDespawn(e.getEntity().getId());
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) {
            if (e.getEntity() instanceof EmptyPokeBallEntity ball) {
                BallEmitters.startForEntity(ball);
            }
        }
    }
}