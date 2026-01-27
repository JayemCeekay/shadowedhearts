package com.jayemceekay.shadowedhearts.config;

public interface ISnagConfig extends IModConfig {
    default int energyPerAttempt() { return 10; }
    default int toggleCooldownTicks() { return 20; }

    // Recharge
    default boolean rechargeOnVictory() { return true; }
    default boolean rechargeInPvp() { return false; }
    default int rechargeBase() { return 10; }
    default double rechargePerLevel() { return 0.25; }
    default int rechargePerNpc() { return 3; }
    default int rechargeMin() { return 5; }
    default int rechargeMax() { return 15; }

    // Aura Reader Recharge
    default boolean auraReaderRechargeOnVictory() { return true; }
    default boolean auraReaderRechargeInPvp() { return false; }
    default int auraReaderRechargeBase() { return 200; }
    default double auraReaderRechargePerLevel() { return 5.0; }
    default int auraReaderRechargePerNpc() { return 60; }
    default int auraReaderRechargeMin() { return 100; }
    default int auraReaderRechargeMax() { return 3000; }

    // Prototype Snag Machine
    default int prototypeCapacity() { return 150; }

    // Advanced Snag Machine
    default int advancedCapacity() { return 300; }

    // Snagging Pity
    default boolean failStackingBonus() { return true; }
    default double failBonusPerAttempt() { return 0.05; }
    default double maxFailBonus() { return 0.25; }
}
