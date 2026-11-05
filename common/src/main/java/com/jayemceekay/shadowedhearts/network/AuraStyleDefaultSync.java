package com.jayemceekay.shadowedhearts.network;

import com.jayemceekay.shadowedhearts.common.aura.AuraStyleDefault;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.aura.AuraStyleDefaultS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends the authoritative aura default on join and after live server configuration changes. */
public final class AuraStyleDefaultSync {
    private static final ServerConfigSyncState<MinecraftServer, AuraStyleDefault> STATE = new ServerConfigSyncState<>();

    private AuraStyleDefaultSync() {}

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(AuraStyleDefaultSync::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(AuraStyleDefaultSync::onServerTick);
        LifecycleEvent.SERVER_STOPPED.register(AuraStyleDefaultSync::onServerStopped);
    }

    /** Architectury invokes this on the server thread, once the player's connection is ready. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AuraStyleDefault style = currentStyle();
        send(player, style);
        STATE.playerJoined(server, player.getUUID(), style);
    }

    /** Poll only the enum; inspect players only on a change or to reconcile a newer join snapshot. */
    public static void onServerTick(MinecraftServer server) {
        AuraStyleDefault style = currentStyle();
        if (!STATE.beginTick(server, style)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (STATE.needsUpdate(player.getUUID(), style)) {
                send(player, style);
                STATE.recordDelivery(player.getUUID(), style);
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        STATE.serverStopped(server);
    }

    private static AuraStyleDefault currentStyle() {
        return ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowAuraStyle();
    }

    private static void send(ServerPlayer player, AuraStyleDefault style) {
        ShadowedHeartsNetwork.sendToPlayer(player, new AuraStyleDefaultS2CPacket(style));
    }
}
