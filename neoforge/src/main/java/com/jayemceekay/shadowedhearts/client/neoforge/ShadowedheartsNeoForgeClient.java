package com.jayemceekay.shadowedhearts.client.neoforge;


import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.client.ShadowedHeartsClient;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraEmitters;
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteParticle;
import com.jayemceekay.shadowedhearts.client.particle.PenumbraTrailSystem;
import com.jayemceekay.shadowedhearts.client.particle.PenumbraTrailParticle;
import com.jayemceekay.shadowedhearts.client.particle.RelicStoneMoteParticle;
import com.jayemceekay.shadowedhearts.client.render.VanillaWorldShaderFrame;
import com.jayemceekay.shadowedhearts.config.ConfigLifecycle;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.content.items.ScentItem;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.registry.ModMenus;
import com.jayemceekay.shadowedhearts.client.gui.UmbraInfuserScreen;
import com.jayemceekay.shadowedhearts.registry.util.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import com.jayemceekay.shadowedhearts.client.gui.ShadowedHeartsConfigScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
@Mod(value = Shadowedhearts.MOD_ID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ShadowedheartsNeoForgeClient {


    public ShadowedheartsNeoForgeClient(ModContainer modContainer) {
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::registerKeybinds);
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::registerItemColors);
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::onAddLayers);
        modContainer.getEventBus().addListener(ShadowedheartsNeoForgeClient::registerParticles);
       // ShadowedHeartsConfigs.getInstance().getClientConfig().load();
        modContainer.registerConfig(ModConfig.Type.CLIENT, ShadowedHeartsConfigs.getInstance().getClientConfig().getSpec(), "shadowedhearts/client.toml");
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) ->
                new ShadowedHeartsConfigScreen(parent));
        ShadowedHeartsClient.init();
    }

    public static void registerKeybinds(RegisterKeyMappingsEvent event) {
        ModKeybinds.init();
        event.register(ModKeybinds.AURA_SCANNER);
        event.register(ModKeybinds.AURA_READER_HELP);
        event.register(ModKeybinds.AURA_PULSE);
        event.register(ModKeybinds.AURA_LOCK);
        event.register(ModKeybinds.AURA_NEXT_SIGNAL);
        event.register(ModKeybinds.AURA_PREV_SIGNAL);
        event.register(ModKeybinds.DEBUG_REINIT_HUD);
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
        evt.registerSpriteSet(
                ModParticleTypes.PENUMBRA_TRAIL.get(),
                PenumbraTrailParticle.Provider::new
        );
        evt.registerSpriteSet(
                ModParticleTypes.VOID_SHADOW_CLOUD.get(),
                com.jayemceekay.shadowedhearts.client.particle.VoidShadowCloudParticle.Provider::new
        );
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.UMBRA_INFUSER.get(), UmbraInfuserScreen::new);
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
        ConfigLifecycle.loadClient(event.getConfig().getSpec(), event.getConfig()::getFullPath);
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        ConfigLifecycle.loadClient(event.getConfig().getSpec(), event.getConfig()::getFullPath);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            VanillaWorldShaderFrame.beginFrame();
        }
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            var cam = e.getCamera();
            ShadowAuraEmitters.onRender(cam, cam.getPartialTickTime());
            BallEmitters.onRender(cam, cam.getPartialTickTime());

        } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && !ShadowPokemonAuraSystem.isIrisShaderPackActive()) {
            // Fabulous combines its transparency targets after AFTER_WEATHER.
            PenumbraTrailSystem.compositeWorld();
            ShadowPokemonAuraSystem.compositeWorld();
            VanillaWorldShaderFrame.render();
        }

        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
            LuminousMoteEmitters.onRender(pt);
        }

        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
            PenumbraTrailSystem.renderWorldDensityOnly(e.getCamera(), pt,
                    e.getProjectionMatrix(), e.getModelViewMatrix());
            ShadowPokemonAuraSystem.renderWorldDensityOnly(e.getCamera(), pt,
                    e.getProjectionMatrix(), e.getModelViewMatrix());
            VanillaWorldShaderFrame.capture(e.getCamera(), pt, e.getProjectionMatrix(), e.getModelViewMatrix());
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) {
            ShadowAuraEmitters.onPokemonDespawn(e.getEntity().getId());
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
