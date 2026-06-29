package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.client.sound.RelicStoneSoundManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * NeoForge HUD overlay hook to render the Ball Trail debug quad every frame.
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = Dist.CLIENT)
public final class ClientHudOverlay {
    private ClientHudOverlay() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        AuraPulseRenderer.tick();
        RelicStoneSoundManager.tick();
    }
}
