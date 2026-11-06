package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Dedicated MOD bus subscriber for lifecycle (client setup) on NeoForge.
 * Keeps other FORGE-bus listeners in ShadowedheartsNeoForgeClient unaffected.
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetupSubscriber {

    private ClientSetupSubscriber() {}

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(com.jayemceekay.shadowedhearts.client.UmbraInfuserClient::registerRenderer);
        event.enqueueWork(com.jayemceekay.shadowedhearts.client.UmbrafallCoreClient::registerRenderer);
        // Client-side common init
        AuraReaderPulseRenderer.init();
        DepthCapture.init();
        ModShaders.initClient();
        ModShadersPlatformImpl.registerShaders();


        // Subscribe luminous mote emitters to Cobblemon events
        LuminousMoteEmitters.init();

        // Register Snag Machine accessory renderer
        if (dev.architectury.platform.Platform.isModLoaded("accessories")) {
            try {
                com.jayemceekay.shadowedhearts.client.neoforge.AccessoriesRendererBridge.registerRenderers();
            } catch (Throwable ignored) {}
        }
    }
}
