package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteParticle;
import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import com.jayemceekay.shadowedhearts.util.HeldItemAnchorCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * NeoForge client wiring for luminous motes: provider registration and render tick.
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = Dist.CLIENT)
public final class ClientInit {
    private ClientInit() {
    }

    public static void init(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ShadowedHeartsConfigs.getInstance().getClientConfig().getSpec(), "shadowedhearts/client.toml");

    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent evt) {
        // Load client config early on client
        evt.registerSpriteSet(
                ModParticleTypes.LUMINOUS_MOTE.get(),
                LuminousMoteParticle.Provider::new
        );
        evt.registerSpriteSet(
                ModParticleTypes.RELIC_STONE_MOTE.get(),
                com.jayemceekay.shadowedhearts.client.particle.RelicStoneMoteParticle.Provider::new
        );
    }
}

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = Dist.CLIENT)
final class ClientRenderHooks {
    private ClientRenderHooks() {
    }


    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        // Choose a late stage to render particles after most world translucency

        if(evt.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            float pt = evt.getPartialTick().getGameTimeDeltaTicks() + evt.getPartialTick().getGameTimeDeltaPartialTick(true);
            LuminousMoteEmitters.onRender(pt);
        }

        if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {

            var mc = Minecraft.getInstance();
            if (mc.level == null) return;

            PoseStack pose = evt.getPoseStack();
            var buffers = mc.renderBuffers().bufferSource();
            float pt = evt.getPartialTick().getGameTimeDeltaTicks() + evt.getPartialTick().getGameTimeDeltaPartialTick(true);

            // "frame id" so we only use anchors captured this same frame
            int frameId = mc.getFrameTimeNs() != 0 ? (int) (mc.getFrameTimeNs() & 0x7fffffff) : (int) (System.nanoTime() & 0x7fffffff);
            Vec3 view = mc.gameRenderer.getMainCamera().getPosition();

            pose.pushPose();
            pose.translate(-view.x, -view.y, -view.z); // world-space rendering in this pass :contentReference[oaicite:2]{index=2}

            for (var p : mc.level.players()) {
                var a = HeldItemAnchorCache.get(p, frameId);
                if (a == null) continue;

                pose.pushPose();

                pose.translate(a.worldPos().x, a.worldPos().y, a.worldPos().z);

                // Billboard to *this client’s* camera
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

                if(p == mc.player && !mc.options.getCameraType().isFirstPerson()) {
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
}
