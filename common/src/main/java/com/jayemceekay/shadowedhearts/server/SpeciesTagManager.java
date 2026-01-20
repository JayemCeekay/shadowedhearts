package com.jayemceekay.shadowedhearts.server;

import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom tag system for Cobblemon Species, as they are not a standard Minecraft registry.
 * Loads from data/&lt;namespace&gt;/tags/species/&lt;name&gt;.json
 */
public class SpeciesTagManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final SpeciesTagManager INSTANCE = new SpeciesTagManager();

    private final Map<ResourceLocation, Set<ResourceLocation>> tagMap = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> resolvedTagMap = new ConcurrentHashMap<>();

    private SpeciesTagManager() {
        super(GSON, "tags/species");
    }

    // Improved apply to handle recursive tags
    protected void applyImproved(Map<ResourceLocation, JsonElement> object) {
        Map<ResourceLocation, TagData> rawTags = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation tagId = entry.getKey();
            JsonElement json = entry.getValue();
            if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                if (obj.has("values") && obj.get("values").isJsonArray()) {
                    TagData data = new TagData();
                    for (JsonElement element : obj.getAsJsonArray("values")) {
                        String s = element.getAsString();
                        if (s.startsWith("#")) {
                            data.tags.add(ResourceLocation.parse(s.substring(1)));
                        } else {
                            data.species.add(ResourceLocation.parse(s));
                        }
                    }
                    rawTags.put(tagId, data);
                }
            }
        }

        resolvedTagMap.clear();
        for (ResourceLocation tagId : rawTags.keySet()) {
            Set<ResourceLocation> species = new HashSet<>();
            collectSpecies(tagId, rawTags, species, new HashSet<>());
            resolvedTagMap.put(tagId, species);
        }
    }

    private void collectSpecies(ResourceLocation tagId, Map<ResourceLocation, TagData> rawTags, Set<ResourceLocation> out, Set<ResourceLocation> visited) {
        if (visited.contains(tagId)) return;
        visited.add(tagId);

        TagData data = rawTags.get(tagId);
        if (data == null) return;

        out.addAll(data.species);
        for (ResourceLocation subTag : data.tags) {
            collectSpecies(subTag, rawTags, out, visited);
        }
    }

    private static class TagData {
        Set<ResourceLocation> species = new HashSet<>();
        Set<ResourceLocation> tags = new HashSet<>();
    }

    public boolean isInTag(Species species, String tagWithHash) {
        if (species == null || tagWithHash == null || !tagWithHash.startsWith("#")) return false;
        String tagName = tagWithHash.substring(1);
        ResourceLocation tagId = ResourceLocation.parse(tagName);
        Set<ResourceLocation> speciesList = resolvedTagMap.get(tagId);
        if (speciesList == null) return false;
        return speciesList.contains(species.getResourceIdentifier());
    }

    @Override
    public void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        applyImproved(object);
    }
}
