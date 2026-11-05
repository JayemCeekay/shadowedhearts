package com.jayemceekay.shadowedhearts.common.shadow;

import net.minecraft.network.chat.Component;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessageException;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages;

import java.util.*;
import java.util.function.Function;

/** Immutable, validated interpretation of the legacy NPC tags. No entity or party is mutated. */
public record NPCShadowProfile(boolean enabled, Mode mode, int count, LevelMode levelMode,
                               int levelValue, String poolId, boolean unique, int convertChance,
                               boolean enforceMinEvolutionLevel) {
    public enum Mode { CONVERT, APPEND, REPLACE }
    public enum LevelMode { MATCH, FIXED, PLUS }

    public record Layer(String source, Collection<String> tags) {
        public Layer { tags = List.copyOf(tags); }
    }

    public record Resolution(NPCShadowProfile profile, List<Component> errors, List<Component> warnings,
                             Map<String, String> sources, Component description) {
        public Resolution {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
            sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        }
        public boolean valid() { return errors.isEmpty(); }
    }

    private record Level(LevelMode mode, int value) {}
    private record Setting(Object value, String source) {}
    private record Parsed(String field, Object value) {}
    private static final String PRESET = "sh_shadow_presets/";

    /** Layers are ordered from lowest to highest priority; direct settings override their presets. */
    public static Resolution resolve(List<Layer> layers, Function<String, List<String>> presets) {
        Resolver resolver = new Resolver(presets);
        Map<String, Setting> settings = new LinkedHashMap<>();
        for (Layer layer : layers) {
            settings.putAll(resolver.traits(layer.tags(), layer.source(), new LinkedHashSet<>()));
        }
        Level level = value(settings, "level", new Level(LevelMode.MATCH, 0));
        NPCShadowProfile profile = new NPCShadowProfile(value(settings, "enabled", false),
                value(settings, "mode", Mode.CONVERT), value(settings, "count", 1), level.mode(), level.value(),
                value(settings, "pool", (String) null), value(settings, "unique", false),
                value(settings, "chance", -1), value(settings, "evolutionMinimum", false));
        if (profile.mode() != Mode.CONVERT && profile.convertChance() >= 0) {
            resolver.warnings.add(text("conversion_chance_ignored"));
        }
        if (profile.mode() == Mode.CONVERT && profile.poolId() != null) {
            resolver.warnings.add(text("pool_ignored"));
        }
        Map<String, String> sources = new LinkedHashMap<>();
        settings.forEach((field, setting) -> sources.put(field, setting.source()));
        return new Resolution(profile, resolver.errors, resolver.warnings, sources, text("description"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(Map<String, Setting> settings, String field, T fallback) {
        Setting setting = settings.get(field);
        return setting == null ? fallback : (T) setting.value();
    }

    public static boolean isShadowTag(String tag) {
        return tag != null && (tag.startsWith("sh_shadow_") || tag.startsWith("sh_lvl_")
                || tag.startsWith("sh_pool/") || tag.equals("sh_pool_none")
                || tag.startsWith("sh_unique") || tag.startsWith("sh_convert_chance_"));
    }

    /** Accept both legacy namespace/path and normal namespace:path IDs. */
    public static String canonicalId(String raw) {
        if (raw == null || raw.isBlank()) throw new TrainerEditorMessageException(text("resource_id_empty"));
        String id = raw.trim().toLowerCase(Locale.ROOT);
        int separator = id.indexOf(':');
        if (separator < 0) separator = id.indexOf('/');
        String namespace = separator < 0 ? "shadowedhearts" : id.substring(0, separator);
        String path = separator < 0 ? id : id.substring(separator + 1);
        if (!namespace.matches("[a-z0-9_.-]+") || namespace.equals(".") || namespace.equals("..")
                || !path.matches("[a-z0-9/._-]+")) {
            throw new TrainerEditorMessageException(text("resource_id_invalid", raw));
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new TrainerEditorMessageException(text("resource_path_invalid", raw));
            }
        }
        return namespace + ":" + path;
    }

    /** Syntactic validation; preset and pool existence are checked when resolving on the server. */
    public static Component validateTag(String tag) {
        if (!isShadowTag(tag)) return text("unknown_tag", tag);
        try {
            if (tag.startsWith(PRESET)) canonicalId(tag.substring(PRESET.length()));
            else parse(tag);
            return null;
        } catch (IllegalArgumentException error) {
            return TrainerEditorMessageException.component(error);
        }
    }

    private static Parsed parse(String tag) {
        return switch (tag) {
            case "sh_shadow_party" -> new Parsed("enabled", true);
            case "sh_shadow_party_false" -> new Parsed("enabled", false);
            case "sh_shadow_mode_convert" -> new Parsed("mode", Mode.CONVERT);
            case "sh_shadow_mode_append" -> new Parsed("mode", Mode.APPEND);
            case "sh_shadow_mode_replace" -> new Parsed("mode", Mode.REPLACE);
            case "sh_lvl_match" -> new Parsed("level", new Level(LevelMode.MATCH, 0));
            case "sh_unique" -> new Parsed("unique", true);
            case "sh_unique_false" -> new Parsed("unique", false);
            case "sh_lvl_enforce_evo_min" -> new Parsed("evolutionMinimum", true);
            case "sh_lvl_enforce_evo_min_false" -> new Parsed("evolutionMinimum", false);
            case "sh_pool_none" -> new Parsed("pool", null);
            case "sh_convert_chance_default" -> new Parsed("chance", -1);
            default -> {
                if (tag.startsWith("sh_shadow_n")) {
                    yield new Parsed("count", number(tag, "sh_shadow_n", 0, 6));
                }
                if (tag.startsWith("sh_lvl_fixed_")) {
                    yield new Parsed("level", new Level(LevelMode.FIXED,
                            number(tag, "sh_lvl_fixed_", 1, Integer.MAX_VALUE)));
                }
                if (tag.startsWith("sh_lvl_plus_")) {
                    yield new Parsed("level", new Level(LevelMode.PLUS,
                            number(tag, "sh_lvl_plus_", Integer.MIN_VALUE, Integer.MAX_VALUE)));
                }
                if (tag.startsWith("sh_convert_chance_")) {
                    yield new Parsed("chance", number(tag, "sh_convert_chance_", 0, 100));
                }
                if (tag.startsWith("sh_pool/")) {
                    yield new Parsed("pool", canonicalId(tag.substring("sh_pool/".length())));
                }
                throw new TrainerEditorMessageException(text("unknown_tag", tag));
            }
        };
    }

    private static int number(String tag, String prefix, int min, int max) {
        try {
            int value = Integer.parseInt(tag.substring(prefix.length()));
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        throw new TrainerEditorMessageException(text("tag_range", tag, min, max));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("message.shadowedhearts.npc_profile." + key, args);
    }

    private static final class Resolver {
        private final Function<String, List<String>> presets;
        private final List<Component> errors = new ArrayList<>();
        private final List<Component> warnings = new ArrayList<>();

        private Resolver(Function<String, List<String>> presets) { this.presets = presets; }

        private Map<String, Setting> traits(Collection<String> tags, String source, Set<String> active) {
            Map<String, Setting> direct = new LinkedHashMap<>();
            Set<String> presetIds = new TreeSet<>();
            for (String tag : new TreeSet<>(tags)) {
                if (!isShadowTag(tag)) {
                    if (!active.isEmpty()) warnings.add(text("non_shadow_trait", TrainerEditorMessages.source(source), tag));
                    continue;
                }
                try {
                    if (tag.startsWith(PRESET)) {
                        presetIds.add(canonicalId(tag.substring(PRESET.length())));
                    } else {
                        Parsed parsed = parse(tag);
                        merge(direct, parsed.field(), new Setting(parsed.value(), source), source);
                    }
                } catch (IllegalArgumentException error) {
                    errors.add(text("source_detail", TrainerEditorMessages.source(source), TrainerEditorMessageException.component(error)));
                }
            }
            Map<String, Setting> inherited = new LinkedHashMap<>();
            for (String id : presetIds) {
                if (active.size() >= 16 || !active.add(id)) {
                    errors.add(text("recursive_preset", TrainerEditorMessages.source(source), id));
                    continue;
                }
                try {
                    List<String> contents = presets.apply(id);
                    if (contents == null || contents.isEmpty()) {
                        errors.add(text("preset_missing", TrainerEditorMessages.source(source), id));
                        continue;
                    }
                    Map<String, Setting> expanded = traits(contents, source + " > preset " + id, active);
                    expanded.forEach((field, setting) -> {
                        if (!direct.containsKey(field)) merge(inherited, field, setting, source);
                    });
                } catch (RuntimeException error) {
                    errors.add(text("preset_unreadable", TrainerEditorMessages.source(source), id, TrainerEditorMessageException.component(error)));
                } finally {
                    active.remove(id);
                }
            }
            inherited.putAll(direct);
            return inherited;
        }

        private void merge(Map<String, Setting> target, String field, Setting value, String source) {
            Setting previous = target.putIfAbsent(field, value);
            if (previous != null && !Objects.equals(previous.value(), value.value())) {
                errors.add(text("conflicting_settings", TrainerEditorMessages.source(source), TrainerEditorMessages.field(field),
                        TrainerEditorMessages.source(previous.source()), TrainerEditorMessages.source(value.source())));
            }
        }
    }
}
