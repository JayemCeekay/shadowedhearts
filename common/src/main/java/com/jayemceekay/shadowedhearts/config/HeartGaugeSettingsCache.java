package com.jayemceekay.shadowedhearts.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** File access happens only at explicit reload boundaries; readers see a complete immutable value. */
final class HeartGaugeSettingsCache {
    private static final TypeAdapter<JsonElement> JSON_ELEMENT = new Gson().getAdapter(JsonElement.class);

    record Snapshot(Map<String, Integer> overrides, int globalMax) {
        Snapshot {
            overrides = Map.copyOf(overrides);
        }
    }

    private final Path legacyFile;
    private final BiConsumer<Path, Exception> onFailure;
    private final Snapshot defaults;
    private Map<String, Integer> lastGoodLegacy = Map.of();
    private volatile Snapshot current;

    HeartGaugeSettingsCache(Map<String, Integer> defaults, Path legacyFile,
                           BiConsumer<Path, Exception> onFailure) {
        Objects.requireNonNull(defaults, "defaults");
        this.legacyFile = Objects.requireNonNull(legacyFile, "legacyFile");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        int maximum = 30_000;
        for (int value : defaults.values()) maximum = Math.max(maximum, value);
        this.defaults = new Snapshot(Map.of(), maximum);
        current = this.defaults;
    }

    Snapshot snapshot() {
        return current;
    }

    synchronized Snapshot reload(List<? extends String> configured, boolean allowLegacy) {
        return reload(configured, allowLegacy, Map.of());
    }

    synchronized Snapshot reload(List<? extends String> configured, boolean allowLegacy,
                                 Map<String, Integer> rosterOverlay) {
        Map<String, Integer> values = HeartGaugeConfig.configuredOverrides(configured);
        if (values.isEmpty() && allowLegacy) values = reloadLegacy();
        values = applyRosterOverlay(values, rosterOverlay);
        int maximum = defaults.globalMax();
        for (int value : values.values()) maximum = Math.max(maximum, value);
        Snapshot updated = new Snapshot(values, maximum);
        current = updated;
        return updated;
    }

    /** Explicit short or namespaced keys both outrank the optional original roster. */
    private static Map<String, Integer> applyRosterOverlay(Map<String, Integer> configured,
                                                           Map<String, Integer> overlay) {
        if (overlay.isEmpty()) return configured;
        Map<String, Integer> merged = new HashMap<>(configured);
        overlay.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String full = entry.getKey();
            String shortKey = full.substring(full.indexOf(':') + 1);
            if (!configured.containsKey(full) && !configured.containsKey(shortKey)
                    && merged.size() < HeartGaugeConfig.MAX_OVERRIDE_ENTRIES) {
                merged.put(full, entry.getValue());
            }
        });
        return merged;
    }

    synchronized void clear() {
        lastGoodLegacy = Map.of();
        current = defaults;
    }

    private Map<String, Integer> reloadLegacy() {
        try {
            Map<String, Integer> loaded = readLegacy();
            lastGoodLegacy = Map.copyOf(loaded);
        } catch (NoSuchFileException missing) {
            // Deleting the old file is an intentional removal of its overrides.
            lastGoodLegacy = Map.of();
        } catch (IOException | JsonParseException | SecurityException failure) {
            onFailure.accept(legacyFile, failure);
        }
        // A failed legacy read must never reuse the previously active TOML override map.
        return lastGoodLegacy;
    }

    private Map<String, Integer> readLegacy() throws IOException {
        try (var source = Files.newBufferedReader(legacyFile, StandardCharsets.UTF_8);
             var reader = new JsonReader(source)) {
            // Minecraft 1.21.1 supplies Gson 2.10.1, before Strictness.STRICT was introduced.
            // JsonParser.parseReader enables lenient parsing internally; the adapter respects this reader.
            reader.setLenient(false);
            if (reader.peek() == JsonToken.END_DOCUMENT) {
                throw new JsonParseException("Expected a Heart Gauge overrides object, found an empty file");
            }
            JsonElement root = JSON_ELEMENT.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("Unexpected content after the Heart Gauge overrides object");
            }
            if (root == null || !root.isJsonObject()) {
                throw new JsonParseException("Heart Gauge overrides must be a JSON object");
            }
            return HeartGaugeConfig.legacyOverrides(root);
        }
    }
}
