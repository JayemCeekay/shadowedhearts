package com.jayemceekay.shadowedhearts.config;

public interface ISnagConfig extends IModConfig {
    default int energyPerAttempt() { return 50; }
    default int toggleCooldownTicks() { return 20; }

    // Recharge
    default boolean rechargeOnVictory() { return true; }
    default boolean rechargeInPvp() { return false; }
    default int rechargeBase() { return 10; }
    default double rechargePerLevel() { return 0.25; }
    default int rechargePerNpc() { return 3; }
    default int rechargeMin() { return 5; }
    default int rechargeMax() { return 15; }
}
