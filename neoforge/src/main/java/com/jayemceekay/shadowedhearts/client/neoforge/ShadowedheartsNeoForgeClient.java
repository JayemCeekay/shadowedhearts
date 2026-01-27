package com.jayemceekay.shadowedhearts.client.neoforge;


import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.client.ShadowedHeartsClient;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteParticle;
import com.jayemceekay.shadowedhearts.client.particle.RelicStoneMoteParticle;
import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import com.jayemceekay.shadowedhearts.items.ScentItem;
import com.jayemceekay.shadowedhearts.util.HeldItemAnchorCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
@Mod(value = Shadowedhearts.MOD_ID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ShadowedheartsNeoForgeClient {


    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Client-side common init
        ClientSetupSubscriber.onClientSetup(event);
    }

    public ShadowedheartsNeoForgeClient(ModContainer modContainer) {
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::registerKeybinds);
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::onClientSetup);
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::registerItemColors);
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::onAddLayers);
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::registerParticles);
        ShadowedHeartsConfigs.getInstance().getClientConfig().load();
        modContainer.registerConfig(ModConfig.Type.CLIENT, ShadowedHeartsConfigs.getInstance().getClientConfig().getSpec(), "shadowedhearts/client.toml");
        ShadowedHeartsClient.init();
    }

    public static void registerKeybinds(RegisterKeyMappingsEvent event) {
        ModKeybinds.init();
        event.register(ModKeybinds.AURA_SCANNER);
        event.register(ModKeybinds.AURA_PULSE);
    }

    public static void registerParticles(RegisterParticleProvidersEvent evt) {
        evt.registerSpriteSet(
                ModParticleTypes.LUMINOUS_MOTE.get(),
                LuminousMoteParticle.Provider::new
        );
        evt.registerSpriteSet(
                ModParticleTypes.RELIC_STONE_MOTE.get(),
                RelicStoneMoteParticle.Provider::new
        );
    }

    public static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        net.minecraft.client.renderer.entity.player.PlayerRenderer defaultRenderer = event.getSkin(net.minecraft.client.resources.PlayerSkin.Model.WIDE);
        if (defaultRenderer != null) {
            defaultRenderer.addLayer(new com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderArmorLayer(defaultRenderer, event.getEntityModels()));
            defaultRenderer.addLayer(new com.jayemceekay.shadowedhearts.client.render.armor.SnagMachineAdvancedArmorLayer(defaultRenderer, event.getEntityModels()));
            defaultRenderer.addLayer(new com.jayemceekay.shadowedhearts.client.render.armor.SnagMachinePrototypeArmorLayer(defaultRenderer, event.getEntityModels()));
        }
        net.minecraft.client.renderer.entity.player.PlayerRenderer slimRenderer = event.getSkin(net.minecraft.client.resources.PlayerSkin.Model.SLIM);
        if (slimRenderer != null) {
            slimRenderer.addLayer(new com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderArmorLayer(slimRenderer, event.getEntityModels()));
            slimRenderer.addLayer(new com.jayemceekay.shadowedhearts.client.render.armor.SnagMachineAdvancedArmorLayer(slimRenderer, event.getEntityModels()));
            slimRenderer.addLayer(new com.jayemceekay.shadowedhearts.client.render.armor.SnagMachinePrototypeArmorLayer(slimRenderer, event.getEntityModels()));
        }
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

        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            float pt = e.getPartialTick().getGameTimeDeltaTicks() + e.getPartialTick().getGameTimeDeltaPartialTick(true);
            LuminousMoteEmitters.onRender(pt);
        }

        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;

            PoseStack pose = e.getPoseStack();
            var buffers = mc.renderBuffers().bufferSource();
            float pt = e.getPartialTick().getGameTimeDeltaTicks() + e.getPartialTick().getGameTimeDeltaPartialTick(true);

            // "frame id" so we only use anchors captured this same frame
            int frameId = mc.getFrameTimeNs() != 0 ? (int) (mc.getFrameTimeNs() & 0x7fffffff) : (int) (System.nanoTime() & 0x7fffffff);
            Vec3 view = mc.gameRenderer.getMainCamera().getPosition();

            pose.pushPose();
            pose.translate(-view.x, -view.y, -view.z); // world-space rendering in this pass :contentReference[oaicite:4]{index=4}

            for (var p : mc.level.players()) {
                var a = HeldItemAnchorCache.get(p, frameId);
                if (a == null) continue;

                pose.pushPose();

                pose.translate(a.worldPos().x, a.worldPos().y, a.worldPos().z);

                // Billboard to *this client’s* camera
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

                if (p == mc.player && !mc.options.getCameraType().isFirstPerson()) {
                    if (!HeldBallSnagGlowRenderer.isPokeball(p.getMainHandItem()))
                        continue;
                    HeldBallSnagGlowRenderer.renderAtHandPoseThirdPerson(pt, pose, buffers);
                }

                // draw your quad/rings here (optionally multiply by a.approxScale())
                if (p != mc.player) {
                    if (!HeldBallSnagGlowRenderer.isPokeball(p.getMainHandItem()))
                        continue;
                    HeldBallSnagGlowRenderer.renderAtHandPoseThirdPerson(pt, pose, buffers);
                }

                pose.popPose();
            }

            pose.popPose();
            buffers.endBatch();
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