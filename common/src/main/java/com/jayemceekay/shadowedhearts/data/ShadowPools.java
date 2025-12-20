package com.jayemceekay.shadowedhearts.data;

import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
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
 * Lightweight loader for datapack-defined Shadow candidate pools.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 *
 * Files live at: data/<namespace>/shadow_pools/<id>.json
 * Minimal schema supported:
 * {
 *   "entries": [
 *     "gligar lvl=28",
 *     { "pokemon": "sneasel lvl=29", "weight": 2 }
 *   ]
 * }
 */
public final class ShadowPools {
    private ShadowPools() {}

    public static final class WeightedEntry {
        public final PokemonProperties props;
        public final int weight;
        public WeightedEntry(PokemonProperties props, int weight) {
            this.props = props;
            this.weight = Math.max(1, weight);
        }
    }

    private static final Map<ResourceLocation, List<WeightedEntry>> CACHE = new HashMap<>();

    public static List<WeightedEntry> get(MinecraftServer server, ResourceLocation id) {
        if (id == null || server == null) return Collections.emptyList();
        return CACHE.computeIfAbsent(id, key -> load(server.getResourceManager(), key));
    }

    private static List<WeightedEntry> load(ResourceManager rm, ResourceLocation id) {
        try {
            ResourceLocation file = new ResourceLocation(id.getNamespace(), "shadow_pools/" + id.getPath() + ".json");
            Optional<Resource> res = rm.getResource(file);
            if (res.isEmpty()) return Collections.emptyList();
            try (var in = res.get().open();
                 var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonElement root = JsonParser.parseReader(reader);
                return parsePool(root);
            }
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static List<WeightedEntry> parsePool(JsonElement root) {
        List<WeightedEntry> out = new ArrayList<>();
        if (root == null || root.isJsonNull()) return out;
        if (root.isJsonArray()) {
            parseEntriesArray(root.getAsJsonArray(), out);
            return out;
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            JsonArray arr = obj.getAsJsonArray("entries");
            if (arr != null) parseEntriesArray(arr, out);
        }
        return out;
        
    }

    private static void parseEntriesArray(JsonArray arr, List<WeightedEntry> out) {
        for (JsonElement el : arr) {
            if (el == null || el.isJsonNull()) continue;
            if (el.isJsonPrimitive()) {
                String s = el.getAsString();
                PokemonProperties props = PokemonProperties.Companion.parse(s);
                out.add(new WeightedEntry(props, 1));
            } else if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                String s = o.has("pokemon") ? o.get("pokemon").getAsString() : null;
                if (s == null || s.isBlank()) continue;
                int weight = o.has("weight") ? safeInt(o.get("weight")) : 1;
                PokemonProperties props;
                // Use Cobblemon's configured Gson to allow richer object forms if provided
                if (o.has("properties") && o.get("properties").isJsonObject()) {
                    props = NPCClasses.INSTANCE.getGson().fromJson(o.get("properties"), PokemonProperties.class);
                    // Merge the species string if given as well
                    if (props.getSpecies() == null) {
                        PokemonProperties parsed = PokemonProperties.Companion.parse(s);
                        mergeInto(props, parsed);
                    }
                } else {
                    props = PokemonProperties.Companion.parse(s);
                }
                out.add(new WeightedEntry(props, weight));
            }
        }
    }

    private static int safeInt(JsonElement e) {
        try { return e.getAsInt(); } catch (Throwable t) { return 1; }
    }

    private static void mergeInto(PokemonProperties base, PokemonProperties extra) {
        try {
            if (base.getLevel() == null) base.setLevel(extra.getLevel());
            if (base.getSpecies() == null) base.setSpecies(extra.getSpecies());
            if (base.getForm() == null) base.setForm(extra.getForm());
        } catch (Throwable ignored) {}
    }

    public static List<PokemonProperties> pick(Random rng, List<WeightedEntry> entries, int count) {
        if (entries == null || entries.isEmpty() || count <= 0) return Collections.emptyList();
        // Build cumulative weights
        int total = 0;
        for (WeightedEntry e : entries) total += Math.max(1, e.weight);
        List<PokemonProperties> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int r = rng.nextInt(Math.max(1, total));
            int acc = 0;
            for (WeightedEntry e : entries) {
                acc += Math.max(1, e.weight);
                if (r < acc) {
                    result.add(e.props);
                    break;
                }
            }
        }
        return result;
    }
}
