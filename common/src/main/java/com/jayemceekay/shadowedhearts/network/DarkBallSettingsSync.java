package com.jayemceekay.shadowedhearts.network;

import com.jayemceekay.shadowedhearts.common.capture.DarkBallSettings;
import com.jayemceekay.shadowedhearts.network.items.DarkBallSettingsS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Native config sync covers login; this snapshot also follows live server edits. */
public final class DarkBallSettingsSync {
    private static final ServerConfigSyncState<MinecraftServer, DarkBallSettings> STATE = new ServerConfigSyncState<>();

    private DarkBallSettingsSync() {}

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(DarkBallSettingsSync::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(DarkBallSettingsSync::onServerTick);
        LifecycleEvent.SERVER_STOPPED.register(STATE::serverStopped);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        DarkBallSettings settings = DarkBallSettings.serverCurrent();
        send(player, settings);
        STATE.playerJoined(server, player.getUUID(), settings);
    }

    public static void onServerTick(MinecraftServer server) {
        DarkBallSettings settings = DarkBallSettings.serverCurrent();
        if (!STATE.beginTick(server, settings)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (STATE.needsUpdate(player.getUUID(), settings)) {
                send(player, settings);
                STATE.recordDelivery(player.getUUID(), settings);
            }
        }
    }

    private static void send(ServerPlayer player, DarkBallSettings settings) {
        ShadowedHeartsNetwork.sendToPlayer(player, new DarkBallSettingsS2CPacket(settings));
    }
}
