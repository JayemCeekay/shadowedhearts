package com.jayemceekay.shadowedhearts.common.editor;

import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure editor draft conversion. The server resolver performs reference and conflict validation. */
public final class TrainerEditorTags {
    private static final String PRESET = "sh_shadow_presets/";
    private TrainerEditorTags() {}

    public static JsonObject decode(Collection<String> tags) {
        JsonObject data = new JsonObject();
        for (String field : List.of("enabled", "unique", "enforceEvo", "mode", "count", "levelMode", "pool", "chance")) data.addProperty(field, "inherit");
        data.addProperty("levelValue", "0");
        data.addProperty("preset", "");
        JsonArray extra = new JsonArray();
        Set<String> consumed = new LinkedHashSet<>();
        for (String tag : tags.stream().sorted().toList()) {
            String field = null, value = null;
            if (tag.equals("sh_shadow_party") || tag.equals("sh_shadow_party_false")) { field = "enabled"; value = tag.endsWith("_false") ? "false" : "true"; }
            else if (tag.equals("sh_unique") || tag.equals("sh_unique_false")) { field = "unique"; value = tag.endsWith("_false") ? "false" : "true"; }
            else if (tag.equals("sh_lvl_enforce_evo_min") || tag.equals("sh_lvl_enforce_evo_min_false")) { field = "enforceEvo"; value = tag.endsWith("_false") ? "false" : "true"; }
            else if (tag.startsWith("sh_shadow_mode_") && Set.of("convert", "append", "replace").contains(tag.substring(15))) { field = "mode"; value = tag.substring(15); }
            else if (tag.startsWith("sh_shadow_n") && NPCShadowProfile.validateTag(tag) == null) { field = "count"; value = tag.substring(11); }
            else if (tag.startsWith("sh_convert_chance_") && NPCShadowProfile.validateTag(tag) == null) { field = "chance"; value = tag.substring(18); }
            else if (tag.equals("sh_pool_none")) { field = "pool"; value = "none"; }
            else if (tag.startsWith("sh_pool/") && NPCShadowProfile.validateTag(tag) == null) { field = "pool"; value = NPCShadowProfile.canonicalId(tag.substring(8)); }
            else if (tag.startsWith(PRESET) && NPCShadowProfile.validateTag(tag) == null) { field = "preset"; value = NPCShadowProfile.canonicalId(tag.substring(PRESET.length())); }
            else if (tag.equals("sh_lvl_match")) { field = "levelMode"; value = "match"; }
            else if (tag.startsWith("sh_lvl_fixed_") && NPCShadowProfile.validateTag(tag) == null && !consumed.contains("levelMode")) {
                field = "levelMode"; value = "fixed"; data.addProperty("levelValue", tag.substring(13));
            } else if (tag.startsWith("sh_lvl_plus_") && NPCShadowProfile.validateTag(tag) == null && !consumed.contains("levelMode")) {
                field = "levelMode"; value = "plus"; data.addProperty("levelValue", tag.substring(12));
            }
            if (field != null && consumed.add(field)) data.addProperty(field, value);
            else extra.add(tag);
        }
        data.add("extraTags", extra);
        return data;
    }

    public static List<String> encode(JsonObject data, int maxLevel) {
        Set<String> tags = new LinkedHashSet<>(strings(data, "extraTags", 256, 256));
        addBoolean(data, tags, "enabled", "sh_shadow_party");
        addBoolean(data, tags, "unique", "sh_unique");
        addBoolean(data, tags, "enforceEvo", "sh_lvl_enforce_evo_min");
        String mode = choice(data, "mode", "inherit", Set.of("inherit", "convert", "append", "replace"));
        if (!mode.equals("inherit")) tags.add("sh_shadow_mode_" + mode);
        String count = string(data, "count", "inherit", 16);
        if (!count.equals("inherit")) tags.add("sh_shadow_n" + integer(count, "count", 0, 6));
        String chance = string(data, "chance", "inherit", 16);
        if (!chance.equals("inherit")) tags.add(chance.equals("default") ? "sh_convert_chance_default" : "sh_convert_chance_" + integer(chance, "chance", 0, 100));
        String levelMode = choice(data, "levelMode", "inherit", Set.of("inherit", "match", "fixed", "plus"));
        if (levelMode.equals("match")) tags.add("sh_lvl_match");
        else if (!levelMode.equals("inherit")) {
            int level = integer(string(data, "levelValue", "0", 16), "levelValue", levelMode.equals("fixed") ? 1 : Integer.MIN_VALUE,
                    levelMode.equals("fixed") ? maxLevel : Integer.MAX_VALUE);
            tags.add("sh_lvl_" + levelMode + "_" + level);
        }
        String pool = string(data, "pool", "inherit", 256);
        if (pool.equals("none")) tags.add("sh_pool_none");
        else if (!pool.equals("inherit")) tags.add("sh_pool/" + NPCShadowProfile.canonicalId(pool));
        String preset = string(data, "preset", "", 256);
        if (!preset.isEmpty()) tags.add(PRESET + NPCShadowProfile.canonicalId(preset));
        for (String tag : tags) {
            if (tag.isBlank() || tag.chars().anyMatch(Character::isWhitespace)) throw new TrainerEditorMessageException(message("error.tags_must_be_nonblank_and_contain_no_whitespace"));
            if (NPCShadowProfile.isShadowTag(tag)) {
                var error = NPCShadowProfile.validateTag(tag);
                if (error != null) throw new com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException(error);
            }
        }
        return List.copyOf(tags);
    }

    private static void addBoolean(JsonObject data, Set<String> tags, String field, String tag) {
        String value = choice(data, field, "inherit", Set.of("inherit", "true", "false"));
        if (!value.equals("inherit")) tags.add(tag + (value.equals("false") ? "_false" : ""));
    }

    static String choice(JsonObject object, String key, String fallback, Set<String> allowed) {
        String value = string(object, key, fallback, 64);
        if (!allowed.contains(value)) throw new TrainerEditorMessageException(message("invalid_field" , field(key), value));
        return value;
    }

    static String string(JsonObject object, String key, String fallback, int maximum) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new TrainerEditorMessageException(message("text_required" , field(key)));
        String text = value.getAsString();
        if (text.length() > maximum) throw new TrainerEditorMessageException(message("text_too_long" , field(key), maximum));
        return text;
    }

    static List<String> strings(JsonObject object, String field, int maximum, int maxLength) {
        if (!object.has(field)) return List.of();
        if (!object.get(field).isJsonArray()) throw new TrainerEditorMessageException(message("array_required" , field(field)));
        JsonArray array = object.getAsJsonArray(field);
        if (array.size() > maximum) throw new TrainerEditorMessageException(message("too_many_entries" , field(field)));
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().length() > maxLength) {
                throw new TrainerEditorMessageException(message("invalid_entry" , field(field)));
            }
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    static int integer(String text, String name, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(text);
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) { throw new TrainerEditorMessageException(message("integer_range" , field(name), minimum, maximum)); }
    }

    static String revision(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
