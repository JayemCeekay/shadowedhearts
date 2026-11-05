package com.jayemceekay.shadowedhearts.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrates saved config layouts before either loader corrects them against the new specs.
 * File names describe the contents; both gameplay files continue to use server-authoritative specs.
 */
public final class ConfigFileMigration {
    static final String MARKER = ".config-layout-v2-migrated";
    static final String BACKUP_SUFFIX = ".pre-layout-v2.bak";
    static final String CLIENT_HUD_BACKUP_SUFFIX = ".pre-hud-top-inset.bak";
    private static final String MOD_DIRECTORY = "shadowedhearts";

    private ConfigFileMigration() {
    }

    /** Migrate the configured template directory and then the instance's config directory. */
    public static boolean migrateGlobal(Path configBasePath, Path defaultConfigBasePath) {
        try {
            Path defaults = defaultConfigBasePath.resolve(MOD_DIRECTORY);
            Path global = configBasePath.resolve(MOD_DIRECTORY);
            boolean migrated = migrateClientHud(defaults);
            migrated = migrateClientHud(global) || migrated;
            migrated = migrateDirectory(defaults, Snapshot.empty()) || migrated;
            return migrateDirectory(global, readSnapshot(defaults)) || migrated;
        } catch (IOException | RuntimeException exception) {
            throw failure(configBasePath, exception);
        }
    }

    /**
     * Migrate only the active world's overrides. Call before MinecraftServer.initServer, since
     * NeoForge loads configs before its first server lifecycle event.
     */
    public static boolean migrateWorld(Path configBasePath, Path defaultConfigBasePath, Path worldServerConfigBasePath) {
        try {
            Snapshot defaults = readSnapshot(defaultConfigBasePath.resolve(MOD_DIRECTORY));
            Snapshot global = readSnapshot(configBasePath.resolve(MOD_DIRECTORY));
            // Loaders select overrides separately for each file, rather than merging their keys.
            Snapshot effectiveGlobal = new Snapshot(
                    global.common != null ? global.common : defaults.common,
                    global.snag != null ? global.snag : defaults.snag,
                    global.server != null ? global.server : defaults.server);
            return migrateDirectory(worldServerConfigBasePath.resolve(MOD_DIRECTORY), effectiveGlobal);
        } catch (IOException | RuntimeException exception) {
            throw failure(worldServerConfigBasePath, exception);
        }
    }

    private static IllegalStateException failure(Path path, Exception exception) {
        return new IllegalStateException("Could not migrate Shadowed Hearts configs at " + path
                + "; original files and any completed migration backups were retained. "
                + "Resolve the file error before loading these configs.", exception);
    }

    private static boolean migrateClientHud(Path directory) throws IOException {
        Path file = directory.resolve("client.toml");
        CommentedConfig original = read(file);
        if (original == null) {
            return false;
        }
        String offsetKey = "auraReaderHud.compassVerticalOffset";
        String marginKey = "auraReaderHud.bossBarMargin";
        if (!original.contains(offsetKey) && !original.contains(marginKey)) {
            return false;
        }

        CommentedConfig migrated = copy(original);
        String insetKey = "auraReaderHud.topInset";
        if (!migrated.contains(insetKey)) {
            // Match the old native specs' numeric correction and absent-value defaults.
            int offset = legacyHudOffset(original.getRaw(offsetKey), -64, 128);
            int margin = legacyHudOffset(original.getRaw(marginKey), 0, 96);
            migrated.set(insetKey, offset + margin);
        }
        migrated.remove(offsetKey);
        migrated.removeComment(offsetKey);
        migrated.remove(marginKey);
        migrated.removeComment(marginKey);

        // Commit the replacement and removal together, only after retaining the original file.
        backup(file, CLIENT_HUD_BACKUP_SUFFIX);
        write(file, migrated);
        return true;
    }

    private static int legacyHudOffset(Object value, int min, int max) {
        if (!(value instanceof Number number)) {
            return 8;
        }
        return (int) Math.max(min, Math.min(max, number.doubleValue()));
    }

    private static boolean migrateDirectory(Path directory, Snapshot fallback) throws IOException {
        if (Files.exists(directory.resolve(MARKER))) {
            return false;
        }
        Snapshot original = readSnapshot(directory);
        boolean movedWorldSettings = original.common != null && original.common.contains("worldAlteration");
        boolean legacyCommon = original.common != null && !original.common.contains("snag");
        if (original.snag == null && !movedWorldSettings && !legacyCommon) {
            return false;
        }

        // A snag-only world override must inherit the original base gameplay file, while a
        // common-only override inherits base snag settings. Preserve this independent selection.
        CommentedConfig common = copy(original.common != null ? original.common : fallback.common);
        if (original.common == null) {
            common.remove("snag");
            common.removeComment("snag");
        }
        common.remove("worldAlteration");
        common.removeComment("worldAlteration");

        UnmodifiableCommentedConfig snag = original.snag != null ? original.snag : snagSettings(fallback);
        if (snag != null) {
            CommentedConfig incoming = CommentedConfig.inMemory();
            incoming.set("snag", copy(snag));
            mergeMissing(common, incoming);
        }

        CommentedConfig server = copy(original.server);
        if (movedWorldSettings) {
            CommentedConfig moved = CommentedConfig.inMemory();
            moved.set("worldAlteration", copyValue(original.common.getRaw("worldAlteration")));
            String comment = original.common.getComment("worldAlteration");
            if (comment != null) {
                moved.setComment("worldAlteration", comment);
            }
            mergeMissing(server, moved);
        }

        boolean writeCommon = !same(common, original.common);
        // A trimmed legacy common file still overrode the entire old base common file. Missing
        // world settings therefore meant spec defaults, not values from a base/template file.
        // An empty local server file preserves that behavior when the native spec corrects it.
        boolean needsDefaultServerOverride = legacyCommon && !movedWorldSettings && original.server == null;
        boolean writeServer = (movedWorldSettings || needsDefaultServerOverride) && !same(server, original.server);
        Files.createDirectories(directory);
        // Back up every target before any target is changed. Backups never rotate or overwrite.
        if (writeCommon) {
            backup(directory.resolve("common.toml"));
        }
        if (writeServer) {
            backup(directory.resolve("server.toml"));
        }
        // The source section remains in common until its destination has been committed. If a
        // later write fails, retrying merges missing keys and preserves the completed destination.
        if (writeServer) {
            write(directory.resolve("server.toml"), server);
        }
        if (writeCommon) {
            write(directory.resolve("common.toml"), common);
        }
        Files.writeString(directory.resolve(MARKER), "Migrated common/snag config layout to version 2.\n"
                        + "Legacy snag.toml and *.pre-layout-v2.bak files are retained for reference.\n",
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return true;
    }

    private static UnmodifiableCommentedConfig snagSettings(Snapshot snapshot) {
        if (snapshot.common != null) {
            Object value = snapshot.common.getRaw("snag");
            if (value instanceof UnmodifiableCommentedConfig config) {
                return config;
            }
        }
        return snapshot.snag;
    }

    private static Snapshot readSnapshot(Path directory) throws IOException {
        // A retained legacy file is inert after migration, even if later edited or malformed.
        CommentedConfig snag = Files.exists(directory.resolve(MARKER)) ? null : read(directory.resolve("snag.toml"));
        return new Snapshot(read(directory.resolve("common.toml")), snag,
                read(directory.resolve("server.toml")));
    }

    private static CommentedConfig read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        try (var reader = Files.newBufferedReader(path)) {
            return new TomlParser().parse(reader);
        }
    }

    private static void write(Path path, CommentedConfig config) {
        new TomlWriter().write(config, path, WritingMode.REPLACE_ATOMIC);
    }

    private static void backup(Path path) throws IOException {
        backup(path, BACKUP_SUFFIX);
    }

    private static void backup(Path path, String suffix) throws IOException {
        Path backup = path.resolveSibling(path.getFileName() + suffix);
        if (Files.exists(backup) && !Files.isRegularFile(backup)) {
            throw new IOException("Config backup is not a regular file: " + backup);
        }
        if (Files.exists(path) && !Files.exists(backup)) {
            Files.copy(path, backup);
        }
    }

    private static boolean same(CommentedConfig migrated, CommentedConfig original) {
        // Comments are only copied alongside newly inserted values, never changed on their own.
        return original != null && migrated.equals(original);
    }

    /** Destination scalars and lists, including empty lists, always win over legacy values. */
    private static void mergeMissing(CommentedConfig destination, UnmodifiableCommentedConfig source) {
        for (UnmodifiableCommentedConfig.Entry entry : source.entrySet()) {
            List<String> path = List.of(entry.getKey());
            if (!destination.contains(path)) {
                destination.set(path, copyValue(entry.getValue()));
                if (entry.getComment() != null) {
                    destination.setComment(path, entry.getComment());
                }
            } else if (destination.getRaw(path) instanceof CommentedConfig nested
                    && entry.getValue() instanceof UnmodifiableCommentedConfig incoming) {
                mergeMissing(nested, incoming);
            }
        }
    }

    private static CommentedConfig copy(UnmodifiableCommentedConfig source) {
        CommentedConfig copy = CommentedConfig.inMemory();
        if (source != null) {
            mergeMissing(copy, source);
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof UnmodifiableCommentedConfig config) {
            return copy(config);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyValue(element)));
            return copy;
        }
        return value;
    }

    private record Snapshot(CommentedConfig common, CommentedConfig snag, CommentedConfig server) {
        private static Snapshot empty() {
            return new Snapshot(null, null, null);
        }
    }
}
