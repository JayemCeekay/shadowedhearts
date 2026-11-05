package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Client draft only. Retains unedited native Pokemon properties and hidden authored tags. */
public final class TrainerEditorDraft {
    public final String category;
    public final String originalId;
    public String id;
    public String revision;
    public final boolean created;
    private JsonObject baseline;
    private JsonObject data;
    private String latestRevision;
    private final Set<String> structuralConflicts = new LinkedHashSet<>();

    public TrainerEditorDraft(String category, String id, JsonObject data, String revision, boolean created) {
        this.category = category;
        this.originalId = id;
        this.id = id;
        this.baseline = data.deepCopy();
        this.data = data.deepCopy();
        this.revision = revision;
        this.latestRevision = revision;
        this.created = created;
    }

    public JsonObject data() { return data; }
    /** A detached editing checkpoint, including its merge baseline and unresolved conflicts. */
    public TrainerEditorDraft copy() {
        TrainerEditorDraft copy = new TrainerEditorDraft(category, originalId, baseline, revision, created);
        copy.id = id;
        copy.data = data.deepCopy();
        copy.latestRevision = latestRevision;
        copy.structuralConflicts.addAll(structuralConflicts);
        return copy;
    }
    public JsonObject snapshot() { return data.deepCopy(); }
    public String value(String key, String fallback) { return text(data, key, fallback); }
    public void setValue(String key, String value) { data.addProperty(key, value); }
    public boolean dirty() { return created || !id.equals(originalId) || !baseline.equals(data); }
    public void refreshRevision(String revision) {
        latestRevision = revision;
        this.revision = structuralConflicts.isEmpty() ? revision : "";
    }
    public void revert() {
        data = baseline.deepCopy();
        id = originalId;
        structuralConflicts.clear();
        revision = latestRevision;
    }
    public boolean sameRuleIdentity(JsonObject latest) {
        if (!category.equals("rules")) return true;
        for (String key : List.of("kind", "target", "section", "excluded")) {
            if (!java.util.Objects.equals(baseline.get(key), latest.get(key))) return false;
        }
        return true;
    }

    /** Explicit refresh preserves local edits, merges untouched server fields, and reports overlapping edits. */
    public List<String> rebase(JsonObject latest, String revision) {
        List<String> conflicts = new ArrayList<>();
        JsonObject merged = merge(baseline, data, latest, "", conflicts).getAsJsonObject();
        baseline = latest.deepCopy();
        data = merged;
        latestRevision = revision;
        this.revision = structuralConflicts.isEmpty() ? revision : "";
        for (String path : structuralConflicts) conflicts.add(structuralMessage(path));
        return conflicts;
    }

    private JsonElement merge(JsonElement before, JsonElement local, JsonElement remote, String path, List<String> conflicts) {
        if (java.util.Objects.equals(before, local)) return copy(remote);
        if (java.util.Objects.equals(before, remote) || java.util.Objects.equals(local, remote)) return copy(local);
        if (before != null && local != null && remote != null
                && before.isJsonObject() && local.isJsonObject() && remote.isJsonObject()) {
            JsonObject merged = new JsonObject();
            Set<String> keys = new LinkedHashSet<>(before.getAsJsonObject().keySet());
            keys.addAll(local.getAsJsonObject().keySet());
            keys.addAll(remote.getAsJsonObject().keySet());
            for (String key : keys) {
                JsonElement value = merge(before.getAsJsonObject().get(key), local.getAsJsonObject().get(key),
                        remote.getAsJsonObject().get(key), path.isBlank() ? label(key) : path + " / " + label(key), conflicts);
                if (value != null) merged.add(key, value);
            }
            return merged;
        }
        if (before != null && local != null && remote != null
                && before.isJsonArray() && local.isJsonArray() && remote.isJsonArray()) {
            JsonArray baseArray = before.getAsJsonArray();
            JsonArray localArray = local.getAsJsonArray();
            JsonArray remoteArray = remote.getAsJsonArray();
            if (baseArray.size() == localArray.size() && baseArray.size() == remoteArray.size()
                    && !ambiguousPoolRows(baseArray, localArray, remoteArray)) {
                JsonArray merged = new JsonArray();
                for (int index = 0; index < baseArray.size(); index++) {
                    merged.add(merge(baseArray.get(index), localArray.get(index), remoteArray.get(index),
                            path + " [" + (index + 1) + "]", conflicts));
                }
                return merged;
            }
            structuralConflicts.add(path);
            return local.deepCopy();
        }
        conflicts.add(tr("screen.shadowedhearts.trainer_editor.server_changed_field", path));
        return copy(local);
    }

    /**
     * Pool entries have no durable ID. A unique, unchanged species can anchor a row; changing that
     * species may also be a replacement or shift. When both sides edit duplicate-species rows,
     * distinct, unchanged native properties must anchor those rows instead. Otherwise require an
     * explicit restart from the refreshed pool rather than guess which Pokemon an edit belongs to.
     */
    private static boolean ambiguousPoolRows(JsonArray before, JsonArray local, JsonArray remote) {
        List<String> species = poolSpecies(before);
        // Tags, moves and other arrays have no safe positional identity when both sides edit them.
        if (species.isEmpty()) return true;
        if (!species.equals(poolSpecies(local)) || !species.equals(poolSpecies(remote))) return true;
        for (String id : new LinkedHashSet<>(species)) {
            List<Integer> positions = new ArrayList<>();
            for (int index = 0; index < species.size(); index++) if (species.get(index).equals(id)) positions.add(index);
            if (positions.size() < 2) continue;
            boolean localChanged = positions.stream().anyMatch(index -> !before.get(index).equals(local.get(index)));
            boolean remoteChanged = positions.stream().anyMatch(index -> !before.get(index).equals(remote.get(index)));
            if (!localChanged || !remoteChanged) continue;
            Set<JsonObject> anchors = new LinkedHashSet<>();
            for (int index : positions) {
                JsonObject anchor = before.get(index).getAsJsonObject().getAsJsonObject("properties");
                if (!anchors.add(anchor)
                        || !anchor.equals(local.get(index).getAsJsonObject().getAsJsonObject("properties"))
                        || !anchor.equals(remote.get(index).getAsJsonObject().getAsJsonObject("properties"))) return true;
            }
        }
        return false;
    }

    private static List<String> poolSpecies(JsonArray entries) {
        List<String> species = new ArrayList<>();
        for (JsonElement value : entries) {
            if (!value.isJsonObject() || !value.getAsJsonObject().has("properties")
                    || !value.getAsJsonObject().get("properties").isJsonObject()) return List.of();
            species.add(text(value.getAsJsonObject().getAsJsonObject("properties"), "SpeciesText", ""));
        }
        return species;
    }

    private static JsonElement copy(JsonElement value) { return value == null ? null : value.deepCopy(); }
    private static String structuralMessage(String path) {
        return tr("screen.shadowedhearts.trainer_editor.ambiguous_merge", path);
    }

    public List<String> changes() { return changes(false, new JsonArray()); }

    /** Select review fields by identity, never by parsing translated display text. */
    List<String> overviewChanges(JsonArray definitions) { return changes(true, definitions); }

    private List<String> changes(boolean overview, JsonArray definitions) {
        List<String> changes = new ArrayList<>();
        if (created && !overview) changes.add(tr("screen.shadowedhearts.trainer_editor.create_definition", TrainerEditorText.noun(category), id));
        Set<String> keys = new LinkedHashSet<>(baseline.keySet());
        keys.addAll(data.keySet());
        for (String key : keys) {
            if (!created && java.util.Objects.equals(baseline.get(key), data.get(key))) continue;
            if (overview && (key.equals("kind") || key.equals("target"))) continue;
            JsonElement currentValue = data.get(key);
            if (overview && created && (currentValue == null || currentValue.isJsonNull()
                    || currentValue.isJsonArray() && currentValue.getAsJsonArray().isEmpty()
                    || currentValue.isJsonPrimitive() && (currentValue.getAsString().isEmpty() || currentValue.getAsString().equals("inherit"))
                    || key.equals("levelValue") && value("levelMode", "inherit").equals("inherit")
                    || key.equals("section"))) continue;
            if (key.equals("tags") || key.equals("extraTags")) {
                List<String> previous = strings(baseline.get(key)), current = strings(currentValue);
                if (created) changes.add(tr("screen.shadowedhearts.trainer_editor.field_value", label(key), current.isEmpty() ? TrainerEditorText.display("none") : String.join(", ", current)));
                else {
                    previous.stream().filter(tag -> !current.contains(tag)).forEach(tag -> changes.add(tr("screen.shadowedhearts.trainer_editor.removed_tag", label(key), tag)));
                    current.stream().filter(tag -> !previous.contains(tag)).forEach(tag -> changes.add(tr("screen.shadowedhearts.trainer_editor.added_tag", label(key), tag)));
                    if (previous.containsAll(current) && current.containsAll(previous)) changes.add(tr("screen.shadowedhearts.trainer_editor.field_value", label(key), String.join(", ", current)));
                }
            } else changes.add(created ? tr("screen.shadowedhearts.trainer_editor.field_value", label(key), summary(currentValue, definitions, overview)) : tr("screen.shadowedhearts.trainer_editor.field_change", label(key), summary(baseline.get(key), definitions, overview), summary(currentValue, definitions, overview)));
        }
        if (changes.isEmpty() && !overview) changes.add(created ? tr("screen.shadowedhearts.trainer_editor.use_values") : tr("screen.shadowedhearts.trainer_editor.no_changes"));
        return changes;
    }

    public List<String> validation() {
        List<String> errors = new ArrayList<>();
        structuralConflicts.forEach(path -> errors.add(structuralMessage(path)));
        if ((category.equals("presets") || category.equals("pools")) && !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            errors.add(tr("screen.shadowedhearts.trainer_editor.copy.use_an_id_such_as_shadowedhearts_my_team"));
        }
        if (category.equals("rules") && value("target", "").isBlank()) errors.add(tr("screen.shadowedhearts.trainer_editor.copy.choose_a_trainer_or_trainer_type"));
        if (category.equals("npc") || category.equals("presets")) {
            number(errors, "count", 0, 6, "inherit");
            number(errors, "chance", 0, 100, "inherit", "default");
            if (value("levelMode", "inherit").equals("fixed")) number(errors, "levelValue", 1, Integer.MAX_VALUE);
            if (value("levelMode", "inherit").equals("plus")) number(errors, "levelValue", Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if (category.equals("pools")) {
            JsonArray entries = entries();
            if (entries.isEmpty()) errors.add(tr("screen.shadowedhearts.trainer_editor.copy.add_at_least_one_pokemon_to_the_pool"));
            for (int index = 0; index < entries.size(); index++) {
                JsonObject entry = entries.get(index).getAsJsonObject();
                JsonObject properties = properties(entry);
                if (text(properties, "SpeciesText", "").isBlank()) errors.add(tr("screen.shadowedhearts.trainer_editor.species_required", index + 1));
                if (!integer(entry.get("weight"), 1, Integer.MAX_VALUE)) errors.add(tr("screen.shadowedhearts.trainer_editor.weight_required", index + 1));
                if (properties.has("Level") && !integer(properties.get("Level"), 1, Integer.MAX_VALUE)) errors.add(tr("screen.shadowedhearts.trainer_editor.level_required", index + 1));
            }
        }
        return errors;
    }

    private void number(List<String> errors, String key, int min, int max, String... special) {
        String value = value(key, "");
        for (String allowed : special) if (value.equals(allowed)) return;
        try {
            long parsed = Long.parseLong(value);
            if (parsed >= min && parsed <= max) return;
        } catch (NumberFormatException ignored) {}
        errors.add(tr("screen.shadowedhearts.trainer_editor.number_range", label(key), min, max));
    }

    private static boolean integer(JsonElement value, int min, int max) {
        if (value == null || !value.isJsonPrimitive()) return false;
        try {
            int number = value.getAsBigDecimal().intValueExact();
            return number >= min && number <= max;
        } catch (RuntimeException ignored) { return false; }
    }

    public JsonArray entries() {
        if (!data.has("entries") || !data.get("entries").isJsonArray()) data.add("entries", new JsonArray());
        return data.getAsJsonArray("entries");
    }

    public static JsonObject properties(JsonObject entry) {
        if (!entry.has("properties") || !entry.get("properties").isJsonObject()) entry.add("properties", new JsonObject());
        return entry.getAsJsonObject("properties");
    }

    public void addEntry() {
        JsonObject entry = new JsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("SpeciesText", "bulbasaur");
        entry.add("properties", properties);
        entry.addProperty("weight", 1);
        entries().add(entry);
    }

    public static JsonObject defaults(String category) {
        JsonObject result = new JsonObject();
        if (category.equals("rules")) {
            result.addProperty("kind", "trainer");
            result.addProperty("target", "");
            result.addProperty("section", "convert");
            result.addProperty("preset", "");
            result.add("tags", new JsonArray());
        } else if (category.equals("pools")) {
            result.add("entries", new JsonArray());
        } else {
            for (String field : List.of("enabled", "unique", "enforceEvo", "mode", "count", "levelMode", "pool", "chance")) result.addProperty(field, "inherit");
            result.addProperty("levelValue", "0");
            result.addProperty("preset", "");
            result.add("extraTags", new JsonArray());
        }
        return result;
    }

    public static String text(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? fallback : value.getAsString();
    }

    public static List<String> strings(JsonElement value) {
        List<String> result = new ArrayList<>();
        if (value != null && value.isJsonArray()) for (JsonElement entry : value.getAsJsonArray()) if (entry.isJsonPrimitive()) result.add(entry.getAsString());
        return result;
    }

    public static String label(String key) {
        return com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.field(key).getString();
    }

    private static String summary(JsonElement value, JsonArray definitions, boolean overview) {
        if (value == null || value.isJsonNull()) return TrainerEditorText.display("none");
        if (value.isJsonArray()) return tr("screen.shadowedhearts.trainer_editor.entry_count", value.getAsJsonArray().size());
        if (value.isJsonObject()) return tr("screen.shadowedhearts.trainer_editor.updated_properties");
        String text = value.getAsString();
        if (overview && text.startsWith("shadowedhearts:editor/")) for (JsonElement element : definitions) {
            JsonObject definition = element.getAsJsonObject();
            if (text.equals(text(definition, "id", "")) && text(definition, "created", "").equals("true"))
                return tr("screen.shadowedhearts.trainer_editor.new_inline", TrainerEditorText.noun(text(definition, "category", "")));
        }
        return TrainerEditorText.display(text);
    }
}
