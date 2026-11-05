package com.jayemceekay.shadowedhearts.network;

import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.network.restrictions.HeartGaugeSettingsS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Syncs effective overrides, including legacy JSON, rather than only the native TOML list. */
public final class HeartGaugeSettingsSync {
    private static final ServerConfigSyncState<MinecraftServer, Map<String, Integer>> STATE = new ServerConfigSyncState<>();

    private HeartGaugeSettingsSync() {}

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(HeartGaugeSettingsSync::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(HeartGaugeSettingsSync::onServerTick);
        LifecycleEvent.SERVER_STOPPED.register(HeartGaugeSettingsSync::onServerStopped);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Map<String, Integer> overrides = HeartGaugeConfig.currentOverrides();
        send(player, overrides);
        STATE.playerJoined(server, player.getUUID(), overrides);
    }

    public static void onServerTick(MinecraftServer server) {
        Map<String, Integer> overrides = HeartGaugeConfig.currentOverrides();
        if (!STATE.beginTick(server, overrides)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (STATE.needsUpdate(player.getUUID(), overrides)) {
                send(player, overrides);
                STATE.recordDelivery(player.getUUID(), overrides);
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        STATE.serverStopped(server);
    }

    private static void send(ServerPlayer player, Map<String, Integer> overrides) {
        ShadowedHeartsNetwork.sendToPlayer(player, new HeartGaugeSettingsS2CPacket(overrides));
    }
}
