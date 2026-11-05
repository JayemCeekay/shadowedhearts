package com.jayemceekay.shadowedhearts.common.editor;

import static com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages.*;

import com.cobblemon.mod.common.Cobblemon;
import com.google.gson.*;
import com.jayemceekay.shadowedhearts.AspectHolder;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowProfile;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowProfiles;
import com.jayemceekay.shadowedhearts.common.shadow.NPCShadowTargets;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.data.ShadowAspectPresets;
import com.jayemceekay.shadowedhearts.data.ShadowPools;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTBridgeHolder;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTTrainerCatalog;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTShadowRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

/** Server-owned, permission-checked authoring. No commands or executable client strings are evaluated. */
public final class TrainerEditorService {
    private static final int PAGE_SIZE = 12;
    private static final int MAX_REQUEST = 8192;
    private static final int MAX_RESPONSE = 200000;
    private static final Set<String> CATEGORIES = Set.of("trainers", "types", "rules", "presets", "pools", "npc");
    private static final Set<String> SECTIONS = Set.of("replace", "append", "convert");
    private static final String PRESET_PREFIX = "sh_shadow_presets/";
    private static RCTTrainerCatalog lastCatalog;
    private static String lastCatalogRevision;

    private TrainerEditorService() {}

    public static String handle(ServerPlayer player, String json) {
        JsonObject response = new JsonObject();
        response.addProperty("requestId", 0);
        response.addProperty("ok", false);
        try {
            if (!player.createCommandSourceStack().hasPermission(2)) throw new TrainerEditorMessageException(message("error.operator_permission_level_2_is_required"));
            MinecraftServer server = player.getServer();
            if (server == null) throw TrainerEditorMessageException.state(message("server_unavailable"));
            if (!server.isSameThread()) throw TrainerEditorMessageException.state(message("error.editor_requests_must_run_on_the_server_thread"));
            validateEnvelope(json);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new TrainerEditorMessageException(message("error.editor_request_must_be_an_object"));
            JsonObject request = parsed.getAsJsonObject();
            int requestId = number(request, "requestId", 0, 0, Integer.MAX_VALUE);
            String action = TrainerEditorTags.choice(request, "action", "query", Set.of("query", "save", "delete"));
            String category = TrainerEditorTags.choice(request, "category", "trainers", CATEGORIES);
            response.addProperty("requestId", requestId);
            response.addProperty("action", action);
            response.addProperty("category", category);
            String target = TrainerEditorTags.string(request, "target", "", 36);
            String id = TrainerEditorTags.string(request, "id", "", 256);
            Snapshot snapshot = snapshot(server, category, target);
            if (action.equals("query")) {
                query(server, response, request, snapshot, category);
            } else {
                String revision = TrainerEditorTags.string(request, "revision", "", 64);
                requireRevision(revision, snapshot.revision());
                if (action.equals("delete")) delete(server, category, target, id, snapshot);
                else {
                    if (!request.has("data") || !request.get("data").isJsonObject()) throw new TrainerEditorMessageException(message("error.an_editor_draft_object_is_required"));
                    saveWorkflow(server, category, target, id, request, snapshot);
                }
                response.add("message", json(action.equals("delete") ? message("deleted_override") : message("saved_changes")));
            }
            response.addProperty("ok", true);
        } catch (RuntimeException ex) {
            response.addProperty("ok", false);
            response.add("message", json(TrainerEditorMessageException.component(ex)));
        }
        String encoded = response.toString();
        if (encoded.length() > MAX_RESPONSE) {
            JsonObject failure = new JsonObject();
            failure.add("requestId", response.get("requestId"));
            if (response.has("action")) failure.add("action", response.get("action"));
            if (response.has("category")) failure.add("category", response.get("category"));
            failure.addProperty("ok", false);
            failure.add("message", json(message("response_too_large")));
            return failure.toString();
        }
        return encoded;
    }

    static void requireRevision(String expected, String current) {
        if (expected.isEmpty() || !expected.equals(current)) throw TrainerEditorMessageException.state(message("error.these_settings_changed_since_you_opened_them_refresh_and_review_your_draft_before_saving"));
    }

    public static void validateEnvelope(String json) {
        if (json == null || json.length() > MAX_REQUEST) throw new TrainerEditorMessageException(message("error.editor_request_is_too_large"));
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') quoted = true;
            else if (character == '{' || character == '[') {
                if (++depth > 32) throw new TrainerEditorMessageException(message("error.editor_request_is_nested_too_deeply"));
            } else if (character == '}' || character == ']') depth--;
        }
    }

    private record Snapshot(List<JsonObject> rows, List<String> presets, List<String> pools,
                            List<Object> diagnostics, String revision, String configFingerprint, RCTTrainerCatalog catalog) {}

    private static Snapshot snapshot(MinecraftServer server, String category, String target) {
        RCTTrainerCatalog catalog = RCTBridgeHolder.INSTANCE.catalog(server);
        List<String> presets = ShadowAspectPresets.ids(server).stream().map(ResourceLocation::toString).sorted().toList();
        List<String> pools = ShadowPools.ids(server).stream().map(ResourceLocation::toString).sorted().toList();
        List<JsonObject> rows = switch (category) {
            case "trainers" -> catalogRows(catalog.trainers(), "trainer", catalog.types());
            case "types" -> catalogRows(catalog.types(), "type", catalog.types());
            case "rules" -> ruleRows(server, catalog);
            case "presets" -> presetRows(server, presets);
            case "pools" -> poolRows(server, pools);
            case "npc" -> npcRows(server, target);
            default -> throw new TrainerEditorMessageException(message("error.unknown_category"));
        };
        StringBuilder state = new StringBuilder();
        String configFingerprint = TrainerEditorConfigBinding.fingerprint();
        if (Set.of("trainers", "types", "rules").contains(category)) {
            state.append("rct|").append(catalogRevision(catalog)).append(ruleRows(server, catalog)).append(configFingerprint);
        } else {
            state.append(category).append('|').append(target).append('|').append(rows);
            if (category.equals("npc") && !target.isEmpty()) {
                Entity entity = target(server, target);
                state.append(new TreeSet<>(entity.getTags()));
                if (entity instanceof AspectHolder holder) state.append(new TreeSet<>(holder.shadowedhearts$getAspects()));
            }
        }
        // A changed reference invalidates the draft too, so validation/review uses the same definitions.
        for (String preset : presets) {
            ResourceLocation resourceId = ResourceLocation.parse(preset);
            state.append(preset).append(ShadowAspectPresets.get(server, resourceId)).append(ShadowAspectPresets.diagnostics(server, resourceId));
        }
        for (String pool : pools) {
            ResourceLocation resourceId = ResourceLocation.parse(pool);
            state.append(pool).append(ShadowPools.diagnostics(server, resourceId));
            for (var entry : ShadowPools.get(server, resourceId)) state.append(entry.toJson());
        }
        List<Object> diagnostics = new ArrayList<>(catalog.diagnostics());
        if (catalog.loading()) diagnostics.add(message("rct_loading"));
        if (category.equals("npc") && target.isEmpty()) diagnostics.add(message("loaded_npcs"));
        return new Snapshot(List.copyOf(rows), presets, pools, List.copyOf(diagnostics), TrainerEditorTags.revision(state.toString()), configFingerprint, catalog);
    }

    private static void query(MinecraftServer server, JsonObject response, JsonObject request, Snapshot snapshot, String category) {
        String search = TrainerEditorTags.string(request, "search", "", 128).toLowerCase(Locale.ROOT);
        String exact = TrainerEditorTags.string(request, "id", "", 256);
        Set<String> selectablePresets = ShadowAspectPresets.selectableIds(server).stream()
                .map(ResourceLocation::toString).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        List<JsonObject> filtered = snapshot.rows().stream().filter(row -> exact.isEmpty() || row.get("id").getAsString().equals(exact))
                // Exact lookups still resolve aliases used by already-saved rules and NPCs.
                .filter(row -> !exact.isEmpty() || !category.equals("presets") || selectablePresets.contains(row.get("id").getAsString()))
                .filter(row -> search.isEmpty() || searchable(row).contains(search))
                .sorted(Comparator.comparing((JsonObject row) -> row.get("name").getAsString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(row -> row.get("id").getAsString())).toList();
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(number(request, "page", 0, 0, 1000000), pages - 1);
        JsonArray rows = new JsonArray();
        filtered.subList(Math.min(filtered.size(), page * PAGE_SIZE), Math.min(filtered.size(), (page + 1) * PAGE_SIZE)).forEach(rows::add);
        response.addProperty("revision", snapshot.revision());
        response.addProperty("page", page);
        response.addProperty("pages", pages);
        response.addProperty("total", filtered.size());
        response.add("rows", rows);
        response.add("presets", array(selectablePresets));
        response.add("pools", array(snapshot.pools()));
        response.add("diagnostics", diagnostics(snapshot.diagnostics()));
        addDefinitions(server, response, rows, category);
        response.add("message", json(filtered.isEmpty() ? message("no_matching_category" , message("category." + category)) : ""));
    }

    private static void addDefinitions(MinecraftServer server, JsonObject response, JsonArray rows, String category) {
        Snapshot presets = snapshot(server, "presets", "");
        Snapshot pools = snapshot(server, "pools", "");
        JsonObject revisions = new JsonObject();
        revisions.addProperty("presets", presets.revision()); revisions.addProperty("pools", pools.revision());
        response.add("definitionRevisions", revisions);
        Map<String, JsonObject> available = new LinkedHashMap<>();
        for (var entry : Map.of("presets", presets, "pools", pools).entrySet()) {
            for (JsonObject row : entry.getValue().rows()) {
                JsonObject definition = row.deepCopy();
                definition.addProperty("category", entry.getKey());
                definition.addProperty("revision", entry.getValue().revision());
                available.put(entry.getKey() + "/" + row.get("id").getAsString(), definition);
            }
        }
        Set<String> referenced = new LinkedHashSet<>();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            if (category.equals("presets") || category.equals("pools")) referenced.add(category + "/" + row.get("id").getAsString());
            collectReferences(row, referenced);
        }
        JsonArray definitions = new JsonArray();
        Set<String> included = new HashSet<>();
        while (included.size() < referenced.size()) {
            String key = referenced.stream().filter(value -> !included.contains(value)).findFirst().orElseThrow();
            included.add(key);
            JsonObject definition = available.get(key);
            if (definition != null) { definitions.add(definition); collectReferences(definition.getAsJsonObject("data"), referenced); }
        }
        response.add("definitions", definitions);
    }

    private static void collectReferences(JsonElement value, Set<String> references) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(element -> collectReferences(element, references)); return; }
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if ((entry.getKey().equals("preset") || entry.getKey().equals("pool")) && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String id = child.getAsString();
                    if (!id.isEmpty() && !Set.of("inherit", "none").contains(id)) {
                        try { references.add((entry.getKey().equals("preset") ? "presets/" : "pools/") + NPCShadowProfile.canonicalId(id)); }
                        catch (IllegalArgumentException ignored) { /* The row already carries the definition diagnostic. */ }
                    }
                }
                collectReferences(child, references);
            }
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String tag = value.getAsString();
            try {
                if (tag.startsWith(PRESET_PREFIX)) references.add("presets/" + NPCShadowProfile.canonicalId(tag.substring(PRESET_PREFIX.length())));
                else if (tag.startsWith("sh_pool/")) references.add("pools/" + NPCShadowProfile.canonicalId(tag.substring(8)));
            } catch (IllegalArgumentException ignored) { /* Malformed legacy tags remain visible for repair. */ }
        }
    }

    static String searchable(JsonObject row) {
        StringBuilder text = new StringBuilder();
        for (String field : List.of("id", "name", "subtitle", "type", "source")) {
            if (row.has(field)) text.append(' ').append(row.get(field).isJsonPrimitive() ? row.get(field).getAsString() : row.get(field).toString());
        }
        if (row.has("data") && row.getAsJsonObject("data").has("target")) text.append(' ').append(row.getAsJsonObject("data").get("target").getAsString());
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static synchronized String catalogRevision(RCTTrainerCatalog catalog) {
        if (lastCatalog != catalog) {
            lastCatalog = catalog;
            lastCatalogRevision = TrainerEditorTags.revision(catalog.toString());
        }
        return lastCatalogRevision;
    }

    private static List<JsonObject> catalogRows(List<RCTTrainerCatalog.Entry> entries, String kind, List<RCTTrainerCatalog.Entry> types) {
        List<JsonObject> rows = new ArrayList<>();
        Map<String, RCTTrainerCatalog.Entry> typeNames = new HashMap<>(); types.forEach(type -> typeNames.put(type.id(), type));
        for (var entry : entries) {
            JsonObject data = new JsonObject();
            data.addProperty("kind", kind); data.addProperty("target", entry.id()); data.addProperty("section", "replace");
            data.addProperty("preset", ""); data.add("tags", new JsonArray());
            var type = typeNames.get(entry.type());
            var label = type == null ? net.minecraft.network.chat.Component.literal(entry.type()) : type.displayName().copy();
            if (type != null && !type.name().equals(entry.type())) label.append(" (" + entry.type() + ")");
            JsonObject row = row(entry.id(), entry.name(), label, entry.type(), entry.sourcePack(), entry.details(), entry.diagnostics(), data);
            row.add("displayName", json(entry.displayName()));
            rows.add(row);
        }
        return rows;
    }

    private static List<JsonObject> presetRows(MinecraftServer server, List<String> ids) {
        List<JsonObject> rows = new ArrayList<>();
        for (String text : ids) {
            ResourceLocation id = ResourceLocation.parse(text);
            List<String> tags = ShadowAspectPresets.get(server, id);
            rows.add(row(text, text, message("tag_count" , tags.size()), "preset", ShadowAspectPresets.hasOverride(id) ? "runtime" : "datapack",
                    tags, ShadowAspectPresets.diagnostics(server, id), TrainerEditorTags.decode(tags)));
        }
        return rows;
    }

    private static List<JsonObject> poolRows(MinecraftServer server, List<String> ids) {
        List<JsonObject> rows = new ArrayList<>();
        for (String text : ids) {
            ResourceLocation id = ResourceLocation.parse(text);
            JsonArray entries = new JsonArray();
            for (var entry : ShadowPools.get(server, id)) entries.add(entry.toJson());
            JsonObject data = new JsonObject(); data.add("entries", entries);
            rows.add(row(text, text, message("weighted_entries" , entries.size()), "pool", ShadowPools.hasOverride(id) ? "runtime" : "datapack",
                    List.of(message("override_help")), ShadowPools.diagnostics(server, id), data));
        }
        return rows;
    }

    private static List<JsonObject> npcRows(MinecraftServer server, String target) {
        if (!target.isEmpty()) return List.of(npcRow(target(server, target)));
        List<JsonObject> rows = new ArrayList<>();
        for (var level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (NPCShadowTargets.isSupported(entity)) rows.add(npcRow(entity));
            }
        }
        return rows;
    }

    private static JsonObject npcRow(Entity entity) {
        Set<String> tags = authored(entity);
        var resolution = NPCShadowProfiles.resolve(entity);
        List<Object> details = new ArrayList<>(List.of(NPCShadowTargets.labelText(entity), NPCShadowTargets.helpText(entity),
                message("effective_heading")));
        NPCShadowProfile effectiveProfile = resolution.profile();
        Map<String, String> effectiveFields = Map.of("enabled", Boolean.toString(effectiveProfile.enabled()), "mode", effectiveProfile.mode().name().toLowerCase(Locale.ROOT),
                "count", Integer.toString(effectiveProfile.count()), "chance", effectiveProfile.convertChance() < 0 ? "default" : Integer.toString(effectiveProfile.convertChance()),
                "pool", effectiveProfile.poolId() == null ? "none" : effectiveProfile.poolId(), "levelMode", effectiveProfile.levelMode().name().toLowerCase(Locale.ROOT),
                "levelValue", Integer.toString(effectiveProfile.levelValue()), "unique", Boolean.toString(effectiveProfile.unique()), "enforceEvo", Boolean.toString(effectiveProfile.enforceMinEvolutionLevel()));
        effectiveFields.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> details.add(message("field_value" , field(entry.getKey()), value(entry.getValue()))));
        resolution.sources().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> details.add(message("field_source" , field(entry.getKey()), source(entry.getValue()))));
        List<net.minecraft.network.chat.Component> diagnostics = new ArrayList<>(resolution.errors()); diagnostics.addAll(resolution.warnings());
        JsonObject row = row(entity.getUUID().toString(), entity.getName().getString(), entity.level().dimension().location() + " · " + entity.getType().toShortString(), "npc", "entity",
                details, diagnostics, TrainerEditorTags.decode(tags));
        row.addProperty("targetKind", NPCShadowTargets.capability(entity));
        row.add("targetKindLabel", json(NPCShadowTargets.labelText(entity)));
        row.add("targetHelp", json(NPCShadowTargets.helpText(entity)));
        row.add("profile", row.get("data").deepCopy());
        addResolution(row, resolution, "effective", "sources");
        var rct = RCTShadowRules.resolve(entity);
        var inherited = NPCShadowProfile.resolve(List.of(new NPCShadowProfile.Layer("RCT defaults", rct.defaults()),
                new NPCShadowProfile.Layer("RCT rule", rct.tags()), new NPCShadowProfile.Layer("NPC tags/aspects", presetReferences(tags))),
                id -> ShadowAspectPresets.get(entity.getServer(), ResourceLocation.parse(id)));
        addResolution(row, inherited, "inherited", "inheritedSources");
        addResolution(row, NPCShadowProfile.resolve(List.of(new NPCShadowProfile.Layer("RCT defaults", rct.defaults()),
                new NPCShadowProfile.Layer("RCT rule", rct.tags())), id -> ShadowAspectPresets.get(entity.getServer(), ResourceLocation.parse(id))), "base", "baseSources");
        return row;
    }

    private static List<String> presetReferences(Collection<String> tags) { return tags.stream().filter(tag -> tag.startsWith(PRESET_PREFIX)).toList(); }

    static void addResolution(JsonObject row, NPCShadowProfile.Resolution resolution, String valuesKey, String sourcesKey) {
        NPCShadowProfile profile = resolution.profile();
        JsonObject effective = new JsonObject();
        effective.addProperty("enabled", profile.enabled()); effective.addProperty("mode", profile.mode().name().toLowerCase(Locale.ROOT));
        effective.addProperty("count", profile.count()); effective.addProperty("levelMode", profile.levelMode().name().toLowerCase(Locale.ROOT));
        effective.addProperty("levelValue", profile.levelValue()); effective.addProperty("pool", profile.poolId() == null ? "none" : profile.poolId());
        effective.addProperty("unique", profile.unique()); effective.addProperty("chance", profile.convertChance()); effective.addProperty("enforceEvo", profile.enforceMinEvolutionLevel());
        JsonObject sources = new JsonObject();
        for (String field : effective.keySet()) {
            String sourceField = switch (field) { case "levelMode", "levelValue" -> "level"; case "enforceEvo" -> "evolutionMinimum"; default -> field; };
            sources.add(field, json(source(resolution.sources().getOrDefault(sourceField, "Default"))));
        }
        row.add(valuesKey, effective); row.add(sourcesKey, sources);
    }

    private static JsonObject row(String id, String name, Object subtitle, String type, String source,
                                  List<?> details, List<?> diagnostics, JsonObject data) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id); row.addProperty("name", name); row.add("subtitle", json(subtitle));
        row.addProperty("type", type); row.addProperty("source", source);
        row.add("details", array(details)); row.add("diagnostics", diagnostics(diagnostics)); row.add("data", data);
        return row;
    }

    private record Rule(String rowId, String section, String list, int index, String raw, String kind,
                        String target, String preset, List<String> tags, boolean excluded, List<Object> diagnostics) {}

    private static List<Rule> rules() {
        List<Rule> result = new ArrayList<>();
        for (String section : List.of("replace", "append", "convert")) {
            for (String list : List.of("trainers", "trainerTypes", "typePresets", "trainerBlacklist")) {
                List<? extends String> values = ruleValue(section, list).get();
                for (int i = 0; i < values.size(); i++) result.add(parseRule(section, list, i, values.get(i)));
            }
        }
        return result;
    }

    private static Rule parseRule(String section, String list, int index, String raw) {
        String kind = list.equals("trainerTypes") || list.equals("typePresets") ? "type" : "trainer";
        String target = raw == null ? "" : raw.trim(), preset = "";
        List<String> tags = new ArrayList<>();
        List<Object> diagnostics = new ArrayList<>();
        if (list.equals("trainers")) {
            String[] fields = target.split(";", -1); target = fields[0].trim();
            if (fields.length > 1) preset = fields[1].trim();
            if (fields.length > 2) tags.addAll(Arrays.stream(fields[2].split(",", -1)).map(String::trim).filter(value -> !value.isEmpty()).toList());
            if (fields.length > 3) diagnostics.add(message("extra_rule_fields"));
        } else if (list.equals("typePresets")) {
            String[] fields = target.split("=", 2); target = fields[0].trim();
            if (fields.length > 1) preset = fields[1].trim(); else diagnostics.add(message("missing_type_preset"));
        }
        if (!preset.isEmpty()) {
            try { preset = NPCShadowProfile.canonicalId(preset.startsWith(PRESET_PREFIX) ? preset.substring(PRESET_PREFIX.length()) : preset); }
            catch (IllegalArgumentException ex) { diagnostics.add(TrainerEditorMessageException.component(ex)); }
        }
        if (target.isEmpty()) diagnostics.add(message("empty_rule_target"));
        return new Rule(section + "/" + list + "/" + index, section, list, index, raw, kind, target, preset,
                List.copyOf(tags), list.equals("trainerBlacklist"), List.copyOf(diagnostics));
    }

    private static List<JsonObject> ruleRows(MinecraftServer server, RCTTrainerCatalog catalog) {
        List<JsonObject> rows = new ArrayList<>();
        if (!ModConfig.SPEC.isLoaded()) return rows;
        List<Rule> allRules = rules();
        for (Rule rule : allRules) {
            JsonObject data = new JsonObject();
            data.addProperty("kind", rule.kind()); data.addProperty("target", rule.target()); data.addProperty("section", rule.section());
            data.addProperty("preset", rule.preset()); data.add("tags", array(rule.tags())); data.addProperty("excluded", rule.excluded());
            List<Object> diagnostics = new ArrayList<>(rule.diagnostics());
            if (!rule.excluded() && allRules.stream().anyMatch(other -> !other.rowId().equals(rule.rowId())
                    && !other.excluded() && other.kind().equals(rule.kind()) && other.target().equalsIgnoreCase(rule.target())
                    && (!other.section().equals(rule.section()) || other.list().equals(rule.list())))) {
                diagnostics.add(message("other_rule_matches"));
            }
            if (catalog.available() && !catalog.loading()) {
                var entries = rule.kind().equals("trainer") ? catalog.trainers() : catalog.types();
                if (entries.stream().noneMatch(entry -> entry.id().equals(rule.target()))) diagnostics.add(message("unknown_rct" , value(rule.kind()), rule.target()));
            }
            if (!rule.excluded()) {
                List<String> tags = new ArrayList<>(rule.tags());
                if (!rule.preset().isEmpty()) tags.add(PRESET_PREFIX + rule.preset());
                try { validateTags(server, tags, null); } catch (RuntimeException ex) { diagnostics.add(TrainerEditorMessageException.component(ex)); }
            }
            List<Object> details = new ArrayList<>(List.of(message("stored_row" , rule.raw())));
            if (rule.kind().equals("type")) details.add(message("type_preset_priority"));
            var catalogEntries = rule.kind().equals("trainer") ? catalog.trainers() : catalog.types();
            String name = catalogEntries.stream().filter(entry -> entry.id().equals(rule.target())).map(RCTTrainerCatalog.Entry::name).findFirst().orElse(rule.target());
            JsonObject row = row(rule.rowId(), name, message(rule.excluded() ? "rule_exclusion" : "rule_subtitle", value(rule.section()), value(rule.kind())),
                    rule.kind(), "server config", details, diagnostics, data);
            catalogEntries.stream().filter(entry -> entry.id().equals(rule.target())).findFirst()
                    .ifPresent(entry -> row.add("displayName", json(entry.displayName())));
            List<String> authored = new ArrayList<>(rule.tags());
            if (!rule.preset().isEmpty()) authored.add(PRESET_PREFIX + rule.preset());
            row.add("profile", TrainerEditorTags.decode(authored));
            List<String> defaults = rule.excluded() ? List.of() : List.of("sh_shadow_party", "sh_shadow_n1", "sh_shadow_mode_" + rule.section());
            addResolution(row, NPCShadowProfile.resolve(List.of(new NPCShadowProfile.Layer("RCT defaults", defaults), new NPCShadowProfile.Layer("RCT rule", authored)),
                    id -> ShadowAspectPresets.get(server, ResourceLocation.parse(id))), "effective", "sources");
            addResolution(row, NPCShadowProfile.resolve(List.of(new NPCShadowProfile.Layer("RCT defaults", defaults), new NPCShadowProfile.Layer("RCT rule", presetReferences(authored))),
                    id -> ShadowAspectPresets.get(server, ResourceLocation.parse(id))), "inherited", "inheritedSources");
            addResolution(row, NPCShadowProfile.resolve(List.of(new NPCShadowProfile.Layer("RCT defaults", defaults)),
                    id -> ShadowAspectPresets.get(server, ResourceLocation.parse(id))), "base", "baseSources");
            rows.add(row);
        }
        return rows;
    }

    private static ModConfigSpec.ConfigValue<List<? extends String>> ruleValue(String section, String list) {
        var config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        ModConfig.RCTSection values = (ModConfig.RCTSection) switch (section) {
            case "replace" -> config.replace(); case "append" -> config.append(); case "convert" -> config.convert();
            default -> throw new TrainerEditorMessageException(message("error.unknown_rct_section"));
        };
        return switch (list) {
            case "trainers" -> values.trainers; case "trainerTypes" -> values.trainerTypes;
            case "typePresets" -> values.typePresets; case "trainerBlacklist" -> values.trainerBlacklist;
            default -> throw new TrainerEditorMessageException(message("error.unknown_rct_rule_list"));
        };
    }

    private record Definitions(Map<String, List<String>> presets, Set<String> pools) {
        private static final Definitions EMPTY = new Definitions(Map.of(), Set.of());
    }

    private record DefinitionDraft(String category, String id, JsonObject data) {}

    /** The complete draft graph is decoded and checked before even the first disk write. */
    private static void saveWorkflow(MinecraftServer server, String category, String target, String id, JsonObject request, Snapshot snapshot) {
        boolean definitionRoot = category.equals("presets") || category.equals("pools");
        String rootDefinitionId = definitionRoot ? definitionId(id).toString() : "";
        // Older standalone editor requests omit intent; coordinated clients always send it.
        if (definitionRoot && request.has("created")) {
            requireDefinitionIntent(rootDefinitionId, bool(request, "created", false),
                    snapshot.rows().stream().anyMatch(row -> row.get("id").getAsString().equals(rootDefinitionId)));
        }
        List<DefinitionDraft> drafts = new ArrayList<>();
        Map<String, List<String>> presets = new LinkedHashMap<>();
        Set<String> pools = new LinkedHashSet<>(), keys = new HashSet<>();
        Map<String, Snapshot> snapshots = new HashMap<>();
        if (request.has("definitions")) {
            if (!request.get("definitions").isJsonArray() || request.getAsJsonArray("definitions").size() > 16) {
                throw new TrainerEditorMessageException(message("error.a_workflow_can_stage_at_most_16_preset_and_pool_definitions"));
            }
            for (JsonElement element : request.getAsJsonArray("definitions")) {
                if (!element.isJsonObject()) throw new TrainerEditorMessageException(message("error.each_staged_definition_must_be_an_object"));
                JsonObject definition = element.getAsJsonObject();
                String childCategory = TrainerEditorTags.choice(definition, "category", "", Set.of("presets", "pools"));
                String childId = definitionId(TrainerEditorTags.string(definition, "id", "", 256)).toString();
                String key = childCategory + "/" + childId;
                if (!keys.add(key) || (category.equals(childCategory) && childId.equals(rootDefinitionId))) throw new TrainerEditorMessageException(message("duplicate_definition" , childId));
                Snapshot childSnapshot = snapshots.computeIfAbsent(childCategory, name -> snapshot(server, name, ""));
                requireRevision(TrainerEditorTags.string(definition, "revision", "", 64), childSnapshot.revision());
                boolean exists = childSnapshot.rows().stream().anyMatch(row -> row.get("id").getAsString().equals(childId));
                requireDefinitionIntent(childId, bool(definition, "created", false), exists);
                if (!definition.has("data") || !definition.get("data").isJsonObject()) throw new TrainerEditorMessageException(message("error.a_staged_definition_draft_is_required"));
                JsonObject data = definition.getAsJsonObject("data");
                if (childCategory.equals("presets")) presets.put(childId, encodePreset(data));
                else { parsePool(data); pools.add(childId); }
                drafts.add(new DefinitionDraft(childCategory, childId, data));
            }
        }
        // A definition can itself be the parent while its pool or inherited preset is managed inline.
        if (category.equals("presets")) presets.put(definitionId(id).toString(), encodePreset(request.getAsJsonObject("data")));
        else if (category.equals("pools")) { parsePool(request.getAsJsonObject("data")); pools.add(definitionId(id).toString()); }
        Definitions definitions = new Definitions(Map.copyOf(presets), Set.copyOf(pools));
        List<TrainerEditorTransaction.Change> changes = new ArrayList<>();
        for (DefinitionDraft draft : drafts) changes.add(prepareSave(server, draft.category(), "", draft.id(), draft.data(), snapshots.get(draft.category()), definitions));
        changes.add(prepareSave(server, category, target, id, request.getAsJsonObject("data"), snapshot, definitions));
        TrainerEditorTransaction.commit(changes);
    }

    static void requireDefinitionIntent(String id, boolean created, boolean exists) {
        if (created && exists) throw TrainerEditorMessageException.state(message("definition_exists" , id));
        if (!created && !exists) throw TrainerEditorMessageException.state(message("definition_removed" , id));
    }

    private static List<String> encodePreset(JsonObject data) {
        List<String> tags = TrainerEditorTags.encode(data, maxLevel());
        if (tags.isEmpty()) throw new TrainerEditorMessageException(message("error.a_preset_needs_at_least_one_tag_or_preset_reference"));
        return tags;
    }

    private static List<ShadowPools.WeightedEntry> parsePool(JsonObject data) {
        if (!data.has("entries") || !data.get("entries").isJsonArray()) throw new TrainerEditorMessageException(message("error.pool_entries_are_required"));
        if (data.getAsJsonArray("entries").isEmpty() || data.getAsJsonArray("entries").size() > 64) throw new TrainerEditorMessageException(message("error.an_editor_pool_needs_between_1_and_64_entries_edit_larger_pools_in_their_source_file"));
        return ShadowPools.parseDefinition(data);
    }

    private static TrainerEditorTransaction.Change prepareSave(MinecraftServer server, String category, String target, String id, JsonObject data, Snapshot snapshot, Definitions definitions) {
        return switch (category) {
            case "presets" -> {
                ResourceLocation resourceId = definitionId(id);
                List<String> tags = encodePreset(data);
                validateTags(server, tags, resourceId.toString(), definitions);
                yield new TrainerEditorTransaction.Change(() -> ShadowAspectPresets.savePreset(server, resourceId, tags), ShadowAspectPresets.editorCheckpoint(resourceId));
            }
            case "pools" -> {
                ResourceLocation resourceId = definitionId(id);
                List<ShadowPools.WeightedEntry> entries = parsePool(data);
                yield new TrainerEditorTransaction.Change(() -> ShadowPools.savePool(server, resourceId, entries), ShadowPools.editorCheckpoint(resourceId));
            }
            // These final parent operations already perform their own exact rollback if publication fails.
            case "npc" -> new TrainerEditorTransaction.Change(prepareNpc(server, target, data, definitions), () -> {});
            case "rules" -> new TrainerEditorTransaction.Change(prepareRule(server, id, data, snapshot, definitions), () -> {});
            default -> throw new TrainerEditorMessageException(message("error.trainer_type_discovery_is_read_only_save_a_rule_from_the_rules_category"));
        };
    }

    private static void delete(MinecraftServer server, String category, String target, String id, Snapshot snapshot) {
        switch (category) {
            case "presets" -> ShadowAspectPresets.deletePreset(server, definitionId(id));
            case "pools" -> ShadowPools.deletePool(server, definitionId(id));
            case "npc" -> replaceNpcTags(target(server, target), List.of());
            case "rules" -> {
                if (!ModConfig.SPEC.isLoaded()) throw TrainerEditorMessageException.state(message("error.rct_config_is_not_loaded"));
                Rule existing = rules().stream().filter(rule -> rule.rowId().equals(id)).findFirst().orElseThrow(() -> new TrainerEditorMessageException(message("error.rule_no_longer_exists")));
                var field = ruleValue(existing.section(), existing.list());
                List<String> replacement = new ArrayList<>(field.get()); replacement.remove(existing.index());
                TrainerEditorConfigBinding.save(Map.of(field, replacement), snapshot.configFingerprint());
            }
            default -> throw new TrainerEditorMessageException(message("error.this_category_does_not_support_deletion"));
        }
    }

    private static Runnable prepareNpc(MinecraftServer server, String target, JsonObject data, Definitions definitions) {
        Entity entity = target(server, target);
        JsonObject shadowDraft = data.deepCopy();
        shadowDraft.add("extraTags", array(TrainerEditorTags.strings(data, "extraTags", 1024, 4096).stream()
                .filter(NPCShadowProfile::isShadowTag).toList()));
        List<String> tags = TrainerEditorTags.encode(shadowDraft, maxLevel());
        validateTags(server, tags, null, definitions);
        var rct = RCTShadowRules.resolve(entity);
        if (!rct.diagnostics().isEmpty()) throw new TrainerEditorMessageException(TrainerEditorMessages.join(rct.diagnostics()));
        validateLayers(server, List.of(new NPCShadowProfile.Layer("RCT defaults", rct.defaults()),
                new NPCShadowProfile.Layer("RCT rule", rct.tags()), new NPCShadowProfile.Layer("Editor draft", tags)), null, List.of(), definitions);
        // Preserve every unrelated tag/aspect even if the client sends an incomplete extraTags array.
        List<String> shadow = tags.stream().filter(NPCShadowProfile::isShadowTag).toList();
        Set<String> next = new HashSet<>(entity.getTags()); next.removeIf(NPCShadowProfile::isShadowTag); next.addAll(shadow);
        if (next.size() > 1024) throw new TrainerEditorMessageException(message("error.this_entity_has_no_free_entity_tag_slots_for_these_settings"));
        return () -> replaceNpcTags(entity, shadow);
    }

    private static void replaceNpcTags(Entity entity, List<String> replacement) {
        Set<String> oldTags = new LinkedHashSet<>(entity.getTags());
        Set<String> oldAspects = entity instanceof AspectHolder holder ? new LinkedHashSet<>(holder.shadowedhearts$getAspects()) : Set.of();
        Set<String> nextTags = new LinkedHashSet<>(oldTags); nextTags.removeIf(NPCShadowProfile::isShadowTag); nextTags.addAll(replacement);
        Set<String> nextAspects = new LinkedHashSet<>(oldAspects); nextAspects.removeIf(NPCShadowProfile::isShadowTag);
        if (nextTags.size() > 1024) throw new TrainerEditorMessageException(message("error.this_entity_has_no_free_entity_tag_slots_for_these_settings"));
        try {
            for (String value : oldTags) if (!nextTags.contains(value)) entity.removeTag(value);
            for (String value : nextTags) if (!entity.getTags().contains(value) && !entity.addTag(value)) throw TrainerEditorMessageException.state(message("error.could_not_store_an_npc_tag"));
            if (entity instanceof AspectHolder holder) holder.shadowedhearts$setAspects(nextAspects);
        } catch (RuntimeException failure) {
            for (String value : new HashSet<>(entity.getTags())) if (!oldTags.contains(value)) entity.removeTag(value);
            for (String value : oldTags) if (!entity.getTags().contains(value)) entity.addTag(value);
            if (entity instanceof AspectHolder holder) holder.shadowedhearts$setAspects(oldAspects);
            throw failure;
        }
    }

    private static Runnable prepareRule(MinecraftServer server, String id, JsonObject data, Snapshot snapshot, Definitions definitions) {
        RCTTrainerCatalog catalog = snapshot.catalog();
        if (!ModConfig.SPEC.isLoaded()) throw TrainerEditorMessageException.state(message("error.rct_config_is_not_loaded"));
        if (!catalog.available() || catalog.loading()) throw TrainerEditorMessageException.state(message("error.rct_trainer_discovery_must_finish_before_saving_a_rule"));
        String kind = TrainerEditorTags.choice(data, "kind", "trainer", Set.of("trainer", "type"));
        String section = TrainerEditorTags.choice(data, "section", "replace", SECTIONS);
        String target = TrainerEditorTags.string(data, "target", "", 256);
        if (target.isBlank() || target.contains(";") || target.contains("=") || target.contains(",")) throw new TrainerEditorMessageException(message("error.invalid_rule_target_id"));
        List<RCTTrainerCatalog.Entry> catalogEntries = kind.equals("trainer") ? catalog.trainers() : catalog.types();
        if (catalogEntries.stream().noneMatch(entry -> entry.id().equals(target))) throw new TrainerEditorMessageException(message("unknown_rct" , value(kind), target));
        String preset = TrainerEditorTags.string(data, "preset", "", 256);
        if (!preset.isEmpty()) preset = NPCShadowProfile.canonicalId(preset);
        List<String> tags = TrainerEditorTags.strings(data, "tags", 128, 256);
        for (String tag : tags) if (tag.contains(",") || tag.contains(";") || tag.isBlank()) throw new TrainerEditorMessageException(message("error.rule_tags_cannot_contain_separators_or_be_blank"));
        List<String> effective = new ArrayList<>(tags);
        if (!preset.isEmpty()) effective.add(PRESET_PREFIX + preset);
        validateTags(server, effective, null, definitions);
        boolean excluded = bool(data, "excluded", false);
        if (excluded && !kind.equals("trainer")) throw new TrainerEditorMessageException(message("error.only_trainer_ids_support_exclusions"));
        if (kind.equals("type") && !tags.isEmpty()) throw new TrainerEditorMessageException(message("error.type_rules_store_advanced_tags_in_a_preset_select_or_create_a_preset_first"));
        String list = excluded ? "trainerBlacklist" : kind.equals("trainer") ? "trainers" : preset.isEmpty() ? "trainerTypes" : "typePresets";
        String raw = excluded ? target : kind.equals("trainer") ? target + ";" + preset + ";" + String.join(",", tags)
                : preset.isEmpty() ? target : target + "=" + preset;
        Map<ModConfigSpec.ConfigValue<List<? extends String>>, List<String>> changes = new LinkedHashMap<>();
        Rule existing = id.isEmpty() ? null : rules().stream().filter(rule -> rule.rowId().equals(id)).findFirst().orElseThrow(() -> new TrainerEditorMessageException(message("error.rule_no_longer_exists")));
        if (rules().stream().anyMatch(rule -> !rule.rowId().equals(id)
                && sameRuleDestination(rule.section(), rule.list(), rule.target(), section, list, target))) {
            throw new TrainerEditorMessageException(message("duplicate_rule" , value(kind), target));
        }
        var destination = ruleValue(section, list);
        if (existing != null) {
            var origin = ruleValue(existing.section(), existing.list());
            List<String> original = new ArrayList<>(origin.get());
            if (origin == destination) { original.set(existing.index(), raw); changes.put(origin, original); }
            else { original.remove(existing.index()); changes.put(origin, original); }
        }
        if (!changes.containsKey(destination)) {
            List<String> values = new ArrayList<>(destination.get());
            if (values.contains(raw)) throw new TrainerEditorMessageException(message("error.an_identical_rct_rule_already_exists"));
            values.add(raw); changes.put(destination, values);
        }
        TrainerEditorConfigBinding.validateSave(changes, snapshot.configFingerprint());
        return () -> TrainerEditorConfigBinding.save(changes, snapshot.configFingerprint());
    }

    static boolean sameRuleDestination(String section, String list, String target, String otherSection, String otherList, String otherTarget) {
        return section.equals(otherSection) && list.equals(otherList) && target.equalsIgnoreCase(otherTarget);
    }

    private static void validateTags(MinecraftServer server, List<String> tags, String self) {
        validateTags(server, tags, self, Definitions.EMPTY);
    }

    private static void validateTags(MinecraftServer server, List<String> tags, String self, Definitions definitions) {
        validateLayers(server, List.of(new NPCShadowProfile.Layer("Editor draft", tags)), self, tags, definitions);
    }

    private static void validateLayers(MinecraftServer server, List<NPCShadowProfile.Layer> layers, String self, List<String> selfTags, Definitions definitions) {
        TrainerEditorDraftReferences.validate(layers, definitions.presets(), definitions.pools(), self, selfTags, referenced -> {
            ResourceLocation id = ResourceLocation.parse(referenced);
            var issues = ShadowAspectPresets.diagnostics(server, id);
            if (!issues.isEmpty()) throw new TrainerEditorMessageException(TrainerEditorMessages.join(issues));
            return ShadowAspectPresets.get(server, id);
        }, pool -> {
            var issues = ShadowPools.diagnostics(server, ResourceLocation.parse(pool));
            if (!issues.isEmpty()) throw new TrainerEditorMessageException(TrainerEditorMessages.join(issues));
        });
    }

    private static ResourceLocation definitionId(String text) {
        if (text.isEmpty()) throw new TrainerEditorMessageException(message("error.a_namespace_path_definition_id_is_required"));
        return ResourceLocation.parse(NPCShadowProfile.canonicalId(text));
    }

    private static Entity target(MinecraftServer server, String target) {
        UUID uuid;
        try { uuid = UUID.fromString(target); } catch (IllegalArgumentException ex) { throw new TrainerEditorMessageException(message("error.select_a_valid_loaded_npc_or_battle_entity_uuid")); }
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                if (NPCShadowTargets.isSupported(entity)) return entity;
                throw new TrainerEditorMessageException(message("error.unsupported_battle_target"));
            }
        }
        throw new TrainerEditorMessageException(message("error.this_npc_or_battle_entity_is_no_longer_loaded_open_the_editor_on_it_again"));
    }

    private static Set<String> authored(Entity entity) {
        Set<String> tags = new TreeSet<>(entity.getTags());
        if (entity instanceof AspectHolder holder) tags.addAll(holder.shadowedhearts$getAspects());
        return tags;
    }

    private static int maxLevel() { return Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel(); }
    private static JsonArray array(Collection<?> values) { JsonArray result = new JsonArray(); values.forEach(value -> result.add(json(value))); return result; }
    private static JsonArray diagnostics(Collection<?> values) {
        JsonArray result = new JsonArray();
        values.forEach(value -> result.add(json(value instanceof net.minecraft.network.chat.Component ? value : message("validation_detail", value))));
        return result;
    }
    private static boolean bool(JsonObject data, String field, boolean fallback) {
        if (!data.has(field)) return fallback;
        JsonElement value = data.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new TrainerEditorMessageException(message("boolean_required" , field(field)));
        return value.getAsBoolean();
    }
    private static int number(JsonObject object, String field, int fallback, int min, int max) {
        if (!object.has(field)) return fallback;
        try {
            JsonElement value = object.get(field);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            int number = value.getAsBigDecimal().intValueExact();
            if (number < min || number > max) throw new ArithmeticException();
            return number;
        } catch (ArithmeticException | NumberFormatException ex) { throw new TrainerEditorMessageException(message("out_of_range" , field(field))); }
    }
}
