package com.jayemceekay.shadowedhearts.client.gui.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.jayemceekay.shadowedhearts.config.ClientConfig;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Player-facing topics can change independently of the paths used in config files and presets. */
final class SettingsLayout {
    private static final String PREFIX = "gui.shadowedhearts.settings.group.";
    private static final List<String> CLIENT_ORDER = List.of(
            "accessibility", "reader", "reader_layout", "auras", "capture_effects", "moonwound_display", "sound");
    private static final List<String> WORLD_ORDER = List.of(
            "sites", "signals", "lobbies", "excavation", "waves", "finales");

    private SettingsLayout() {}

    static Component title(String id) { return Component.translatable(PREFIX + id); }
    static Component description(String id) { return Component.translatable(PREFIX + id + ".description"); }

    static Component label(ModConfigSpec.ConfigValue<?> value) {
        String key = value.getSpec().getTranslationKey();
        String leaf = value.getPath().getLast();
        return key == null ? Component.literal(leaf) : Component.translatableWithFallback(key, leaf);
    }

    static Component optionDescription(ModConfigSpec.ConfigValue<?> value) {
        var spec = value.getSpec();
        String key = spec.getTranslationKey();
        String comment = spec.getComment() == null ? "" : spec.getComment();
        return key == null ? Component.literal(comment) : Component.translatableWithFallback(key + ".tooltip", comment);
    }

    static Component enumValue(Enum<?> value) {
        return Component.translatableWithFallback("gui.shadowedhearts.settings.value." + value.name().toLowerCase(Locale.ROOT),
                value.toString());
    }

    static Map<String, List<ModConfigSpec.ConfigValue<?>>> groups(ModConfigSpec spec) {
        List<String> order;
        Function<List<String>, String> topic;
        if (spec == ClientConfig.SPEC) {
            order = CLIENT_ORDER;
            topic = SettingsLayout::clientTopic;
        } else if (spec == ModConfig.SPEC) {
            order = GameplaySettingsLayout.ORDER;
            topic = GameplaySettingsLayout::group;
        } else {
            order = WORLD_ORDER;
            topic = SettingsLayout::worldTopic;
        }

        Map<String, List<ModConfigSpec.ConfigValue<?>>> groups = new LinkedHashMap<>();
        for (String id : order) groups.put(id, new ArrayList<>());
        collect(spec.getValues(), topic, groups);
        groups.values().removeIf(List::isEmpty);
        return groups;
    }

    private static void collect(UnmodifiableConfig section, Function<List<String>, String> topic,
                                Map<String, List<ModConfigSpec.ConfigValue<?>>> groups) {
        for (Object entry : section.valueMap().values()) {
            if (entry instanceof UnmodifiableConfig child) collect(child, topic, groups);
            else if (entry instanceof ModConfigSpec.ConfigValue<?> value)
                groups.computeIfAbsent(topic.apply(value.getPath()), ignored -> new ArrayList<>()).add(value);
        }
    }

    private static String clientTopic(List<String> path) {
        return switch (path.getFirst()) {
            case "accessibility" -> "accessibility";
            case "shadowAura", "enableShadowAura" -> "auras";
            case "auraReaderYOffset" -> "reader";
            case "auraReaderHud" -> switch (path.getLast()) {
                case "topInset", "bottomSafeMargin", "partyOverlayMargin", "rightOverlayMargin" -> "reader_layout";
                case "reducedMotion", "staticIntensity" -> "accessibility";
                default -> "reader";
            };
            case "snagVfx" -> path.getLast().equals("snagReducedMotion") ? "accessibility" : "capture_effects";
            case "darkBallVfx" -> "capture_effects";
            case "moonwoundObjective", "moonwoundPortal" -> "moonwound_display";
            case "sounds" -> "sound";
            default -> "other";
        };
    }

    private static String worldTopic(List<String> path) {
        String key = path.getLast();
        return switch (path.getFirst()) {
            case "worldAlteration" -> key.startsWith("umbrafallSignal") || key.equals("umbrafallWorkPerTick")
                    ? "signals" : "sites";
            case "moonwound" -> switch (key) {
                case "newEventsEnabled", "missingMemberGraceSeconds", "lobbyIdleTimeoutSeconds" -> "lobbies";
                case "shellExcavationTarget", "mantleExcavationTarget", "casingExcavationTarget",
                     "excavationPaceSeconds" -> "excavation";
                case "shellWaveOpponentsPerMember", "mantleWaveOpponentsPerMember", "casingWaveOpponentsPerMember",
                     "strongWaveLevel", "resonantWaveLevel", "wavePaceSecondsPerOpponent" -> "waves";
                case "strongAlphaLevel", "resonantAlphaLevel", "raidBossLevel" -> "finales";
                default -> "other";
            };
            default -> "other";
        };
    }
}
