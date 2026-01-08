package com.jayemceekay.shadowedhearts.config;

import java.util.List;

public interface IShadowConfig extends IModConfig {
    default double shadowSpawnChancePercent() { return 0.78125; }
    default List<? extends String> shadowSpawnBlacklist() { return List.of("#shadowedhearts:legendaries", "#shadowedhearts:mythical"); }

    // Hyper Mode
    default boolean hyperModeEnabled() { return true; }

    // Reverse Mode
    default boolean reverseModeEnabled() { return true; }

    // Call Button
    default boolean callButtonReducesHeartGauge() { return true; }
    default boolean callButtonAccuracyBoost() { return true; }
    default boolean callButtonRemoveSleep() { return true; }

    // Scent
    default int scentCooldownSeconds() { return 300; }

    // Shadow Moves
    default String shadowMovesReplaceCount() { return "1"; }
    default boolean shadowMovesOnlyShadowRush() { return false; }

    // RCT Integration
    default boolean rctIntegrationEnabled() { return false; }
    
    IRCTSection append();
    IRCTSection convert();
    IRCTSection replace();

    interface IRCTSection {
        List<? extends String> trainerTypes();
        List<? extends String> typePresets();
        List<? extends String> trainerBlacklist();
        List<? extends String> trainers();
    }
}
