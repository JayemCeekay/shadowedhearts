package com.jayemceekay.shadowedhearts.network.sync;

import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.common.snag.SnagMachineSettings;
import com.jayemceekay.shadowedhearts.network.items.SnagMachineSettingsS2CPacket;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Native config sync covers login; this snapshot also follows live server edits. */
public final class SnagMachineSettingsSync {
    private static final ServerConfigSyncState<MinecraftServer, SnagMachineSettings> STATE = new ServerConfigSyncState<>();

    private SnagMachineSettingsSync() {}

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(SnagMachineSettingsSync::onPlayerJoin);
        PlayerEvent.PLAYER_QUIT.register(player -> STATE.playerLeft(player.getServer(), player.getUUID()));
        TickEvent.SERVER_POST.register(SnagMachineSettingsSync::onServerTick);
        LifecycleEvent.SERVER_STOPPED.register(STATE::serverStopped);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        SnagMachineSettings settings = SnagMachineSettings.serverCurrent();
        send(player, settings);
        STATE.playerJoined(server, player.getUUID(), settings);
    }

    public static void onServerTick(MinecraftServer server) {
        SnagMachineSettings settings = SnagMachineSettings.serverCurrent();
        if (!STATE.beginTick(server, settings)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (STATE.needsUpdate(player.getUUID(), settings)) {
                send(player, settings);
                STATE.recordDelivery(player.getUUID(), settings);
            }
        }
    }

    private static void send(ServerPlayer player, SnagMachineSettings settings) {
        ShadowedHeartsNetwork.sendToPlayer(player, new SnagMachineSettingsS2CPacket(settings));
    }
}
