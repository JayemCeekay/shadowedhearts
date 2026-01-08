package com.jayemceekay.shadowedhearts.config;

public interface ITrainerSpawnConfig extends IModConfig {
    default int spawnChancePercent() { return 75; }
    default int maxPerPlayer() { return 2; }
    default int radiusMin() { return 12; }
    default int radiusMax() { return 32; }
    default int avgPartyLevel() { return 25; }
    default int partySizeMin() { return 3; }
    default int partySizeMax() { return 6; }
    default int shadow1Percent() { return 20; }
    default int shadow2Percent() { return 3; }
    default int spawnIntervalTicks() { return 400; }
    default int despawnAfterTicks() { return 6000; }
}
