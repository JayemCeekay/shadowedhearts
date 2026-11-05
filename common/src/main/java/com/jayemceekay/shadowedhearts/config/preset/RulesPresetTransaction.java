package com.jayemceekay.shadowedhearts.config.preset;

import com.google.gson.Gson;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** A validated, bounded patch to an already loaded spec; never replaces the entire config. */
final class RulesPresetTransaction {
    private static final Gson JSON = new Gson();

    private RulesPresetTransaction() { }

    record Change(String path, String labelKey, ModConfigSpec.ConfigValue<Object> setting,
                  Object current, Object proposed) {
        boolean changed() { return !Objects.equals(current, proposed); }
    }

    @SuppressWarnings("unchecked")
    static List<Change> plan(ModConfigSpec spec, Set<String> managedPaths,
                             Map<String, Object> proposed, boolean defaults) {
        if (!spec.isLoaded() || (!defaults && !proposed.keySet().equals(managedPaths))) {
            throw new IllegalArgumentException("Invalid preset paths or unloaded config");
        }
        List<Change> result = new ArrayList<>();
        for (String path : new TreeSet<>(managedPaths)) {
            Object raw = spec.getValues().get(path);
            if (!(raw instanceof ModConfigSpec.ConfigValue<?>)) throw new IllegalArgumentException("Unknown preset setting: " + path);
            var setting = (ModConfigSpec.ConfigValue<Object>) raw;
            Object replacement = copy(defaults ? setting.getSpec().getDefault() : proposed.get(path));
            if (!setting.getSpec().test(replacement)) throw new IllegalArgumentException("Invalid preset value: " + path);
            String label = setting.getSpec().getTranslationKey();
            result.add(new Change(path, label == null ? path : label, setting, copy(setting.get()), replacement));
        }
        return List.copyOf(result);
    }

    static String revision(Path source, byte[] file, String presetId, List<Change> changes) {
        Map<String, Object> current = new TreeMap<>(), proposed = new TreeMap<>();
        changes.forEach(change -> { current.put(change.path, change.current); proposed.put(change.path, change.proposed); });
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (byte[] part : List.of(source.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8),
                    presetId.getBytes(StandardCharsets.UTF_8), file, JSON.toJson(current).getBytes(StandardCharsets.UTF_8),
                    JSON.toJson(proposed).getBytes(StandardCharsets.UTF_8))) {
                hash.update(Integer.toString(part.length).getBytes(StandardCharsets.US_ASCII));
                hash.update((byte) ':');
                hash.update(part);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** A file watcher may not yet have published an external edit. Never save over that pending edit. */
    static boolean persistedMatchesRuntime(ModConfigSpec spec, byte[] file) {
        CommentedConfig parsed = parse(file);
        for (var setting : settings(spec.getValues())) {
            Object current = setting.get();
            Object disk = parsed.contains(setting.getPath()) ? parsed.get(setting.getPath()) : setting.getSpec().getDefault();
            if (!Objects.equals(normalize(current, current), normalize(disk, current))) return false;
        }
        return true;
    }

    /** Predict the native writer's result so a rollback cannot overwrite a newer external file. */
    static byte[] expectedSavedBytes(ModConfigSpec spec, byte[] original) {
        CommentedConfig parsed = parse(original);
        for (var setting : settings(spec.getValues())) parsed.set(setting.getPath(), setting.get());
        StringWriter output = new StringWriter();
        new TomlWriter().write(parsed, output);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    static List<Change> planSnapshot(ModConfigSpec spec, byte[] file) {
        CommentedConfig parsed = parse(file);
        // correct() counts value/schema changes, but not refreshed comments. Comment wording is not a schema change.
        if (!spec.isLoaded() || spec.correct(parsed, (action, path, before, after) -> {}) != 0)
            throw new IllegalArgumentException("Snapshot schema differs from the loaded config");
        Map<String, Object> proposed = new LinkedHashMap<>();
        for (var setting : settings(spec.getValues())) {
            Object value = parsed.get(setting.getPath());
            Object expected = setting.get();
            if (expected instanceof Enum<?> enumeration) {
                String enumName = String.valueOf(value);
                value = Arrays.stream(enumeration.getDeclaringClass().getEnumConstants())
                        .filter(item -> item.name().equalsIgnoreCase(enumName))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid enum value"));
            } else if (expected instanceof Integer && value instanceof Number number) {
                value = new BigDecimal(number.toString()).intValueExact();
            } else if (expected instanceof Long && value instanceof Number number) {
                value = new BigDecimal(number.toString()).longValueExact();
            } else if (expected instanceof Double && value instanceof Number number) value = number.doubleValue();
            proposed.put(String.join(".", setting.getPath()), value);
        }
        return plan(spec, proposed.keySet(), proposed, false);
    }

    private static CommentedConfig parse(byte[] file) {
        return new TomlParser().parse(new StringReader(new String(file, StandardCharsets.UTF_8)));
    }

    private static List<ModConfigSpec.ConfigValue<?>> settings(UnmodifiableConfig values) {
        List<ModConfigSpec.ConfigValue<?>> result = new ArrayList<>();
        for (Object value : values.valueMap().values()) {
            if (value instanceof ModConfigSpec.ConfigValue<?> setting) result.add(setting);
            else if (value instanceof UnmodifiableConfig nested) result.addAll(settings(nested));
        }
        return result;
    }

    private static Object normalize(Object value, Object expected) {
        if (expected instanceof Enum<?>) return value == null ? null
                : (value instanceof Enum<?> item ? item.name() : value.toString()).toUpperCase(Locale.ROOT);
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros();
        if (value instanceof List<?> list) return list.stream().map(item -> normalize(item, item)).toList();
        return value;
    }

    /** On either publication or save/reload failure, restore memory and attempt native persistence again. */
    static boolean apply(List<Change> changes, Runnable save) {
        List<Change> changed = changes.stream().filter(Change::changed).toList();
        if (changed.isEmpty()) return false;
        try {
            for (Change change : changed) change.setting.set(copy(change.proposed));
            save.run();
            return true;
        } catch (RuntimeException failure) {
            for (Change change : changed) {
                try { change.setting.set(copy(change.current)); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            try { save.run(); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    static Object copy(Object value) {
        if (value instanceof List<?> list) return list.stream().map(RulesPresetTransaction::copy).toList();
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(key, copy(item)));
            return Collections.unmodifiableMap(result);
        }
        return value;
    }

    static String display(Object value) {
        return value instanceof String string ? string : value instanceof Enum<?> item ? item.name() : JSON.toJson(value);
    }
}
