package com.jayemceekay.shadowedhearts.data;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException;
import static com.jayemceekay.shadowedhearts.data.DefinitionMessages.text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/** Reloadable weighted pools at data/<namespace>/shadow_pools/<path>.json. */
public final class ShadowPools {
    private static final Pattern LEVEL = Pattern.compile("(?:^|\\s)(?:level|lvl|l)=([^\\s]*)", Pattern.CASE_INSENSITIVE);
    private static final ShadowDefinitionRegistry<List<WeightedEntry>> REGISTRY = new ShadowDefinitionRegistry<>(
            () -> Platform.getConfigFolder().resolve("shadowedhearts").resolve("shadow_pools"),
            "shadow_pools", false, ShadowPools::parsePool, ShadowPools::encode);
    private static boolean initialized;

    private ShadowPools() {}

    public static final class WeightedEntry {
        public final PokemonProperties props;
        public final int weight;

        public WeightedEntry(PokemonProperties props, int weight) {
            if (props == null) throw new TrainerEditorMessageException(text("pool.properties_required"));
            if (weight <= 0) throw new TrainerEditorMessageException(text("pool.entry_weight_positive"));
            this.props = props.copy();
            this.weight = weight;
        }

        public JsonElement toJson() {
            JsonObject object = new JsonObject();
            // Native object serialization preserves values even when originalString is stale.
            object.add("properties", props.saveToJSON());
            object.addProperty("weight", weight);
            return object;
        }
    }

    public static List<WeightedEntry> get(MinecraftServer server, ResourceLocation id) {
        List<WeightedEntry> value = REGISTRY.get(server == null ? null : server.getResourceManager(), id);
        return value == null ? List.of() : copyEntries(value);
    }

    public static Set<ResourceLocation> ids(MinecraftServer server) {
        return REGISTRY.ids(server == null ? null : server.getResourceManager());
    }

    public static boolean hasOverride(ResourceLocation id) { return REGISTRY.hasOverride(id); }

    /** Rollback for a coordinated editor save; captures raw overrides, including invalid definitions. */
    public static Runnable editorCheckpoint(ResourceLocation id) { return REGISTRY.checkpoint(id); }

    /** Validates and decodes the same native pool object used by datapacks and the in-game editor. */
    public static List<WeightedEntry> parseDefinition(JsonElement definition) { return parsePool(definition); }

    public static List<Component> diagnostics(MinecraftServer server, ResourceLocation id) {
        List<Component> issues = REGISTRY.diagnostics(server == null ? null : server.getResourceManager(), id);
        if (!issues.isEmpty()) return issues;
        return get(server, id).isEmpty() ? List.of(text("pool.empty", id)) : List.of();
    }

    public static void savePool(MinecraftServer server, ResourceLocation id, List<WeightedEntry> entries) {
        if (entries == null) throw new TrainerEditorMessageException(text("pool.entries_required"));
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) == null) throw new TrainerEditorMessageException(text("pool.entry_null", i));
            validateProperties(entries.get(i).props);
        }
        REGISTRY.save(id, copyEntries(entries));
    }

    /** Removes the disk override; an existing datapack definition becomes effective again. */
    public static void deletePool(MinecraftServer server, ResourceLocation id) {
        REGISTRY.delete(id);
    }

    public static Map<ResourceLocation, List<WeightedEntry>> getRuntimePools() {
        Map<ResourceLocation, List<WeightedEntry>> result = new LinkedHashMap<>();
        REGISTRY.runtimeValues().forEach((id, entries) -> result.put(id, copyEntries(entries)));
        return java.util.Collections.unmodifiableMap(result);
    }

    public static synchronized void init() {
        if (initialized) return;
        // Keep raw JSON until first use: Cobblemon's species/property registries load later.
        REGISTRY.reload();
        ReloadListenerRegistry.register(PackType.SERVER_DATA, (ResourceManagerReloadListener) manager -> REGISTRY.reload());
        initialized = true;
    }

    static List<WeightedEntry> parsePool(JsonElement root) {
        JsonArray array = ShadowAspectPresets.definitionArray(root, "entries");
        List<WeightedEntry> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonElement element = array.get(i);
                int weight = 1;
                PokemonProperties properties;
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    properties = parsePropertyText(element.getAsString());
                } else if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    for (String key : object.keySet()) {
                        if (!Set.of("pokemon", "properties", "weight").contains(key)) {
                            throw new TrainerEditorMessageException(text("pool.unknown_field", key));
                        }
                    }
                    if (object.has("weight")) weight = parseWeight(object.get("weight"));
                    PokemonProperties textProperties = null;
                    if (object.has("pokemon")) {
                        if (!object.get("pokemon").isJsonPrimitive() || !object.getAsJsonPrimitive("pokemon").isString()) {
                            throw new TrainerEditorMessageException(text("pool.pokemon_string"));
                        }
                        textProperties = parsePropertyText(object.get("pokemon").getAsString());
                    }
                    if (object.has("properties")) {
                        if (!object.get("properties").isJsonObject()) throw new TrainerEditorMessageException(text("pool.properties_object"));
                        if (PokemonSpecies.INSTANCE.getSpecies().isEmpty()) {
                            throw new ShadowDefinitionRegistry.NotReadyException(text("pool.species_not_ready"));
                        }
                        JsonObject nativeProperties = object.getAsJsonObject("properties");
                        validateNativeProperties(nativeProperties, Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel());
                        properties = new PokemonProperties().loadFromJSON(nativeProperties);
                        // Preserve the existing object-over-string precedence and missing-field merge.
                        if (textProperties != null) {
                            if (properties.getSpecies() == null) properties.setSpecies(textProperties.getSpecies());
                            if (properties.getLevel() == null) properties.setLevel(textProperties.getLevel());
                            if (properties.getForm() == null) properties.setForm(textProperties.getForm());
                        }
                    } else {
                        if (textProperties == null) throw new TrainerEditorMessageException(text("pool.properties_missing"));
                        properties = textProperties;
                    }
                } else {
                    throw new TrainerEditorMessageException(text("pool.entry_type"));
                }
                validateProperties(properties);
                result.add(new WeightedEntry(properties, weight));
            } catch (ShadowDefinitionRegistry.NotReadyException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new TrainerEditorMessageException(text("pool.entry_error", i, TrainerEditorMessageException.component(ex)), ex);
            }
        }
        return List.copyOf(result);
    }

    static int parseWeight(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new TrainerEditorMessageException(text("pool.weight_positive"));
        }
        try {
            int weight = element.getAsBigDecimal().intValueExact();
            if (weight <= 0) throw new ArithmeticException("not positive");
            return weight;
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new TrainerEditorMessageException(text("pool.weight_range", Integer.MAX_VALUE), ex);
        }
    }

    /** Native JSON uses 'Level'; reject fractions/overflow before Gson's asInt can truncate them. */
    static void validateNativeProperties(JsonObject properties, int maxLevel) {
        if (!properties.has("Level")) return;
        JsonElement level = properties.get("Level");
        Component message = text("pool.native_level_range", maxLevel);
        if (!level.isJsonPrimitive() || !level.getAsJsonPrimitive().isNumber()) {
            throw new TrainerEditorMessageException(message);
        }
        try {
            int value = level.getAsBigDecimal().intValueExact();
            if (value < 1 || value > maxLevel) throw new ArithmeticException("out of range");
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new TrainerEditorMessageException(message, ex);
        }
    }

    /** Authoring boundary: validates raw level input before Cobblemon can silently clamp it. */
    public static PokemonProperties parseProperties(String text) {
        PokemonProperties properties = parsePropertyText(text);
        validateProperties(properties);
        return properties;
    }

    private static PokemonProperties parsePropertyText(String text) {
        if (text == null || text.isBlank()) throw new TrainerEditorMessageException(text("pool.properties_blank"));
        if (PokemonSpecies.INSTANCE.getSpecies().isEmpty()) {
            throw new ShadowDefinitionRegistry.NotReadyException(text("pool.species_not_ready"));
        }
        PokemonProperties properties = PokemonProperties.Companion.parse(text);
        var levels = LEVEL.matcher(text);
        while (levels.find()) {
            try {
                int requested = Integer.parseInt(levels.group(1));
                if (requested < 1 || properties.getLevel() == null || properties.getLevel() != requested) {
                    throw new NumberFormatException("clamped or conflicting level");
                }
            } catch (NumberFormatException ex) {
                throw new TrainerEditorMessageException(text("pool.level_conflict", levels.group(1)), ex);
            }
        }
        return properties;
    }

    private static void validateProperties(PokemonProperties properties) {
        if (PokemonSpecies.INSTANCE.getSpecies().isEmpty()) {
            throw new ShadowDefinitionRegistry.NotReadyException(text("pool.species_not_ready"));
        }
        String species = properties.getSpecies();
        if (species == null || species.isBlank()) {
            throw new TrainerEditorMessageException(text("pool.species_required"));
        }
        int maxLevel = Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel();
        if (properties.getLevel() != null && (properties.getLevel() < 1 || properties.getLevel() > maxLevel)) {
            throw new TrainerEditorMessageException(text("pool.level_range", maxLevel));
        }
        if (!species.equals("random")) {
            ResourceLocation id = species.contains(":") ? ResourceLocation.tryParse(species)
                    : ResourceLocation.tryBuild("cobblemon", species);
            if (id == null) throw new TrainerEditorMessageException(text("pool.species_id_invalid", species));
            if (PokemonSpecies.INSTANCE.getByIdentifier(id) == null) {
                throw new TrainerEditorMessageException(text("pool.species_unknown", species));
            }
        }
    }

    private static JsonElement encode(List<WeightedEntry> entries) {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        entries.forEach(entry -> array.add(entry.toJson()));
        object.add("entries", array);
        return object;
    }

    private static List<WeightedEntry> copyEntries(List<WeightedEntry> entries) {
        return entries.stream().map(entry -> new WeightedEntry(entry.props, entry.weight)).toList();
    }

    public static List<PokemonProperties> pick(Random rng, List<WeightedEntry> entries, int count) {
        if (entries == null || entries.isEmpty() || count <= 0) return List.of();
        long total = 0;
        for (WeightedEntry entry : entries) total = Math.addExact(total, entry.weight);
        List<PokemonProperties> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long roll = rng.nextLong(total);
            long accumulated = 0;
            for (WeightedEntry entry : entries) {
                accumulated += entry.weight;
                if (roll < accumulated) {
                    result.add(entry.props.copy());
                    break;
                }
            }
        }
        return result;
    }
}
