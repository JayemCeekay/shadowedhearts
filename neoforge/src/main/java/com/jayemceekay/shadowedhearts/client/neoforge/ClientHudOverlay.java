package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderInputHandler;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderHud;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.sound.RelicStoneSoundManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * NeoForge hooks for client effects and the Aura Reader HUD.
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = Dist.CLIENT)
public final class ClientHudOverlay {
    private ClientHudOverlay() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        BallEmitters.onClientTick(minecraft);
        com.jayemceekay.shadowedhearts.client.voidshadow.VoidShadowTransitions.tick(minecraft);
        AuraReaderInputHandler.tick(minecraft);
        AuraReaderPulseRenderer.tick();
        RelicStoneSoundManager.tick();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        AuraReaderHud.render(event.getGuiGraphics(), event.getPartialTick());
        com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundClient.renderHud(event.getGuiGraphics());
    }
}
