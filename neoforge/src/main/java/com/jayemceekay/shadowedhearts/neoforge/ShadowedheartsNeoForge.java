package com.jayemceekay.shadowedhearts.neoforge;

import com.jayemceekay.shadowedhearts.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(Shadowedhearts.MOD_ID)
public final class ShadowedheartsNeoForge {

    public ShadowedheartsNeoForge(IEventBus modEventBus) {
        // Run our common setup.
        Shadowedhearts.init();
        modEventBus.addListener((final FMLCommonSetupEvent e) -> ShadowPokemonData.bootstrap());
    }
}
