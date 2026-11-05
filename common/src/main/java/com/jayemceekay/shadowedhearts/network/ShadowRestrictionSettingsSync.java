package com.jayemceekay.shadowedhearts.network;

import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionSettings;
import com.jayemceekay.shadowedhearts.network.restrictions.ShadowRestrictionSettingsS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Keeps client restrictions in step with authoritative server config without per-tick packets. */
public final class ShadowRestrictionSettingsSync {
    private static final ServerConfigSyncState<MinecraftServer, ShadowRestrictionSettings> STATE = new ServerConfigSyncState<>();

    private ShadowRestrictionSettingsSync() {}

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(ShadowRestrictionSettingsSync::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(ShadowRestrictionSettingsSync::onServerTick);
        LifecycleEvent.SERVER_STOPPED.register(ShadowRestrictionSettingsSync::onServerStopped);
    }

    /** Architectury invokes this on the server thread after the connection is ready. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ShadowRestrictionSettings settings = ShadowRestrictionSettings.serverCurrent();
        send(player, settings);
        STATE.playerJoined(server, player.getUUID(), settings);
    }

    public static void onServerTick(MinecraftServer server) {
        ShadowRestrictionSettings settings = ShadowRestrictionSettings.serverCurrent();
        if (!STATE.beginTick(server, settings)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (STATE.needsUpdate(player.getUUID(), settings)) {
                send(player, settings);
                STATE.recordDelivery(player.getUUID(), settings);
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        STATE.serverStopped(server);
    }

    private static void send(ServerPlayer player, ShadowRestrictionSettings settings) {
        ShadowedHeartsNetwork.sendToPlayer(player, new ShadowRestrictionSettingsS2CPacket(settings));
    }
}
