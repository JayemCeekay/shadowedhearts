package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Optional original-game pacing; defaults preserve the mod's existing accounting. */
public final class OrreProgressionConfig {
    public enum WalkingAccounting { PLAYER_SESSION, POKEMON_PERSISTENT }

    public static final OrreProgressionConfig INSTANCE = new OrreProgressionConfig();
    private ModConfigSpec.EnumValue<WalkingAccounting> walkingAccounting;
    private ModConfigSpec ownerSpec;

    OrreProgressionConfig() {}

    void build(ModConfigSpec.Builder builder) {
        builder.translation("shadowedhearts.configuration.orreProgression").push("orreProgression");
        walkingAccounting = builder.comment("PLAYER_SESSION preserves the shared party cadence. POKEMON_PERSISTENT stores each Shadow Pokemon's partial walking cycle across party changes, storage and relogs. Teleport rejection and chamber steps are unchanged.")
                .translation("shadowedhearts.configuration.orreProgression.walkingAccounting")
                .defineEnum("walkingAccounting", WalkingAccounting.PLAYER_SESSION);
        builder.pop();
    }

    void bind(ModConfigSpec spec) { ownerSpec = spec; }
    private boolean loaded() { return ownerSpec != null && ownerSpec.isLoaded(); }
    public WalkingAccounting walkingAccounting() {
        return loaded() ? walkingAccounting.get() : WalkingAccounting.PLAYER_SESSION;
    }
}
