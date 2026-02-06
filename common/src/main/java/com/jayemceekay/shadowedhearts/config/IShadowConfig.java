package com.jayemceekay.shadowedhearts.config;

import java.util.List;

public interface IShadowConfig extends IModConfig {
    default double shadowSpawnChancePercent() { return 0.78125; }
    default List<? extends String> shadowSpawnBlacklist() { return List.of("#shadowedhearts:legendaries", "#shadowedhearts:mythical"); }

    // Hyper Mode
    default boolean hyperModeEnabled() { return true; }

    // Reverse Mode
    default boolean reverseModeEnabled() { return true; }

    // GO Damage Modifier
    default boolean goDamageModifierEnabled() { return false; }

    // Call Button
    default boolean callButtonReducesHeartGauge() { return true; }
    default boolean callButtonAccuracyBoost() { return true; }
    default boolean callButtonRemoveSleep() { return true; }

    // Scent
    default int scentCooldownSeconds() { return 300; }
    default boolean expandedScentSystemEnabled() { return true; }

    // Shadow Moves
    default boolean superEffectiveShadowMovesEnabled() { return true; }
    default String shadowMovesReplaceCount() { return "1"; }
    default boolean shadowMovesOnlyShadowRush() { return false; }

    // Shadow Stat Changes
    default String shadowIVMode() { return "SCALED"; }
    default int shadowFixedPerfectIVs() { return 3; }
    default int shadowMaxPerfectIVs() { return 5; }

    // Catch Rate
    default boolean shadowCatchRateScaleEnabled() { return true; }
    default double shadowCatchRateMinMultiplier() { return 0.25; }
    default double shadowCatchRateExponent() { return 1.4; }

    // RCT Integration
    default boolean rctIntegrationEnabled() { return false; }

    // Relic Stone
    default int relicStoneCooldownMinutes() { return 5; }

    // Purification Chamber
    default int purificationChamberStepRequirement() { return 161; }

    // Aura Scanner
    default int auraScannerShadowRange() { return 64; }
    default int auraScannerMeteoroidRange() { return 128; }

    IRCTSection append();
    IRCTSection convert();
    IRCTSection replace();

    IWorldAlterationConfig worldAlteration();

    interface IRCTSection {
        List<? extends String> trainerTypes();
        List<? extends String> typePresets();
        List<? extends String> trainerBlacklist();
        List<? extends String> trainers();
    }
}
