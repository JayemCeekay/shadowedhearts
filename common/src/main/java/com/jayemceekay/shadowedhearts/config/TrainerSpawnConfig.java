package com.jayemceekay.shadowedhearts.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Config for spawning overworld NPC trainers near players.
 * File: config/shadowedhearts/trainer_spawn.json
 * {
 *   "spawn_chance_percent": 5,
 *   "max_per_player": 2,
 *   "radius_min": 12,
 *   "radius_max": 32,
 *   "avg_party_level": 25,
 *   "party_size_min": 3,
 *   "party_size_max": 6,
 *   "shadow1_percent": 20,
 *   "shadow2_percent": 3,
 *   "spawn_interval_ticks": 400,
 *   "despawn_after_ticks": 6000
 * }
 */
public final class TrainerSpawnConfig {
    private TrainerSpawnConfig() {}

    private static volatile boolean loaded = false;

    private static int spawnChancePercent = 75;
    private static int maxPerPlayer = 2;
    private static int radiusMin = 12;
    private static int radiusMax = 32;
    private static int avgPartyLevel = 25;
    private static int partySizeMin = 3;
    private static int partySizeMax = 6;
    private static int shadow1Percent = 20;
    private static int shadow2Percent = 3;
    private static int spawnIntervalTicks = 400;
    private static int despawnAfterTicks = 6000;

    private static Path configPath() {
        return Path.of("config", "shadowedhearts", "trainer_spawn.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (TrainerSpawnConfig.class) {
            if (loaded) return;
            Path path = configPath();
            if (Files.exists(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (root != null && root.isJsonObject()) {
                        JsonObject obj = root.getAsJsonObject();
                        spawnChancePercent = clamp(obj, "spawn_chance_percent", spawnChancePercent, 0, 100);
                        maxPerPlayer = clamp(obj, "max_per_player", maxPerPlayer, 0, 32);
                        radiusMin = clamp(obj, "radius_min", radiusMin, 1, 256);
                        radiusMax = clamp(obj, "radius_max", radiusMax, radiusMin, 512);
                        avgPartyLevel = clamp(obj, "avg_party_level", avgPartyLevel, 1, 100);
                        partySizeMin = clamp(obj, "party_size_min", partySizeMin, 1, 6);
                        partySizeMax = clamp(obj, "party_size_max", partySizeMax, partySizeMin, 6);
                        shadow1Percent = clamp(obj, "shadow1_percent", shadow1Percent, 0, 100);
                        shadow2Percent = clamp(obj, "shadow2_percent", shadow2Percent, 0, 100);
                        spawnIntervalTicks = clamp(obj, "spawn_interval_ticks", spawnIntervalTicks, 20, 20_000);
                        despawnAfterTicks = clamp(obj, "despawn_after_ticks", despawnAfterTicks, 200, 120_000);
                    }
                } catch (IOException ignored) {}
            }
            loaded = true;
        }
    }

    private static int clamp(JsonObject obj, String key, int def, int min, int max) {
        try {
            if (obj.has(key)) {
                int v = obj.get(key).getAsInt();
                if (v < min) v = min;
                if (v > max) v = max;
                return v;
            }
        } catch (Exception ignored) {}
        return def;
    }

    public static int getSpawnChancePercent() { ensureLoaded(); return spawnChancePercent; }
    public static int getMaxPerPlayer() { ensureLoaded(); return maxPerPlayer; }
    public static int getRadiusMin() { ensureLoaded(); return radiusMin; }
    public static int getRadiusMax() { ensureLoaded(); return radiusMax; }
    public static int getAvgPartyLevel() { ensureLoaded(); return avgPartyLevel; }
    public static int getPartySizeMin() { ensureLoaded(); return partySizeMin; }
    public static int getPartySizeMax() { ensureLoaded(); return partySizeMax; }
    public static int getShadow1Percent() { ensureLoaded(); return shadow1Percent; }
    public static int getShadow2Percent() { ensureLoaded(); return shadow2Percent; }
    public static int getSpawnIntervalTicks() { ensureLoaded(); return spawnIntervalTicks; }
    public static int getDespawnAfterTicks() { ensureLoaded(); return despawnAfterTicks; }
}
