package com.jayemceekay.shadowedhearts.neoforge;

import com.jayemceekay.shadowedhearts.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.world.WorldspaceManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(Shadowedhearts.MOD_ID)
public final class ShadowedheartsNeoForge {

    public ShadowedheartsNeoForge(IEventBus modEventBus) {
        // Run our common setup.
        Shadowedhearts.init();
        modEventBus.addListener((final FMLCommonSetupEvent e) -> ShadowPokemonData.bootstrap());

        // When the server finishes starting, check for our Missions dimension
        NeoForge.EVENT_BUS.addListener((final ServerStartedEvent e) -> {
            ServerLevel level = e.getServer().getLevel(WorldspaceManager.MISSIONS_LEVEL_KEY);
            if (level != null) {
                WorldspaceManager.onMissionsLevelLoaded(level);
                System.out.println("[ShadowedHearts] Missions dimension loaded: " + level.dimension().location());
            } else {
                System.out.println("[ShadowedHearts] WARNING: Missions dimension 'shadowedhearts:missions' not found. Ensure the datapack is enabled.");
            }
        });
    }
}
