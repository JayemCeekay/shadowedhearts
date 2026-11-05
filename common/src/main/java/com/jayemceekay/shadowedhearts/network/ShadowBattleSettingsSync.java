package com.jayemceekay.shadowedhearts.network;

import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleSettings;
import com.jayemceekay.shadowedhearts.network.restrictions.ShadowBattleSettingsS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Join and live-edit synchronization of the three effective battle presentation flags. */
public final class ShadowBattleSettingsSync {
    private static final ServerConfigSyncState<MinecraftServer, ShadowBattleSettings> STATE = new ServerConfigSyncState<>();

    private ShadowBattleSettingsSync() {}
    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(ShadowBattleSettingsSync::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(ShadowBattleSettingsSync::onServerTick);
        LifecycleEvent.SERVER_STOPPED.register(STATE::serverStopped);
    }
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ShadowBattleSettings settings = ShadowBattleSettings.serverCurrent();
        send(player, settings);
        STATE.playerJoined(server, player.getUUID(), settings);
    }
    public static void onServerTick(MinecraftServer server) {
        ShadowBattleSettings settings = ShadowBattleSettings.serverCurrent();
        if (!STATE.beginTick(server, settings)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (STATE.needsUpdate(player.getUUID(), settings)) {
                send(player, settings);
                STATE.recordDelivery(player.getUUID(), settings);
            }
        }
    }
    private static void send(ServerPlayer player, ShadowBattleSettings settings) {
        ShadowedHeartsNetwork.sendToPlayer(player, new ShadowBattleSettingsS2CPacket(settings));
    }
}
