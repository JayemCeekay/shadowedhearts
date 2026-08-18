package com.jayemceekay.shadowedhearts.client.neoforge;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderInputHandler;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderHud;
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallFboDebugPreview;
import com.jayemceekay.shadowedhearts.client.sound.RelicStoneSoundManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * NeoForge HUD overlay hook to render the Ball Trail debug quad every frame.
 */
@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = Dist.CLIENT)
public final class ClientHudOverlay {
    private ClientHudOverlay() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        BallEmitters.onClientTick(minecraft);
        AuraReaderInputHandler.tick(minecraft);
        AuraReaderPulseRenderer.tick();
        RelicStoneSoundManager.tick();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        AuraReaderHud.render(event.getGuiGraphics());
        ShadowPokemonAuraSystem.renderDebugHud(event.getGuiGraphics());
        DarkBallFboDebugPreview.renderHud(event.getGuiGraphics());
    }
}
