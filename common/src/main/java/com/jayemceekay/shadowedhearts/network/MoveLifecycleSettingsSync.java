package com.jayemceekay.shadowedhearts.network;

import com.jayemceekay.shadowedhearts.config.MoveLifecycleConfig;
import com.jayemceekay.shadowedhearts.network.items.MoveLifecycleSettingsS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MoveLifecycleSettingsSync {
    private static final ServerConfigSyncState<MinecraftServer, Boolean> STATE = new ServerConfigSyncState<>();
    private MoveLifecycleSettingsSync() { }
    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(player -> {
            var server = player.getServer();
            if (server == null) return;
            boolean value = MoveLifecycleConfig.INSTANCE.frustrationRemovalAllowed();
            send(player, value);
            STATE.playerJoined(server, player.getUUID(), value);
        });
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(server -> {
            boolean value = MoveLifecycleConfig.INSTANCE.frustrationRemovalAllowed();
            if (!STATE.beginTick(server, value)) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (STATE.needsUpdate(player.getUUID(), value)) {
                    send(player, value);
                    STATE.recordDelivery(player.getUUID(), value);
                }
            }
        });
        LifecycleEvent.SERVER_STOPPED.register(STATE::serverStopped);
    }
    private static void send(ServerPlayer player, boolean value) {
        ShadowedHeartsNetwork.sendToPlayer(player, new MoveLifecycleSettingsS2CPacket(value));
    }
}
