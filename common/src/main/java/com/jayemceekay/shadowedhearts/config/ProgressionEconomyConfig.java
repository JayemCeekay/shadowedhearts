package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Optional earning and care rules. Compatibility defaults retain existing restriction switches. */
public final class ProgressionEconomyConfig {
    public enum FriendshipPolicy { FOLLOW_RESTRICTIONS, BLOCK, ALLOW, BUFFER }
    public enum EvPolicy { FOLLOW_RESTRICTIONS, ALLOW, BUFFER, DISCARD }
    public enum CareTable { XD, COLOSSEUM }

    public static final ProgressionEconomyConfig INSTANCE = new ProgressionEconomyConfig();
    private ModConfigSpec ownerSpec;
    private ModConfigSpec.IntValue experienceStartBelowPercent, friendshipStartBelowPercent,
            itemFriendshipStartBelowPercent, purificationFriendshipBonus;
    private ModConfigSpec.DoubleValue experienceMultiplier, friendshipMultiplier;
    private ModConfigSpec.EnumValue<FriendshipPolicy> friendshipPolicy;
    private ModConfigSpec.EnumValue<EvPolicy> battleEvPolicy, otherEvPolicy;
    private ModConfigSpec.EnumValue<CareTable> careTable;
    private ModConfigSpec.BooleanValue scentsClearModes;

    ProgressionEconomyConfig() {}

    void build(ModConfigSpec.Builder builder) {
        builder.translation(key("")).push("progressionEconomy");
        experienceStartBelowPercent = threshold(builder, "experienceStartBelowPercent",
                "Shadow Pokemon may earn EXP only below this remaining Heart Gauge percentage. Existing buffers are retained.");
        experienceMultiplier = multiplier(builder, "experienceMultiplier",
                "Multiplier for newly accepted Shadow EXP, applied after native PRE events. Does not rescale saved buffers.");
        friendshipPolicy = builder.comment("FOLLOW_RESTRICTIONS honors heartGauge.restrictions.blockFriendshipChanges. BUFFER defers accepted changes until purification; ALLOW applies them immediately; BLOCK rejects them.")
                .translation(key("friendshipPolicy")).defineEnum("friendshipPolicy", FriendshipPolicy.FOLLOW_RESTRICTIONS);
        friendshipStartBelowPercent = threshold(builder, "friendshipStartBelowPercent",
                "Remaining gauge threshold for positive friendship earned through walking and other non-item actions.");
        itemFriendshipStartBelowPercent = threshold(builder, "itemFriendshipStartBelowPercent",
                "Remaining gauge threshold for positive friendship from native Cobblemon friendship items. Integrations can use ShadowFriendshipSources.withItem.");
        friendshipMultiplier = multiplier(builder, "friendshipMultiplier",
                "Multiplier for new positive friendship in ALLOW/BUFFER modes. Fractional gains accumulate; losses remain unscaled.");
        purificationFriendshipBonus = builder.comment("Additional friendship awarded once when a Shadow Pokemon is purified, after releasing its saved friendship buffer.")
                .translation(key("purificationFriendshipBonus")).defineInRange("purificationFriendshipBonus", 0, 0, 1000);
        battleEvPolicy = evPolicy(builder, "battleEvPolicy", "EVs earned from native battle sources");
        otherEvPolicy = evPolicy(builder, "otherEvPolicy", "EVs earned from items and sidemods");
        careTable = builder.comment("Nature-based battle, Call, walking and classic scent reductions. XD preserves this mod's existing values. Chamber support mathematics is unchanged.")
                .translation(key("careTable")).defineEnum("careTable", CareTable.XD);
        scentsClearModes = builder.comment("Allow an owned Shadow Pokemon's scent treatment to clear Hyper/Reverse Mode, including at zero Heart Gauge. A useful calming treatment consumes one dose and starts the normal scent cooldown.")
                .translation(key("scentsClearModes")).define("scentsClearModes", false);
        builder.pop();
    }

    private static String key(String suffix) {
        return "shadowedhearts.configuration.progressionEconomy" + (suffix.isEmpty() ? "" : "." + suffix);
    }

    private static ModConfigSpec.IntValue threshold(ModConfigSpec.Builder builder, String name, String description) {
        return builder.comment(description, "Uses the exact meter: 60 means strictly below 60%; 101 removes the threshold; 0 disables positive earning.")
                .translation(key(name)).defineInRange(name, 101, 0, 101);
    }

    private static ModConfigSpec.DoubleValue multiplier(ModConfigSpec.Builder builder, String name, String description) {
        return builder.comment(description).translation(key(name)).defineInRange(name, 1.0, 0.0, 100.0);
    }

    private static ModConfigSpec.EnumValue<EvPolicy> evPolicy(ModConfigSpec.Builder builder, String name, String description) {
        return builder.comment(description + ". FOLLOW_RESTRICTIONS honors bufferEVs; ALLOW grants immediately, BUFFER defers, DISCARD accepts zero and does not consume ineffective native items.")
                .translation(key(name)).defineEnum(name, EvPolicy.FOLLOW_RESTRICTIONS);
    }

    void bind(ModConfigSpec spec) { ownerSpec = spec; }
    private boolean loaded() { return ownerSpec != null && ownerSpec.isLoaded(); }
    public int experienceStartBelowPercent() { return loaded() ? experienceStartBelowPercent.get() : 101; }
    public double experienceMultiplier() { return loaded() ? experienceMultiplier.get() : 1.0; }
    public FriendshipPolicy friendshipPolicy() { return loaded() ? friendshipPolicy.get() : FriendshipPolicy.FOLLOW_RESTRICTIONS; }
    public int friendshipStartBelowPercent() { return loaded() ? friendshipStartBelowPercent.get() : 101; }
    public int itemFriendshipStartBelowPercent() { return loaded() ? itemFriendshipStartBelowPercent.get() : 101; }
    public double friendshipMultiplier() { return loaded() ? friendshipMultiplier.get() : 1.0; }
    public int purificationFriendshipBonus() { return loaded() ? purificationFriendshipBonus.get() : 0; }
    public EvPolicy battleEvPolicy() { return loaded() ? battleEvPolicy.get() : EvPolicy.FOLLOW_RESTRICTIONS; }
    public EvPolicy otherEvPolicy() { return loaded() ? otherEvPolicy.get() : EvPolicy.FOLLOW_RESTRICTIONS; }
    public CareTable careTable() { return loaded() ? careTable.get() : CareTable.XD; }
    public boolean scentsClearModes() { return loaded() && scentsClearModes.get(); }
}
