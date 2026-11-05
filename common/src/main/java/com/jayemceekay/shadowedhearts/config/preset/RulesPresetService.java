package com.jayemceekay.shadowedhearts.config.preset;

import com.google.gson.*;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Server-thread, permission-checked, one-shot presets for the selected common.toml file. */
public final class RulesPresetService {
    public static final int MAX_REQUEST = 4096;
    public static final int MAX_RESPONSE = 65536;
    private static final String MESSAGES = "message.shadowedhearts.rules_presets.";
    private static volatile Path source;

    private RulesPresetService() { }

    public static void bind(Path path) {
        source = path == null ? null : path.toAbsolutePath().normalize();
    }

    public static Optional<Path> boundPath() { return Optional.ofNullable(source); }

    public static boolean canEdit(ServerPlayer player) {
        var server = player.getServer();
        return server != null && allowed(player.createCommandSourceStack().hasPermission(2),
                server.isSingleplayer(), server.isSingleplayerOwner(player.getGameProfile()));
    }

    static boolean allowed(boolean operator, boolean integratedServer, boolean owner) {
        return operator || (integratedServer && owner);
    }

    public static String handle(ServerPlayer player, String json) {
        return handleChecked(json, () -> {
            var server = player.getServer();
            if (server == null || !server.isSameThread()) throw failure("unavailable");
            if (!canEdit(player)) throw failure("denied");
        });
    }

    /** Local preferences never pass through a server request or operator permission. */
    public static String handleLocalSnapshots(String json) {
        return handleChecked(json, () -> { }, true);
    }

    /** Commands retain the existing operator-only /sh root, including console support. */
    static String handleCommand(CommandSourceStack caller, String json) {
        return handleChecked(json, () -> {
            if (!caller.hasPermission(2)) throw failure("denied");
            if (!caller.getServer().isSameThread()) throw failure("unavailable");
        });
    }

    private static String handleChecked(String json, Runnable permission) {
        return handleChecked(json, permission, false);
    }

    private static String handleChecked(String json, Runnable permission, boolean local) {
        JsonObject response = response();
        try {
            JsonObject request = parseRequest(json, response);
            response.add("requestId", request.get("requestId"));
            response.add("presetId", request.get("presetId"));
            permission.run();
            boolean snapshot = ConfigSnapshotService.ACTIONS.contains(request.get("action").getAsString());
            boolean client = request.get("presetId").getAsString().equals("client");
            if (local ? !snapshot || !client : client) throw failure("denied");
            if (snapshot) ConfigSnapshotService.process(request, response);
            else process(request, response);
        } catch (RequestFailure failure) {
            response.addProperty("status", "error");
            response.addProperty("messageKey", MESSAGES + failure.key);
        } catch (RuntimeException failure) {
            Shadowedhearts.LOGGER.error("Unable to process rules preset request", failure);
            response.addProperty("status", "error");
            response.addProperty("messageKey", MESSAGES + "save_failed");
        }
        String result = response.toString();
        if (result.length() <= MAX_RESPONSE) return result;
        JsonObject small = response();
        small.add("requestId", response.get("requestId"));
        small.add("presetId", response.get("presetId"));
        small.addProperty("messageKey", MESSAGES + "response_too_large");
        return small.toString();
    }

    private static void process(JsonObject request, JsonObject response) {
        Path selectedSource = source;
        if (selectedSource == null || !ModConfig.SPEC.isLoaded()) throw failure("unavailable");
        String id = request.get("presetId").getAsString();
        RulesPresetCatalog.Preset preset = RulesPresetCatalog.find(id).orElseThrow(() -> failure("unknown_preset"));
        List<RulesPresetTransaction.Change> changes = changes(preset);
        byte[] before = readSource(selectedSource);
        String revision = RulesPresetTransaction.revision(selectedSource, before, id, changes);
        response.addProperty("source", selectedSource.toString());
        boolean applying = request.get("action").getAsString().equals("apply");
        if (applying) {
            String expected = string(request, "revision", 64);
            if (expected.isEmpty() || !expected.equals(revision)) throw failure("stale");
        }
        requireSynchronizedFile(before);
        if (applying) {
            // Check the file immediately before publication, not just against an old UI snapshot.
            if (!selectedSource.equals(source) || !Arrays.equals(before, readSource(selectedSource))) throw failure("stale");
            requireSynchronizedFile(before);
            boolean changesSettings = changes.stream().anyMatch(RulesPresetTransaction.Change::changed);
            if (changesSettings) ConfigSnapshotService.beforePreset(selectedSource, before, id);
            int[] saveAttempt = {0};
            byte[][] expectedWrite = {null};
            try { RulesPresetTransaction.apply(changes, () -> {
                byte[] currentFile = readSource(selectedSource);
                if (saveAttempt[0]++ == 0) {
                    if (!selectedSource.equals(source) || !Arrays.equals(before, currentFile)) throw failure("stale");
                    expectedWrite[0] = RulesPresetTransaction.expectedSavedBytes(ModConfig.SPEC, before);
                } else if (!selectedSource.equals(source) || (!Arrays.equals(before, currentFile)
                        && !Arrays.equals(expectedWrite[0], currentFile))) {
                    // Restore in-memory values, but never replace an independently edited file during rollback.
                    throw new IllegalStateException("Gameplay file changed independently; rollback did not overwrite it");
                }
                ModConfig.SPEC.save();
            }); }
            catch (RuntimeException failure) {
                Shadowedhearts.LOGGER.error("Rules preset {} failed; previous values were restored where possible", id, failure);
                throw failure("save_failed");
            }
            changes = changes(preset);
            revision = RulesPresetTransaction.revision(selectedSource, readSource(selectedSource), id, changes);
            response.addProperty("status", "applied");
            response.addProperty("messageKey", MESSAGES + (changesSettings ? "applied" : "unchanged"));
        } else {
            response.addProperty("status", "preview");
            response.addProperty("messageKey", MESSAGES + "preview");
        }
        response.addProperty("revision", revision);
        JsonArray rows = new JsonArray();
        for (var change : changes) {
            JsonObject row = new JsonObject();
            row.addProperty("path", change.path());
            row.addProperty("labelKey", change.labelKey());
            row.addProperty("currentValue", RulesPresetTransaction.display(change.current()));
            row.addProperty("proposedValue", RulesPresetTransaction.display(change.proposed()));
            row.addProperty("changed", change.changed());
            rows.add(row);
        }
        response.add("rows", rows);
    }

    /** Typed effective values, including schema defaults, for native validation and integrations. */
    public static Map<String, Object> proposedValues(String presetId) {
        var preset = RulesPresetCatalog.find(presetId).orElseThrow(() -> failure("unknown_preset"));
        Map<String, Object> values = new LinkedHashMap<>();
        changes(preset).forEach(change -> values.put(change.path(), RulesPresetTransaction.copy(change.proposed())));
        return Collections.unmodifiableMap(values);
    }

    private static List<RulesPresetTransaction.Change> changes(RulesPresetCatalog.Preset preset) {
        try {
            return RulesPresetTransaction.plan(ModConfig.SPEC, RulesPresetCatalog.managedPaths(),
                    preset.values(), preset.id().equals("mod_defaults"));
        } catch (IllegalArgumentException failure) { throw failure("invalid_config"); }
    }

    private static byte[] readSource(Path path) {
        try { return Files.readAllBytes(path); }
        catch (IOException failure) { throw failure("unavailable"); }
    }

    private static void requireSynchronizedFile(byte[] file) {
        try {
            if (!RulesPresetTransaction.persistedMatchesRuntime(ModConfig.SPEC, file)) throw failure("stale");
        } catch (RequestFailure failure) { throw failure; }
        catch (RuntimeException invalidFile) { throw failure("stale"); }
    }

    static JsonObject parseRequest(String json) {
        return parseRequest(json, null);
    }

    private static JsonObject parseRequest(String json, JsonObject response) {
        if (json == null || json.length() > MAX_REQUEST) throw failure("invalid_request");
        try {
            // Requests are flat and have only bounded scalar fields; reject excessive nesting before parsing.
            int depth = 0; boolean quoted = false, escaped = false;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') quoted = false;
                } else if (c == '"') quoted = true;
                else if (c == '{' || c == '[') { if (++depth > 4) throw failure("invalid_request"); }
                else if (c == '}' || c == ']') depth--;
            }
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw failure("invalid_request");
            JsonObject request = parsed.getAsJsonObject();
            if (!request.has("requestId") || !request.get("requestId").isJsonPrimitive()
                    || !request.getAsJsonPrimitive("requestId").isNumber()) throw failure("invalid_request");
            int requestId = request.get("requestId").getAsBigDecimal().intValueExact();
            if (requestId < 0) throw failure("invalid_request");
            if (response != null) response.addProperty("requestId", requestId);
            String id = string(request, "presetId", 40);
            if (response != null) response.addProperty("presetId", id);
            String action = string(request, "action", 16);
            if (id.isBlank() || (!action.equals("preview") && !action.equals("apply")
                    && !ConfigSnapshotService.ACTIONS.contains(action))) throw failure("invalid_request");
            if (action.equals("apply")) string(request, "revision", 64);
            request.addProperty("requestId", requestId);
            return request;
        } catch (RequestFailure failure) { throw failure; }
        catch (RuntimeException failure) { throw failure("invalid_request"); }
    }

    private static String string(JsonObject object, String key, int max) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw failure("invalid_request");
        String text = value.getAsString();
        if (text.length() > max) throw failure("invalid_request");
        return text;
    }

    private static JsonObject response() {
        JsonObject result = new JsonObject();
        result.addProperty("requestId", 0);
        result.addProperty("presetId", "");
        result.addProperty("status", "error");
        result.addProperty("revision", "");
        result.add("rows", new JsonArray());
        result.addProperty("messageKey", MESSAGES + "invalid_request");
        result.addProperty("source", "");
        return result;
    }

    private static RequestFailure failure(String key) { return new RequestFailure(key); }
    private static final class RequestFailure extends RuntimeException {
        final String key;
        RequestFailure(String key) { super(key); this.key = key; }
    }
}
