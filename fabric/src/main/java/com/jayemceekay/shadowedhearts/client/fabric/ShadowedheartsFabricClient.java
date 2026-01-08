package com.jayemceekay.shadowedhearts.client.fabric;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteParticle;
import com.jayemceekay.shadowedhearts.client.render.BallGlowHudDebug;
import com.jayemceekay.shadowedhearts.client.trail.BallTrailHudDebug;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
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
        com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager.registerClientHandlers();
        // Client-side common init
        AuraEmitters.init();
        //DepthCapture.init();
        ModShaders.initClient();
        ModShadersPlatformImpl.registerShaders();
        // Register keybinds
        ModKeybinds.init();
        ModKeybindsPlatformImpl.register(ModKeybinds.ORDER_WHEEL);
        // Particle factory for luminous motes
        ParticleFactoryRegistry.getInstance().register(
                ModParticleTypes.LUMINOUS_MOTE.get(),
                LuminousMoteParticle.Provider::new
        );

        // Subscribe luminous mote emitters to Cobblemon events
        LuminousMoteEmitters.init();

        // Render hooks: aura + luminous motes at late translucent stage
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            AuraEmitters.onRender(context.camera(), context.camera().getPartialTickTime());
            BallEmitters.onRender(context.camera(), context.camera().getPartialTickTime());
            LuminousMoteEmitters.onRender(context.camera().getPartialTickTime());
        });

        // Always-on HUD preview of the trail shader for debugging
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            BallTrailHudDebug.render(graphics, tickDelta);
            BallGlowHudDebug.render(graphics, tickDelta);
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
                com.jayemceekay.shadowedhearts.client.fabric.AccessoriesRendererBridge.registerRenderers();
            } catch (Throwable ignored) {}
        }
    }
}
