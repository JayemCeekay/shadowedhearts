package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import dev.architectury.platform.Platform;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Server/Common config system for Shadowed Hearts using ModConfigSpec.
 */
public final class ModConfig implements IShadowConfig {
    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        GameplayTuningConfig.INSTANCE.build(builder);
        OrreProgressionConfig.INSTANCE.build(builder);
        ShadowBattleRulesConfig.INSTANCE.build(builder);
        ProgressionEconomyConfig.INSTANCE.build(builder);
        PresetCompatibilityConfig.INSTANCE.build(builder);
        MoveLifecycleConfig.INSTANCE.build(builder);
        SPEC = builder.build();
        GameplayTuningConfig.INSTANCE.bind(SPEC);
        OrreProgressionConfig.INSTANCE.bind(SPEC);
        ShadowBattleRulesConfig.INSTANCE.bind(SPEC);
        ProgressionEconomyConfig.INSTANCE.bind(SPEC);
        PresetCompatibilityConfig.INSTANCE.bind(SPEC);
        MoveLifecycleConfig.INSTANCE.bind(SPEC);
    }

    @Override
    public double shadowSpawnChancePercent() {
        if (!isLoaded()) return IShadowConfig.super.shadowSpawnChancePercent();
        return DATA.shadowSpawnChancePercent.get();
    }

    @Override
    public List<? extends String> shadowSpawnBlacklist() {
        if (!isLoaded()) return IShadowConfig.super.shadowSpawnBlacklist();
        return DATA.shadowSpawnBlacklist.get();
    }

    @Override
    public boolean hyperModeEnabled() {
        if (!isLoaded()) return IShadowConfig.super.hyperModeEnabled();
        return DATA.hyperMode.enabled.get();
    }

    @Override
    public boolean reverseModeEnabled() {
        if (!isLoaded()) return IShadowConfig.super.reverseModeEnabled();
        return DATA.reverseMode.enabled.get();
    }

    @Override
    public boolean goDamageModifierEnabled() {
        if (!isLoaded()) return IShadowConfig.super.goDamageModifierEnabled();
        return DATA.goDamageModifier.enabled.get();
    }

    @Override
    public boolean goDamageModifierScaleWithHeartGauge() {
        if (!isLoaded()) return IShadowConfig.super.goDamageModifierScaleWithHeartGauge();
        return DATA.goDamageModifier.scaleWithHeartGauge.get();
    }

    @Override
    public boolean callButtonReducesHeartGauge() {
        if (!isLoaded()) return IShadowConfig.super.callButtonReducesHeartGauge();
        return DATA.callButton.reducesHeartGauge.get();
    }

    @Override
    public boolean callButtonAccuracyBoost() {
        if (!isLoaded()) return IShadowConfig.super.callButtonAccuracyBoost();
        return DATA.callButton.accuracyBoost.get();
    }

    @Override
    public boolean callButtonRemoveSleep() {
        if (!isLoaded()) return IShadowConfig.super.callButtonRemoveSleep();
        return DATA.callButton.removeSleep.get();
    }

    @Override
    public int scentCooldownSeconds() {
        if (!isLoaded()) return IShadowConfig.super.scentCooldownSeconds();
        return DATA.scent.cooldownSeconds.get();
    }

    @Override
    public boolean expandedScentSystemEnabled() {
        if (!isLoaded()) return IShadowConfig.super.expandedScentSystemEnabled();
        return DATA.scent.expandedSystemEnabled.get();
    }

    @Override
    public boolean superEffectiveShadowMovesEnabled() {
        if (!isLoaded()) return IShadowConfig.super.superEffectiveShadowMovesEnabled();
        return DATA.shadowMoves.superEffectiveEnabled.get();
    }

    @Override
    public String shadowMovesReplaceCount() {
        if (!isLoaded()) return IShadowConfig.super.shadowMovesReplaceCount();
        return DATA.shadowMoves.replaceCount.get();
    }

    @Override
    public boolean shadowMovesOnlyShadowRush() {
        if (!isLoaded()) return IShadowConfig.super.shadowMovesOnlyShadowRush();
        return DATA.shadowMoves.onlyShadowRush.get();
    }

    @Override
    public String shadowIVMode() {
        if (!isLoaded()) return IShadowConfig.super.shadowIVMode();
        return DATA.shadowStatChanges.ivMode.get();
    }

    @Override
    public int shadowFixedPerfectIVs() {
        if (!isLoaded()) return IShadowConfig.super.shadowFixedPerfectIVs();
        return DATA.shadowStatChanges.fixedPerfectIVs.get();
    }

    @Override
    public int shadowMaxPerfectIVs() {
        if (!isLoaded()) return IShadowConfig.super.shadowMaxPerfectIVs();
        return DATA.shadowStatChanges.maxPerfectIVs.get();
    }

    @Override
    public boolean shadowCatchRateScaleEnabled() {
        if (!isLoaded()) return IShadowConfig.super.shadowCatchRateScaleEnabled();
        return DATA.shadowStatChanges.catchRateScaleEnabled.get();
    }

    @Override
    public double shadowCatchRateMinMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.shadowCatchRateMinMultiplier();
        return DATA.shadowStatChanges.catchRateMinMultiplier.get();
    }

    @Override
    public double shadowCatchRateExponent() {
        if (!isLoaded()) return IShadowConfig.super.shadowCatchRateExponent();
        return DATA.shadowStatChanges.catchRateExponent.get();
    }

    @Override
    public boolean rctIntegrationEnabled() {
        if (!isLoaded()) return IShadowConfig.super.rctIntegrationEnabled();
        return DATA.rctIntegration.enabled.get();
    }

    @Override
    public boolean limitExpToRCTCap() {
        if (!isLoaded()) return IShadowConfig.super.limitExpToRCTCap();
        return DATA.rctIntegration.limitExpToRCTCap.get();
    }

    @Override
    public int relicStoneCooldownMinutes() {
        if (!isLoaded()) return IShadowConfig.super.relicStoneCooldownMinutes();
        return DATA.relicStone.cooldownMinutes.get();
    }

    @Override
    public int purificationChamberStepRequirement() {
        if (!isLoaded()) return IShadowConfig.super.purificationChamberStepRequirement();
        return DATA.purificationChamber.stepRequirement.get();
    }

    @Override
    public int auraScannerShadowRange() {
        if (!isLoaded()) return IShadowConfig.super.auraScannerShadowRange();
        return DATA.auraScanner.auraScannerShadowRange.get();
    }

    @Override
    public int auraScannerMeteoroidRange() {
        if (!isLoaded()) return IShadowConfig.super.auraScannerMeteoroidRange();
        return DATA.auraScanner.auraScannerMeteoroidRange.get();
    }

    @Override
    public int auraReaderPulseCooldownTicks() {
        if (!isLoaded()) return IShadowConfig.super.auraReaderPulseCooldownTicks();
        return DATA.auraScanner.auraReaderPulseCooldownTicks.get();
    }

    @Override
    public int trailMinNodeDistance() {
        if (!isLoaded()) return IShadowConfig.super.trailMinNodeDistance();
        return DATA.auraScanner.trailMinNodeDistance.get();
    }

    @Override
    public int trailMaxNodeDistance() {
        if (!isLoaded()) return IShadowConfig.super.trailMaxNodeDistance();
        return DATA.auraScanner.trailMaxNodeDistance.get();
    }

    @Override
    public boolean auraReaderRequiredForAura() {
        if (!isLoaded()) return IShadowConfig.super.auraReaderRequiredForAura();
        return DATA.auraScanner.auraReaderRequiredForAura.get();
    }

    @Override
    public boolean expandedAuraReaderEnabled() {
        if (!isLoaded()) return IShadowConfig.super.expandedAuraReaderEnabled();
        return DATA.auraScanner.expandedAuraReaderEnabled.get();
    }

    @Override
    public int auraLockMaxSeconds() {
        if (!isLoaded()) return IShadowConfig.super.auraLockMaxSeconds();
        return DATA.auraScanner.auraLockMaxSeconds.get();
    }

    @Override
    public int auraLockRange() {
        if (!isLoaded()) return IShadowConfig.super.auraLockRange();
        return DATA.auraScanner.auraLockRange.get();
    }

    @Override
    public boolean auraLockPersistsWhenAFK() {
        if (!isLoaded()) return IShadowConfig.super.auraLockPersistsWhenAFK();
        return DATA.auraScanner.auraLockPersistsWhenAFK.get();
    }

    @Override
    public List<? extends String> heartGaugeMaxOverrides() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeMaxOverrides();
        return DATA.heartGauge.maxOverrides.get();
    }

    @Override
    public boolean heartGaugeBufferExperience() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBufferExperience();
        return DATA.heartGauge.restrictions.bufferExperience.get();
    }

    @Override
    public boolean heartGaugeBufferEVs() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBufferEVs();
        return DATA.heartGauge.restrictions.bufferEVs.get();
    }

    @Override
    public boolean heartGaugeBlockFriendshipChanges() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBlockFriendshipChanges();
        return DATA.heartGauge.restrictions.blockFriendshipChanges.get();
    }

    @Override
    public boolean heartGaugeBlockEvolution() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBlockEvolution();
        return DATA.heartGauge.restrictions.blockEvolution.get();
    }

    @Override
    public boolean heartGaugeBlockNicknaming() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBlockNicknaming();
        return DATA.heartGauge.restrictions.blockNicknaming.get();
    }

    @Override
    public boolean heartGaugeBlockTrading() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBlockTrading();
        return DATA.heartGauge.restrictions.blockTrading.get();
    }

    @Override
    public boolean heartGaugeBlockMoveChanges() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBlockMoveChanges();
        return DATA.heartGauge.restrictions.blockMoveChanges.get();
    }

    @Override
    public boolean heartGaugeBlockMoveTeaching() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBlockMoveTeaching();
        return DATA.heartGauge.restrictions.blockMoveTeaching.get();
    }

    @Override
    public boolean heartGaugeBlockHyperTraining() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeBlockHyperTraining();
        return DATA.heartGauge.restrictions.blockHyperTraining.get();
    }

    @Override
    public boolean heartGaugeExactThresholds() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeExactThresholds();
        return DATA.heartGauge.restrictions.exactThresholds.get();
    }

    @Override
    public boolean heartGaugeHideNature() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeHideNature();
        return DATA.heartGauge.restrictions.hideNature.get();
    }

    @Override
    public int heartGaugeNatureRevealPercent() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeNatureRevealPercent();
        return DATA.heartGauge.restrictions.natureRevealPercent.get();
    }

    @Override
    public boolean heartGaugeHideLevelAndExperience() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeHideLevelAndExperience();
        return DATA.heartGauge.restrictions.hideLevelAndExperience.get();
    }

    @Override
    public int heartGaugeLevelExpRevealPercent() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeLevelExpRevealPercent();
        return DATA.heartGauge.restrictions.levelExpRevealPercent.get();
    }

    @Override
    public boolean heartGaugeHideEVs() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeHideEVs();
        return DATA.heartGauge.restrictions.hideEVs.get();
    }

    @Override
    public int heartGaugeEvRevealPercent() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeEvRevealPercent();
        return DATA.heartGauge.restrictions.evRevealPercent.get();
    }

    @Override
    public boolean heartGaugeHideIVs() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeHideIVs();
        return DATA.heartGauge.restrictions.hideIVs.get();
    }

    @Override
    public int heartGaugeIvRevealPercent() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeIvRevealPercent();
        return DATA.heartGauge.restrictions.ivRevealPercent.get();
    }

    @Override
    public boolean heartGaugeRestrictNonShadowMoves() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeRestrictNonShadowMoves();
        return DATA.heartGauge.restrictions.restrictNonShadowMoves.get();
    }

    @Override
    public int heartGaugeFirstMoveUnlockPercent() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeFirstMoveUnlockPercent();
        return DATA.heartGauge.restrictions.firstMoveUnlockPercent.get();
    }

    @Override
    public int heartGaugeSecondMoveUnlockPercent() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeSecondMoveUnlockPercent();
        return DATA.heartGauge.restrictions.secondMoveUnlockPercent.get();
    }

    @Override
    public int heartGaugeThirdMoveUnlockPercent() {
        if (!isLoaded()) return IShadowConfig.super.heartGaugeThirdMoveUnlockPercent();
        return DATA.heartGauge.restrictions.thirdMoveUnlockPercent.get();
    }

    @Override
    public IRCTSection append() {
        return DATA.rctIntegration.append;
    }

    @Override
    public IRCTSection convert() {
        return DATA.rctIntegration.convert;
    }

    @Override
    public IRCTSection replace() {
        return DATA.rctIntegration.replace;
    }

    @Override
    public boolean shadowCanMegaEvolve() {
        if (!isLoaded()) return IShadowConfig.super.shadowCanMegaEvolve();
        return DATA.shadowGimmick.shadowCanMegaEvolve.get();
    }

    @Override
    public boolean shadowCanDynamax() {
        if (!isLoaded()) return IShadowConfig.super.shadowCanDynamax();
        return DATA.shadowGimmick.shadowCanDynamax.get();
    }

    @Override
    public boolean shadowCanTerastallize() {
        if (!isLoaded()) return IShadowConfig.super.shadowCanTerastallize();
        return DATA.shadowGimmick.shadowCanTerastallize.get();
    }

    @Override
    public boolean shadowCanUseZMoves() {
        if (!isLoaded()) return IShadowConfig.super.shadowCanUseZMoves();
        return DATA.shadowGimmick.shadowCanUseZMoves.get();
    }

    @Override
    public boolean shadowCanUltraBurst() {
        if (!isLoaded()) return IShadowConfig.super.shadowCanUltraBurst();
        return DATA.shadowGimmick.shadowCanUltraBurst.get();
    }

    @Override
    public IWorldAlterationConfig worldAlteration() {
        return DATA.worldAlteration;
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    private static final class Data {
        public ModConfigSpec.DoubleValue shadowSpawnChancePercent;
        public ModConfigSpec.ConfigValue<List<? extends String>> shadowSpawnBlacklist;

        public final HyperModeConfig hyperMode = new HyperModeConfig();
        public final ReverseModeConfig reverseMode = new ReverseModeConfig();
        public final GODamageModifierConfig goDamageModifier = new GODamageModifierConfig();
        public final CallButtonConfig callButton = new CallButtonConfig();
        public final ScentConfig scent = new ScentConfig();
        public final ShadowMovesConfig shadowMoves = new ShadowMovesConfig();
        public final ShadowStatConfig shadowStatChanges = new ShadowStatConfig();
        public final AuraScannerConfig auraScanner = new AuraScannerConfig();
        public final HeartGaugeConfig heartGauge = new HeartGaugeConfig();
        public final RelicStoneConfig relicStone = new RelicStoneConfig();
        public final PurificationChamberConfig purificationChamber = new PurificationChamberConfig();
        public final RCTIntegrationConfig rctIntegration = new RCTIntegrationConfig();
        public final ShadowGimmickConfig shadowGimmick = new ShadowGimmickConfig();
        public final WorldAlterationConfig worldAlteration = new WorldAlterationConfig();

        private void build(ModConfigSpec.Builder builder) {
            builder.push("hyperMode");
            hyperMode.build(builder);
            builder.pop();

            builder.push("reverseMode");
            reverseMode.build(builder);
            builder.pop();

            builder.push("goDamageModifier");
            goDamageModifier.build(builder);
            builder.pop();

            builder.push("callButton");
            callButton.build(builder);
            builder.pop();

            builder.push("scent");
            scent.build(builder);
            builder.pop();

            builder.push("shadowMoves");
            shadowMoves.build(builder);
            builder.pop();

            builder.push("shadowStatChanges");
            shadowStatChanges.build(builder);
            builder.pop();

            builder.push("auraScanner");
            auraScanner.build(builder);
            builder.pop();

            builder.push("heartGauge");
            heartGauge.build(builder);
            builder.pop();

            builder.push("relicStone");
            relicStone.build(builder);
            builder.pop();

            builder.push("purificationChamber");
            purificationChamber.build(builder);
            builder.pop();

            builder.push("shadowSpawn");
            shadowSpawnChancePercent = builder
                    .comment("The percentage chance (0.0-100.0) for a wild Pokémon to spawn as a Shadow Pokémon.")
                    .defineInRange("chancePercent", 0.78125, 0.0, 100.0);
            
            shadowSpawnBlacklist = builder
                    .comment("List of Pokémon species or tags that cannot spawn as Shadow Pokémon.")
                    .defineList("blacklist", List.of("#shadowedhearts:legendaries", "#shadowedhearts:mythical"), o -> o instanceof String);
            builder.pop();

            builder.push("shadowGimmickRestrictions");
            shadowGimmick.build(builder);
            builder.pop();

            builder.push("modIntegrations");
            builder.push("rctmod");
            rctIntegration.build(builder);
            builder.pop();
            builder.pop();

            builder.push("worldAlteration");
            worldAlteration.build(builder);
            builder.pop();
        }
    }

    public static final class WorldAlterationConfig implements IWorldAlterationConfig {
        public ModConfigSpec.IntValue minCraterRadius;
        public ModConfigSpec.IntValue maxCraterRadius;
        public ModConfigSpec.DoubleValue meteoroidShadowSpawnChanceMultiplier;
        public ModConfigSpec.BooleanValue meteoroidWorldGenEnabled;
        public ModConfigSpec.IntValue meteoroidSpacing;
        public ModConfigSpec.IntValue meteoroidSeparation;
        public ModConfigSpec.ConfigValue<List<? extends String>> meteoroidBiomeBlacklist;
        public ModConfigSpec.ConfigValue<List<? extends String>> meteoroidBiomeWhitelist;

        private void build(ModConfigSpec.Builder builder) {
            minCraterRadius = builder
                    .comment("Minimum radius of generated craters.")
                    .defineInRange("minCraterRadius", 8, 1, 100);
            
            maxCraterRadius = builder
                    .comment("Maximum radius of generated craters.")
                    .defineInRange("maxCraterRadius", 16, 1, 100);

                        meteoroidShadowSpawnChanceMultiplier = builder
                    .comment("The multiplier applied to the base shadow spawn chance when a Pokemon spawns near a meteoroid.")
                    .defineInRange("meteoroidShadowSpawnChanceMultiplier", 5.0, 0.0, 100.0);
            
            meteoroidWorldGenEnabled = builder
                    .comment("Whether shadowfall meteoroids and craters are placed in the world during chunk generation.")
                    .define("meteoroidWorldGenEnabled", true);
            
            meteoroidSpacing = builder
                    .comment("Average distance (in chunks) between meteoroid structure placement attempts.")
                    .defineInRange("meteoroidSpacing", 26, 0, 4096);
            
            meteoroidSeparation = builder
                    .comment("Minimum distance (in chunks) between meteoroid structure placement attempts. Must be less than spacing.")
                    .defineInRange("meteoroidSeparation", 11, 0, 4096);
            
            meteoroidBiomeBlacklist = builder
                    .comment("A list of biomes where shadowfall meteoroids cannot impact. Biome Tags, such as #minecraft:is_forest are also acceptable.")
                    .defineList("meteoroidBiomeBlacklist", Collections.emptyList(), o -> o instanceof String);
            
            meteoroidBiomeWhitelist = builder
                    .comment("A list of biomes where shadowfall meteoroids can impact. If not empty, only these biomes will be allowed.  Biome Tags, such as #minecraft:is_forest are also acceptable.")
                    .defineList("meteoroidBiomeWhitelist", Collections.emptyList(), o -> o instanceof String);
        }




        @Override
        public int minCraterRadius() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.minCraterRadius();
            return minCraterRadius.get();
        }

        @Override
        public int maxCraterRadius() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.maxCraterRadius();
            return maxCraterRadius.get();
        }


        @Override
        public double meteoroidShadowSpawnChanceMultiplier() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowSpawnChanceMultiplier();
            return meteoroidShadowSpawnChanceMultiplier.get();
        }

        @Override
        public boolean meteoroidWorldGenEnabled() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidWorldGenEnabled();
            return meteoroidWorldGenEnabled.get();
        }

        @Override
        public int meteoroidSpacing() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidSpacing();
            return meteoroidSpacing.get();
        }

        @Override
        public int meteoroidSeparation() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidSeparation();
            return meteoroidSeparation.get();
        }

        @Override
        public List<? extends String> meteoroidBiomeBlacklist() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidBiomeBlacklist();
            return meteoroidBiomeBlacklist.get();
        }

        @Override
        public List<? extends String> meteoroidBiomeWhitelist() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidBiomeWhitelist();
            return meteoroidBiomeWhitelist.get();
        }
    }

    public static final class HyperModeConfig {
        public ModConfigSpec.BooleanValue enabled;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Hyper Mode mechanics are enabled.")
                    .define("enabled", true);
        }
    }

    public static final class ReverseModeConfig {
        public ModConfigSpec.BooleanValue enabled;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Reverse Mode mechanics are enabled.")
                    .define("enabled", true);
        }
    }

    public static final class GODamageModifierConfig {
        public ModConfigSpec.BooleanValue enabled;
        public ModConfigSpec.BooleanValue scaleWithHeartGauge;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Shadow Pokemon do and take up to 20% more damage like in Pokemon GO. Uses a flat 20% unless Heart Gauge scaling is enabled.")
                    .translation("shadowedhearts.configuration.goDamageModifier.enabled")
                    .define("enabled", false);
            scaleWithHeartGauge = builder
                    .comment("Scale both the damage bonus and extra damage taken with the current Heart Gauge fraction: 0% at empty, 10% at half full, and at most 20% at full. Requires goDamageModifier.enabled.")
                    .translation("shadowedhearts.configuration.goDamageModifier.scaleWithHeartGauge")
                    .define("scaleWithHeartGauge", false);
        }
    }

    public static final class CallButtonConfig {
        public ModConfigSpec.BooleanValue reducesHeartGauge;
        public ModConfigSpec.BooleanValue accuracyBoost;
        public ModConfigSpec.BooleanValue removeSleep;

        private void build(ModConfigSpec.Builder builder) {
            reducesHeartGauge = builder
                    .comment("If true, the Call button in battle reduces the Heart Gauge of Shadow Pokémon when snapping them out of Hyper Mode or Reverse Mode.")
                    .define("reducesHeartGauge", true);
            
            accuracyBoost = builder
                    .comment("If true, the Call button provides an accuracy boost to the Pokémon if they are not in Hyper Mode or Reverse Mode.")
                    .define("accuracyBoost", true);
            
            removeSleep = builder
                    .comment("If true, the Call button can wake up a sleeping Pokémon.")
                    .define("removeSleep", true);
        }
    }

    public static final class ScentConfig {
        public ModConfigSpec.IntValue cooldownSeconds;
        public ModConfigSpec.BooleanValue expandedSystemEnabled;

        private void build(ModConfigSpec.Builder builder) {
            cooldownSeconds = builder
                    .comment("Cooldown in seconds between using Scent items on a Pokémon.")
                    .defineInRange("cooldownSeconds", 300, 0, Integer.MAX_VALUE);
            
            expandedSystemEnabled = builder
                    .comment("If true, the expanded scent system with more scents and nature affinities is enabled.")
                    .define("expandedSystemEnabled", true);
        }
    }

    public static final class ShadowMovesConfig {
        public ModConfigSpec.BooleanValue superEffectiveEnabled;
        public ModConfigSpec.ConfigValue<String> replaceCount;
        public ModConfigSpec.BooleanValue onlyShadowRush;

        private void build(ModConfigSpec.Builder builder) {
            superEffectiveEnabled = builder
                    .comment("If true, Shadow moves are super effective against non-Shadow Pokémon.")
                    .define("superEffectiveEnabled", true);
            
            replaceCount = builder
                    .comment("How many moves to replace with Shadow moves. Supports single values (e.g. '1') or ranges (e.g. '1-3').")
                    .define("replaceCount", "1");
            
            onlyShadowRush = builder
                    .comment("If true, only 'Shadow Rush' will be assigned, even if replaceCount > 1.")
                    .define("onlyShadowRush", false);
        }
    }

    public static final class ShadowStatConfig {

        public ModConfigSpec.ConfigValue<String> ivMode;
        public ModConfigSpec.IntValue fixedPerfectIVs;
        public ModConfigSpec.IntValue maxPerfectIVs;
        public ModConfigSpec.BooleanValue catchRateScaleEnabled;
        public ModConfigSpec.DoubleValue catchRateMinMultiplier;
        public ModConfigSpec.DoubleValue catchRateExponent;

        private void build(ModConfigSpec.Builder builder) {
            ivMode = builder
                    .comment("The mode for determining perfect IVs for Shadow Pokémon. Options: OFF, FIXED, SCALED")
                    .define("ivMode", "SCALED");

            fixedPerfectIVs = builder
                    .comment("The number of perfect IVs for Shadow Pokémon when ivMode is FIXED.")
                    .defineInRange("fixedPerfectIVs", 3, 0, 6);

            maxPerfectIVs = builder
                    .comment("The maximum number of perfect IVs for Shadow Pokémon when ivMode is SCALED.")
                    .defineInRange("maxPerfectIVs", 5, 0, 6);

            catchRateScaleEnabled = builder
                    .comment("If true, the catch rate of Shadow Pokémon scales with their maximum heart gauge.")
                    .define("catchRateScaleEnabled", true);

            catchRateMinMultiplier = builder
                    .comment("The minimum catch rate multiplier for Shadow Pokémon at maximum heart gauge.")
                    .defineInRange("catchRateMinMultiplier", 0.25, 0.0, 1.0);

            catchRateExponent = builder
                    .comment("The exponent for the catch rate scaling curve.")
                    .defineInRange("catchRateExponent", 1.4, 0.0, 10.0);
        }
    }

    public static final class AuraScannerConfig {
        public ModConfigSpec.IntValue auraScannerShadowRange;
        public ModConfigSpec.IntValue auraScannerMeteoroidRange;
        public ModConfigSpec.IntValue auraReaderPulseCooldownTicks;
        public ModConfigSpec.IntValue trailMinNodeDistance;
        public ModConfigSpec.IntValue trailMaxNodeDistance;
        public ModConfigSpec.BooleanValue auraReaderRequiredForAura;
        public ModConfigSpec.BooleanValue expandedAuraReaderEnabled;
        public ModConfigSpec.IntValue auraLockMaxSeconds;
        public ModConfigSpec.IntValue auraLockRange;
        public ModConfigSpec.BooleanValue auraLockPersistsWhenAFK;

        private void build(ModConfigSpec.Builder builder) {
            auraScannerShadowRange = builder
                    .comment("The range (in blocks) at which the Aura Scanner can detect Shadow Pokemon.")
                    .defineInRange("auraScannerShadowRange", 128, 1, 512);
            
            auraScannerMeteoroidRange = builder
                    .comment("The range (in blocks) at which the Aura Scanner can detect Shadowfall meteoroids.")
                    .defineInRange("auraScannerMeteoroidRange", 256, 1, 512);

            auraReaderPulseCooldownTicks = builder
                    .comment("Cooldown in ticks after an accepted Aura Reader pulse scan.")
                    .defineInRange("auraReaderPulseCooldownTicks", 40, 0, 20 * 60);

            trailMinNodeDistance = builder
                    .comment("The minimum distance (in blocks) between evidence nodes in a trail.")
                    .defineInRange("trailMinNodeDistance", 8, 1, 64);

            trailMaxNodeDistance = builder
                    .comment("The maximum distance (in blocks) between evidence nodes in a trail.")
                    .defineInRange("trailMaxNodeDistance", 18, 1, 128);

            auraReaderRequiredForAura = builder
                    .comment("Whether the Aura Reader is required to see Shadow Auras in the overworld.")
                    .define("auraReaderRequiredForAura", false);

            expandedAuraReaderEnabled = builder
                    .comment("If true, the expanded Aura Reader system (charge, recharge, scanning, and pulse mechanics) is enabled.")
                    .define("expandedAuraReaderEnabled", true);

            builder.push("auraLock");
            auraLockMaxSeconds = builder
                    .comment("Maximum seconds a single aura lock request can persist before expiring.")
                    .defineInRange("maxSeconds", 60, 1, 600);

            auraLockRange = builder
                    .comment("Maximum distance (blocks) from the player to the target Pokemon to accept a lock request.")
                    .defineInRange("range", 96, 8, 512);

            auraLockPersistsWhenAFK = builder
                    .comment("If true, aura locks remain effective even when the locking player is AFK. If false, AFK players may be ignored in future logic.")
                    .define("persistsWhenAFK", false);
            builder.pop();
        }
    }

    public static final class HeartGaugeConfig {
        public ModConfigSpec.ConfigValue<List<? extends String>> maxOverrides;
        public final HeartGaugeRestrictionsConfig restrictions = new HeartGaugeRestrictionsConfig();

        private void build(ModConfigSpec.Builder builder) {
            maxOverrides = builder
                    .comment("List of species-specific maximum Heart Gauge values. Format: species=max")
                    .translation("shadowedhearts.configuration.heartGauge.maxOverrides")
                    .defineListAllowEmpty("maxOverrides", List.of(), () -> "bulbasaur=3000", ModConfig::isHeartGaugeOverride);
            builder.translation("shadowedhearts.configuration.heartGauge.restrictions").push("restrictions");
            restrictions.build(builder);
            builder.pop();
        }
    }

    public static final class HeartGaugeRestrictionsConfig {
        public ModConfigSpec.BooleanValue bufferExperience;
        public ModConfigSpec.BooleanValue bufferEVs;
        public ModConfigSpec.BooleanValue blockFriendshipChanges;
        public ModConfigSpec.BooleanValue blockEvolution;
        public ModConfigSpec.BooleanValue blockNicknaming;
        public ModConfigSpec.BooleanValue blockTrading;
        public ModConfigSpec.BooleanValue blockMoveChanges;
        public ModConfigSpec.BooleanValue blockMoveTeaching;
        public ModConfigSpec.BooleanValue blockHyperTraining;
        public ModConfigSpec.BooleanValue exactThresholds;
        public ModConfigSpec.BooleanValue hideNature;
        public ModConfigSpec.IntValue natureRevealPercent;
        public ModConfigSpec.BooleanValue hideLevelAndExperience;
        public ModConfigSpec.IntValue levelExpRevealPercent;
        public ModConfigSpec.BooleanValue hideEVs;
        public ModConfigSpec.IntValue evRevealPercent;
        public ModConfigSpec.BooleanValue hideIVs;
        public ModConfigSpec.IntValue ivRevealPercent;
        public ModConfigSpec.BooleanValue restrictNonShadowMoves;
        public ModConfigSpec.IntValue firstMoveUnlockPercent;
        public ModConfigSpec.IntValue secondMoveUnlockPercent;
        public ModConfigSpec.IntValue thirdMoveUnlockPercent;

        private void build(ModConfigSpec.Builder builder) {
            bufferExperience = builder
                    .comment("Save newly earned EXP until purification instead of allowing normal leveling. Existing buffered EXP is retained when disabled.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.bufferExperience")
                    .define("bufferExperience", true);
            bufferEVs = builder
                    .comment("Save newly earned EVs until purification. Existing buffered EVs are retained when disabled.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.bufferEVs")
                    .define("bufferEVs", true);
            blockFriendshipChanges = builder
                    .comment("Prevent friendship from increasing or decreasing while a Pokemon is Shadow.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.blockFriendshipChanges")
                    .define("blockFriendshipChanges", true);
            blockEvolution = builder
                    .comment("Prevent a Shadow Pokemon from accepting an evolution, including evolution stones.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.blockEvolution")
                    .define("blockEvolution", true);
            blockNicknaming = builder
                    .comment("Prevent nickname changes while a Pokemon is Shadow.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.blockNicknaming")
                    .define("blockNicknaming", true);
            blockTrading = builder
                    .comment("Prevent trades involving a Shadow Pokemon.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.blockTrading")
                    .define("blockTrading", true);
            blockMoveChanges = builder
                    .comment("Prevent reordering, swapping and benching moves while a Pokemon is Shadow. Assigned Shadow moves remain protected.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.blockMoveChanges")
                    .define("blockMoveChanges", true);
            blockMoveTeaching = builder
                    .comment("Prevent native TM and supported SimpleTMs teaching while a Pokemon is Shadow.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.blockMoveTeaching")
                    .define("blockMoveTeaching", true);
            hideNature = builder
                    .comment("Hide a Shadow Pokemon's nature above the configured reveal percentage.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.hideNature")
                    .define("hideNature", true);
            blockHyperTraining = builder
                    .comment("Prevent Hyper Training, including native IV candies, while a Pokemon is Shadow. Already trained IVs are retained.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.blockHyperTraining")
                    .define("blockHyperTraining", false);
            exactThresholds = builder
                    .comment("Use the exact remaining gauge and unlock strictly below positive reveal/move thresholds. A threshold of zero requires an empty gauge. Disabled preserves rounded, inclusive comparisons.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.exactThresholds")
                    .define("exactThresholds", false);
            natureRevealPercent = builder
                    .comment("Nature reveal threshold in remaining Heart Gauge percent. Rounded/inclusive by default; strictly below when exactThresholds is enabled. Zero requires an empty gauge in exact mode.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.natureRevealPercent")
                    .defineInRange("natureRevealPercent", 40, 0, 100);
            hideLevelAndExperience = builder
                    .comment("Hide a Shadow Pokemon's level and EXP above the configured reveal percentage.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.hideLevelAndExperience")
                    .define("hideLevelAndExperience", true);
            levelExpRevealPercent = builder
                    .comment("Level/EXP reveal threshold in remaining Heart Gauge percent; comparison follows exactThresholds.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.levelExpRevealPercent")
                    .defineInRange("levelExpRevealPercent", 40, 0, 100);
            hideEVs = builder
                    .comment("Hide a Shadow Pokemon's EVs above the configured reveal percentage.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.hideEVs")
                    .define("hideEVs", true);
            evRevealPercent = builder
                    .comment("EV reveal threshold in remaining Heart Gauge percent; comparison follows exactThresholds.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.evRevealPercent")
                    .defineInRange("evRevealPercent", 40, 0, 100);
            hideIVs = builder
                    .comment("Hide a Shadow Pokemon's IVs above the configured reveal percentage.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.hideIVs")
                    .define("hideIVs", true);
            ivRevealPercent = builder
                    .comment("IV reveal threshold in remaining Heart Gauge percent; comparison follows exactThresholds.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.ivRevealPercent")
                    .defineInRange("ivRevealPercent", 40, 0, 100);
            restrictNonShadowMoves = builder
                    .comment("Restrict ordinary move visibility and battle use until their unlock percentages. Disable to allow all ordinary moves.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.restrictNonShadowMoves")
                    .define("restrictNonShadowMoves", true);
            firstMoveUnlockPercent = builder
                    .comment("First ordinary move threshold in remaining Heart Gauge percent; comparison follows exactThresholds. Use 80 with exact mode for the original Orre below-80% milestone.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.firstMoveUnlockPercent")
                    .defineInRange("firstMoveUnlockPercent", 79, 0, 100);
            secondMoveUnlockPercent = builder
                    .comment("Second ordinary move threshold after the first has unlocked; comparison follows exactThresholds.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.secondMoveUnlockPercent")
                    .defineInRange("secondMoveUnlockPercent", 40, 0, 100);
            thirdMoveUnlockPercent = builder
                    .comment("Third ordinary move threshold after the second has unlocked; comparison follows exactThresholds.")
                    .translation("shadowedhearts.configuration.heartGauge.restrictions.thirdMoveUnlockPercent")
                    .defineInRange("thirdMoveUnlockPercent", 20, 0, 100);
        }
    }

    public static final class ShadowGimmickConfig {
        public ModConfigSpec.BooleanValue shadowCanMegaEvolve;
        public ModConfigSpec.BooleanValue shadowCanDynamax;
        public ModConfigSpec.BooleanValue shadowCanTerastallize;
        public ModConfigSpec.BooleanValue shadowCanUseZMoves;
        public ModConfigSpec.BooleanValue shadowCanUltraBurst;

        private void build(ModConfigSpec.Builder builder) {
            shadowCanMegaEvolve = builder
                    .comment("Allow shadow Pokémon to mega evolve.")
                    .define("shadowCanMegaEvolve", false);
            shadowCanDynamax = builder
                    .comment("Allow shadow Pokémon to dynamax/gigantamax.")
                    .define("shadowCanDynamax", false);
            shadowCanTerastallize = builder
                    .comment("Allow shadow Pokémon to terastallize.")
                    .define("shadowCanTerastallize", false);
            shadowCanUseZMoves = builder
                    .comment("Allow shadow Pokémon to use Z-moves.")
                    .define("shadowCanUseZMoves", false);
            shadowCanUltraBurst = builder
                    .comment("Allow shadow Pokémon to ultra burst.")
                    .define("shadowCanUltraBurst", false);
        }
    }

    public static final class RelicStoneConfig {
        public ModConfigSpec.IntValue cooldownMinutes;


        private void build(ModConfigSpec.Builder builder) {
            cooldownMinutes = builder
                    .comment("Cooldown in minutes between using the Relic Stone's purification function.")
                    .defineInRange("cooldownMinutes", 5, 0, Integer.MAX_VALUE);
        }
    }

    public static final class PurificationChamberConfig {
        public ModConfigSpec.IntValue stepRequirement;

        private void build(ModConfigSpec.Builder builder) {
            stepRequirement = builder
                    .comment("The number of steps required to advance the purification chamber by one tick.")
                    .defineInRange("stepRequirement", 161, 1, Integer.MAX_VALUE);
        }
    }

    public static final class RCTIntegrationConfig {
        public ModConfigSpec.BooleanValue enabled;
        public ModConfigSpec.BooleanValue limitExpToRCTCap;
        public RCTSection append;
        public RCTSection convert;
        public RCTSection replace;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Enables integration with Radical Cobblemon Trainers.")
                    .define("enabled", Platform.isModLoaded("rctmod"));

            limitExpToRCTCap = builder
                    .comment("If true, the XP buffer will be capped to the player's current RCT level cap during purification.")
                    .define("limitExpToRCTCap", false);
            
            builder.push("append");
            append = new RCTSection();
            append.build(builder);
            builder.pop();
            
            builder.push("convert");
            convert = new RCTSection();
            convert.build(builder);
            builder.pop();
            
            builder.push("replace");
            replace = new RCTSection(true);
            replace.build(builder);
            builder.pop();
        }
    }

    public static final class RCTSection implements IRCTSection {
        public ModConfigSpec.ConfigValue<List<? extends String>> trainerTypes;
        public ModConfigSpec.ConfigValue<List<? extends String>> typePresets; // We'll store as key=value strings
        public ModConfigSpec.ConfigValue<List<? extends String>> trainerBlacklist;
        public ModConfigSpec.ConfigValue<List<? extends String>> trainers; // We'll store as JSON strings or key=value

        private final boolean isDefaultReplace;

        public RCTSection() {
            this(false);
        }

        public RCTSection(boolean isDefaultReplace) {
            this.isDefaultReplace = isDefaultReplace;
        }

        private void build(ModConfigSpec.Builder builder) {
            trainerTypes = builder
                    .comment("The trainer types that this section applies to.")
                    .defineList("trainerTypes",
                    isDefaultReplace ? List.of("team_rocket", "team_galactic", "team_shadow") : List.of("normal"),
                    o -> o instanceof String);
            
            typePresets = builder
                    .comment("Format: type=presetId. Mapping of trainer types to their respective Shadow Pokémon presets.")
                    .defineList("typePresets",
                            isDefaultReplace ? List.of("team_rocket=shadowedhearts/team_rocket", "team_galactic=shadowedhearts/team_galactic", "team_shadow=shadowedhearts/team_shadow") : List.of("normal=shadowedhearts/normal_20"),
                            o -> o instanceof String && ((String) o).contains("="));
            
            trainerBlacklist = builder
                    .comment("List of specific trainer IDs that should be excluded from Shadow Pokémon injection.")
                    .defineList("trainerBlacklist", Collections.emptyList(), o -> o instanceof String);
            
            trainers = builder
                    .comment("Format: id;preset;tag1,tag2... Specific trainer definitions with presets and optional tags.")
                    .defineList("trainers",
                            isDefaultReplace ? List.of("team_rocket;shadowedhearts/team_rocket;", "team_galactic;shadowedhearts/team_galactic;", "team_shadow;shadowedhearts/team_shadow;") : List.of("normal;shadowedhearts/normal_20;"),
                            o -> o instanceof String);
        }

        @Override
        public List<? extends String> trainerTypes() {
            return trainerTypes.get();
        }

        @Override
        public List<? extends String> typePresets() {
            return typePresets.get();
        }

        @Override
        public List<? extends String> trainerBlacklist() {
            return trainerBlacklist.get();
        }

        @Override
        public List<? extends String> trainers() {
            return trainers.get();
        }
    }

    @Override
    public void load() {
        loaded = true;
        com.jayemceekay.shadowedhearts.config.HeartGaugeConfig.reload();
        Shadowedhearts.LOGGER.info("ModConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded && SPEC.isLoaded();
    }

    private static boolean isHeartGaugeOverride(Object value) {
        if (!(value instanceof String text)) return false;
        String[] parts = text.split("=", 2);
        if (parts.length != 2 || !com.jayemceekay.shadowedhearts.config.HeartGaugeConfig.isValidSpeciesKey(
                parts[0].toLowerCase(java.util.Locale.ROOT).trim())) return false;
        try {
            return Integer.parseInt(parts[1].trim()) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public static int resolveReplaceCount(Random rng) {
        IShadowConfig cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        String raw = cfg.shadowMovesReplaceCount();
        return resolveConfigRange(raw, rng, 1);
    }

    private static int resolveConfigRange(String raw, Random rng, int defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            if (raw.contains("-")) {
                String[] parts = raw.split("-");
                if (parts.length == 2) {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    if (max < min) {
                        int temp = min;
                        min = max;
                        max = temp;
                    }
                    return min + rng.nextInt(max - min + 1);
                }
            }
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
