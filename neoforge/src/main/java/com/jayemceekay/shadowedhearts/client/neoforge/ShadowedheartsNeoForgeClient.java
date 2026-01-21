package com.jayemceekay.shadowedhearts.client.neoforge;


import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.items.ScentItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
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
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::registerItemColors);
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
        } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && (AuraPulseRenderer.IRIS_HANDLER == null || !AuraPulseRenderer.IRIS_HANDLER.isShaderPackInUse())) {
            var cam = e.getCamera();
            AuraPulseRenderer.onRenderWorld(cam, e.getProjectionMatrix(), e.getModelViewMatrix(), cam.getPartialTickTime());
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

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "obj"), ObjLoader.INSTANCE);
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
                    if (tintIndex == 1 && stack.getItem() instanceof ScentItem scentItem) {
                        return 0xFF000000 | scentItem.getColor();
                    }
                    return 0xFFFFFFFF;
                },
                ModItems.JOY_SCENT.get(),
                ModItems.EXCITE_SCENT.get(),
                ModItems.VIVID_SCENT.get(),
                ModItems.TRANQUIL_SCENT.get(),
                ModItems.MEADOW_SCENT.get(),
                ModItems.SPARK_SCENT.get(),
                ModItems.FOCUS_SCENT.get(),
                ModItems.COMFORT_SCENT.get(),
                ModItems.FAMILIAR_SCENT.get(),
                ModItems.HEARTH_SCENT.get(),
                ModItems.INSIGHT_SCENT.get(),
                ModItems.LUCID_SCENT.get(),
                ModItems.RESOLVE_SCENT.get(),
                ModItems.STEADY_SCENT.get(),
                ModItems.GROUNDING_SCENT.get()
        );
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