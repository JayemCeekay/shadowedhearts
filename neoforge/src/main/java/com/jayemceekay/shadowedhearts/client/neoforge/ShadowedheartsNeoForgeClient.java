package com.jayemceekay.shadowedhearts.client.neoforge;


import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.poketoss.client.WhistleSelectionClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

@EventBusSubscriber(modid = Shadowedhearts.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ShadowedheartsNeoForgeClient {


    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Client-side common init
        ClientSetupSubscriber.onClientSetup(null);
    }

    public static void attach(IEventBus modBus) {
        modBus.addListener(ShadowedheartsNeoForgeClient::onClientSetup);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            var cam = e.getCamera();
            AuraEmitters.onRender(cam, cam.getPartialTickTime());
            WhistleSelectionClient.onRender();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        com.jayemceekay.shadowedhearts.poketoss.client.WhistleSelectionClient.onTick();
        com.jayemceekay.shadowedhearts.poketoss.client.TargetSelectionClient.onTick();
        com.jayemceekay.shadowedhearts.poketoss.client.PositionSelectionClient.onTick();
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) {
            AuraEmitters.onPokemonDespawn(e.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onGuiRender(RenderGuiEvent.Post e) {
        com.jayemceekay.shadowedhearts.poketoss.client.WhistleSelectionClient.onHudRender(e.getGuiGraphics(), 0f);
    }
}