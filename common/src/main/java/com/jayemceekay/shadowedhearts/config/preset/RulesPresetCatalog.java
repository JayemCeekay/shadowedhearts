package com.jayemceekay.shadowedhearts.config.preset;

import com.jayemceekay.shadowedhearts.common.aura.AuraStyleDefault;
import com.jayemceekay.shadowedhearts.config.ShadowBattleRulesConfig;
import com.jayemceekay.shadowedhearts.config.ProgressionEconomyConfig;
import com.jayemceekay.shadowedhearts.config.OrreProgressionConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** One-shot, explicitly applied templates. Loading this catalog never loads or changes a config. */
public final class RulesPresetCatalog {
    public record Preset(String id, String titleKey, String descriptionKey, String notesKey,
                         Map<String, Object> values) {
        public Preset {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private static final List<Preset> PRESETS = List.of(
            preset("colosseum", rules(false, false)),
            preset("xd", rules(true, false)),
            preset("go", rules(false, true)),
            preset("mod_defaults", Map.of()));
    private static final Set<String> MANAGED_PATHS = Collections.unmodifiableSet(
            new LinkedHashSet<>(PRESETS.getFirst().values().keySet()));

    private RulesPresetCatalog() {}

    public static List<Preset> all() { return PRESETS; }
    public static Optional<Preset> find(String id) {
        return PRESETS.stream().filter(preset -> preset.id().equals(id)).findFirst();
    }
    public static Set<String> managedPaths() { return MANAGED_PATHS; }

    private static Preset preset(String id, Map<String, Object> values) {
        String key = "gui.shadowedhearts.rules_presets." + id;
        return new Preset(id, key + ".title", key + ".description", key + ".notes", values);
    }

    private static Map<String, Object> rules(boolean xd, boolean go) {
        var values = new LinkedHashMap<String, Object>();
        values.put("shadowAuraStyle", go ? AuraStyleDefault.SIGNATURE : xd ? AuraStyleDefault.XD : AuraStyleDefault.COLOSSEUM);
        values.put("hyperMode.enabled", !xd && !go);
        values.put("reverseMode.enabled", xd);
        values.put("goDamageModifier.enabled", go);
        values.put("goDamageModifier.scaleWithHeartGauge", false);
        values.put("callButton.reducesHeartGauge", !go);
        values.put("callButton.accuracyBoost", xd);
        values.put("callButton.removeSleep", !go);
        values.put("scent.expandedSystemEnabled", false);
        values.put("scent.cooldownSeconds", 0);
        values.put("shadowMoves.superEffectiveEnabled", xd);
        values.put("shadowMoves.replaceCount", go ? "0" : xd ? "2-4" : "1");
        values.put("shadowMoves.onlyShadowRush", !xd && !go);
        values.put("shadowMoves.preferLostLoreShadowMoves", false);
        values.put("shadowBattleRules.shadowRushVariant", !xd && !go
                ? ShadowBattleRulesConfig.ShadowRushVariant.COLOSSEUM : ShadowBattleRulesConfig.ShadowRushVariant.MODERN);
        values.put("shadowBattleRules.unlimitedShadowPP", !go);
        values.put("shadowBattleRules.hyperShadowRushCrit", !xd && !go);
        values.put("shadowBattleRules.hyperBehavior", !xd && !go
                ? ShadowBattleRulesConfig.HyperBehavior.COLOSSEUM : ShadowBattleRulesConfig.HyperBehavior.MODERN);
        values.put("shadowBattleRules.reverseEntry", xd
                ? ShadowBattleRulesConfig.ReverseEntry.XD_SPECIES : ShadowBattleRulesConfig.ReverseEntry.NATURE);
        values.put("shadowBattleRules.hyperItemRefusal", !xd && !go);
        values.put("shadowBattleRules.reverseItemRefusal", xd);
        values.put("moveLifecycle.retainShadowMovesUntilPurification", !go);
        values.put("moveLifecycle.goMoves", go);
        values.put("moveLifecycle.frustrationRemovalAllowed", false);

        values.put("progressionEconomy.experienceStartBelowPercent", !xd && !go ? 60 : 101);
        values.put("progressionEconomy.experienceMultiplier", xd ? 0.8 : 1.0);
        values.put("progressionEconomy.friendshipPolicy", go ? ProgressionEconomyConfig.FriendshipPolicy.FOLLOW_RESTRICTIONS
                : ProgressionEconomyConfig.FriendshipPolicy.BUFFER);
        values.put("progressionEconomy.friendshipStartBelowPercent", go ? 101 : xd ? 80 : 60);
        values.put("progressionEconomy.itemFriendshipStartBelowPercent", go ? 101 : 60);
        values.put("progressionEconomy.friendshipMultiplier", xd ? 0.8 : 1.0);
        values.put("progressionEconomy.purificationFriendshipBonus", go ? 0 : 70);
        values.put("progressionEconomy.battleEvPolicy", go ? ProgressionEconomyConfig.EvPolicy.FOLLOW_RESTRICTIONS
                : ProgressionEconomyConfig.EvPolicy.DISCARD);
        values.put("progressionEconomy.otherEvPolicy", go ? ProgressionEconomyConfig.EvPolicy.FOLLOW_RESTRICTIONS
                : ProgressionEconomyConfig.EvPolicy.ALLOW);
        values.put("progressionEconomy.careTable", !xd && !go ? ProgressionEconomyConfig.CareTable.COLOSSEUM
                : ProgressionEconomyConfig.CareTable.XD);
        values.put("progressionEconomy.scentsClearModes", !go);
        values.put("orreProgression.walkingAccounting", go
                ? OrreProgressionConfig.WalkingAccounting.PLAYER_SESSION
                : OrreProgressionConfig.WalkingAccounting.POKEMON_PERSISTENT);
        values.put("orreProgression.gaugeCapacityProfile", go
                ? OrreProgressionConfig.GaugeCapacityProfile.MOD_DEFAULTS
                : xd ? OrreProgressionConfig.GaugeCapacityProfile.XD : OrreProgressionConfig.GaugeCapacityProfile.COLOSSEUM);

        values.put("compatibilityItems.darkBallCorruptionEnabled", false);
        values.put("compatibilityItems.nullCharmCancelsGoBonus", false);
        values.put("compatibilityItems.nullCharmWardEnabled", false);
        values.put("compatibilityItems.nullCharmDamageGuardEnabled", false);
        values.put("compatibilityItems.expandedScentItemsEnabled", false);
        values.put("compatibilityItems.shadowTMsEnabled", false);
        for (String effect : List.of("shadowGem", "rivenOrb", "shadowCollar", "emWaveAmplifier",
                "dissonanceBand", "breachLens", "calmingSachet", "heartSootheBell", "ruinPolicy",
                "shadowiumZ", "purifiedGem", "bitterReawakening", "shadowScale")) {
            values.put("compatibilityItems.effects." + effect, false);
        }
        for (String tier : List.of("prototype", "advanced")) {
            values.put("snagMachines." + tier + ".attemptTurnSpacing", 1);
            values.put("snagMachines." + tier + ".catchMultiplier", 1.0);
            values.put("snagMachines." + tier + ".gaugeReductionPercent", 0.0);
        }
        values.put("umbraBall.catchMultiplier", 1.0);
        values.put("umbraBall.shadowCatchMultiplier", 1.0);
        values.put("umbraBall.antumbraCatchMultiplier", 1.0);
        values.put("umbraBall.antumbraShadowCatchMultiplier", 1.0);
        values.put("umbraBall.umbraCatchMultiplier", 1.0);
        values.put("umbraBall.umbraShadowCatchMultiplier", 1.0);
        values.put("shadowStatChanges.ivMode", "OFF");
        values.put("shadowStatChanges.catchRateScaleEnabled", false);

        // Preserve authored NPC/world encounters while removing the automatic wild-conversion source.
        values.put("shadowSpawn.chancePercent", 0.0);
        values.put("contextualSpawn.speciesChanceOverrides", List.of());
        values.put("infuser.pokemonEnabled", false);
        values.put("umbraBall.shadowConversionChance", 0.0);
        values.put("umbraBall.antumbraShadowConversionChance", 0.0);
        values.put("umbraBall.umbraShadowConversionChance", 0.0);
        values.put("umbraBall.heartGaugeMaxBonus", 0.0);
        values.put("umbraBall.antumbraHeartGaugeMaxBonus", 0.0);
        values.put("umbraBall.umbraHeartGaugeMaxBonus", 0.0);
        values.put("darkBall.levelBoost", 0);
        values.put("darkBall.heartGaugeMultiplier", 1.0);

        values.put("gameplayTuning.features.snaggingEnabled", true);
        values.put("gameplayTuning.features.relicStonePurificationEnabled", true);
        values.put("gameplayTuning.features.chamberPurificationEnabled", xd || go);
        for (String source : List.of("battle", "call", "walking", "scent", "chamber")) {
            // GO retains usable native purification; disabling every source would strand existing Shadows.
            values.put("gameplayTuning.progression." + source + "Multiplier", go && source.equals("call") ? 0.0 : 1.0);
        }
        values.put("gameplayTuning.progression.partyStepInterval", 256);
        values.put("relicStone.cooldownMinutes", 0);
        values.put("purificationChamber.stepRequirement", 161);
        values.put("snag.snagging_pity.failStackingBonus", false);
        values.put("snag.deferDeliveryUntilVictory", xd);

        String restrictions = "heartGauge.restrictions.";
        for (String key : List.of("bufferExperience", "bufferEVs", "blockFriendshipChanges", "blockEvolution",
                "blockNicknaming", "blockMoveChanges", "blockMoveTeaching", "hideNature", "restrictNonShadowMoves")) {
            values.put(restrictions + key, !go);
        }
        values.put(restrictions + "blockTrading", true);
        values.put(restrictions + "blockHyperTraining", go);
        values.put(restrictions + "exactThresholds", !go);
        values.put(restrictions + "natureRevealPercent", go ? 59 : 60);
        values.put(restrictions + "hideLevelAndExperience", false);
        values.put(restrictions + "levelExpRevealPercent", 40);
        values.put(restrictions + "hideEVs", false);
        values.put(restrictions + "evRevealPercent", 40);
        values.put(restrictions + "hideIVs", false);
        values.put(restrictions + "ivRevealPercent", 40);
        values.put(restrictions + "firstMoveUnlockPercent", go ? 79 : 80);
        values.put(restrictions + "secondMoveUnlockPercent", go ? 39 : 40);
        values.put(restrictions + "thirdMoveUnlockPercent", xd ? 0 : go ? 19 : 20);
        for (String gimmick : List.of("shadowCanMegaEvolve", "shadowCanDynamax", "shadowCanTerastallize",
                "shadowCanUseZMoves", "shadowCanUltraBurst")) {
            values.put("shadowGimmickRestrictions." + gimmick, false);
        }
        return values;
    }
}
