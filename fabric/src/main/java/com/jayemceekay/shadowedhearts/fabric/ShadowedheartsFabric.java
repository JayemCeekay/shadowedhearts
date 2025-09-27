package com.jayemceekay.shadowedhearts.fabric;

import com.jayemceekay.shadowedhearts.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.world.WorldspaceManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;

public final class ShadowedheartsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        Shadowedhearts.init();
        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            ShadowPokemonData.bootstrap();
        });
        // When the dedicated/integrated server finishes starting, check for our Missions dimension
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel level = server.getLevel(WorldspaceManager.MISSIONS_LEVEL_KEY);
            if (level != null) {
                WorldspaceManager.onMissionsLevelLoaded(level);
                System.out.println("[ShadowedHearts] Missions dimension loaded: " + level.dimension().location());
            } else {
                System.out.println("[ShadowedHearts] WARNING: Missions dimension 'shadowedhearts:missions' not found. Ensure the datapack is enabled.");
            }
        });
    }
}
