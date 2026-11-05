package com.jayemceekay.shadowedhearts.client.gui.trainer;

import static com.jayemceekay.shadowedhearts.client.gui.trainer.TrainerEditorText.tr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorTags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One local authoring session. Inline definitions are checkpoints, never independent server saves. */
public final class TrainerEditorWorkflow {
    private record Frame(TrainerEditorDraft draft, JsonObject row, Map<String, TrainerEditorDraft> before,
                         Map<TrainerEditorDraft, JsonObject> parents, Set<String> privateBefore) {}
    private final TrainerEditorDraft main;
    private JsonObject mainRow;
    private JsonObject savedMainRow;
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Map<String, TrainerEditorDraft> staged = new LinkedHashMap<>();
    private final Map<String, JsonObject> knownDefinitions = new LinkedHashMap<>();
    private final Set<String> privatePresets = new LinkedHashSet<>();
    private final Map<String, String> definitionConflicts = new LinkedHashMap<>();
    private JsonObject definitionRevisions = new JsonObject();
    private TrainerEditorDraft ruleProfile;
    private JsonElement lastRuleTags;
    private String guidedSection;
    private boolean sectionExplicit;
    private String mainConflict = "";
    private String createdConflictId = "";
    private String mainLatestRevision;
    private JsonObject chosenTarget;

    public TrainerEditorWorkflow(TrainerEditorDraft main) { this(main, new JsonObject()); }
    public TrainerEditorWorkflow(TrainerEditorDraft main, JsonObject row) {
        this.main = main;
        this.mainRow = row == null ? new JsonObject() : row.deepCopy();
        this.savedMainRow = mainRow.deepCopy();
        guidedSection = main.value("section", "convert");
        mainLatestRevision = main.revision;
        resetRuleProfile();
    }
    public TrainerEditorDraft main() { return main; }
    public TrainerEditorDraft active() { return stack.isEmpty() ? main : stack.peek().draft(); }
    public boolean nested() { return !stack.isEmpty(); }
    public boolean inlineDirty() {
        if (!nested()) return dirty();
        Frame frame = stack.peek();
        if (frame.draft().dirty() || !staged.keySet().equals(frame.before().keySet())) return true;
        for (String key : staged.keySet()) if (!staged.get(key).snapshot().equals(frame.before().get(key).snapshot())) return true;
        return frame.parents().entrySet().stream().anyMatch(entry -> !entry.getKey().snapshot().equals(entry.getValue()));
    }
    public List<String> activeValidation() { return validateDefinition(active()); }
    public JsonObject activeRow() { return stack.isEmpty() ? mainRow : stack.peek().row(); }
    public void setDefinitionRevisions(JsonObject revisions) { definitionRevisions = revisions.deepCopy(); }

    public void rememberDefinitions(JsonArray definitions) {
        for (JsonElement element : definitions) if (element.isJsonObject()) {
            JsonObject definition = element.getAsJsonObject();
            knownDefinitions.put(key(text(definition, "category", ""), text(definition, "id", "")), definition.deepCopy());
        }
    }

    public JsonObject definition(String category, String id) {
        TrainerEditorDraft local = staged.get(key(category, id));
        if (local != null) return definitionJson(local);
        JsonObject known = knownDefinitions.get(key(category, id));
        return known == null ? null : known.deepCopy();
    }

    public static String newId(String category) {
        return "shadowedhearts:editor/" + (category.equals("pools") ? "pool_" : "preset_") + UUID.randomUUID().toString().replace("-", "");
    }

    public TrainerEditorDraft beginInline(String category, String id, JsonObject data, String revision, boolean created) {
        return beginInline(category, id, data, revision, created, new JsonObject());
    }
    public TrainerEditorDraft beginInline(String category, String id, JsonObject data, String revision, boolean created, JsonObject row) {
        if (!category.equals("presets") && !category.equals("pools")) throw new IllegalArgumentException(tr("screen.shadowedhearts.trainer_editor.copy.only_presets_and_pools_can_be_edited_inline"));
        TrainerEditorDraft previous = staged.get(key(category, id));
        TrainerEditorDraft child = previous == null ? new TrainerEditorDraft(category, id, data, revision, created) : previous.copy();
        Map<TrainerEditorDraft, JsonObject> parents = new LinkedHashMap<>();
        parents.put(main, main.snapshot());
        if (ruleProfile != null) parents.put(ruleProfile, ruleProfile.snapshot());
        stack.forEach(frame -> parents.put(frame.draft(), frame.draft().snapshot()));
        stack.push(new Frame(child, row == null ? new JsonObject() : row.deepCopy(), copyStaged(), parents, new LinkedHashSet<>(privatePresets)));
        return child;
    }

    public TrainerEditorDraft acceptInline() {
        if (stack.isEmpty()) throw new IllegalStateException(tr("screen.shadowedhearts.trainer_editor.copy.no_inline_editor_is_open"));
        TrainerEditorDraft child = active();
        List<String> errors = activeValidation();
        if (!errors.isEmpty()) throw new IllegalStateException(errors.getFirst());
        stack.pop();
        staged.remove(key(child.category, child.originalId));
        staged.put(key(child.category, child.id), child);
        if (!child.id.equals(child.originalId)) {
            renameReferences(child.category, child.originalId, child.id);
            if (child.created) child.refreshRevision(text(definitionRevisions, child.category, child.revision));
        }
        return child;
    }

    /** Cancel includes definitions accepted inside this child, while retaining earlier parent work. */
    public void cancelInline() {
        if (stack.isEmpty()) return;
        Frame frame = stack.pop();
        restoreCheckpoint(frame);
    }

    public JsonObject profile() {
        TrainerEditorDraft draft = active();
        if (!draft.category.equals("rules")) return draft.data();
        syncRuleTags();
        if (draft.value("kind", "trainer").equals("type")) {
            TrainerEditorDraft own = privatePreset();
            JsonObject data = own == null ? ruleProfile.snapshot() : own.snapshot();
            data.addProperty("preset", main.value("preset", ""));
            return data;
        }
        ruleProfile.data().addProperty("preset", main.value("preset", ""));
        return ruleProfile.data();
    }

    public void setProfileValue(String field, String value) {
        TrainerEditorDraft draft = active();
        if (!draft.category.equals("rules")) { draft.setValue(field, value); return; }
        syncRuleTags();
        if (field.equals("preset")) {
            String previous = main.value("preset", "");
            main.setValue(field, value);
            ruleProfile.setValue(field, value);
            if (!previous.equals(value) && privatePresets.contains(previous) && !referencedByDraft(previous)) {
                privatePresets.remove(previous); staged.remove(key("presets", previous));
            }
            return;
        }
        if (main.value("excluded", "false").equals("true")) return;
        if (field.equals("mode") && Set.of("convert", "append", "replace").contains(value)) {
            if (!main.value("section", "convert").equals(guidedSection)) sectionExplicit = true;
            if (!sectionExplicit) { main.setValue("section", value); guidedSection = value; }
        }
        if (main.value("kind", "trainer").equals("type")) {
            TrainerEditorDraft own = privatePreset();
            if (own == null) {
                String id = newId("presets");
                JsonObject data = TrainerEditorDraft.defaults("presets");
                data.addProperty("preset", main.value("preset", ""));
                own = new TrainerEditorDraft("presets", id, data, text(definitionRevisions, "presets", ""), true);
                staged.put(key("presets", id), own);
                privatePresets.add(id);
                main.setValue("preset", id);
            }
            own.setValue(field, value);
            try {
                if (TrainerEditorTags.encode(own.data(), Integer.MAX_VALUE).isEmpty()) {
                    staged.remove(key("presets", own.id)); privatePresets.remove(own.id); main.setValue("preset", "");
                }
            } catch (IllegalArgumentException ignored) { /* Partial text stays editable until validation. */ }
        } else ruleProfile.setValue(field, value);
    }

    /** Advanced section placement is independent of the actual profile behavior. */
    public void setRuleSection(String section) { main.setValue("section", section); sectionExplicit = true; }

    /** Type rules store their behavior in a preset; switching scope keeps the complete authored data. */
    public void setRuleKind(String kind) {
        if (!main.category.equals("rules") || main.value("kind", "trainer").equals(kind)) return;
        syncRuleTags();
        if (kind.equals("type")) {
            JsonObject authored = ruleProfile.snapshot();
            authored.addProperty("preset", main.value("preset", ""));
            boolean hasDirect = !TrainerEditorDraft.strings(authored.get("extraTags")).isEmpty();
            for (String field : List.of("enabled", "mode", "count", "chance", "pool", "levelMode", "unique", "enforceEvo"))
                hasDirect |= !text(authored, field, "inherit").equals("inherit");
            if (hasDirect) {
                String id = newId("presets");
                TrainerEditorDraft own = new TrainerEditorDraft("presets", id, authored, text(definitionRevisions, "presets", ""), true);
                staged.put(key("presets", id), own); privatePresets.add(id);
                main.setValue("preset", id);
                main.data().add("tags", new JsonArray());
            }
        }
        main.setValue("kind", kind);
        resetRuleProfile();
    }

    /** Select a different recipient while retaining the original saved rule identity and all local edits. */
    public void selectRuleTarget(String kind, String target, JsonObject row, String revision) {
        if (!main.category.equals("rules")) throw new IllegalStateException(tr("screen.shadowedhearts.trainer_editor.copy.only_a_trainer_rule_has_a_catalog_target"));
        setRuleKind(kind);
        main.setValue("target", target);
        if (main.created) { main.refreshRevision(revision); mainLatestRevision = revision; }
        chosenTarget = row == null ? new JsonObject() : row.deepCopy();
        mainRow = targetContext(chosenTarget);
    }

    private TrainerEditorDraft privatePreset() {
        String id = main.value("preset", "");
        return privatePresets.contains(id) ? staged.get(key("presets", id)) : null;
    }

    /** Resolve fields for presentation; the server remains authoritative for complete validation. */
    public String effectiveValue(String field, String fallback) {
        JsonObject profile = profile();
        String value = field.equals("levelValue") && text(profile, "levelMode", "inherit").equals("inherit")
                ? "inherit" : text(profile, field, "inherit");
        if (!value.equals("inherit")) return value;
        String preset = text(profile, "preset", "");
        String inherited = presetValue(preset, field, new LinkedHashSet<>());
        if (inherited != null) return inherited;
        JsonObject row = activeRow();
        String originalPreset = row.has("data") && row.get("data").isJsonObject() ? text(row.getAsJsonObject("data"), "preset", "") : "";
        if (!preset.equals(originalPreset) && !preset.isBlank() && definition("presets", preset) == null) return "inherit";
        if (active().category.equals("rules") && field.equals("mode") && row.has("data")
                && !main.value("section", "convert").equals(text(row.getAsJsonObject("data"), "section", "convert"))) return main.value("section", "convert");
        if ((!preset.equals(originalPreset) || presetChainKnown(preset, new LinkedHashSet<>()))
                && row.has("base") && row.get("base").isJsonObject()) return text(row.getAsJsonObject("base"), field, fallback);
        if (row.has("inherited") && row.get("inherited").isJsonObject()) return text(row.getAsJsonObject("inherited"), field, fallback);
        if (row.has("effective") && row.get("effective").isJsonObject()) return text(row.getAsJsonObject("effective"), field, fallback);
        if (active().category.equals("rules") && field.equals("mode")) return main.value("section", fallback);
        return fallback;
    }

    private String presetValue(String id, String field, Set<String> visited) {
        if (id.isBlank() || !visited.add(id) || visited.size() > 16) return null;
        JsonObject definition = definition("presets", id);
        if (definition == null || !definition.has("data")) return null;
        JsonObject data = definition.getAsJsonObject("data");
        String value = field.equals("levelValue") && text(data, "levelMode", "inherit").equals("inherit")
                ? "inherit" : text(data, field, "inherit");
        return value.equals("inherit") ? presetValue(text(data, "preset", ""), field, visited) : value;
    }

    private boolean presetChainKnown(String id, Set<String> visited) {
        if (id.isBlank()) return true;
        if (!visited.add(id) || visited.size() > 16) return false;
        JsonObject definition = definition("presets", id);
        return definition != null && definition.has("data")
                && presetChainKnown(text(definition.getAsJsonObject("data"), "preset", ""), visited);
    }

    public String effectiveMode() { return effectiveValue("mode", "convert"); }
    public boolean visible(String field) {
        String mode = effectiveMode();
        boolean convert = mode.equals("convert");
        if (!Set.of("convert", "append", "replace").contains(mode)) return true;
        return switch (field) {
            case "chance" -> convert;
            case "pool", "levelMode", "unique", "enforceEvo" -> !convert;
            case "levelValue" -> !convert && Set.of("fixed", "plus").contains(text(profile(), "levelMode", "inherit"));
            default -> true;
        };
    }

    public String requestedSummary() {
        if (active().category.equals("pools")) return tr("screen.shadowedhearts.trainer_editor.summary.pool", active().entries().size());
        if (active().category.equals("rules") && main.value("excluded", "false").equals("true")) return tr("screen.shadowedhearts.trainer_editor.summary.excluded", TrainerEditorText.display(main.value("section", "convert")));
        if (effectiveValue("enabled", "true").equals("false")) return tr("screen.shadowedhearts.trainer_editor.summary.disabled");
        String count = effectiveValue("count", "1");
        if (count.equals("0")) return tr("screen.shadowedhearts.trainer_editor.summary.zero");
        String mode = effectiveMode();
        if (!Set.of("convert", "append", "replace").contains(mode) || count.equals("inherit")) return tr("screen.shadowedhearts.trainer_editor.summary.inherit");
        if (mode.equals("convert")) {
            String chance = effectiveValue("chance", "default");
            String chanceText = chance.equals("default") || chance.equals("-1") ? tr("screen.shadowedhearts.trainer_editor.summary.no_roll") : chance.equals("inherit") ? tr("screen.shadowedhearts.trainer_editor.summary.inherited_roll") : tr("screen.shadowedhearts.trainer_editor.summary.chance", chance);
            return tr("screen.shadowedhearts.trainer_editor.summary.convert", count, chanceText);
        }
        String pool = effectiveValue("pool", "none");
        String from = pool.equals("none") || pool.isBlank() ? tr("screen.shadowedhearts.trainer_editor.summary.team_source") : pool.equals("inherit") ? tr("screen.shadowedhearts.trainer_editor.summary.inherited_source") : tr("screen.shadowedhearts.trainer_editor.summary.pool_source", pool);
        return mode.equals("append") ? tr("screen.shadowedhearts.trainer_editor.summary.append", count, from) : tr("screen.shadowedhearts.trainer_editor.summary.replace", count, from);
    }

    public boolean dirty() {
        return main.dirty() || (ruleProfile != null && ruleProfile.dirty()) || staged.values().stream().anyMatch(TrainerEditorDraft::dirty)
                || stack.stream().anyMatch(frame -> frame.draft().dirty());
    }

    public List<String> validation() {
        syncRuleTags();
        if (main.created && !createdConflictId.isBlank() && !main.id.equals(createdConflictId)) {
            createdConflictId = ""; mainConflict = ""; main.refreshRevision(mainLatestRevision);
        }
        List<String> errors = new ArrayList<>(validateDefinition(main));
        if (!mainConflict.isBlank()) errors.add(mainConflict);
        errors.addAll(definitionConflicts.values());
        if (ruleProfile != null && main.value("kind", "trainer").equals("trainer") && !main.value("excluded", "false").equals("true")) {
            errors.addAll(ruleProfile.validation());
            try { TrainerEditorTags.encode(ruleProfile.data(), Integer.MAX_VALUE); }
            catch (IllegalArgumentException invalid) { errors.add(com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.component(invalid).getString()); }
        }
        staged.values().forEach(draft -> validateDefinition(draft).forEach(error -> errors.add(draft.id + ": " + error)));
        stack.forEach(frame -> validateDefinition(frame.draft()).forEach(error -> errors.add(frame.draft().id + ": " + error)));
        for (TrainerEditorDraft draft : staged.values()) {
            if (draft.created && knownDefinitions.containsKey(key(draft.category, draft.id))) errors.add(tr("screen.shadowedhearts.trainer_editor.id_exists", draft.id));
        }
        if (main.revision.isBlank()) errors.add(tr("screen.shadowedhearts.trainer_editor.copy.refresh_the_server_settings_before_applying"));
        for (TrainerEditorDraft draft : staged.values()) if (draft.dirty() && draft.revision.isBlank()) errors.add(tr("screen.shadowedhearts.trainer_editor.refresh_category", TrainerEditorText.category(draft.category), draft.id));
        return errors;
    }

    public List<String> changes() {
        List<String> changes = new ArrayList<>(main.changes());
        if (ruleProfile != null && ruleProfile.dirty()) changes.addAll(ruleProfile.changes());
        staged.values().stream().filter(TrainerEditorDraft::dirty).forEach(draft -> {
            changes.add(tr("screen.shadowedhearts.trainer_editor.inline_definition", TrainerEditorText.category(draft.category), draft.id));
            changes.addAll(draft.changes());
        });
        return changes;
    }

    public List<String> overviewChanges() {
        List<String> changes = new ArrayList<>(main.overviewChanges(definitions()));
        if (ruleProfile != null && ruleProfile.dirty()) changes.addAll(ruleProfile.overviewChanges(definitions()));
        return changes;
    }

    public JsonArray definitions() {
        JsonArray definitions = new JsonArray();
        staged.values().stream().filter(TrainerEditorDraft::dirty).forEach(draft -> definitions.add(definitionJson(draft)));
        return definitions;
    }

    public JsonObject saveRequest(String target) {
        if (nested()) throw new IllegalStateException(tr("screen.shadowedhearts.trainer_editor.copy.finish_the_inline_editor_before_applying"));
        List<String> errors = validation();
        if (!errors.isEmpty()) throw new IllegalStateException(errors.getFirst());
        JsonObject data = main.snapshot();
        if (main.category.equals("rules") && main.value("kind", "trainer").equals("trainer") && !main.value("excluded", "false").equals("true") && ruleProfile.dirty()) {
            JsonObject profile = ruleProfile.snapshot();
            profile.addProperty("preset", "");
            JsonArray tags = new JsonArray();
            TrainerEditorTags.encode(profile, Integer.MAX_VALUE).forEach(tags::add);
            data.add("tags", tags);
        }
        JsonObject request = new JsonObject();
        request.addProperty("action", "save"); request.addProperty("category", main.category);
        request.addProperty("target", target); request.addProperty("id", main.id); request.addProperty("revision", main.revision);
        request.addProperty("created", main.created);
        request.add("data", data); request.add("definitions", definitions());
        return request;
    }

    public void revert() {
        if (nested()) {
            Frame frame = stack.peek();
            frame.draft().revert();
            restoreCheckpoint(frame);
            return;
        }
        main.revert(); staged.clear(); privatePresets.clear(); definitionConflicts.clear(); mainConflict = ""; createdConflictId = "";
        sectionExplicit = false; guidedSection = main.value("section", "convert"); chosenTarget = null;
        mainRow = savedMainRow.deepCopy(); resetRuleProfile();
    }

    public List<String> rebaseMain(JsonObject row, String revision) {
        List<String> conflicts = new ArrayList<>();
        mainConflict = "";
        mainLatestRevision = revision;
        if (main.created) {
            if ((main.category.equals("presets") || main.category.equals("pools")) && row != null && text(row, "id", "").equals(main.id)) {
                main.refreshRevision(revision);
                main.revision = "";
                createdConflictId = main.id;
                mainConflict = tr("screen.shadowedhearts.trainer_editor.new_id_exists", main.id);
                return List.of(mainConflict);
            }
            main.refreshRevision(revision);
            return List.of();
        }
        if (row == null || !row.has("data") || !row.get("data").isJsonObject()) {
            main.revision = "";
            mainConflict = tr("screen.shadowedhearts.trainer_editor.copy.target_unavailable_keep_draft");
            return List.of(mainConflict);
        }
        if (!main.created && !main.sameRuleIdentity(row.getAsJsonObject("data"))) {
            main.revision = "";
            mainConflict = tr("screen.shadowedhearts.trainer_editor.copy.this_rule_changed_identity_cancel_and_locate_the_intended_row_again_before_saving");
            return List.of(mainConflict);
        }
        conflicts.addAll(main.rebase(row.getAsJsonObject("data"), revision));
        savedMainRow = row.deepCopy();
        boolean changedTarget = main.category.equals("rules")
                && (!main.value("target", "").equals(text(row.getAsJsonObject("data"), "target", ""))
                || !main.value("kind", "trainer").equals(text(row.getAsJsonObject("data"), "kind", "trainer")));
        mainRow = changedTarget ? targetContext(chosenTarget == null ? new JsonObject() : chosenTarget) : row.deepCopy();
        if (ruleProfile != null) {
            conflicts.addAll(ruleProfile.rebase(decodedRule(row.getAsJsonObject("data")), revision));
            lastRuleTags = copy(main.data().get("tags"));
        }
        return conflicts;
    }

    public List<String> rebaseDefinition(String category, String id, JsonObject data, String revision) {
        TrainerEditorDraft draft = staged.get(key(category, id));
        String key = key(category, id);
        definitionConflicts.remove(key);
        if (data == null) {
            knownDefinitions.remove(key);
            if (draft != null && draft.created) { draft.refreshRevision(revision); return List.of(); }
            if (draft != null) {
                draft.revision = "";
                String conflict = tr("screen.shadowedhearts.trainer_editor.inline_removed", id);
                definitionConflicts.put(key, conflict);
                return List.of(conflict);
            }
            return List.of();
        }
        List<String> conflicts;
        if (draft != null && draft.created) {
            draft.revision = "";
            String conflict = tr("screen.shadowedhearts.trainer_editor.inline_exists", id);
            definitionConflicts.put(key, conflict);
            conflicts = List.of(conflict);
        } else conflicts = draft == null ? List.of() : draft.rebase(data, revision);
        JsonObject definition = new JsonObject(); definition.addProperty("category", category); definition.addProperty("id", id);
        definition.addProperty("revision", revision); definition.add("data", data.deepCopy());
        knownDefinitions.put(key, definition);
        return conflicts;
    }

    private void resetRuleProfile() {
        if (!main.category.equals("rules")) return;
        ruleProfile = new TrainerEditorDraft("presets", "shadowedhearts:editor/rule_behavior", decodedRule(main.data()), main.revision, false);
        lastRuleTags = copy(main.data().get("tags"));
    }
    private JsonObject targetContext(JsonObject selected) {
        JsonObject context = selected.deepCopy();
        context.addProperty("id", main.value("target", ""));
        if (!context.has("name")) context.addProperty("name", main.value("target", ""));
        context.add("data", main.snapshot());
        if (!context.has("base")) {
            // RCT membership supplies these defaults; it does not inherit another target's rule.
            JsonObject base = new JsonObject();
            base.addProperty("enabled", !main.value("excluded", "false").equals("true"));
            base.addProperty("mode", main.value("section", "convert")); base.addProperty("count", 1);
            base.addProperty("levelMode", "match"); base.addProperty("levelValue", 0); base.addProperty("pool", "none");
            base.addProperty("unique", false); base.addProperty("chance", -1); base.addProperty("enforceEvo", false);
            context.add("base", base);
            JsonObject sources = new JsonObject();
            for (String field : base.keySet()) sources.addProperty(field, List.of("enabled", "mode", "count").contains(field) ? tr("screen.shadowedhearts.trainer_editor.source.rct_defaults") : tr("screen.shadowedhearts.trainer_editor.source.default"));
            context.add("baseSources", sources);
        }
        return context;
    }

    private void syncRuleTags() {
        if (ruleProfile == null || java.util.Objects.equals(lastRuleTags, main.data().get("tags"))) return;
        ruleProfile.rebase(decodedRule(main.data()), main.revision);
        lastRuleTags = copy(main.data().get("tags"));
    }

    private static JsonObject decodedRule(JsonObject data) {
        JsonObject result = TrainerEditorTags.decode(TrainerEditorDraft.strings(data.get("tags")));
        result.addProperty("preset", text(data, "preset", ""));
        return result;
    }
    private static JsonObject definitionJson(TrainerEditorDraft draft) {
        JsonObject result = new JsonObject(); result.addProperty("category", draft.category); result.addProperty("id", draft.id);
        result.addProperty("revision", draft.revision); result.addProperty("created", draft.created); result.add("data", draft.snapshot());
        return result;
    }
    private static List<String> validateDefinition(TrainerEditorDraft draft) {
        List<String> errors = new ArrayList<>(draft.validation());
        if (draft.category.equals("presets")) {
            try {
                if (TrainerEditorTags.encode(draft.data(), Integer.MAX_VALUE).isEmpty()) errors.add(tr("screen.shadowedhearts.trainer_editor.copy.choose_at_least_one_preset_setting_or_a_parent_preset_before_using_this_definition"));
            } catch (IllegalArgumentException invalid) { if (!errors.contains(com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.component(invalid).getString())) errors.add(com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException.component(invalid).getString()); }
        }
        return errors;
    }
    private boolean referencedByDraft(String id) {
        if (references(main.data(), "presets", id)) return true;
        for (TrainerEditorDraft draft : staged.values()) if (!draft.id.equals(id) && references(draft.data(), "presets", id)) return true;
        return false;
    }
    private static boolean references(JsonObject data, String category, String id) {
        String field = category.equals("presets") ? "preset" : "pool";
        if (text(data, field, "").equals(id)) return true;
        String tag = (category.equals("presets") ? "sh_shadow_presets/" : "sh_pool/") + id;
        return TrainerEditorDraft.strings(data.get("tags")).contains(tag) || TrainerEditorDraft.strings(data.get("extraTags")).contains(tag);
    }
    private void renameReferences(String category, String oldId, String newId) {
        replaceReference(main.data(), category, oldId, newId);
        if (ruleProfile != null) replaceReference(ruleProfile.data(), category, oldId, newId);
        staged.values().forEach(draft -> replaceReference(draft.data(), category, oldId, newId));
        stack.forEach(frame -> replaceReference(frame.draft().data(), category, oldId, newId));
        if (category.equals("presets") && privatePresets.remove(oldId)) privatePresets.add(newId);
        definitionConflicts.remove(key(category, oldId));
    }
    private static void replaceReference(JsonObject data, String category, String oldId, String newId) {
        String field = category.equals("presets") ? "preset" : "pool";
        if (text(data, field, "").equals(oldId)) data.addProperty(field, newId);
        String prefix = category.equals("presets") ? "sh_shadow_presets/" : "sh_pool/";
        for (String array : List.of("tags", "extraTags")) if (data.has(array) && data.get(array).isJsonArray()) {
            JsonArray tags = data.getAsJsonArray(array);
            for (int i = 0; i < tags.size(); i++) if (tags.get(i).isJsonPrimitive() && tags.get(i).getAsString().equals(prefix + oldId))
                tags.set(i, new com.google.gson.JsonPrimitive(prefix + newId));
        }
    }
    private Map<String, TrainerEditorDraft> copyStaged() { return copyMap(staged); }
    private void restoreCheckpoint(Frame frame) {
        staged.clear(); staged.putAll(copyMap(frame.before()));
        frame.parents().forEach((draft, data) -> {
            List.copyOf(draft.data().keySet()).forEach(draft.data()::remove);
            data.entrySet().forEach(entry -> draft.data().add(entry.getKey(), entry.getValue().deepCopy()));
        });
        privatePresets.clear(); privatePresets.addAll(frame.privateBefore());
    }
    private static Map<String, TrainerEditorDraft> copyMap(Map<String, TrainerEditorDraft> source) {
        Map<String, TrainerEditorDraft> result = new LinkedHashMap<>(); source.forEach((key, draft) -> result.put(key, draft.copy())); return result;
    }
    private static JsonElement copy(JsonElement value) { return value == null ? null : value.deepCopy(); }
    private static String key(String category, String id) { return category + "/" + id; }
    private static String text(JsonObject data, String key, String fallback) { return TrainerEditorDraft.text(data, key, fallback); }
}
