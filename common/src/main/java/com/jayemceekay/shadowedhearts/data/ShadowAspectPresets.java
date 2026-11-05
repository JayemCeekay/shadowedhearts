package com.jayemceekay.shadowedhearts.data;

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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reloadable preset definitions, supporting conventional and legacy datapack locations. */
public final class ShadowAspectPresets {
    private static final String PREFIX = "sh_shadow_presets/";
    private static final ShadowDefinitionRegistry<List<String>> REGISTRY = new ShadowDefinitionRegistry<>(
            () -> Platform.getConfigFolder().resolve("shadowedhearts").resolve("presets"),
            "shadow_presets", true, ShadowAspectPresets::parse, ShadowAspectPresets::encode);
    private static boolean initialized;

    private ShadowAspectPresets() {}

    public static boolean isPresetKey(String aspect) {
        return aspect != null && aspect.startsWith(PREFIX);
    }

    /** Accepts sh_shadow_presets/namespace/id and sh_shadow_presets/namespace:id. */
    public static ResourceLocation toPresetId(String key) {
        if (!isPresetKey(key)) return null;
        String text = key.substring(PREFIX.length());
        int colon = text.indexOf(':');
        int slash = text.indexOf('/');
        int separator = colon >= 0 ? colon : slash;
        if (separator <= 0 || separator == text.length() - 1) return null;
        return ResourceLocation.tryBuild(text.substring(0, separator), text.substring(separator + 1));
    }

    public static List<String> get(MinecraftServer server, ResourceLocation id) {
        List<String> value = REGISTRY.get(server == null ? null : server.getResourceManager(), id);
        return value == null ? List.of() : value;
    }

    public static Set<ResourceLocation> ids(MinecraftServer server) {
        return REGISTRY.ids(server == null ? null : server.getResourceManager());
    }

    /** Picker/library entries; old aliases remain valid through ids() and get(). */
    public static Set<ResourceLocation> selectableIds(MinecraftServer server) {
        return REGISTRY.selectableIds(server == null ? null : server.getResourceManager());
    }

    public static boolean hasOverride(ResourceLocation id) { return REGISTRY.hasOverride(id); }

    /** Rollback for a coordinated editor save; captures raw overrides, including invalid definitions. */
    public static Runnable editorCheckpoint(ResourceLocation id) { return REGISTRY.checkpoint(id); }

    public static List<Component> diagnostics(MinecraftServer server, ResourceLocation id) {
        List<Component> issues = REGISTRY.diagnostics(server == null ? null : server.getResourceManager(), id);
        if (!issues.isEmpty()) return issues;
        return get(server, id).isEmpty() ? List.of(text("preset.empty", id)) : List.of();
    }

    public static void savePreset(MinecraftServer server, ResourceLocation id, List<String> aspects) {
        if (aspects == null) throw new TrainerEditorMessageException(text("preset.aspects_required"));
        REGISTRY.save(id, aspects);
    }

    /** Removes the disk override; an existing datapack definition becomes effective again. */
    public static void deletePreset(MinecraftServer server, ResourceLocation id) {
        REGISTRY.delete(id);
    }

    public static Map<ResourceLocation, List<String>> getRuntimePresets() {
        return REGISTRY.runtimeValues();
    }

    public static synchronized void init() {
        if (initialized) return;
        REGISTRY.reload();
        ReloadListenerRegistry.register(PackType.SERVER_DATA, (ResourceManagerReloadListener) manager -> REGISTRY.reload());
        initialized = true;
    }

    static List<String> parse(JsonElement root) {
        JsonArray array = definitionArray(root, "aspects");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.size() != 1 || !object.has("aspect")) {
                    throw new TrainerEditorMessageException(text("preset.aspect_object", i));
                }
                element = object.get("aspect");
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new TrainerEditorMessageException(text("preset.aspect_string", i));
            }
            String aspect = element.getAsString();
            if (aspect.isBlank() || aspect.chars().anyMatch(Character::isWhitespace)) {
                throw new TrainerEditorMessageException(text("preset.aspect_whitespace", i));
            }
            if (isPresetKey(aspect) && toPresetId(aspect) == null) {
                throw new TrainerEditorMessageException(text("preset.reference_invalid", i, aspect));
            }
            result.add(aspect);
        }
        return List.copyOf(result);
    }

    static JsonArray definitionArray(JsonElement root, String field) {
        if (root != null && root.isJsonArray()) return root.getAsJsonArray();
        if (root != null && root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (object.size() != 1 || !object.has(field) || !object.get(field).isJsonArray()) {
                throw new TrainerEditorMessageException(text("array_only", field));
            }
            return object.getAsJsonArray(field);
        }
        throw new TrainerEditorMessageException(text("array_required", field));
    }

    private static JsonElement encode(List<String> aspects) {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        aspects.forEach(array::add);
        object.add("aspects", array);
        return object;
    }
}
