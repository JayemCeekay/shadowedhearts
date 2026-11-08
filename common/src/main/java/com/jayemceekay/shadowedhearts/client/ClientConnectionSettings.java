package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.client.aura.shadow.style.AuraStyleClientState;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallClientState;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleClientState;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsetClientState;
import com.jayemceekay.shadowedhearts.common.shadow.HeartGaugeClientState;
import com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycleClientState;
import com.jayemceekay.shadowedhearts.common.shadow.NullCharmClientState;
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionClientState;
import com.jayemceekay.shadowedhearts.common.snag.SnagMachineClientState;
import net.minecraft.client.Minecraft;

/** Connects domain settings to the client connection lifecycle. */
final class ClientConnectionSettings {
    private ClientConnectionSettings() { }

    static void initialize() {
        AuraStyleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        NullCharmClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        DarkBallClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        ShadowBattleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        MoveLifecycleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        ItemRuleClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        ShadowLearnsetClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        SnagMachineClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        ShadowRestrictionClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection());
        HeartGaugeClientState.INSTANCE.initialize(() -> Minecraft.getInstance().getConnection(),
                () -> Minecraft.getInstance().isLocalServer());
    }

    static void refreshConnection() {
        AuraStyleClientState.INSTANCE.refreshConnection();
        NullCharmClientState.INSTANCE.refreshConnection();
        DarkBallClientState.INSTANCE.refreshConnection();
        ShadowBattleClientState.INSTANCE.refreshConnection();
        MoveLifecycleClientState.INSTANCE.refreshConnection();
        ItemRuleClientState.INSTANCE.refreshConnection();
        ShadowLearnsetClientState.INSTANCE.refreshConnection();
        SnagMachineClientState.INSTANCE.refreshConnection();
        ShadowRestrictionClientState.INSTANCE.refreshConnection();
        HeartGaugeClientState.INSTANCE.refreshConnection();
    }
}
