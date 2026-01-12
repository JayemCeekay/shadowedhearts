package com.jayemceekay.shadowedhearts.config;

import dev.architectury.platform.Platform;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Server/Common config system for Shadowed Hearts using ModConfigSpec.
 */
public final class ModConfig implements IShadowConfig {
    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        SPEC = builder.build();
    }

    @Override
    public double shadowSpawnChancePercent() {
        return DATA.shadowSpawnChancePercent.get();
    }

    @Override
    public List<? extends String> shadowSpawnBlacklist() {
        return DATA.shadowSpawnBlacklist.get();
    }

    @Override
    public boolean hyperModeEnabled() {
        return DATA.hyperMode.enabled.get();
    }

    @Override
    public boolean reverseModeEnabled() {
        return DATA.reverseMode.enabled.get();
    }

    @Override
    public boolean callButtonReducesHeartGauge() {
        return DATA.callButton.reducesHeartGauge.get();
    }

    @Override
    public boolean callButtonAccuracyBoost() {
        return DATA.callButton.accuracyBoost.get();
    }

    @Override
    public boolean callButtonRemoveSleep() {
        return DATA.callButton.removeSleep.get();
    }

    @Override
    public int scentCooldownSeconds() {
        return DATA.scent.cooldownSeconds.get();
    }

    @Override
    public String shadowMovesReplaceCount() {
        return DATA.shadowMoves.replaceCount.get();
    }

    @Override
    public boolean shadowMovesOnlyShadowRush() {
        return DATA.shadowMoves.onlyShadowRush.get();
    }

    @Override
    public boolean rctIntegrationEnabled() {
        return DATA.rctIntegration.enabled.get();
    }

    @Override
    public int relicStoneCooldownMinutes() {
        return DATA.relicStone.cooldownMinutes.get();
    }

    @Override
    public IRCTSection append() {
        return DATA.rctIntegration.append;
    }

    @Override
    public IRCTSection convert() {
        return DATA.rctIntegration.convert;
    }

    @Override
    public IRCTSection replace() {
        return DATA.rctIntegration.replace;
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    private static final class Data {
        public ModConfigSpec.DoubleValue shadowSpawnChancePercent;
        public ModConfigSpec.ConfigValue<List<? extends String>> shadowSpawnBlacklist;

        public final HyperModeConfig hyperMode = new HyperModeConfig();
        public final ReverseModeConfig reverseMode = new ReverseModeConfig();
        public final CallButtonConfig callButton = new CallButtonConfig();
        public final ScentConfig scent = new ScentConfig();
        public final ShadowMovesConfig shadowMoves = new ShadowMovesConfig();
        public final RelicStoneConfig relicStone = new RelicStoneConfig();
        public final RCTIntegrationConfig rctIntegration = new RCTIntegrationConfig();

        private void build(ModConfigSpec.Builder builder) {
            builder.push("hyperMode");
            hyperMode.build(builder);
            builder.pop();

            builder.push("reverseMode");
            reverseMode.build(builder);
            builder.pop();

            builder.push("callButton");
            callButton.build(builder);
            builder.pop();

            builder.push("scent");
            scent.build(builder);
            builder.pop();

            builder.push("shadowMoves");
            shadowMoves.build(builder);
            builder.pop();

            builder.push("relicStone");
            relicStone.build(builder);
            builder.pop();

            builder.push("shadowSpawn");
            shadowSpawnChancePercent = builder
                    .comment("The percentage chance (0.0-100.0) for a wild Pokémon to spawn as a Shadow Pokémon.")
                    .defineInRange("chancePercent", 0.78125, 0.0, 100.0);

            shadowSpawnBlacklist = builder
                    .comment("List of Pokémon species or tags that cannot spawn as Shadow Pokémon.")
                    .defineList("blacklist", List.of("#shadowedhearts:legendaries", "#shadowedhearts:mythical"), o -> o instanceof String);
            builder.pop();

            builder.push("modIntegrations");
            builder.push("rctmod");
            rctIntegration.build(builder);
            builder.pop();
            builder.pop();
        }
    }

    public static final class HyperModeConfig {
        public ModConfigSpec.BooleanValue enabled;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Hyper Mode mechanics are enabled.")
                    .define("enabled", true);
        }
    }

    public static final class ReverseModeConfig {
        public ModConfigSpec.BooleanValue enabled;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Reverse Mode mechanics are enabled.")
                    .define("enabled", true);
        }
    }

    public static final class CallButtonConfig {
        public ModConfigSpec.BooleanValue reducesHeartGauge;
        public ModConfigSpec.BooleanValue accuracyBoost;
        public ModConfigSpec.BooleanValue removeSleep;

        private void build(ModConfigSpec.Builder builder) {
            reducesHeartGauge = builder
                    .comment("If true, the Call button in battle reduces the Heart Gauge of Shadow Pokémon when snapping them out of Hyper Mode or Reverse Mode.")
                    .define("reducesHeartGauge", true);
            accuracyBoost = builder
                    .comment("If true, the Call button provides an accuracy boost to the Pokémon if they are not in Hyper Mode or Reverse Mode.")
                    .define("accuracyBoost", true);
            removeSleep = builder
                    .comment("If true, the Call button can wake up a sleeping Pokémon.")
                    .define("removeSleep", true);
        }
    }

    public static final class ScentConfig {
        public ModConfigSpec.IntValue cooldownSeconds;

        private void build(ModConfigSpec.Builder builder) {
            cooldownSeconds = builder
                    .comment("Cooldown in seconds between using Scent items on a Pokémon.")
                    .defineInRange("cooldownSeconds", 300, 0, Integer.MAX_VALUE);
        }
    }

    public static final class ShadowMovesConfig {
        public ModConfigSpec.ConfigValue<String> replaceCount;
        public ModConfigSpec.BooleanValue onlyShadowRush;

        private void build(ModConfigSpec.Builder builder) {
            replaceCount = builder
                    .comment("How many moves to replace with Shadow moves. Supports single values (e.g. '1') or ranges (e.g. '1-3').")
                    .define("replaceCount", "1");
            onlyShadowRush = builder
                    .comment("If true, only 'Shadow Rush' will be assigned, even if replaceCount > 1.")
                    .define("onlyShadowRush", false);
        }
    }

    public static final class RelicStoneConfig {
        public ModConfigSpec.IntValue cooldownMinutes;

        private void build(ModConfigSpec.Builder builder) {
            cooldownMinutes = builder
                    .comment("Cooldown in minutes between using the Relic Stone's purification function.")
                    .defineInRange("cooldownMinutes", 5, 0, Integer.MAX_VALUE);
        }
    }

    public static final class RCTIntegrationConfig {
        public ModConfigSpec.BooleanValue enabled;
        public RCTSection append;
        public RCTSection convert;
        public RCTSection replace;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Enables integration with Radical Cobblemon Trainers.")
                    .define("enabled", Platform.isModLoaded("rctmod"));

            builder.push("append");
            append = new RCTSection();
            append.build(builder);
            builder.pop();

            builder.push("convert");
            convert = new RCTSection();
            convert.build(builder);
            builder.pop();

            builder.push("replace");
            replace = new RCTSection(true);
            replace.build(builder);
            builder.pop();
        }
    }

    public static final class RCTSection implements IRCTSection {
        public ModConfigSpec.ConfigValue<List<? extends String>> trainerTypes;
        public ModConfigSpec.ConfigValue<List<? extends String>> typePresets; // We'll store as key=value strings
        public ModConfigSpec.ConfigValue<List<? extends String>> trainerBlacklist;
        public ModConfigSpec.ConfigValue<List<? extends String>> trainers; // We'll store as JSON strings or key=value

        private final boolean isDefaultReplace;

        public RCTSection() {
            this(false);
        }

        public RCTSection(boolean isDefaultReplace) {
            this.isDefaultReplace = isDefaultReplace;
        }

        private void build(ModConfigSpec.Builder builder) {
            trainerTypes = builder.defineList("trainerTypes",
                    isDefaultReplace ? List.of("team_rocket") : Collections.emptyList(),
                    o -> o instanceof String);

            typePresets = builder
                    .comment("Format: type=presetId")
                    .defineList("typePresets",
                            isDefaultReplace ? List.of("team_rocket=shadowedhearts/team_rocket") : Collections.emptyList(),
                            o -> o instanceof String && ((String) o).contains("="));

            trainerBlacklist = builder.defineList("trainerBlacklist", Collections.emptyList(), o -> o instanceof String);

            trainers = builder
                    .comment("Format: id;preset;tag1,tag2...")
                    .defineList("trainers",
                            isDefaultReplace ? List.of("team_rocket;shadowedhearts/team_rocket;") : Collections.emptyList(),
                            o -> o instanceof String);
        }

        @Override
        public List<? extends String> trainerTypes() {
            return trainerTypes.get();
        }

        @Override
        public List<? extends String> typePresets() {
            return typePresets.get();
        }

        @Override
        public List<? extends String> trainerBlacklist() {
            return trainerBlacklist.get();
        }

        @Override
        public List<? extends String> trainers() {
            return trainers.get();
        }
    }

    @Override
    public void load() {
        loaded = true;
        System.out.println("[ShadowedHearts] ModConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    public static int resolveReplaceCount(Random rng) {
        IShadowConfig cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        String raw = cfg.shadowMovesReplaceCount();
        if (raw == null || raw.isBlank()) return 1;
        try {
            if (raw.contains("-")) {
                String[] parts = raw.split("-");
                if (parts.length == 2) {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    if (max < min) {
                        int temp = min;
                        min = max;
                        max = temp;
                    }
                    return min + rng.nextInt(max - min + 1);
                }
            }
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return 1;
        }
    }
}
