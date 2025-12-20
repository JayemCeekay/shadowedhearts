package com.jayemceekay.shadowedhearts.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Client-only config for visual toggles.
 * File: config/shadowedhearts-client.json
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class ClientConfig {
    private ClientConfig() {}

    private static final String CLIENT_FILE = "shadowedhearts-client.json";

    public static final class Data {
        /** Master toggle for client-side Shadow aura rendering. */
        public boolean enableShadowAura = true;
    }

    private static final Data DATA = new Data();

    public static Data get() { return DATA; }

    public static void load() {
        Path configDir = Paths.get("config");
        Path jsonFile = configDir.resolve(CLIENT_FILE);
        if (!Files.isRegularFile(jsonFile)) return;
        try (BufferedReader r = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el != null && el.isJsonObject()) {
                JsonObject root = el.getAsJsonObject();
                DATA.enableShadowAura = optBool(root, "enableShadowAura", DATA.enableShadowAura);
            }
        } catch (IOException ignored) {}
    }

    public static void save() {
        Path configDir = Paths.get("config");
        Path jsonFile = configDir.resolve(CLIENT_FILE);
        try {
            if (!Files.isDirectory(configDir)) Files.createDirectories(configDir);
            JsonObject root = new JsonObject();
            root.addProperty("enableShadowAura", DATA.enableShadowAura);
            try (BufferedWriter w = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
        } catch (IOException ignored) {}
    }

    private static boolean optBool(JsonObject o, String key, boolean def) {
        try { return o.has(key) ? o.get(key).getAsBoolean() : def; } catch (Exception e) { return def; }
    }
}
