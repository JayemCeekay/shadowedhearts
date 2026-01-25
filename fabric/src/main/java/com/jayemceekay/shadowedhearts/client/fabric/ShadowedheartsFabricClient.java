package com.jayemceekay.shadowedhearts.client.fabric;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.gui.AuraScannerHUD;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteParticle;
import com.jayemceekay.shadowedhearts.client.particle.RelicStoneMoteParticle;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
import com.jayemceekay.shadowedhearts.client.sound.RelicStoneSoundManager;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModBlocks;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager;
import com.jayemceekay.shadowedhearts.items.ScentItem;
import com.jayemceekay.shadowedhearts.util.HeldItemAnchorCache;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.felnull.specialmodelloader.api.event.SpecialModelLoaderEvents;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.config.ModConfig.Type;

public final class ShadowedheartsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeoForgeConfigRegistry.INSTANCE.register(Shadowedhearts.MOD_ID, Type.CLIENT, ClientConfig.SPEC, "shadowedhearts/client.toml");
        NeoForgeModConfigEvents.loading(Shadowedhearts.MOD_ID).register(config -> {
            if (config.getSpec() == ClientConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getClientConfig().load();
            }
        });
        NeoForgeModConfigEvents.reloading(Shadowedhearts.MOD_ID).register(config -> {
            if (config.getSpec() == ClientConfig.SPEC) {
                ShadowedHeartsConfigs.getInstance().getClientConfig().load();
            }
        });
        // Register client-side handlers for our Cobblemon-style packets
        ShadowedHeartsFabricNetworkManager.registerClientHandlers();
        // Client-side common init
        AuraEmitters.init();
        AuraPulseRenderer.init();
        DepthCapture.init();
        ModShaders.initClient();
        ModShadersPlatformImpl.registerShaders();
        // Register keybinds
        ModKeybinds.init();
        ModKeybindsPlatformImpl.register(ModKeybinds.ORDER_WHEEL);
        ModKeybindsPlatformImpl.register(ModKeybinds.AURA_SCANNER);
        ModKeybindsPlatformImpl.register(ModKeybinds.AURA_PULSE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AuraScannerHUD.tick();
            AuraPulseRenderer.tick();
            RelicStoneSoundManager.tick();
        });
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) -> AuraScannerHUD.render(guiGraphics, tickCounter.getGameTimeDeltaPartialTick(true)));

        // Special Model Loader registration
        SpecialModelLoaderEvents.LOAD_SCOPE.register(() -> (resourceManager, location) -> Shadowedhearts.MOD_ID.equals(location.getNamespace()));

        // Set RenderType for Relic Stone
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.RELIC_STONE.get(), RenderType.cutout());

        com.jayemceekay.shadowedhearts.client.ShadowedHeartsClient.init();

        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
            (entityType, renderer, helper, context) -> {
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
                    playerRenderer.addLayer(
                        new com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderArmorLayer(
                            playerRenderer,
                            context.getModelSet()
                        )
                    );
                }
            }
        );

        // Particle factory for luminous motes
        ParticleFactoryRegistry.getInstance().register(
                ModParticleTypes.LUMINOUS_MOTE.get(),
                LuminousMoteParticle.Provider::new
        );
        ParticleFactoryRegistry.getInstance().register(
                ModParticleTypes.RELIC_STONE_MOTE.get(),
                RelicStoneMoteParticle.Provider::new
        );

        // Subscribe luminous mote emitters to Cobblemon events
        LuminousMoteEmitters.init();

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            AuraEmitters.onRender(context.camera(), context.camera().getPartialTickTime());
            BallEmitters.onRender(context.camera(), context.camera().getPartialTickTime());
        });

        WorldRenderEvents.END.register(worldRenderContext -> {
            if ((AuraPulseRenderer.IRIS_HANDLER == null || !AuraPulseRenderer.IRIS_HANDLER.isShaderPackInUse())) {
                AuraPulseRenderer.onRenderWorld(worldRenderContext.camera(), worldRenderContext.projectionMatrix(), worldRenderContext.positionMatrix(), worldRenderContext.camera().getPartialTickTime());
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> LuminousMoteEmitters.onRender(context.tickCounter().getGameTimeDeltaPartialTick(true)));

        WorldRenderEvents.LAST.register(context -> {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;

            PoseStack pose = context.matrixStack();
            var buffers = mc.renderBuffers().bufferSource();
            float pt = context.tickCounter().getGameTimeDeltaPartialTick(true);

            // "frame id" so we only use anchors captured this same frame
            int frameId = (int) (System.nanoTime() & 0x7fffffff);
            Vec3 view = context.camera().getPosition();

            pose.pushPose();
            pose.translate(-view.x, -view.y, -view.z);

            for (var p : mc.level.players()) {
                var a = HeldItemAnchorCache.get(p, frameId);
                if (a == null) continue;

                pose.pushPose();
                pose.translate(a.worldPos().x, a.worldPos().y, a.worldPos().z);
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

                if (p == mc.player && !mc.options.getCameraType().isFirstPerson()) {
                    if (!HeldBallSnagGlowRenderer.isPokeball(p.getMainHandItem()))
                        continue;
                    HeldBallSnagGlowRenderer.renderAtHandPoseThirdPerson(pt, pose, buffers);
                }

                if (p != mc.player) {
                    if (!HeldBallSnagGlowRenderer.isPokeball(p.getMainHandItem()))
                        continue;
                    HeldBallSnagGlowRenderer.renderAtHandPoseThirdPerson(pt, pose, buffers);
                }

                pose.popPose();
            }

            pose.popPose();
            buffers.endBatch();
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, clientLevel) -> {
            AuraEmitters.onPokemonDespawn(entity.getId());
            if (entity instanceof EmptyPokeBallEntity) {
                BallEmitters.onEntityDespawn(entity.getId());
            }
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, clientLevel) -> {
            if (entity instanceof EmptyPokeBallEntity e) {
                BallEmitters.startForEntity(e);
            }
        });
        // Register Snag Machine accessory renderer
        if (dev.architectury.platform.Platform.isModLoaded("accessories")) {
            try {
                AccessoriesRendererBridge.registerRenderers();
            } catch (Throwable ignored) {
            }
        }

        // Register Item Colors
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
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
}
