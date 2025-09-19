package com.jayemceekay.shadowedhearts.client.fabric;

import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteParticle;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class ShadowedheartsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Client-side common init
        AuraEmitters.init();
        //DepthCapture.init();
        ModShaders.initClient();

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
            LuminousMoteEmitters.onRender(context.camera().getPartialTickTime());
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, clientLevel) -> {
            AuraEmitters.onPokemonDespawn(entity.getId());
        });
    }
}
