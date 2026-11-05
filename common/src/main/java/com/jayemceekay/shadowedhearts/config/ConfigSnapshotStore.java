package com.jayemceekay.shadowedhearts.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Persistent, per-file history. Display names are metadata, never filesystem paths. */
public final class ConfigSnapshotStore {
    public static final ConfigSnapshotStore INSTANCE = new ConfigSnapshotStore();
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_AUTOMATIC = 20;
    public static final int MAX_NAMED = 100;

    public record Entry(String id, String name, long createdAt, boolean automatic) { }

    public Path backup(Path sourceConfig, Path snapshotDirectory) throws IOException {
        byte[] contents = readBounded(sourceConfig);
        Files.createDirectories(snapshotDirectory);
        Path target = Files.createTempFile(snapshotDirectory, sourceConfig.getFileName() + "-snapshot-", ".bak");
        try {
            Files.write(target, contents);
            return target;
        } catch (IOException failure) {
            try { Files.deleteIfExists(target); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static Path directory(Path source) {
        return source.toAbsolutePath().normalize().getParent().resolve("snapshots").resolve(source.getFileName().toString());
    }

    public static byte[] readBounded(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
            throw new IOException("Missing, linked, or oversized configuration: " + file);
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Configuration is too large");
            return bytes;
        }
    }

    public synchronized Entry save(Path source, byte[] contents, String name, boolean automatic) throws IOException {
        if (contents.length > MAX_BYTES) throw new IOException("Configuration is too large");
        name = name.strip();
        if (name.isEmpty() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
            throw new IOException("Snapshot names must contain 1-64 printable characters");
        Path folder = prepare(source);
        var entries = list(source);
        if (!automatic && entries.stream().filter(entry -> !entry.automatic()).count() >= MAX_NAMED)
            throw new IOException("Named snapshot limit reached; delete an older snapshot first");
        long createdAt = Math.max(System.currentTimeMillis(), entries.stream().mapToLong(Entry::createdAt).max().orElse(0) + 1);
        Entry entry = new Entry(UUID.randomUUID().toString(), name, createdAt, automatic);
        JsonObject json = new JsonObject();
        json.addProperty("format", 1);
        json.addProperty("file", source.getFileName().toString());
        json.addProperty("id", entry.id());
        json.addProperty("name", entry.name());
        json.addProperty("createdAt", entry.createdAt());
        json.addProperty("automatic", automatic);
        json.addProperty("sha256", digest(contents));
        json.addProperty("contents", Base64.getEncoder().encodeToString(contents));
        publish(folder.resolve(entry.id() + ".json"), json.toString().getBytes(StandardCharsets.UTF_8), false);
        if (automatic) {
            var history = list(source).stream().filter(Entry::automatic).toList();
            for (int i = MAX_AUTOMATIC; i < history.size(); i++) Files.delete(path(source, history.get(i).id()));
        }
        return entry;
    }

    /** Preserve the last observed version even when an edit happened while the game was stopped. */
    public synchronized void observe(Path source, byte[] current) throws IOException {
        Path baseline = prepare(source).resolve("baseline.toml");
        if (Files.exists(baseline, LinkOption.NOFOLLOW_LINKS)) {
            byte[] previous = readBounded(baseline);
            if (Arrays.equals(previous, current)) return;
            automatic(source, previous, "Before configuration change");
        } else automatic(source, current, "Initial configuration");
        publish(baseline, current, true);
    }

    public synchronized void automatic(Path source, byte[] contents, String reason) throws IOException {
        var latest = list(source).stream().filter(Entry::automatic).findFirst();
        if (latest.isPresent()) {
            try {
                if (Arrays.equals(read(source, latest.get().id()), contents)) return;
            } catch (IOException invalid) {
                // A damaged recovery point must not prevent saving a new usable one.
            }
        }
        save(source, contents, reason, true);
    }

    public synchronized List<Entry> list(Path source) throws IOException {
        Path folder = directory(source);
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return List.of();
        checkDirectory(folder);
        List<Entry> entries = new ArrayList<>();
        try (var files = Files.newDirectoryStream(folder, "*.json")) {
            for (Path file : files) {
                String filename = file.getFileName().toString();
                String id = filename.substring(0, filename.length() - 5);
                try {
                    JsonObject json = envelope(source, id);
                    entries.add(new Entry(id, json.get("name").getAsString(),
                            json.get("createdAt").getAsLong(), json.get("automatic").getAsBoolean()));
                } catch (IOException | RuntimeException invalid) {
                    // A damaged entry must not hide the remaining recovery points.
                }
            }
        }
        entries.sort(Comparator.comparingLong(Entry::createdAt).reversed().thenComparing(Entry::id));
        return List.copyOf(entries);
    }

    public synchronized byte[] read(Path source, String id) throws IOException {
        try {
            JsonObject json = envelope(source, id);
            byte[] contents = Base64.getDecoder().decode(json.get("contents").getAsString());
            if (contents.length > MAX_BYTES || !digest(contents).equals(json.get("sha256").getAsString()))
                throw new IOException("Snapshot checksum mismatch");
            return contents;
        } catch (RuntimeException invalid) { throw new IOException("Invalid snapshot", invalid); }
    }

    public synchronized void delete(Path source, String id) throws IOException { Files.delete(path(source, id)); }

    private JsonObject envelope(Path source, String id) throws IOException {
        Path file = path(source, id);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES * 2L)
            throw new IOException("Invalid snapshot file");
        String text;
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_BYTES * 2 + 1);
            if (bytes.length > MAX_BYTES * 2) throw new IOException("Snapshot too large");
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        if (json.get("format").getAsInt() != 1 || !id.equals(json.get("id").getAsString())
                || !source.getFileName().toString().equals(json.get("file").getAsString())
                || json.get("name").getAsString().length() > 64)
            throw new IOException("Incompatible snapshot");
        return json;
    }

    private static Path path(Path source, String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IOException("Invalid snapshot ID");
        checkDirectory(directory(source));
        return directory(source).resolve(id + ".json");
    }

    private static Path prepare(Path source) throws IOException {
        Path folder = directory(source);
        if (Files.isSymbolicLink(folder.getParent()) || Files.isSymbolicLink(folder))
            throw new IOException("Invalid snapshot directory");
        Files.createDirectories(folder);
        checkDirectory(folder);
        return folder;
    }

    private static void checkDirectory(Path folder) throws IOException {
        if (Files.isSymbolicLink(folder) || Files.isSymbolicLink(folder.getParent())
                || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid snapshot directory");
    }

    private static void publish(Path target, byte[] data, boolean replace) throws IOException {
        Path pending = Files.createTempFile(target.getParent(), ".pending-", ".tmp");
        try {
            Files.write(pending, data);
            if (replace) {
                try { Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING); }
            } else Files.move(pending, target);
        } finally { Files.deleteIfExists(pending); }
    }

    private static String digest(byte[] contents) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
