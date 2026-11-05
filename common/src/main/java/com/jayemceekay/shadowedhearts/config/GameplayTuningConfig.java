package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative feature and Heart Gauge pacing controls in common.toml. */
public final class GameplayTuningConfig {
    public static final GameplayTuningConfig INSTANCE = new GameplayTuningConfig();

    private ModConfigSpec.BooleanValue snaggingEnabled;
    private ModConfigSpec.BooleanValue relicStonePurificationEnabled;
    private ModConfigSpec.BooleanValue chamberPurificationEnabled;
    private ModConfigSpec.DoubleValue battleMultiplier, callMultiplier, walkingMultiplier, scentMultiplier, chamberMultiplier;
    private ModConfigSpec.IntValue partyStepInterval;
    private ModConfigSpec ownerSpec;

    GameplayTuningConfig() {}

    void build(ModConfigSpec.Builder builder) {
        builder.translation("shadowedhearts.configuration.gameplayTuning").push("gameplayTuning");
        builder.translation("shadowedhearts.configuration.gameplayTuning.features").push("features");
        snaggingEnabled = builder.comment("Allow new NPC Shadow snag attempts. Does not affect ordinary wild captures.")
                .translation("shadowedhearts.configuration.gameplayTuning.features.snaggingEnabled")
                .define("snaggingEnabled", true);
        relicStonePurificationEnabled = builder.comment("Allow Relic Stones to purify ready Pokemon. Shard-to-gem exchanges remain available.")
                .translation("shadowedhearts.configuration.gameplayTuning.features.relicStonePurificationEnabled")
                .define("relicStonePurificationEnabled", true);
        chamberPurificationEnabled = builder.comment("Allow chamber deposits, walking progress and final purification. Stored Pokemon can always be retrieved.")
                .translation("shadowedhearts.configuration.gameplayTuning.features.chamberPurificationEnabled")
                .define("chamberPurificationEnabled", true);
        builder.pop();
        builder.translation("shadowedhearts.configuration.gameplayTuning.progression").push("progression");
        battleMultiplier = multiplier(builder, "battleMultiplier", "Battle participation Heart Gauge reduction");
        callMultiplier = multiplier(builder, "callMultiplier", "Call Heart Gauge reduction; also requires callButton.reducesHeartGauge");
        walkingMultiplier = multiplier(builder, "walkingMultiplier", "Party walking Heart Gauge reduction, after held-item bonuses");
        scentMultiplier = multiplier(builder, "scentMultiplier", "Scent Heart Gauge reduction, after potency and nature affinity; ineffective doses are not consumed");
        chamberMultiplier = multiplier(builder, "chamberMultiplier", "Chamber Heart Gauge reduction, after support and perfect-set bonuses");
        partyStepInterval = builder.comment("Horizontal blocks of movement per party walking reduction. A cadence change preserves the fraction of the current cycle; teleport rejection remains unchanged.")
                .translation("shadowedhearts.configuration.gameplayTuning.progression.partyStepInterval")
                .defineInRange("partyStepInterval", 256, 1, 1_000_000);
        builder.pop(2);
    }

    private static ModConfigSpec.DoubleValue multiplier(ModConfigSpec.Builder builder, String key, String description) {
        return builder.comment(description + ". 1 preserves normal pacing; 0 disables this source. Results round to the nearest point.")
                .translation("shadowedhearts.configuration.gameplayTuning.progression." + key)
                .defineInRange(key, 1.0, 0.0, 100.0);
    }

    void bind(ModConfigSpec spec) { ownerSpec = spec; }

    private boolean loaded() { return ownerSpec != null && ownerSpec.isLoaded(); }

    public boolean snaggingEnabled() { return !loaded() || snaggingEnabled.get(); }
    public boolean relicStonePurificationEnabled() { return !loaded() || relicStonePurificationEnabled.get(); }
    public boolean chamberPurificationEnabled() { return !loaded() || chamberPurificationEnabled.get(); }
    public double battleMultiplier() { return loaded() ? battleMultiplier.get() : 1.0; }
    public double callMultiplier() { return loaded() ? callMultiplier.get() : 1.0; }
    public double walkingMultiplier() { return loaded() ? walkingMultiplier.get() : 1.0; }
    public double scentMultiplier() { return loaded() ? scentMultiplier.get() : 1.0; }
    public double chamberMultiplier() { return loaded() ? chamberMultiplier.get() : 1.0; }
    public int partyStepInterval() { return loaded() ? partyStepInterval.get() : 256; }
}
