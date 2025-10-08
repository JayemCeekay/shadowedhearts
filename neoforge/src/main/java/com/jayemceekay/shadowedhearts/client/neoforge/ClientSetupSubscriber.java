package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.ModShaders;
import com.jayemceekay.shadowedhearts.client.render.DepthCapture;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Dedicated MOD bus subscriber for lifecycle (client setup) on NeoForge.
 * Keeps other FORGE-bus listeners in ShadowedheartsNeoForgeClient unaffected.
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientSetupSubscriber {

    private ClientSetupSubscriber() {}

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Client-side common init
        DepthCapture.init();
        ModShaders.initClient();
        // Register keybinds
        com.jayemceekay.shadowedhearts.client.ModKeybinds.init();
    }
}
