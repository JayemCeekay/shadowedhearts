package com.jayemceekay.shadowedhearts.config;

import java.util.List;

public interface IWorldAlterationConfig {
    default int minCraterRadius() { return 4; }
    default int maxCraterRadius() { return 12; }
    default boolean meteoroidWorldGenEnabled() { return true; }
    default int meteoroidSpacing() { return 16; }
    default int meteoroidSeparation() { return 8; }
    default java.util.List<? extends String> meteoroidBiomeBlacklist() { return List.of("#minecraft:is_river", "#minecraft:is_beach"); }
    default java.util.List<? extends String> meteoroidBiomeWhitelist() { return java.util.Collections.emptyList(); }

    default double meteoroidShadowSpawnChanceMultiplier() { return 5.0; }
}
