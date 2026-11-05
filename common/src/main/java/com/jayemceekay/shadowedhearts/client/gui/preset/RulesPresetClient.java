package com.jayemceekay.shadowedhearts.client.gui.preset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayemceekay.shadowedhearts.network.preset.RulesPresetRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client transport state belongs to one screen and one connection, including late apply responses. */
public final class RulesPresetClient {
    private static int nextRequestId;
    private static RulesPresetScreen active;
    private static ConfigSnapshotScreen snapshots;
    private static Object connection;

    private RulesPresetClient() {}

    public static void open(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        connection = client.getConnection();
        snapshots = null;
        active = new RulesPresetScreen(parent);
        client.setScreen(active);
    }

    static void openSnapshots(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        connection = client.getConnection();
        active = null;
        snapshots = new ConfigSnapshotScreen(parent);
        client.setScreen(snapshots);
    }

    static boolean connected(ConfigSnapshotScreen screen) {
        Minecraft client = Minecraft.getInstance();
        return snapshots == screen && connection != null && client.getConnection() == connection && client.level != null;
    }

    static void send(ConfigSnapshotScreen screen, JsonObject request) {
        if (!connected(screen)) throw new IllegalStateException("Snapshot screen disconnected");
        new RulesPresetRequestPacket(request.toString()).sendToServer();
    }

    static void closed(ConfigSnapshotScreen screen) {
        if (snapshots == screen) { snapshots = null; connection = null; }
    }

    static int nextRequestId() {
        nextRequestId = nextRequestId == Integer.MAX_VALUE ? 1 : nextRequestId + 1;
        return nextRequestId;
    }

    static boolean connected(RulesPresetScreen screen) {
        Minecraft client = Minecraft.getInstance();
        return active == screen && connection != null && client.getConnection() == connection && client.level != null;
    }

    static void send(RulesPresetScreen screen, JsonObject request) {
        if (!connected(screen)) throw new IllegalStateException("This preset screen no longer belongs to a connected world.");
        new RulesPresetRequestPacket(request.toString()).sendToServer();
    }

    public static void receive(String json) {
        Minecraft client = Minecraft.getInstance();
        if (snapshots != null && connected(snapshots) && snapshots.isOpenAt(client.screen)) {
            try { snapshots.receive(JsonParser.parseString(json).getAsJsonObject()); }
            catch (RuntimeException invalid) { snapshots.responseFailed(); }
            return;
        }
        RulesPresetScreen screen = active;
        if (screen == null || !connected(screen) || !screen.isOpenAt(client.screen)) return;
        try {
            screen.receive(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException invalid) {
            screen.responseFailed();
        }
    }

    static void closed(RulesPresetScreen screen) {
        if (active == screen) {
            active = null;
            connection = null;
        }
    }
}
