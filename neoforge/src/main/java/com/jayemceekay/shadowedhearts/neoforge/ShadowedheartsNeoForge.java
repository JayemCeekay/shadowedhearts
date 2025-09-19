package com.jayemceekay.shadowedhearts.neoforge;

import com.jayemceekay.shadowedhearts.ShadowPokemonData;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Shadowedhearts.MOD_ID)
public final class ShadowedheartsNeoForge {

    public ShadowedheartsNeoForge() {
        // Run our common setup.
        Shadowedhearts.init();
    }

}
