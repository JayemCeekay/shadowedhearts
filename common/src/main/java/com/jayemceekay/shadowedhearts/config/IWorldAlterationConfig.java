package com.jayemceekay.shadowedhearts.config;

import java.util.List;

public interface IWorldAlterationConfig {
    default boolean meteoroidWorldGenEnabled() { return true; }
    default boolean umbrafallSignalsEnabled() { return true; }
    default UmbrafallSignalMode umbrafallSignalMode() { return UmbrafallSignalMode.LOCATE_OR_GENERATE; }
    default int umbrafallSignalLocateMinDistance() { return 64; }
    default int umbrafallSignalLocateMaxDistance() { return 640; }
    default int umbrafallSignalLifetimeSeconds() { return 3600; }
    default List<? extends String> umbrafallSignalAllowedTiers() { return List.of("faint", "weak", "moderate", "strong", "resonant"); }
    default int umbrafallWorkPerTick() { return 4096; }
    default int meteoroidSpacing() { return 16; }
    default int meteoroidSeparation() { return 8; }
    default java.util.List<? extends String> meteoroidBiomeBlacklist() { return List.of("#minecraft:is_river", "#minecraft:is_beach"); }
    default java.util.List<? extends String> meteoroidBiomeWhitelist() { return java.util.Collections.emptyList(); }

    default double meteoroidShadowSpawnChanceMultiplier() { return 5.0; }
}
