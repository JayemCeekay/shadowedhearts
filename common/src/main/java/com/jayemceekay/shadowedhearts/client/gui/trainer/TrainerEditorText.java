package com.jayemceekay.shadowedhearts.client.gui.trainer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

/** Resolve presentation strings on the client; identifiers and authored values stay untouched. */
public final class TrainerEditorText {
    private TrainerEditorText() {}

    public static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    public static String display(String value) {
        return switch (value) {
            case "inherit", "true", "false", "none", "default", "convert", "append", "replace",
                    "match", "fixed", "plus", "trainer", "type" -> tr("screen.shadowedhearts.trainer_editor.value." + value);
            case "" -> tr("screen.shadowedhearts.trainer_editor.value.none");
            default -> value;
        };
    }

    public static String category(String value) {
        return switch (value) {
            case "trainers", "types", "rules", "presets", "pools", "npc" ->
                    tr("screen.shadowedhearts.trainer_editor.category." + value);
            default -> value;
        };
    }

    public static String noun(String value) {
        return switch (value) {
            case "trainers", "types", "rules", "presets", "pools", "npc" ->
                    tr("screen.shadowedhearts.trainer_editor.noun." + value);
            default -> tr("screen.shadowedhearts.trainer_editor.noun.settings");
        };
    }

    /** Structured components are accepted only at explicitly marked presentation fields. */
    public static String serverText(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        Component component = Component.Serializer.fromJson(value, RegistryAccess.EMPTY);
        return component == null ? "" : component.getString();
    }

    public static void localizeResponse(JsonObject response) {
        resolveField(response, "message");
        resolveArray(response, "diagnostics");
        for (String collection : new String[]{"rows", "definitions"}) {
            if (!response.has(collection)) continue;
            for (JsonElement element : response.getAsJsonArray(collection)) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                if (row.has("displayName")) row.addProperty("name", serverText(row.remove("displayName")));
                for (String field : new String[]{"subtitle", "targetKindLabel", "targetHelp"}) resolveField(row, field);
                for (String field : new String[]{"details", "diagnostics"}) resolveArray(row, field);
                for (String field : new String[]{"sources", "inheritedSources", "baseSources"}) {
                    if (!row.has(field) || !row.get(field).isJsonObject()) continue;
                    JsonObject sources = row.getAsJsonObject(field);
                    for (String key : java.util.List.copyOf(sources.keySet())) resolveField(sources, key);
                }
            }
        }
    }

    private static void resolveField(JsonObject object, String field) {
        if (object.has(field)) object.addProperty(field, serverText(object.get(field)));
    }

    private static void resolveArray(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonArray()) return;
        var array = object.getAsJsonArray(field);
        for (int i = 0; i < array.size(); i++) array.set(i, new com.google.gson.JsonPrimitive(serverText(array.get(i))));
    }
}
