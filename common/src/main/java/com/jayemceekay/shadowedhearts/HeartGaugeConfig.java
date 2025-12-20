package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads per-species maximum Heart Gauge values from a JSON config.
 * Format: { "namespace:species": max, "species": max, ... }
 * Missing entries fall back to DEFAULT_MAX.
 */
public final class HeartGaugeConfig {
    private HeartGaugeConfig() {}

    public static final int DEFAULT_MAX = 20000;

    private static volatile boolean loaded = false;
    private static final Map<String, Integer> overrides = new HashMap<>();

    private static Path configPath() {
        return Path.of("config", "shadowedhearts", "heart_gauge_max.json");
    }

    private static String speciesKey(Species species) {
        try {
            // Prefer fully-qualified id, e.g., "cobblemon:bulbasaur"
            String full = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
            return full;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String speciesAltKey(Species species) {
        try {
            // Fallback: just the path component (e.g., "bulbasaur")
            String path = species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
            return path;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (HeartGaugeConfig.class) {
            if (loaded) return;
            Path path = configPath();
            if (Files.exists(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (root != null && root.isJsonObject()) {
                        JsonObject obj = root.getAsJsonObject();
                        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                            String key = e.getKey();
                            if (key == null) continue;
                            key = key.toLowerCase(Locale.ROOT).trim();
                            try {
                                int val = e.getValue().getAsInt();
                                if (val > 0) {
                                    overrides.put(key, val);
                                }
                            } catch (Exception ignored) { }
                        }
                    }
                } catch (IOException ignored) {
                    // If the file can't be read, keep defaults.
                }
            }
            loaded = true;
        }
    }

    /**
     * Returns the configured maximum Heart Gauge for a given Pokemon's species.
     * If not configured, returns DEFAULT_MAX.
     */
    public static int getMax(Pokemon pokemon) {
        if (pokemon == null) return DEFAULT_MAX;
        Species species = pokemon.getSpecies();
        if (species == null) return DEFAULT_MAX;
        return getMax(species);
    }

    /**
     * Returns the configured maximum Heart Gauge for a species.
     */
    public static int getMax(Species species) {
        ensureLoaded();
        String full = speciesKey(species);
        String alt = speciesAltKey(species);
        if (full != null) {
            Integer v = overrides.get(full);
            if (v != null) return v;
        }
        if (alt != null) {
            Integer v = overrides.get(alt);
            if (v != null) return v;
        }
        return DEFAULT_MAX;
    }

    /**
     * Direct lookup by textual key. Keys are matched case-insensitively.
     */
    public static int getMaxByKey(String key) {
        ensureLoaded();
        if (key == null) return DEFAULT_MAX;
        Integer v = overrides.get(key.toLowerCase(Locale.ROOT));
        return v != null ? v : DEFAULT_MAX;
    }
}
