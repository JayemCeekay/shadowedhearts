package com.jayemceekay.shadowedhearts.common.tracking;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data-driven registry that loads shadow signal species pools from JSON files.
 * Pool files are located at: data/shadowedhearts/shadow_signal_pools/{pool_name}.json
 * <p>
 * Each pool file contains weighted species entries that can be customized by
 * server operators or modpack authors to control which Shadow Pokémon appear
 * at each tier.
 * <p>
 * Format:
 * <pre>
 * {
 *   "entries": [
 *     { "pokemon": "rattata", "weight": 5 },
 *     { "pokemon": "pidgey", "weight": 3 }
 *   ]
 * }
 * </pre>
 */
public final class ShadowSignalPoolRegistry {
    private static final Logger LOG = Shadowedhearts.LOGGER;
    private static final Gson GSON = new Gson();
    private static final String POOL_PATH_PREFIX = "shadow_signal_pools";

    /** Cached pool data: pool name -> list of (species, weight) */
    private static final Map<String, List<WeightedSpecies>> POOLS = new ConcurrentHashMap<>();

    /** Whether the registry has been loaded at least once */
    private static volatile boolean loaded = false;

    private ShadowSignalPoolRegistry() {}

    /**
     * A species entry with an associated weight for weighted random selection.
     */
    public record WeightedSpecies(Species species, int weight) {}

    /**
     * Load or reload all shadow signal pool JSON files from the server's resource manager.
     * Should be called during server start or resource reload.
     */
    public static void load(MinecraftServer server) {
        POOLS.clear();
        ResourceManager resourceManager = server.getResourceManager();

        // Load each known pool
        String[] poolNames = {"common", "uncommon", "rare", "elite", "legendary"};
        for (String poolName : poolNames) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    Shadowedhearts.MOD_ID, POOL_PATH_PREFIX + "/" + poolName + ".json");
            try {
                Optional<Resource> resource = resourceManager.getResource(location);
                if (resource.isPresent()) {
                    try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                        JsonObject json = GSON.fromJson(reader, JsonObject.class);
                        List<WeightedSpecies> entries = parsePool(poolName, json);
                        if (!entries.isEmpty()) {
                            POOLS.put(poolName, entries);
                            LOG.info("[ShadowHunt] Loaded shadow signal pool '{}' with {} species entries",
                                    poolName, entries.size());
                        }
                    }
                } else {
                    LOG.debug("[ShadowHunt] No shadow signal pool found for '{}'", poolName);
                }
            } catch (Exception e) {
                LOG.error("[ShadowHunt] Failed to load shadow signal pool '{}': {}", poolName, e.getMessage());
            }
        }
        loaded = true;
        LOG.info("[ShadowHunt] Shadow signal pool registry loaded: {} pools", POOLS.size());
    }

    /**
     * Parse a pool JSON object into a list of weighted species entries.
     */
    private static List<WeightedSpecies> parsePool(String poolName, JsonObject json) {
        List<WeightedSpecies> entries = new ArrayList<>();
        if (!json.has("entries")) return entries;

        JsonArray array = json.getAsJsonArray("entries");
        for (JsonElement elem : array) {
            if (!elem.isJsonObject()) continue;
            JsonObject entry = elem.getAsJsonObject();
            String pokemonName = entry.has("pokemon") ? entry.get("pokemon").getAsString() : "";
            int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;

            if (pokemonName.isEmpty() || weight <= 0) continue;

            // Resolve species from Cobblemon registry
            ResourceLocation speciesId = pokemonName.contains(":")
                    ? ResourceLocation.parse(pokemonName)
                    : ResourceLocation.fromNamespaceAndPath("cobblemon", pokemonName);

            Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
            if (species != null) {
                entries.add(new WeightedSpecies(species, weight));
            } else {
                LOG.debug("[ShadowHunt] Unknown species '{}' in pool '{}', skipping", pokemonName, poolName);
            }
        }
        return entries;
    }

    /**
     * Select a random species from the given rarity pool using weighted random selection.
     * Falls back to BST-based selection if the pool is not loaded or empty.
     *
     * @param rarityPool the pool name (e.g., "common", "rare", "legendary")
     * @param rng the random instance to use
     * @return the selected species, or null if nothing could be selected from the pool
     */
    public static Species selectFromPool(String rarityPool, Random rng) {
        List<WeightedSpecies> pool = POOLS.get(rarityPool);
        if (pool == null || pool.isEmpty()) {
            return null; // caller should fall back to BST-based selection
        }

        int totalWeight = 0;
        for (WeightedSpecies ws : pool) {
            totalWeight += ws.weight();
        }

        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (WeightedSpecies ws : pool) {
            cumulative += ws.weight();
            if (roll < cumulative) {
                return ws.species();
            }
        }

        // Should never reach here, but fallback
        return pool.get(rng.nextInt(pool.size())).species();
    }

    /**
     * Get the pool entries for a given pool name (for inspection/debugging).
     */
    public static List<WeightedSpecies> getPool(String poolName) {
        return POOLS.getOrDefault(poolName, Collections.emptyList());
    }

    /**
     * Whether the registry has been loaded.
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Get all loaded pool names.
     */
    public static Set<String> getPoolNames() {
        return Collections.unmodifiableSet(POOLS.keySet());
    }
}
