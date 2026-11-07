package com.jayemceekay.shadowedhearts.config.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ConfigSnapshotStore;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Permission checks live at the transport boundary; paths only come from loaded config bindings. */
public final class ConfigSnapshotService {
    private static final Map<String, Path> PATHS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ConfigSnapshotStore STORE = ConfigSnapshotStore.INSTANCE;
    private static final String MESSAGES = "message.shadowedhearts.snapshots.";
    public static final Set<String> ACTIONS = Set.of("snapshot_list", "snapshot_create", "snapshot_preview", "snapshot_restore", "snapshot_delete");

    private ConfigSnapshotService() { }

    private static ModConfigSpec spec(String scope) {
        return switch (scope) {
            case "gameplay" -> ModConfig.SPEC;
            case "world" -> ServerConfig.SPEC;
            case "client" -> ClientConfig.SPEC;
            default -> throw new IllegalArgumentException("Invalid snapshot scope");
        };
    }

    private static String scope(net.neoforged.fml.config.IConfigSpec config) {
        if (config == ClientConfig.SPEC) return "client";
        if (config == ModConfig.SPEC) return "gameplay";
        if (config == ServerConfig.SPEC) return "world";
        return null;
    }

    public static void loaded(net.neoforged.fml.config.IConfigSpec config, java.util.function.Supplier<Path> source) {
        String scope = scope(config);
        if (scope == null) return;
        Path path;
        try { path = source.get().toAbsolutePath().normalize(); }
        catch (IllegalStateException remote) { PATHS.remove(scope); return; }
        PATHS.put(scope, path);
        try { STORE.observe(path, ConfigSnapshotStore.readBounded(path)); }
        catch (IOException | RuntimeException failure) {
            Shadowedhearts.LOGGER.error("Unable to preserve configuration history for {}", path, failure);
        }
    }

    public static void unloaded(net.neoforged.fml.config.IConfigSpec config) {
        String scope = scope(config);
        if (scope != null) PATHS.remove(scope);
    }

    /** Check the loader-selected file before a screen publishes edits into its live spec. */
    public static ScreenEditFile beginScreenEdit(ModConfigSpec spec) {
        String scope = scope(spec);
        Path path = scope == null ? null : PATHS.get(scope);
        if (path == null || !spec.isLoaded()) throw new IllegalStateException("Settings file is not bound");
        return new ScreenEditFile(scope, spec, path);
    }

    public static final class ScreenEditFile {
        private final String scope;
        private final ModConfigSpec spec;
        private final Path path;
        private final byte[] before;
        private byte[] expectedWrite;

        private ScreenEditFile(String scope, ModConfigSpec spec, Path path) {
            this.scope = scope;
            this.spec = spec;
            this.path = path;
            this.before = readCurrent();
            if (!RulesPresetTransaction.persistedMatchesRuntime(spec, before)) throw stale();
        }

        public void save() {
            byte[] expected = RulesPresetTransaction.expectedSavedBytes(spec, before);
            if (!Arrays.equals(before, readCurrent())) throw stale();
            expectedWrite = expected;
            spec.save();
        }

        public void saveRollback() {
            if (expectedWrite == null) return;
            byte[] current = readCurrent();
            if (Arrays.equals(before, current)) return;
            if (!Arrays.equals(expectedWrite, current)) throw stale();
            spec.save();
        }

        public boolean isBound() { return spec.isLoaded() && path.equals(PATHS.get(scope)); }

        private byte[] readCurrent() {
            if (!isBound()) throw stale();
            try { return Files.readAllBytes(path); }
            catch (IOException failure) { throw new UncheckedIOException("Could not read " + path, failure); }
        }

        private ConcurrentModificationException stale() { return new ConcurrentModificationException(path.toString()); }
    }

    public static void beforeMutation(Path path, byte[] before) {
        try { STORE.observe(path, before); STORE.automatic(path, before, "Before applying changes"); }
        catch (IOException failure) { throw new UncheckedIOException("Could not create recovery snapshot; changes were cancelled", failure); }
    }

    /** Preserve a labelled recovery point before applying a preset that changes settings. */
    static void beforePreset(Path path, byte[] before, String presetId) {
        try {
            STORE.observe(path, before);
            STORE.save(path, before, "Before preset: " + presetId, true);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not create recovery snapshot; preset application was cancelled", failure);
        }
    }

    static void process(JsonObject request, JsonObject response) {
        String scope = request.get("presetId").getAsString();
        ModConfigSpec spec = spec(scope);
        Path source = PATHS.get(scope);
        if (source == null || !spec.isLoaded()) { error(response, "unavailable"); return; }
        response.addProperty("source", source.toString());
        String action = request.get("action").getAsString();
        try {
            byte[] before = ConfigSnapshotStore.readBounded(source);
            if (!RulesPresetTransaction.persistedMatchesRuntime(spec, before)) { error(response, "stale"); return; }
            STORE.observe(source, before);
            switch (action) {
                case "snapshot_create" -> {
                    STORE.save(source, before, field(request, "name", 64), false);
                    response.addProperty("messageKey", MESSAGES + "saved");
                }
                case "snapshot_delete" -> {
                    STORE.delete(source, field(request, "id", 36));
                    response.addProperty("messageKey", MESSAGES + "deleted");
                }
                case "snapshot_preview", "snapshot_restore" -> {
                    String id = field(request, "id", 36);
                    byte[] target = STORE.read(source, id);
                    List<RulesPresetTransaction.Change> changes = RulesPresetTransaction.planSnapshot(spec, target);
                    String revision = RulesPresetTransaction.revision(source, before, id + Base64.getEncoder().encodeToString(target), changes);
                    if (action.equals("snapshot_restore")) {
                        if (!revision.equals(field(request, "revision", 64))) { error(response, "stale"); return; }
                        beforeMutation(source, before);
                        int[] attempt = {0};
                        byte[][] expected = {null};
                        RulesPresetTransaction.apply(changes, () -> {
                            try {
                                byte[] current = ConfigSnapshotStore.readBounded(source);
                                if (!source.equals(PATHS.get(scope))) throw new IllegalStateException("Config binding changed");
                                if (attempt[0]++ == 0) {
                                    if (!Arrays.equals(before, current)) throw new IllegalStateException("Config changed before restore");
                                    expected[0] = RulesPresetTransaction.expectedSavedBytes(spec, before);
                                } else if (!Arrays.equals(before, current) && !Arrays.equals(expected[0], current)) {
                                    throw new IllegalStateException("External edit preserved during rollback");
                                }
                                spec.save();
                            } catch (IOException failure) { throw new UncheckedIOException(failure); }
                        });
                        STORE.observe(source, ConfigSnapshotStore.readBounded(source));
                        response.addProperty("status", "applied");
                        response.addProperty("messageKey", MESSAGES + "restored");
                        list(source, response);
                    } else {
                        response.addProperty("revision", revision);
                        response.addProperty("status", "preview");
                        response.addProperty("messageKey", MESSAGES + "preview");
                        response.addProperty("changedCount", changes.stream().filter(RulesPresetTransaction.Change::changed).count());
                        JsonArray rows = new JsonArray();
                        for (var change : changes.stream().filter(RulesPresetTransaction.Change::changed).limit(80).toList()) {
                            JsonObject row = new JsonObject();
                            row.addProperty("path", change.path());
                            row.addProperty("current", bounded(RulesPresetTransaction.display(change.current())));
                            row.addProperty("proposed", bounded(RulesPresetTransaction.display(change.proposed())));
                            rows.add(row);
                        }
                        response.add("rows", rows);
                    }
                    return;
                }
                case "snapshot_list" -> response.addProperty("messageKey", MESSAGES + "listed");
                default -> throw new IllegalArgumentException("Unknown snapshot action");
            }
            list(source, response);
            response.addProperty("status", "listed");
        } catch (IllegalArgumentException failure) { error(response, "incompatible"); }
        catch (IOException | RuntimeException failure) {
            Shadowedhearts.LOGGER.error("Configuration snapshot operation failed for {}", source, failure);
            error(response, "failed");
        }
    }

    private static void list(Path source, JsonObject response) throws IOException {
        JsonArray entries = new JsonArray();
        for (var entry : STORE.list(source).stream().limit(ConfigSnapshotStore.MAX_AUTOMATIC + ConfigSnapshotStore.MAX_NAMED).toList()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", entry.id()); json.addProperty("name", entry.name());
            json.addProperty("createdAt", entry.createdAt()); json.addProperty("automatic", entry.automatic());
            entries.add(json);
        }
        response.add("snapshots", entries);
    }

    private static String field(JsonObject request, String key, int limit) {
        if (!request.has(key) || !request.get(key).isJsonPrimitive() || !request.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Missing " + key);
        String value = request.get(key).getAsString();
        if (value.isBlank() || value.length() > limit) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }

    private static String bounded(String value) { return value.length() > 180 ? value.substring(0, 177) + "..." : value; }
    private static void error(JsonObject response, String key) {
        response.addProperty("status", "error");
        response.addProperty("messageKey", MESSAGES + key);
    }
}
