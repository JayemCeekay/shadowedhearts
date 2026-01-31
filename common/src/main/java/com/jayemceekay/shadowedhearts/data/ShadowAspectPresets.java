package com.jayemceekay.shadowedhearts.data;

import com.google.gson.*;
import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Datapack loader for NPC aspect presets.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 * <p>
 * Files live at: data/shadowedhearts/shadow_presets/<ns>/<id>.json
 * Minimal schema supported:
 * <p>
 * [ "aspect-a", "aspect-b" ]
 * or
 * { "aspects": [ "aspect-a", "aspect-b" ] }
 * <p>
 * Access via a pseudo-aspect on NPCs: "shadowedhearts:shadow_presets/<ns>/<id>"
 */
public final class ShadowAspectPresets {
    private ShadowAspectPresets() {
    }

    private static final Map<ResourceLocation, List<String>> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, List<String>> RUNTIME_PRESETS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean isPresetKey(String aspect) {
        return aspect != null && aspect.startsWith("shadowedhearts:shadow_presets/");
    }

    /**
     * Extracts the preset id from a preset aspect string.
     * Example: shadowedhearts:shadow_presets/mymod/my_preset -> new ResourceLocation("mymod", "my_preset")
     */
    public static ResourceLocation toPresetId(String presetAspect) {
        if (!isPresetKey(presetAspect)) return null;
        String path = presetAspect.substring("shadowedhearts:shadow_presets/".length());
        int slash = path.indexOf('/');
        if (slash <= 0) return null;
        String ns = path.substring(0, slash);
        String id = path.substring(slash + 1);
        if (ns.isBlank() || id.isBlank()) return null;
        return new ResourceLocation(ns, id);
    }

    /**
     * Load preset list from datapacks or runtime overrides.
     */
    public static List<String> get(MinecraftServer server, ResourceLocation presetId) {
        if (presetId == null) return Collections.emptyList();

        // Check runtime presets first
        if (RUNTIME_PRESETS.containsKey(presetId)) {
            return RUNTIME_PRESETS.get(presetId);
        }

        if (server == null) return Collections.emptyList();
        return CACHE.computeIfAbsent(presetId, id -> load(server.getResourceManager(), id));
    }

    public static void savePreset(MinecraftServer server, ResourceLocation presetId, List<String> aspects) {
        RUNTIME_PRESETS.put(presetId, new ArrayList<>(aspects));
        saveToDisk(presetId, aspects);
        // Clear cache so it reloads if it was cached from datapack
        CACHE.remove(presetId);
    }

    public static void deletePreset(MinecraftServer server, ResourceLocation presetId) {
        RUNTIME_PRESETS.remove(presetId);
        deleteFromDisk(presetId);
        CACHE.remove(presetId);
    }

    public static Map<ResourceLocation, List<String>> getRuntimePresets() {
        return Collections.unmodifiableMap(RUNTIME_PRESETS);
    }

    public static void init() {
        loadFromDisk();
    }

    private static Path getPresetsDir() {
        Path configDir = Platform.getConfigFolder();
        return configDir.resolve("shadowedhearts").resolve("presets");
    }

    private static void saveToDisk(ResourceLocation presetId, List<String> aspects) {
        try {
            Path dir = getPresetsDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(presetId.getNamespace() + "_" + presetId.getPath() + ".json");

            JsonObject obj = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String s : aspects) arr.add(s);
            obj.add("aspects", arr);

            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(obj, writer);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void deleteFromDisk(ResourceLocation presetId) {
        try {
            Path dir = getPresetsDir();
            Path file = dir.resolve(presetId.getNamespace() + "_" + presetId.getPath() + ".json");
            Files.deleteIfExists(file);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void loadFromDisk() {
        try {
            Path dir = getPresetsDir();
            if (!Files.exists(dir)) return;

            Files.list(dir).forEach(file -> {
                if (file.toString().endsWith(".json")) {
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        String filename = file.getFileName().toString();
                        filename = filename.substring(0, filename.length() - 5);
                        int underscore = filename.indexOf('_');
                        if (underscore <= 0) return;
                        String ns = filename.substring(0, underscore);
                        String id = filename.substring(underscore + 1);
                        ResourceLocation presetId = new ResourceLocation(ns, id);

                        JsonElement root = JsonParser.parseReader(reader);
                        RUNTIME_PRESETS.put(presetId, parse(root));
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static List<String> load(ResourceManager rm, ResourceLocation id) {
        try {
            // Files are under our mod namespace (shadowedhearts), grouping by target namespace in the path
            ResourceLocation file = new ResourceLocation("shadowedhearts", "shadow_presets/" + id.getNamespace() + "/" + id.getPath() + ".json");
            Optional<Resource> res = rm.getResource(file);
            if (res.isEmpty()) return Collections.emptyList();
            try (var in = res.get().open();
                 var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonElement root = JsonParser.parseReader(reader);
                return parse(root);
            }
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static List<String> parse(JsonElement root) {
        List<String> out = new ArrayList<>();
        if (root == null || root.isJsonNull()) return out;
        if (root.isJsonArray()) {
            parseArray(root.getAsJsonArray(), out);
            return out;
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("aspects");
            if (arr != null) parseArray(arr, out);
        }
        return out;
    }

    private static void parseArray(JsonArray arr, List<String> out) {
        for (JsonElement el : arr) {
            if (el == null || el.isJsonNull()) continue;
            if (el.isJsonPrimitive()) {
                String s = el.getAsString();
                if (s != null && !s.isBlank()) out.add(s);
            } else if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("aspect")) {
                    String s = o.get("aspect").getAsString();
                    if (s != null && !s.isBlank()) out.add(s);
                }
            }
        }
    }
}
