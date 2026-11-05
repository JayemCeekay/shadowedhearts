package com.jayemceekay.shadowedhearts.config;

public interface ISnagConfig extends IModConfig {
    default int toggleCooldownTicks() { return 20; }
    default boolean deferDeliveryUntilVictory() { return false; }

    // Snagging Pity
    default boolean failStackingBonus() { return true; }
    default double failBonusPerAttempt() { return 0.05; }
    default double maxFailBonus() { return 0.25; }
}
