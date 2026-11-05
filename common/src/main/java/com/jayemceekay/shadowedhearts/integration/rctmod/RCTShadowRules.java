package com.jayemceekay.shadowedhearts.integration.rctmod;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.IShadowConfig.IRCTSection;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves current RCT configuration without changing the trainer entity or its saved traits. */
public final class RCTShadowRules {
    private static final String PRESET_PREFIX = "sh_shadow_presets/";
    private static final Result EMPTY = new Result(List.of(), List.of(), List.of(), text("no_rule"));

    private RCTShadowRules() {}

    public record Result(List<String> defaults, List<String> tags, List<Component> diagnostics, Component description) {
        public Result {
            defaults = List.copyOf(defaults);
            tags = List.copyOf(tags);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public static Result resolve(Entity entity) {
        if (entity == null) return EMPTY;
        var bridge = RCTBridgeHolder.INSTANCE;
        if (!bridge.isTrainer(entity)) return EMPTY;
        try {
            var config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
            if (!config.rctIntegrationEnabled()) {
                return new Result(List.of(), List.of(), List.of(), text("integration_disabled"));
            }
            var identity = bridge.trainerIdentity(entity);
            if (identity == null) {
                List<Component> diagnostics = bridge.diagnostics();
                if (diagnostics.isEmpty()) diagnostics = List.of(text("identity_unavailable"));
                return new Result(List.of(), List.of(), diagnostics, text("unresolved"));
            }
            return select(identity, config.replace(), config.append(), config.convert());
        } catch (RuntimeException | LinkageError failure) {
            Shadowedhearts.LOGGER.warn("Could not resolve Shadowed Hearts RCT trainer rules", failure);
            return new Result(List.of(), List.of(), List.of(text("resolution_failed")), text("unresolved"));
        }
    }

    /** Pure selector: explicit trainers outrank every type rule; section order breaks remaining ties. */
    static Result select(RCTBridge.TrainerIdentity identity, IRCTSection replace, IRCTSection append, IRCTSection convert) {
        String trainerId = normalized(identity.id());
        String typeId = normalized(identity.type());
        List<Section> sections = List.of(new Section("replace", replace), new Section("append", append),
                new Section("convert", convert));
        List<String> excluded = new ArrayList<>();

        for (Section section : sections) {
            if (section.config == null) continue;
            if (contains(section.config.trainerBlacklist(), trainerId)) {
                excluded.add(section.name);
                continue;
            }
            for (String raw : strings(section.config.trainers())) {
                if (raw == null) continue;
                String[] fields = raw.split(";", -1);
                if (trainerId.isEmpty() || !trainerId.equals(normalized(fields[0]))) continue;
                List<Component> diagnostics = new ArrayList<>();
                List<String> tags = new ArrayList<>();
                if (fields.length > 3) diagnostics.add(text("trainer_extra_fields", section.name, trainerId));
                if (fields.length > 1 && !fields[1].isBlank()) addPreset(fields[1], tags, diagnostics);
                if (fields.length > 2) {
                    for (String rawTag : fields[2].split(",", -1)) {
                        String tag = normalized(rawTag);
                        if (tag.isEmpty()) continue;
                        if (tag.startsWith(PRESET_PREFIX)) {
                            addPreset(tag.substring(PRESET_PREFIX.length()), tags, diagnostics);
                        } else {
                            tags.add(tag);
                        }
                    }
                }
                return selected(section.name, tags, diagnostics, text("matched_trainer", section.name, trainerId));
            }
        }

        for (Section section : sections) {
            if (section.config == null || excluded.contains(section.name) || typeId.isEmpty()) continue;
            for (String raw : strings(section.config.typePresets())) {
                if (raw == null) continue;
                String[] fields = raw.split("=", 2);
                if (!typeId.equals(normalized(fields[0]))) continue;
                List<String> tags = new ArrayList<>();
                List<Component> diagnostics = new ArrayList<>();
                if (fields.length < 2) diagnostics.add(text("type_missing_preset", section.name, typeId));
                else addPreset(fields[1], tags, diagnostics);
                return selected(section.name, tags, diagnostics, text("matched_type", section.name, typeId, trainerId));
            }
            if (contains(section.config.trainerTypes(), typeId)) {
                return selected(section.name, List.of(), List.of(), text("matched_type", section.name, typeId, trainerId));
            }
        }
        Component description = excluded.isEmpty() ? text("no_match", trainerId, typeId)
                : text("no_match_excluded", trainerId, typeId, String.join(", ", excluded));
        return new Result(List.of(), List.of(), List.of(), description);
    }

    private static Result selected(String mode, List<String> tags, List<Component> diagnostics, Component description) {
        return new Result(List.of("sh_shadow_party", "sh_shadow_n1", "sh_shadow_mode_" + mode), tags,
                diagnostics, description);
    }

    private static void addPreset(String raw, List<String> tags, List<Component> diagnostics) {
        String id = canonicalPreset(raw);
        if (id == null) diagnostics.add(text("invalid_preset", raw.trim()));
        else tags.add(PRESET_PREFIX + id);
    }

    private static Component text(String key, Object... arguments) {
        return TrainerEditorMessages.message("rct.rules." + key, arguments);
    }

    /** Accepts namespace:path, namespace/path, or a bare Shadowed Hearts preset ID. */
    static String canonicalPreset(String raw) {
        String value = normalized(raw);
        if (value.startsWith(PRESET_PREFIX)) value = value.substring(PRESET_PREFIX.length());
        int separator = value.indexOf(':');
        if (separator < 0) separator = value.indexOf('/');
        String namespace = separator < 0 ? "shadowedhearts" : value.substring(0, separator);
        String path = separator < 0 ? value : value.substring(separator + 1);
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9/._-]+")) return null;
        if (namespace.equals(".") || namespace.equals("..")) return null;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return null;
        }
        return namespace + "/" + path;
    }

    private static boolean contains(List<? extends String> values, String expected) {
        return !expected.isEmpty() && strings(values).stream().anyMatch(value -> expected.equals(normalized(value)));
    }

    private static List<? extends String> strings(List<? extends String> values) {
        return values == null ? List.of() : values;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Section(String name, IRCTSection config) {}
}
