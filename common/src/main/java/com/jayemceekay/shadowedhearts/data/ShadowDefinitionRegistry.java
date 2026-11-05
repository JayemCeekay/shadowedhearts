package com.jayemceekay.shadowedhearts.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException;
import static com.jayemceekay.shadowedhearts.data.DefinitionMessages.text;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared disk overrides and resource-manager-scoped caches for NPC definitions. */
final class ShadowDefinitionRegistry<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowDefinitionRegistry.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DELETED_OVERRIDE = "shadowedhearts:deleted_override";

    @FunctionalInterface
    interface AtomicWriter {
        void write(Path file, String content) throws IOException;
    }

    private record Source(JsonElement json, String description, Component error) {}
    private record Loaded<T>(T value, List<Component> diagnostics, boolean retryable) {
        Loaded(T value, List<Component> diagnostics) { this(value, diagnostics, false); }
    }

    static final class NotReadyException extends TrainerEditorMessageException {
        NotReadyException(Component message) { super(message); }
    }

    private final Supplier<Path> directory;
    private final String resourceDirectory;
    private final boolean legacyPresetPaths;
    private final Function<JsonElement, T> decoder;
    private final Function<T, JsonElement> encoder;
    private final AtomicWriter writer;
    private Map<ResourceLocation, Source> overrides = Map.of();
    private final Map<ResourceManager, Map<ResourceLocation, Loaded<T>>> caches = new WeakHashMap<>();

    ShadowDefinitionRegistry(Supplier<Path> directory, String resourceDirectory, boolean legacyPresetPaths,
                             Function<JsonElement, T> decoder, Function<T, JsonElement> encoder) {
        this(directory, resourceDirectory, legacyPresetPaths, decoder, encoder, ShadowDefinitionRegistry::atomicWrite);
    }

    ShadowDefinitionRegistry(Supplier<Path> directory, String resourceDirectory, boolean legacyPresetPaths,
                             Function<JsonElement, T> decoder, Function<T, JsonElement> encoder, AtomicWriter writer) {
        this.directory = directory;
        this.resourceDirectory = resourceDirectory;
        this.legacyPresetPaths = legacyPresetPaths;
        this.decoder = decoder;
        this.encoder = encoder;
        this.writer = writer;
    }

    synchronized void reload() {
        Path base = directory.get().toAbsolutePath().normalize();
        Map<ResourceLocation, Source> next = new LinkedHashMap<>();
        Map<ResourceLocation, Source> canonical = new LinkedHashMap<>();
        if (Files.exists(base)) {
            try (var files = Files.walk(base)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    if (!file.getFileName().toString().endsWith(".json")) continue;
                    Path relative = base.relativize(file);
                    ResourceLocation id;
                    try {
                        id = diskId(relative);
                    } catch (IllegalArgumentException ex) {
                        LOGGER.warn("Ignoring NPC definition {}: {}", file, ex.getMessage());
                        continue;
                    }
                    if (id == null) continue;
                    Source source;
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        source = new Source(JsonParser.parseReader(reader), file.toString(), null);
                    } catch (IOException | RuntimeException ex) {
                        source = new Source(null, file.toString(), text("read_failed", file, TrainerEditorMessageException.component(ex)));
                        LOGGER.warn(source.error().getString());
                    }
                    (relative.getNameCount() > 1 ? canonical : next).put(id, source);
                }
            } catch (IOException ex) {
                throw TrainerEditorMessageException.state(text("reload_failed", base), ex);
            }
        }
        // Canonical files (including deletion markers) always supersede legacy flat files.
        canonical.forEach((id, source) -> {
            if (isDeletionMarker(source)) next.remove(id);
            else next.put(id, source);
        });
        overrides = Collections.unmodifiableMap(next);
        caches.clear();
    }

    synchronized T get(ResourceManager manager, ResourceLocation id) {
        return loaded(manager, id).value();
    }

    synchronized List<Component> diagnostics(ResourceManager manager, ResourceLocation id) {
        return loaded(manager, id).diagnostics();
    }

    synchronized Set<ResourceLocation> ids(ResourceManager manager) {
        Set<ResourceLocation> ids = new java.util.TreeSet<>(overrides.keySet());
        if (manager != null) {
            for (ResourceLocation file : manager.listResources(resourceDirectory, location -> location.getPath().endsWith(".json")).keySet()) {
                String path = file.getPath().substring(resourceDirectory.length() + 1);
                path = path.substring(0, path.length() - 5);
                if (path.isBlank()) continue;
                ids.add(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path));
                if (legacyPresetPaths && file.getNamespace().equals("shadowedhearts") && path.contains("/")) {
                    int slash = path.indexOf('/');
                    ResourceLocation legacy = ResourceLocation.tryBuild(path.substring(0, slash), path.substring(slash + 1));
                    if (legacy != null) ids.add(legacy);
                }
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    /** Present legacy namespace-prefixed presets once, while retaining every supported lookup ID. */
    synchronized Set<ResourceLocation> selectableIds(ResourceManager manager) {
        Set<ResourceLocation> result = new java.util.TreeSet<>(ids(manager));
        if (!legacyPresetPaths || manager == null) return Collections.unmodifiableSet(result);
        for (ResourceLocation id : List.copyOf(result)) {
            // Do not reinterpret ordinary nested paths such as shadowedhearts:editor/my_preset.
            String repeatedNamespace = id.getNamespace() + "/";
            if (!id.getNamespace().equals("shadowedhearts") || !id.getPath().startsWith(repeatedNamespace)) continue;
            ResourceLocation preferred = ResourceLocation.tryBuild(id.getNamespace(), id.getPath().substring(repeatedNamespace.length()));
            if (preferred == null || !result.contains(preferred) || overrides.containsKey(id) || overrides.containsKey(preferred)) continue;
            ResourceLocation source = effectiveResource(manager, id);
            if (source != null && source.equals(effectiveResource(manager, preferred))) result.remove(id);
        }
        return Collections.unmodifiableSet(result);
    }

    private ResourceLocation effectiveResource(ResourceManager manager, ResourceLocation id) {
        for (ResourceLocation file : resourceFiles(id)) if (manager.getResource(file).isPresent()) return file;
        return null;
    }

    synchronized Map<ResourceLocation, T> runtimeValues() {
        Map<ResourceLocation, T> result = new TreeMap<>();
        for (ResourceLocation id : overrides.keySet()) {
            T value = loaded(null, id).value();
            if (value != null) result.put(id, value);
        }
        return Collections.unmodifiableMap(result);
    }

    synchronized boolean hasOverride(ResourceLocation id) { return overrides.containsKey(id); }

    /** Captures this logical override and its exact canonical file before a coordinated editor save. */
    synchronized Runnable checkpoint(ResourceLocation id) {
        Path file = canonicalFile(directory.get(), id);
        Source previous = overrides.get(id);
        String content;
        try { content = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null; }
        catch (IOException ex) { throw TrainerEditorMessageException.state(text("prepare_failed", file, TrainerEditorMessageException.component(ex)), ex); }
        return () -> {
            synchronized (ShadowDefinitionRegistry.this) {
                try {
                    if (content == null) Files.deleteIfExists(file);
                    else writer.write(file, content);
                } catch (IOException ex) {
                    throw TrainerEditorMessageException.state(text("restore_failed", file, TrainerEditorMessageException.component(ex)), ex);
                }
                Map<ResourceLocation, Source> next = new LinkedHashMap<>(overrides);
                if (previous == null) next.remove(id); else next.put(id, previous);
                overrides = Collections.unmodifiableMap(next);
                caches.clear();
            }
        };
    }

    synchronized void save(ResourceLocation id, T value) {
        Path file = canonicalFile(directory.get(), id);
        JsonElement json = encoder.apply(value);
        // Validate exactly what will survive a restart before publishing anything.
        decoder.apply(json.deepCopy());
        write(file, json);
        Map<ResourceLocation, Source> next = new LinkedHashMap<>(overrides);
        next.put(id, new Source(json.deepCopy(), file.toString(), null));
        overrides = Collections.unmodifiableMap(next);
        caches.clear();
    }

    synchronized void delete(ResourceLocation id) {
        Path file = canonicalFile(directory.get(), id);
        if (!overrides.containsKey(id)) {
            throw new TrainerEditorMessageException(text("override_missing", resourceDirectory, id));
        }
        // Preserve ambiguous legacy files. A marker removes only this logical override,
        // prevents the old file from resurfacing after reload, and exposes any datapack default.
        JsonObject marker = new JsonObject();
        marker.addProperty(DELETED_OVERRIDE, true);
        write(file, marker);
        Map<ResourceLocation, Source> next = new LinkedHashMap<>(overrides);
        next.remove(id);
        overrides = Collections.unmodifiableMap(next);
        caches.clear();
    }

    private void write(Path file, JsonElement json) {
        try {
            writer.write(file, GSON.toJson(json) + System.lineSeparator());
        } catch (IOException ex) {
            throw TrainerEditorMessageException.state(text("save_failed", file, TrainerEditorMessageException.component(ex)), ex);
        }
    }

    private Loaded<T> loaded(ResourceManager manager, ResourceLocation id) {
        if (id == null) return new Loaded<>(null, List.of(text("id_missing", resourceDirectory)));
        if (manager == null) return load(null, id);
        Map<ResourceLocation, Loaded<T>> cache = caches.computeIfAbsent(manager, ignored -> new LinkedHashMap<>());
        Loaded<T> result = cache.get(id);
        if (result != null) return result;
        result = load(manager, id);
        if (!result.retryable()) cache.put(id, result);
        return result;
    }

    private Loaded<T> load(ResourceManager manager, ResourceLocation id) {
        Source source = overrides.get(id);
        if (source == null && manager != null) {
            for (ResourceLocation file : resourceFiles(id)) {
                var resource = manager.getResource(file);
                if (resource.isEmpty()) continue;
                try (Reader reader = resource.get().openAsReader()) {
                    source = new Source(JsonParser.parseReader(reader), file.toString(), null);
                } catch (IOException | RuntimeException ex) {
                    source = new Source(null, file.toString(), text("read_failed", file, TrainerEditorMessageException.component(ex)));
                }
                break;
            }
        }
        if (source == null) return new Loaded<>(null, List.of(text("missing", resourceDirectory, id)));
        if (source.error() != null) return new Loaded<>(null, List.of(source.error()));
        try {
            return new Loaded<>(decoder.apply(source.json().deepCopy()), List.of());
        } catch (NotReadyException ex) {
            return new Loaded<>(null, List.of(TrainerEditorMessageException.component(ex)), true);
        } catch (RuntimeException ex) {
            Component problem = text("invalid", resourceDirectory, id, source.description(), TrainerEditorMessageException.component(ex));
            LOGGER.warn(problem.getString());
            return new Loaded<>(null, List.of(problem));
        }
    }

    private List<ResourceLocation> resourceFiles(ResourceLocation id) {
        List<ResourceLocation> files = new ArrayList<>();
        files.add(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), resourceDirectory + "/" + id.getPath() + ".json"));
        if (legacyPresetPaths) files.add(ResourceLocation.fromNamespaceAndPath("shadowedhearts", resourceDirectory + "/" + id.getNamespace() + "/" + id.getPath() + ".json"));
        return files;
    }

    static ResourceLocation diskId(Path relative) {
        String text = relative.toString().replace('\\', '/');
        if (!text.endsWith(".json")) return null;
        text = text.substring(0, text.length() - 5);
        int separator = text.indexOf('/');
        if (separator < 0) separator = text.indexOf('_'); // Match the old loader's interpretation exactly.
        if (separator <= 0 || separator == text.length() - 1) return null;
        ResourceLocation id = ResourceLocation.tryBuild(text.substring(0, separator), text.substring(separator + 1));
        if (id == null) throw new TrainerEditorMessageException(text("invalid_file_id", relative));
        validatePath(id);
        return id;
    }

    static Path canonicalFile(Path directory, ResourceLocation id) {
        if (id == null) throw new TrainerEditorMessageException(text("id_required"));
        validatePath(id);
        Path base = directory.toAbsolutePath().normalize();
        Path file = base.resolve(id.getNamespace()).resolve(id.getPath() + ".json").normalize();
        if (!file.startsWith(base)) throw new TrainerEditorMessageException(text("path_escape", id));
        return file;
    }

    private static void validatePath(ResourceLocation id) {
        for (String segment : (id.getNamespace() + "/" + id.getPath()).split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new TrainerEditorMessageException(text("invalid_path_segment", id));
            }
        }
    }

    private static boolean isDeletionMarker(Source source) {
        return source.json() != null && source.json().isJsonObject()
                && source.json().getAsJsonObject().has(DELETED_OVERRIDE)
                && source.json().getAsJsonObject().get(DELETED_OVERRIDE).isJsonPrimitive()
                && source.json().getAsJsonObject().get(DELETED_OVERRIDE).getAsString().equals("true");
    }

    private static void atomicWrite(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".shadowedhearts-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            try { Files.deleteIfExists(temporary); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }
}
