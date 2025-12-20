package com.jayemceekay.shadowedhearts.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Datapack loader for NPC aspect presets.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 *
 * Files live at: data/shadowedhearts/shadow_presets/<ns>/<id>.json
 * Minimal schema supported:
 *
 * [ "aspect-a", "aspect-b" ]
 * or
 * { "aspects": [ "aspect-a", "aspect-b" ] }
 *
 * Access via a pseudo-aspect on NPCs: "shadowedhearts:shadow_presets/<ns>/<id>"
 */
public final class ShadowAspectPresets {
    private ShadowAspectPresets() {}

    private static final Map<ResourceLocation, List<String>> CACHE = new HashMap<>();

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

    /** Load preset list from datapacks. */
    public static List<String> get(MinecraftServer server, ResourceLocation presetId) {
        if (server == null || presetId == null) return Collections.emptyList();
        return CACHE.computeIfAbsent(presetId, id -> load(server.getResourceManager(), id));
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
