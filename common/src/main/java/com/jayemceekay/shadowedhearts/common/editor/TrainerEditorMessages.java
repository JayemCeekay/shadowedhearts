package com.jayemceekay.shadowedhearts.common.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.Collection;

/** Display-only values; identifiers and authored properties never pass through this formatter. */
public final class TrainerEditorMessages {
    private TrainerEditorMessages() {}

    public static Component message(String key, Object... arguments) {
        return Component.translatable("screen.shadowedhearts.trainer_editor." + key, arguments);
    }

    public static Component field(String key) {
        // Native property names are case-sensitive data, not translation-key conventions.
        // In particular, a pool entry's Level differs from a profile's level policy.
        String label = switch (key) {
            case "Level" -> "entry_level";
            case "OriginalText" -> "original_properties";
            case "SpeciesText" -> "species";
            case "enforceEvo" -> "enforce_evolution_minimum";
            case "evolutionMinimum" -> "evolution_minimum";
            case "extraTags" -> "extra_tags";
            case "levelMode" -> "level_mode";
            case "levelValue" -> "level_value";
            case "enabled", "level", "tags", "entries", "kind", "mode", "count", "chance", "unique", "pool",
                    "preset", "section", "target", "excluded", "properties", "weight" -> key;
            default -> null;
        };
        return label == null ? Component.literal(key) : message("field." + label);
    }

    public static Component value(String value) {
        return Set.of("inherit", "true", "false", "none", "default", "convert", "append", "replace", "match", "fixed", "plus", "trainer", "type").contains(value)
                ? message("value." + value) : Component.literal(value);
    }

    public static Component source(String value) {
        String[] parts = value.split(" > preset ", -1);
        Component source = switch (parts[0]) {
            case "RCT defaults" -> message("source.rct_defaults");
            case "RCT rule" -> message("source.rct_rule");
            case "NPC tags/aspects" -> message("source.npc_tags");
            case "Default" -> message("source.default");
            case "Editor draft" -> message("source.editor_draft");
            case "runtime" -> message("source.runtime");
            case "datapack" -> message("source.datapack");
            case "entity" -> message("source.entity");
            case "server config" -> message("source.server_config");
            default -> parts[0].startsWith("Preset ")
                    ? message("source.preset_definition", parts[0].substring("Preset ".length()))
                    : Component.literal(parts[0]);
        };
        for (int i = 1; i < parts.length; i++) source = message("source.preset", source, parts[i]);
        return source;
    }

    public static Component join(Collection<? extends Component> messages) {
        var result = Component.empty();
        for (Component message : messages) {
            if (!result.getSiblings().isEmpty()) result.append("; ");
            result.append(message);
        }
        return result;
    }

    public static JsonElement json(Object value) {
        // These editor Components contain text and translation arguments, never registry-backed content.
        return value instanceof Component component ? com.google.gson.JsonParser.parseString(Component.Serializer.toJson(component, RegistryAccess.EMPTY))
                : new JsonPrimitive(String.valueOf(value));
    }
}
