package com.jayemceekay.shadowedhearts.client;

import com.cobblemon.mod.common.client.tooltips.TooltipManager;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderTooltips;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowAuraEmitters;
import com.jayemceekay.shadowedhearts.client.ball.ShadowBallTooltips;
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundClient;
import com.jayemceekay.shadowedhearts.client.purification.PurificationChamberClientSession;
import com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderModel;
import com.jayemceekay.shadowedhearts.client.render.armor.SnagMachineAdvancedModel;
import com.jayemceekay.shadowedhearts.client.render.armor.SnagMachinePrototypeModel;
import com.jayemceekay.shadowedhearts.client.sound.ShadowSignalSearchSoundManager;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;

public class ShadowedHeartsClient {
    public static void init() {
        ClientConnectionSettings.initialize();
        ClientTickEvent.CLIENT_POST.register(client -> {
            ShadowAuraEmitters.onClientTick(client);
            ClientConnectionSettings.refreshConnection();
            PurificationChamberClientSession.INSTANCE.onClientTick(client);
            MoonwoundClient.onClientTick(client);
            ShadowSignalSearchSoundManager.onClientTick(client);
        });
        registerTooltips();
        registerModelLayers();

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            Shadowedhearts.injectShowdownConfig();
        });
    }

    private static void registerTooltips() {
        ClientTooltipEvent.ITEM.register(ShadowBallTooltips::append);
        ClientTooltipEvent.ITEM.register(AuraReaderTooltips::wrap);
        TooltipManager.INSTANCE.registerTooltipGenerator(new ShadowGemTooltipGenerator());
    }

    private static void registerModelLayers() {
        EntityModelLayerRegistry.register(
            AuraReaderModel.LAYER_LOCATION,
            AuraReaderModel::createBodyLayer
        );
        EntityModelLayerRegistry.register(
            SnagMachineAdvancedModel.LAYER_LOCATION,
            SnagMachineAdvancedModel::createBodyLayer
        );
        EntityModelLayerRegistry.register(
            SnagMachinePrototypeModel.LAYER_LOCATION,
            SnagMachinePrototypeModel::createBodyLayer
        );
    }
}
