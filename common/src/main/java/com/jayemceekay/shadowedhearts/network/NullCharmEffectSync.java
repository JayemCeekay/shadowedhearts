package com.jayemceekay.shadowedhearts.network;

import com.jayemceekay.shadowedhearts.common.shadow.NullCharmEffect;
import com.jayemceekay.shadowedhearts.network.items.NullCharmEffectS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends only the active held-item effect on join and after relevant server config changes. */
public final class NullCharmEffectSync {
    private static final ServerConfigSyncState<MinecraftServer, NullCharmEffect> STATE = new ServerConfigSyncState<>();

    private NullCharmEffectSync() {}

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(NullCharmEffectSync::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(NullCharmEffectSync::onServerTick);
        LifecycleEvent.SERVER_STOPPED.register(NullCharmEffectSync::onServerStopped);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        NullCharmEffect effect = NullCharmEffect.serverCurrent();
        send(player, effect);
        STATE.playerJoined(server, player.getUUID(), effect);
    }

    public static void onServerTick(MinecraftServer server) {
        NullCharmEffect effect = NullCharmEffect.serverCurrent();
        if (!STATE.beginTick(server, effect)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (STATE.needsUpdate(player.getUUID(), effect)) {
                send(player, effect);
                STATE.recordDelivery(player.getUUID(), effect);
            }
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        STATE.serverStopped(server);
    }

    private static void send(ServerPlayer player, NullCharmEffect effect) {
        ShadowedHeartsNetwork.sendToPlayer(player, new NullCharmEffectS2CPacket(effect));
    }
}
