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
    default boolean goDamageModifierScaleWithHeartGauge() { return false; }

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
    default String shadowIVMode() { return "FIXED"; }
    default int shadowFixedPerfectIVs() { return 3; }
    default int shadowMaxPerfectIVs() { return 5; }

    // Catch Rate
    default boolean shadowCatchRateScaleEnabled() { return true; }
    default double shadowCatchRateMinMultiplier() { return 0.25; }
    default double shadowCatchRateExponent() { return 1.4; }

    // Penumbra Ball: total catch multipliers relative to a basic Poke Ball.
    default double penumbraBallCatchMultiplier() { return 1.0; }
    default double penumbraBallShadowCatchMultiplier() { return 1.5; }
    default double penumbraBallShadowConversionChance() { return 0.10; }
    default double penumbraBallHeartGaugeMaxBonus() { return 0.05; }

    // Antumbra Ball: total catch multipliers relative to a basic Poke Ball.
    default double antumbraBallCatchMultiplier() { return 1.5; }
    default double antumbraBallShadowCatchMultiplier() { return 2.0; }
    default double antumbraBallShadowConversionChance() { return 0.20; }
    default double antumbraBallHeartGaugeMaxBonus() { return 0.10; }

    // Umbra Ball: total catch multipliers relative to a basic Poke Ball.
    default double umbraBallCatchMultiplier() { return 2.0; }
    default double umbraBallShadowCatchMultiplier() { return 2.5; }
    // Chance to convert an ordinary Pokemon after a successful capture.
    default double umbraBallShadowConversionChance() { return 0.35; }
    // Fractional maximum Heart Gauge bonus when capturing an existing Shadow Pokemon.
    default double umbraBallHeartGaugeMaxBonus() { return 0.15; }

    // Dark Ball: successful capture rewards and corruption cost.
    default int darkBallLevelBoost() { return 5; }
    default double darkBallHeartGaugeMultiplier() { return 2.0; }

    // RCT Integration
    default boolean rctIntegrationEnabled() { return false; }
    default boolean limitExpToRCTCap() { return false; }

    // Relic Stone
    default int relicStoneCooldownMinutes() { return 5; }

    // Purification Chamber
    default int purificationChamberStepRequirement() { return 161; }

    // Aura Scanner
    default int auraScannerShadowRange() { return 128; }
    default int auraScannerMeteoroidRange() { return 256; }
    default int auraReaderPulseCooldownTicks() { return 40; }
    default int trailMinNodeDistance() { return 32; }
    default int trailMaxNodeDistance() { return 64; }
    default boolean auraReaderRequiredForAura() { return true; }
    default boolean expandedAuraReaderEnabled() { return true; }

    // Aura Lock (scanner lock to prevent despawn)
    default int auraLockMaxSeconds() { return 60; }
    default int auraLockRange() { return 128; }
    default boolean auraLockPersistsWhenAFK() { return false; }

    // Heart Gauge
    default List<? extends String> heartGaugeMaxOverrides() { return List.of(); }
    default boolean heartGaugeBufferExperience() { return true; }
    default boolean heartGaugeBufferEVs() { return true; }
    default boolean heartGaugeBlockFriendshipChanges() { return true; }
    default boolean heartGaugeBlockEvolution() { return true; }
    default boolean heartGaugeBlockNicknaming() { return true; }
    default boolean heartGaugeBlockTrading() { return true; }
    default boolean heartGaugeBlockMoveChanges() { return true; }
    default boolean heartGaugeBlockMoveTeaching() { return true; }
    default boolean heartGaugeHideNature() { return true; }
    default int heartGaugeNatureRevealPercent() { return 40; }
    default boolean heartGaugeHideLevelAndExperience() { return true; }
    default int heartGaugeLevelExpRevealPercent() { return 40; }
    default boolean heartGaugeHideEVs() { return true; }
    default int heartGaugeEvRevealPercent() { return 40; }
    default boolean heartGaugeHideIVs() { return true; }
    default int heartGaugeIvRevealPercent() { return 40; }
    default boolean heartGaugeRestrictNonShadowMoves() { return true; }
    default int heartGaugeFirstMoveUnlockPercent() { return 79; }
    default int heartGaugeSecondMoveUnlockPercent() { return 40; }
    default int heartGaugeThirdMoveUnlockPercent() { return 20; }
    default boolean heartGaugeExactThresholds() { return false; }
    default boolean heartGaugeBlockHyperTraining() { return false; }

    // Shadow Gimmick Restrictions (requires Mega Showdown)
    default boolean shadowCanMegaEvolve() { return false; }
    default boolean shadowCanDynamax() { return false; }
    default boolean shadowCanTerastallize() { return false; }
    default boolean shadowCanUseZMoves() { return false; }
    default boolean shadowCanUltraBurst() { return false; }

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
