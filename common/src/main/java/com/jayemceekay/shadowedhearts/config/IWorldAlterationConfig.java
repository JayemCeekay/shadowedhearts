package com.jayemceekay.shadowedhearts.config;

public interface IWorldAlterationConfig {
    default boolean shadowfallActive() { return false; }
    default int impactChanceOneInTicks() { return 12000; } // Once every 10 mins on average
    default int civilizedHeatmapThreshold() { return 100; }
    default int heatmapDecayTicks() { return 1200; } // Decay every minute
    default double heatmapDecayAmount() { return 1.0; }
    default int minImpactDistanceToPlayer() { return 64; }
    default int maxImpactDistanceToPlayer() { return 256; }
    default int minImpactDistanceToStructures() { return 64; }
    default int minImpactDistanceToSpawn() { return 128; }
    default int minCraterRadius() { return 4; }
    default int maxCraterRadius() { return 12; }
    default boolean meteoroidWorldGenEnabled() { return true; }
    default int meteoroidSpacing() { return 26; }
    default int meteoroidSeparation() { return 11; }
    default java.util.List<? extends String> meteoroidBiomeBlacklist() { return java.util.Collections.emptyList(); }
    default java.util.List<? extends String> meteoroidBiomeWhitelist() { return java.util.Collections.emptyList(); }

    default int heatmapPresenceRadius() { return 2; }
    default int heatmapFlushIntervalTicks() { return 1200; }

    default int meteoroidImpactBroadcastRadius() { return 256; }

    default boolean meteoroidShadowTransformationEnabled() { return true; }
    default int meteoroidShadowTransformationRadius() { return 32; }
    default int meteoroidShadowTransformationCheckIntervalTicks() { return 100; }
    default double meteoroidShadowTransformationChancePerInterval() { return 0.05; }
    default double meteoroidShadowTransformationExposureIncrease() { return 1.0; }
    default double meteoroidShadowTransformationExposureDecay() { return 0.5; }
    default double meteoroidShadowSpawnChanceMultiplier() { return 5.0; }
}
