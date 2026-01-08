package com.jayemceekay.shadowedhearts.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config for spawning overworld NPC trainers near players using ModConfigSpec.
 */
public final class TrainerSpawnConfig implements ITrainerSpawnConfig {
    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        SPEC = builder.build();
    }

    @Override
    public int spawnChancePercent() {
        return DATA.spawnChancePercent.get();
    }

    @Override
    public int maxPerPlayer() {
        return DATA.maxPerPlayer.get();
    }

    @Override
    public int radiusMin() {
        return DATA.radiusMin.get();
    }

    @Override
    public int radiusMax() {
        return DATA.radiusMax.get();
    }

    @Override
    public int avgPartyLevel() {
        return DATA.avgPartyLevel.get();
    }

    @Override
    public int partySizeMin() {
        return DATA.partySizeMin.get();
    }

    @Override
    public int partySizeMax() {
        return DATA.partySizeMax.get();
    }

    @Override
    public int shadow1Percent() {
        return DATA.shadow1Percent.get();
    }

    @Override
    public int shadow2Percent() {
        return DATA.shadow2Percent.get();
    }

    @Override
    public int spawnIntervalTicks() {
        return DATA.spawnIntervalTicks.get();
    }

    @Override
    public int despawnAfterTicks() {
        return DATA.despawnAfterTicks.get();
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    private static final class Data {
        public ModConfigSpec.IntValue spawnChancePercent;
        public ModConfigSpec.IntValue maxPerPlayer;
        public ModConfigSpec.IntValue radiusMin;
        public ModConfigSpec.IntValue radiusMax;
        public ModConfigSpec.IntValue avgPartyLevel;
        public ModConfigSpec.IntValue partySizeMin;
        public ModConfigSpec.IntValue partySizeMax;
        public ModConfigSpec.IntValue shadow1Percent;
        public ModConfigSpec.IntValue shadow2Percent;
        public ModConfigSpec.IntValue spawnIntervalTicks;
        public ModConfigSpec.IntValue despawnAfterTicks;

        private void build(ModConfigSpec.Builder builder) {
            spawnChancePercent = builder.defineInRange("spawn_chance_percent", 75, 0, 100);
            maxPerPlayer = builder.defineInRange("max_per_player", 2, 0, 32);
            radiusMin = builder.defineInRange("radius_min", 12, 1, 256);
            radiusMax = builder.defineInRange("radius_max", 32, 1, 512);
            avgPartyLevel = builder.defineInRange("avg_party_level", 25, 1, 100);
            partySizeMin = builder.defineInRange("party_size_min", 3, 1, 6);
            partySizeMax = builder.defineInRange("party_size_max", 6, 1, 6);
            shadow1Percent = builder.defineInRange("shadow1_percent", 20, 0, 100);
            shadow2Percent = builder.defineInRange("shadow2_percent", 3, 0, 100);
            spawnIntervalTicks = builder.defineInRange("spawn_interval_ticks", 400, 20, 20000);
            despawnAfterTicks = builder.defineInRange("despawn_after_ticks", 6000, 200, 120000);
        }
    }

    @Override
    public void load() {
        loaded = true;
        System.out.println("[ShadowedHearts] TrainerSpawnConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
