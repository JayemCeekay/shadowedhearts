package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteParticle;
import com.jayemceekay.shadowedhearts.core.ModParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * NeoForge client wiring for luminous motes: provider registration and render tick.
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = Dist.CLIENT)
public final class ClientInit {
    private ClientInit() {}

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent evt) {
        evt.registerSpriteSet(
                ModParticleTypes.LUMINOUS_MOTE.get(),
                LuminousMoteParticle.Provider::new
        );

        // Subscribe emitters on client startup once
        LuminousMoteEmitters.init();
    }
}

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = Dist.CLIENT)
final class ClientRenderHooks {
    private ClientRenderHooks() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        // Choose a late stage to render particles after most world translucency
        if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            // Use event-provided partial tick to drive emitter timing
            LuminousMoteEmitters.onRender(evt.getPartialTick().getGameTimeDeltaTicks());
        }
    }
}
