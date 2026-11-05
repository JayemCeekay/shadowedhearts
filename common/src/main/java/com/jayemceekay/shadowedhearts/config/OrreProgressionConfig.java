package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Optional original-game pacing; defaults preserve the mod's existing accounting. */
public final class OrreProgressionConfig {
    public enum WalkingAccounting { PLAYER_SESSION, POKEMON_PERSISTENT }
    public enum GaugeCapacityProfile { MOD_DEFAULTS, COLOSSEUM, XD }

    public static final OrreProgressionConfig INSTANCE = new OrreProgressionConfig();
    private ModConfigSpec.EnumValue<WalkingAccounting> walkingAccounting;
    private ModConfigSpec.EnumValue<GaugeCapacityProfile> gaugeCapacityProfile;
    private ModConfigSpec ownerSpec;

    OrreProgressionConfig() {}

    void build(ModConfigSpec.Builder builder) {
        builder.translation("shadowedhearts.configuration.orreProgression").push("orreProgression");
        walkingAccounting = builder.comment("PLAYER_SESSION preserves the shared party cadence. POKEMON_PERSISTENT stores each Shadow Pokemon's partial walking cycle across party changes, storage and relogs. Teleport rejection and chamber steps are unchanged.")
                .translation("shadowedhearts.configuration.orreProgression.walkingAccounting")
                .defineEnum("walkingAccounting", WalkingAccounting.PLAYER_SESSION);
        gaugeCapacityProfile = builder.comment("Use original Colosseum or XD Heart Gauge capacities for their Shadow rosters, with mod capacities for other species. Per-Pokemon maxima and user heartGauge.maxOverrides/legacy overrides take precedence. Changing this does not rewrite saved gauges.")
                .translation("shadowedhearts.configuration.orreProgression.gaugeCapacityProfile")
                .defineEnum("gaugeCapacityProfile", GaugeCapacityProfile.MOD_DEFAULTS);
        builder.pop();
    }

    void bind(ModConfigSpec spec) { ownerSpec = spec; }
    private boolean loaded() { return ownerSpec != null && ownerSpec.isLoaded(); }
    public WalkingAccounting walkingAccounting() {
        return loaded() ? walkingAccounting.get() : WalkingAccounting.PLAYER_SESSION;
    }
    public GaugeCapacityProfile gaugeCapacityProfile() {
        return loaded() ? gaugeCapacityProfile.get() : GaugeCapacityProfile.MOD_DEFAULTS;
    }
}
