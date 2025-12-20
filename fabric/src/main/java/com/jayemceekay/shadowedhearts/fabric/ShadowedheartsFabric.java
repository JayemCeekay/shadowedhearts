package com.jayemceekay.shadowedhearts.fabric;

import com.jayemceekay.shadowedhearts.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class ShadowedheartsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Shadowedhearts.init();
        // Register S2C network payloads and client handlers using Cobblemon's system
        com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager.registerMessages();
        // Register C2S codecs and server handlers
        com.jayemceekay.shadowedhearts.fabric.net.ShadowedHeartsFabricNetworkManager.registerServerHandlers();
        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            ShadowPokemonData.bootstrap();
        });
    }
}
