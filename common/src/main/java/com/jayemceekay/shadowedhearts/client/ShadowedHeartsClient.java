package com.jayemceekay.shadowedhearts.client;

import com.cobblemon.mod.common.client.tooltips.TooltipManager;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.client.aura.AuraStyleClientState;
import com.jayemceekay.shadowedhearts.common.shadow.NullCharmClientState;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallClientState;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionClientState;
import com.jayemceekay.shadowedhearts.common.shadow.HeartGaugeClientState;
import com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderModel;
import com.jayemceekay.shadowedhearts.client.render.armor.SnagMachineAdvancedModel;
import com.jayemceekay.shadowedhearts.client.render.armor.SnagMachinePrototypeModel;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;
import net.minecraft.client.Minecraft;

public class ShadowedHeartsClient {
    public static void init() {
        AuraStyleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        NullCharmClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        DarkBallClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        com.jayemceekay.shadowedhearts.common.items.ItemRuleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        com.jayemceekay.shadowedhearts.common.snag.SnagMachineClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        ShadowRestrictionClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        HeartGaugeClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection(),
                () -> Minecraft.getInstance().isLocalServer());
        ClientTickEvent.CLIENT_POST.register(client -> {
            com.jayemceekay.shadowedhearts.client.aura.ShadowAuraEmitters.onClientTick(client);
            AuraStyleClientState.INSTANCE.refreshConnection();
            NullCharmClientState.INSTANCE.refreshConnection();
            DarkBallClientState.INSTANCE.refreshConnection();
            com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState.INSTANCE.refreshConnection();
            com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycleClientState.INSTANCE.refreshConnection();
            com.jayemceekay.shadowedhearts.common.items.ItemRuleClientState.INSTANCE.refreshConnection();
            com.jayemceekay.shadowedhearts.common.snag.SnagMachineClientState.INSTANCE.refreshConnection();
            ShadowRestrictionClientState.INSTANCE.refreshConnection();
            HeartGaugeClientState.INSTANCE.refreshConnection();
            com.jayemceekay.shadowedhearts.client.purification.PurificationChamberClientSession.INSTANCE.onClientTick(client);
            com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundClient.onClientTick(client);
            com.jayemceekay.shadowedhearts.client.sound.ShadowSignalSearchSoundManager.onClientTick(client);
        });
        ClientTooltipEvent.ITEM.register(ShadowBallTooltips::append);
        ClientTooltipEvent.ITEM.register(AuraReaderTooltips::wrap);
        TooltipManager.INSTANCE.registerTooltipGenerator(new ShadowGemTooltipGenerator());

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

        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            Shadowedhearts.injectShowdownConfig();
        });
    }
}
