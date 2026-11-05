package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleDefault;
import dev.architectury.platform.Platform;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Server-authoritative gameplay settings stored in common.toml, including snagging.
 */
public final class ModConfig implements IShadowConfig {
    private boolean loaded = false;
    public static final ModConfigSpec SPEC;
    private static final Data DATA = new Data();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        DATA.build(builder);
        ContextualSpawnConfig.INSTANCE.build(builder);
        GameplayTuningConfig.INSTANCE.build(builder);
        OrreProgressionConfig.INSTANCE.build(builder);
        InfuserConfig.INSTANCE.build(builder);
        ShadowBattleRulesConfig.INSTANCE.build(builder);
        ProgressionEconomyConfig.INSTANCE.build(builder);
        PresetCompatibilityConfig.INSTANCE.build(builder);
        MoveLifecycleConfig.INSTANCE.build(builder);
        SPEC = builder.build();
        ContextualSpawnConfig.INSTANCE.bind(SPEC);
        GameplayTuningConfig.INSTANCE.bind(SPEC);
        OrreProgressionConfig.INSTANCE.bind(SPEC);
        InfuserConfig.INSTANCE.bind(SPEC);
        ShadowBattleRulesConfig.INSTANCE.bind(SPEC);
        ProgressionEconomyConfig.INSTANCE.bind(SPEC);
        PresetCompatibilityConfig.INSTANCE.bind(SPEC);
        MoveLifecycleConfig.INSTANCE.bind(SPEC);
    }

    @Override
    public AuraStyleDefault shadowAuraStyle() {
        if (!isLoaded()) return IShadowConfig.super.shadowAuraStyle();
        return DATA.shadowAuraStyle.get();
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
    public boolean preferLostLoreShadowMoves() {
        if (!isLoaded()) return IShadowConfig.super.preferLostLoreShadowMoves();
        return DATA.shadowMoves.preferLostLoreShadowMoves.get();
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
    public double penumbraBallCatchMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.penumbraBallCatchMultiplier();
        return DATA.shadowBall.penumbraBallCatchMultiplier.get();
    }

    @Override
    public double penumbraBallShadowCatchMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.penumbraBallShadowCatchMultiplier();
        return DATA.shadowBall.penumbraBallShadowCatchMultiplier.get();
    }

    @Override
    public double penumbraBallShadowConversionChance() {
        if (!isLoaded()) return IShadowConfig.super.penumbraBallShadowConversionChance();
        return DATA.shadowBall.penumbraBallShadowConversionChance.get();
    }

    @Override
    public double penumbraBallHeartGaugeMaxBonus() {
        if (!isLoaded()) return IShadowConfig.super.penumbraBallHeartGaugeMaxBonus();
        return DATA.shadowBall.penumbraBallHeartGaugeMaxBonus.get();
    }

    @Override
    public double antumbraBallCatchMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.antumbraBallCatchMultiplier();
        return DATA.shadowBall.antumbraBallCatchMultiplier.get();
    }

    @Override
    public double antumbraBallShadowCatchMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.antumbraBallShadowCatchMultiplier();
        return DATA.shadowBall.antumbraBallShadowCatchMultiplier.get();
    }

    @Override
    public double antumbraBallShadowConversionChance() {
        if (!isLoaded()) return IShadowConfig.super.antumbraBallShadowConversionChance();
        return DATA.shadowBall.antumbraBallShadowConversionChance.get();
    }

    @Override
    public double antumbraBallHeartGaugeMaxBonus() {
        if (!isLoaded()) return IShadowConfig.super.antumbraBallHeartGaugeMaxBonus();
        return DATA.shadowBall.antumbraBallHeartGaugeMaxBonus.get();
    }

    @Override
    public double umbraBallCatchMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.umbraBallCatchMultiplier();
        return DATA.shadowBall.umbraBallCatchMultiplier.get();
    }

    @Override
    public double umbraBallShadowCatchMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.umbraBallShadowCatchMultiplier();
        return DATA.shadowBall.umbraBallShadowCatchMultiplier.get();
    }

    @Override
    public double umbraBallShadowConversionChance() {
        if (!isLoaded()) return IShadowConfig.super.umbraBallShadowConversionChance();
        return DATA.shadowBall.umbraBallShadowConversionChance.get();
    }

    @Override
    public double umbraBallHeartGaugeMaxBonus() {
        if (!isLoaded()) return IShadowConfig.super.umbraBallHeartGaugeMaxBonus();
        return DATA.shadowBall.umbraBallHeartGaugeMaxBonus.get();
    }

    @Override
    public int darkBallLevelBoost() {
        if (!isLoaded()) return IShadowConfig.super.darkBallLevelBoost();
        return DATA.darkBall.levelBoost.get();
    }

    @Override
    public double darkBallHeartGaugeMultiplier() {
        if (!isLoaded()) return IShadowConfig.super.darkBallHeartGaugeMultiplier();
        return DATA.darkBall.heartGaugeMultiplier.get();
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
    public int auraReaderPulseCooldownTicks() {
        if (!isLoaded()) return IShadowConfig.super.auraReaderPulseCooldownTicks();
        return DATA.auraScanner.auraReaderPulseCooldownTicks.get();
    }

    @Override
    public boolean auraReaderRequiredForAura() {
        if (!isLoaded()) return IShadowConfig.super.auraReaderRequiredForAura();
        return DATA.auraScanner.auraReaderRequiredForAura.get();
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
        return ServerConfig.INSTANCE;
    }

    @Override
    public ModConfigSpec getSpec() {
        return SPEC;
    }

    private static final class Data {
        public ModConfigSpec.EnumValue<AuraStyleDefault> shadowAuraStyle;
        public ModConfigSpec.DoubleValue shadowSpawnChancePercent;
        public ModConfigSpec.ConfigValue<List<? extends String>> shadowSpawnBlacklist;

        public final HyperModeConfig hyperMode = new HyperModeConfig();
        public final ReverseModeConfig reverseMode = new ReverseModeConfig();
        public final GODamageModifierConfig goDamageModifier = new GODamageModifierConfig();
        public final CallButtonConfig callButton = new CallButtonConfig();
        public final ScentConfig scent = new ScentConfig();
        public final ShadowMovesConfig shadowMoves = new ShadowMovesConfig();
        public final ShadowStatConfig shadowStatChanges = new ShadowStatConfig();
        public final ShadowBallConfig shadowBall = new ShadowBallConfig();
        public final DarkBallConfig darkBall = new DarkBallConfig();
        public final AuraScannerConfig auraScanner = new AuraScannerConfig();
        public final HeartGaugeConfig heartGauge = new HeartGaugeConfig();
        public final RelicStoneConfig relicStone = new RelicStoneConfig();
        public final PurificationChamberConfig purificationChamber = new PurificationChamberConfig();
        public final RCTIntegrationConfig rctIntegration = new RCTIntegrationConfig();
        public final ShadowGimmickConfig shadowGimmick = new ShadowGimmickConfig();

        private void build(ModConfigSpec.Builder builder) {
            shadowAuraStyle = builder
                    .comment("Default Shadow aura presentation for all players on this server.",
                            "A Pokemon with an explicit sh_aura_style property overrides this value; default inherits it.",
                            "RANDOM gives each Pokemon a stable UUID-based choice of Signature, Colosseum, or XD.")
                    .translation("shadowedhearts.configuration.shadowAuraStyle")
                    .defineEnum("shadowAuraStyle", AuraStyleDefault.SIGNATURE);

            builder.translation("shadowedhearts.configuration.hyperMode").push("hyperMode");
            hyperMode.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.reverseMode").push("reverseMode");
            reverseMode.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.goDamageModifier").push("goDamageModifier");
            goDamageModifier.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.callButton").push("callButton");
            callButton.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.scent").push("scent");
            scent.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.shadowMoves").push("shadowMoves");
            shadowMoves.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.shadowStatChanges").push("shadowStatChanges");
            shadowStatChanges.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.umbraBall").push("umbraBall");
            shadowBall.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.darkBall").push("darkBall");
            darkBall.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.auraScanner").push("auraScanner");
            auraScanner.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.heartGauge").push("heartGauge");
            heartGauge.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.relicStone").push("relicStone");
            relicStone.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.purificationChamber").push("purificationChamber");
            purificationChamber.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.shadowSpawn").push("shadowSpawn");
            shadowSpawnChancePercent = builder
                    .comment("The percentage chance (0.0-100.0) for a wild Pokémon to spawn as a Shadow Pokémon.")
                    .translation("shadowedhearts.configuration.shadowSpawn.chancePercent")
                    .defineInRange("chancePercent", 0.78125, 0.0, 100.0);
            
            shadowSpawnBlacklist = builder
                    .comment("List of Pokémon species or tags that cannot spawn as Shadow Pokémon.")
                    .translation("shadowedhearts.configuration.shadowSpawn.blacklist")
                    .defineListAllowEmpty("blacklist", List.of("#shadowedhearts:legendaries", "#shadowedhearts:mythical"),
                            () -> "cobblemon:bulbasaur", ModConfig::isNonBlankString);
            builder.pop();

            builder.translation("shadowedhearts.configuration.shadowGimmickRestrictions").push("shadowGimmickRestrictions");
            shadowGimmick.build(builder);
            builder.pop();

            builder.translation("shadowedhearts.configuration.modIntegrations").push("modIntegrations");
            builder.translation("shadowedhearts.configuration.modIntegrations.rctmod").push("rctmod");
            rctIntegration.build(builder);
            builder.pop();
            builder.pop();

            builder.translation("shadowedhearts.configuration.snag").push("snag");
            SnagConfig.build(builder);
            builder.pop();
        }
    }

    public static final class HyperModeConfig {
        public ModConfigSpec.BooleanValue enabled;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Hyper Mode mechanics are enabled.")
                    .translation("shadowedhearts.configuration.hyperMode.enabled")
                    .define("enabled", true);
        }
    }

    public static final class ReverseModeConfig {
        public ModConfigSpec.BooleanValue enabled;

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Reverse Mode mechanics are enabled.")
                    .translation("shadowedhearts.configuration.reverseMode.enabled")
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
                    .translation("shadowedhearts.configuration.callButton.reducesHeartGauge")
                    .define("reducesHeartGauge", true);
            
            accuracyBoost = builder
                    .comment("If true, the Call button provides an accuracy boost to the Pokémon if they are not in Hyper Mode or Reverse Mode.")
                    .translation("shadowedhearts.configuration.callButton.accuracyBoost")
                    .define("accuracyBoost", true);
            
            removeSleep = builder
                    .comment("If true, the Call button can wake up a sleeping Pokémon.")
                    .translation("shadowedhearts.configuration.callButton.removeSleep")
                    .define("removeSleep", true);
        }
    }

    public static final class ScentConfig {
        public ModConfigSpec.IntValue cooldownSeconds;
        public ModConfigSpec.BooleanValue expandedSystemEnabled;

        private void build(ModConfigSpec.Builder builder) {
            cooldownSeconds = builder
                    .comment("Cooldown in seconds between using Scent items on a Pokémon.")
                    .translation("shadowedhearts.configuration.scent.cooldownSeconds")
                    .defineInRange("cooldownSeconds", 300, 0, Integer.MAX_VALUE);
            
            expandedSystemEnabled = builder
                    .comment("If true, the expanded scent system with more scents and nature affinities is enabled.")
                    .translation("shadowedhearts.configuration.scent.expandedSystemEnabled")
                    .define("expandedSystemEnabled", true);
        }
    }

    public static final class ShadowMovesConfig {
        public ModConfigSpec.BooleanValue superEffectiveEnabled;
        public ModConfigSpec.ConfigValue<String> replaceCount;
        public ModConfigSpec.BooleanValue onlyShadowRush;
        public ModConfigSpec.BooleanValue preferLostLoreShadowMoves;

        private void build(ModConfigSpec.Builder builder) {
            superEffectiveEnabled = builder
                    .comment("If true, Shadow moves are super effective against non-Shadow Pokémon.")
                    .translation("shadowedhearts.configuration.shadowMoves.superEffectiveEnabled")
                    .define("superEffectiveEnabled", true);
            
            replaceCount = builder
                    .comment("How many moves to replace with Shadow moves. Supports single values (e.g. '1') or ranges (e.g. '1-3').")
                    .translation("shadowedhearts.configuration.shadowMoves.replaceCount")
                    .define("replaceCount", "1");
            
            onlyShadowRush = builder
                    .comment("If true, only 'Shadow Rush' will be assigned, even if replaceCount > 1.")
                    .translation("shadowedhearts.configuration.shadowMoves.onlyShadowRush")
                    .define("onlyShadowRush", false);

            preferLostLoreShadowMoves = builder
                    .comment("Use Lost Lore's Shadow move sets for its Shadow Lugia and Shadow Mewtwo forms when assigning Shadow moves.",
                            "Overrides onlyShadowRush and replaceCount for those forms. Requires Lost Lore.",
                            "Changing this option does not automatically rewrite existing Pokemon's moves.")
                    .translation("shadowedhearts.configuration.shadowMoves.preferLostLoreShadowMoves")
                    .define("preferLostLoreShadowMoves", false);
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
                    .translation("shadowedhearts.configuration.shadowStatChanges.ivMode")
                    .define("ivMode", "SCALED", value -> value instanceof String text
                            && (text.equalsIgnoreCase("OFF")
                            || text.equalsIgnoreCase("FIXED")
                            || text.equalsIgnoreCase("SCALED")));

            fixedPerfectIVs = builder
                    .comment("The number of perfect IVs for Shadow Pokémon when ivMode is FIXED.")
                    .translation("shadowedhearts.configuration.shadowStatChanges.fixedPerfectIVs")
                    .defineInRange("fixedPerfectIVs", 3, 0, 6);

            maxPerfectIVs = builder
                    .comment("The maximum number of perfect IVs for Shadow Pokémon when ivMode is SCALED.")
                    .translation("shadowedhearts.configuration.shadowStatChanges.maxPerfectIVs")
                    .defineInRange("maxPerfectIVs", 5, 0, 6);

            catchRateScaleEnabled = builder
                    .comment("If true, the catch rate of Shadow Pokémon scales with their maximum heart gauge.")
                    .translation("shadowedhearts.configuration.shadowStatChanges.catchRateScaleEnabled")
                    .define("catchRateScaleEnabled", true);

            catchRateMinMultiplier = builder
                    .comment("The minimum catch rate multiplier for Shadow Pokémon at maximum heart gauge.")
                    .translation("shadowedhearts.configuration.shadowStatChanges.catchRateMinMultiplier")
                    .defineInRange("catchRateMinMultiplier", 0.25, 0.0, 1.0);

            catchRateExponent = builder
                    .comment("The exponent for the catch rate scaling curve.")
                    .translation("shadowedhearts.configuration.shadowStatChanges.catchRateExponent")
                    .defineInRange("catchRateExponent", 1.4, 0.0, 10.0);
        }
    }

    public static final class ShadowBallConfig {
        public ModConfigSpec.DoubleValue penumbraBallCatchMultiplier;
        public ModConfigSpec.DoubleValue penumbraBallShadowCatchMultiplier;
        public ModConfigSpec.DoubleValue penumbraBallShadowConversionChance;
        public ModConfigSpec.DoubleValue penumbraBallHeartGaugeMaxBonus;

        public ModConfigSpec.DoubleValue antumbraBallCatchMultiplier;
        public ModConfigSpec.DoubleValue antumbraBallShadowCatchMultiplier;
        public ModConfigSpec.DoubleValue antumbraBallShadowConversionChance;
        public ModConfigSpec.DoubleValue antumbraBallHeartGaugeMaxBonus;

        public ModConfigSpec.DoubleValue umbraBallCatchMultiplier;
        public ModConfigSpec.DoubleValue umbraBallShadowCatchMultiplier;
        public ModConfigSpec.DoubleValue umbraBallShadowConversionChance;
        public ModConfigSpec.DoubleValue umbraBallHeartGaugeMaxBonus;

        private void build(ModConfigSpec.Builder builder) {
            penumbraBallCatchMultiplier = builder
                    .comment("Penumbra Ball catch multiplier for ordinary Pokemon, relative to a basic Poke Ball (default: 1x).")
                    .translation("shadowedhearts.configuration.umbraBall.catchMultiplier")
                    .defineInRange("catchMultiplier", 1.0, 0.1, 100.0);

            penumbraBallShadowCatchMultiplier = builder
                    .comment("Penumbra Ball total catch multiplier for existing Shadow Pokemon, relative to a basic Poke Ball. This replaces the ordinary catch multiplier rather than multiplying it.")
                    .translation("shadowedhearts.configuration.umbraBall.shadowCatchMultiplier")
                    .defineInRange("shadowCatchMultiplier", 1.5, 0.1, 100.0);

            penumbraBallShadowConversionChance = builder
                    .comment("Chance (0.0-1.0) to convert an ordinary Pokemon into a Shadow Pokemon after a successful Penumbra Ball capture.")
                    .translation("shadowedhearts.configuration.umbraBall.shadowConversionChance")
                    .defineInRange("shadowConversionChance", 0.10, 0.0, 1.0);

            penumbraBallHeartGaugeMaxBonus = builder
                    .comment("Fractional maximum Heart Gauge bonus when a Penumbra Ball captures an existing Shadow Pokemon (0.05 means 5%). Current purification progress is preserved.")
                    .translation("shadowedhearts.configuration.umbraBall.heartGaugeMaxBonus")
                    .defineInRange("heartGaugeMaxBonus", 0.05, 0.0, 10.0);

            antumbraBallCatchMultiplier = builder
                    .comment("Antumbra Ball catch multiplier for ordinary Pokemon, relative to a basic Poke Ball (default: 1.5x).")
                    .translation("shadowedhearts.configuration.umbraBall.antumbraCatchMultiplier")
                    .defineInRange("antumbraCatchMultiplier", 1.5, 0.1, 100.0);

            antumbraBallShadowCatchMultiplier = builder
                    .comment("Antumbra Ball total catch multiplier for existing Shadow Pokemon, relative to a basic Poke Ball. This replaces the ordinary catch multiplier rather than multiplying it.")
                    .translation("shadowedhearts.configuration.umbraBall.antumbraShadowCatchMultiplier")
                    .defineInRange("antumbraShadowCatchMultiplier", 2.0, 0.1, 100.0);

            antumbraBallShadowConversionChance = builder
                    .comment("Chance (0.0-1.0) to convert an ordinary Pokemon into a Shadow Pokemon after a successful Antumbra Ball capture.")
                    .translation("shadowedhearts.configuration.umbraBall.antumbraShadowConversionChance")
                    .defineInRange("antumbraShadowConversionChance", 0.20, 0.0, 1.0);

            antumbraBallHeartGaugeMaxBonus = builder
                    .comment("Fractional maximum Heart Gauge bonus when an Antumbra Ball captures an existing Shadow Pokemon (0.10 means 10%). Current purification progress is preserved.")
                    .translation("shadowedhearts.configuration.umbraBall.antumbraHeartGaugeMaxBonus")
                    .defineInRange("antumbraHeartGaugeMaxBonus", 0.10, 0.0, 10.0);

            umbraBallCatchMultiplier = builder
                    .comment("Umbra Ball catch multiplier for ordinary Pokemon, relative to a basic Poke Ball (default: 2x).")
                    .translation("shadowedhearts.configuration.umbraBall.umbraCatchMultiplier")
                    .defineInRange("umbraCatchMultiplier", 2.0, 0.1, 100.0);

            umbraBallShadowCatchMultiplier = builder
                    .comment("Umbra Ball total catch multiplier for existing Shadow Pokemon, relative to a basic Poke Ball. This replaces the ordinary catch multiplier rather than multiplying it.")
                    .translation("shadowedhearts.configuration.umbraBall.umbraShadowCatchMultiplier")
                    .defineInRange("umbraShadowCatchMultiplier", 2.5, 0.1, 100.0);

            umbraBallShadowConversionChance = builder
                    .comment("Chance (0.0-1.0) to convert an ordinary Pokemon into a Shadow Pokemon after a successful Umbra Ball capture.")
                    .translation("shadowedhearts.configuration.umbraBall.umbraShadowConversionChance")
                    .defineInRange("umbraShadowConversionChance", 0.35, 0.0, 1.0);

            umbraBallHeartGaugeMaxBonus = builder
                    .comment("Fractional maximum Heart Gauge bonus when an Umbra Ball captures an existing Shadow Pokemon (0.15 means 15%). Current purification progress is preserved.")
                    .translation("shadowedhearts.configuration.umbraBall.umbraHeartGaugeMaxBonus")
                    .defineInRange("umbraHeartGaugeMaxBonus", 0.15, 0.0, 10.0);
        }
    }

    public static final class DarkBallConfig {
        public ModConfigSpec.IntValue levelBoost;
        public ModConfigSpec.DoubleValue heartGaugeMultiplier;

        private void build(ModConfigSpec.Builder builder) {
            levelBoost = builder
                    .comment("Levels gained after a successful Dark Ball capture, subject to the active level cap. Set to 0 to disable the level bonus.")
                    .translation("shadowedhearts.configuration.darkBall.levelBoost")
                    .defineInRange("levelBoost", 5, 0, 100);

            heartGaugeMultiplier = builder
                    .comment("Maximum Heart Gauge after a Dark Ball capture, as a multiplier of the species' normal maximum (including server species overrides).",
                            "The gauge starts full. This does not multiply an existing Pokemon-specific maximum. Set to 1.0 for the normal species maximum.")
                    .translation("shadowedhearts.configuration.darkBall.heartGaugeMultiplier")
                    .defineInRange("heartGaugeMultiplier", 2.0, 1.0, 100.0);
        }
    }

    public static final class AuraScannerConfig {
        public ModConfigSpec.IntValue auraScannerShadowRange;
        public ModConfigSpec.IntValue auraReaderPulseCooldownTicks;
        public ModConfigSpec.BooleanValue auraReaderRequiredForAura;
        public ModConfigSpec.IntValue auraLockMaxSeconds;
        public ModConfigSpec.IntValue auraLockRange;

        private void build(ModConfigSpec.Builder builder) {
            auraScannerShadowRange = builder
                    .comment("The range (in blocks) at which the Aura Scanner can detect Shadow Pokemon.")
                    .translation("shadowedhearts.configuration.auraScanner.auraScannerShadowRange")
                    .defineInRange("auraScannerShadowRange", 128, 1, 512);

            auraReaderPulseCooldownTicks = builder
                    .comment("Cooldown in ticks after an accepted Aura Reader pulse scan.")
                    .translation("shadowedhearts.configuration.auraScanner.auraReaderPulseCooldownTicks")
                    .defineInRange("auraReaderPulseCooldownTicks", 40, 0, 20 * 60);

            auraReaderRequiredForAura = builder
                    .comment("Whether the Aura Reader is required to see Shadow Auras in the overworld.")
                    .translation("shadowedhearts.configuration.auraScanner.auraReaderRequiredForAura")
                    .define("auraReaderRequiredForAura", false);

            builder.translation("shadowedhearts.configuration.auraScanner.auraLock").push("auraLock");
            auraLockMaxSeconds = builder
                    .comment("Maximum seconds a single aura lock request can persist before expiring.")
                    .translation("shadowedhearts.configuration.auraScanner.auraLock.maxSeconds")
                    .defineInRange("maxSeconds", 60, 1, 600);

            auraLockRange = builder
                    .comment("Maximum distance (blocks) from the player to the target Pokemon to accept a lock request.")
                    .translation("shadowedhearts.configuration.auraScanner.auraLock.range")
                    .defineInRange("range", 96, 8, 512);

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
                    .translation("shadowedhearts.configuration.shadowGimmickRestrictions.shadowCanMegaEvolve")
                    .define("shadowCanMegaEvolve", false);
            shadowCanDynamax = builder
                    .comment("Allow shadow Pokémon to dynamax/gigantamax.")
                    .translation("shadowedhearts.configuration.shadowGimmickRestrictions.shadowCanDynamax")
                    .define("shadowCanDynamax", false);
            shadowCanTerastallize = builder
                    .comment("Allow shadow Pokémon to terastallize.")
                    .translation("shadowedhearts.configuration.shadowGimmickRestrictions.shadowCanTerastallize")
                    .define("shadowCanTerastallize", false);
            shadowCanUseZMoves = builder
                    .comment("Allow shadow Pokémon to use Z-moves.")
                    .translation("shadowedhearts.configuration.shadowGimmickRestrictions.shadowCanUseZMoves")
                    .define("shadowCanUseZMoves", false);
            shadowCanUltraBurst = builder
                    .comment("Allow shadow Pokémon to ultra burst.")
                    .translation("shadowedhearts.configuration.shadowGimmickRestrictions.shadowCanUltraBurst")
                    .define("shadowCanUltraBurst", false);
        }
    }

    public static final class RelicStoneConfig {
        public ModConfigSpec.IntValue cooldownMinutes;


        private void build(ModConfigSpec.Builder builder) {
            cooldownMinutes = builder
                    .comment("Cooldown in minutes between using the Relic Stone's purification function.")
                    .translation("shadowedhearts.configuration.relicStone.cooldownMinutes")
                    .defineInRange("cooldownMinutes", 5, 0, Integer.MAX_VALUE);
        }
    }

    public static final class PurificationChamberConfig {
        public ModConfigSpec.IntValue stepRequirement;

        private void build(ModConfigSpec.Builder builder) {
            stepRequirement = builder
                    .comment("The number of steps required to advance the purification chamber by one tick.")
                    .translation("shadowedhearts.configuration.purificationChamber.stepRequirement")
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
                    .translation("shadowedhearts.configuration.modIntegrations.rctmod.enabled")
                    .define("enabled", Platform.isModLoaded("rctmod"));

            limitExpToRCTCap = builder
                    .comment("If true, the XP buffer will be capped to the player's current RCT level cap during purification.")
                    .translation("shadowedhearts.configuration.modIntegrations.rctmod.limitExpToRCTCap")
                    .define("limitExpToRCTCap", false);
            
            builder.translation("shadowedhearts.configuration.modIntegrations.rctmod.append").push("append");
            append = new RCTSection();
            append.build(builder, "append");
            builder.pop();
            
            builder.translation("shadowedhearts.configuration.modIntegrations.rctmod.convert").push("convert");
            convert = new RCTSection();
            convert.build(builder, "convert");
            builder.pop();
            
            builder.translation("shadowedhearts.configuration.modIntegrations.rctmod.replace").push("replace");
            replace = new RCTSection(true);
            replace.build(builder, "replace");
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

        private void build(ModConfigSpec.Builder builder, String sectionPath) {
            trainerTypes = builder
                    .comment("The trainer types that this section applies to.")
                    .translation("shadowedhearts.configuration.modIntegrations.rctmod." + sectionPath + ".trainerTypes")
                    .defineListAllowEmpty("trainerTypes",
                    isDefaultReplace ? List.of("team_rocket", "team_galactic", "team_shadow") : List.of("normal"),
                    () -> "normal", ModConfig::isNonBlankString);
            
            typePresets = builder
                    .comment("Format: type=presetId. Mapping of trainer types to their respective Shadow Pokémon presets.")
                    .translation("shadowedhearts.configuration.modIntegrations.rctmod." + sectionPath + ".typePresets")
                    .defineListAllowEmpty("typePresets",
                            isDefaultReplace ? List.of("team_rocket=shadowedhearts/team_rocket", "team_galactic=shadowedhearts/team_galactic", "team_shadow=shadowedhearts/team_shadow") : List.of("normal=shadowedhearts/normal_20"),
                            () -> "normal=shadowedhearts/normal_20", ModConfig::isPresetMapping);
            
            trainerBlacklist = builder
                    .comment("Excludes these trainer IDs from this section only. Rules in other sections may still apply.")
                    .translation("shadowedhearts.configuration.modIntegrations.rctmod." + sectionPath + ".trainerBlacklist")
                    .defineListAllowEmpty("trainerBlacklist", Collections.emptyList(), () -> "normal", ModConfig::isNonBlankString);
            
            trainers = builder
                    .comment("Format: id;preset;tag1,tag2... Specific trainer definitions with presets and optional tags.")
                    .translation("shadowedhearts.configuration.modIntegrations.rctmod." + sectionPath + ".trainers")
                    .defineListAllowEmpty("trainers",
                            isDefaultReplace ? List.of("team_rocket;shadowedhearts/team_rocket;", "team_galactic;shadowedhearts/team_galactic;", "team_shadow;shadowedhearts/team_shadow;") : List.of("normal;shadowedhearts/normal_20;"),
                            () -> "normal;shadowedhearts/normal_20;", ModConfig::isTrainerDefinition);
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

    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && !text.isBlank();
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

    private static boolean isPresetMapping(Object value) {
        if (!(value instanceof String text)) return false;
        String[] parts = text.split("=", 2);
        return parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank();
    }

    private static boolean isTrainerDefinition(Object value) {
        if (!(value instanceof String text)) return false;
        // A trainer ID alone is supported; preset and tag fields are optional.
        return !text.split(";", -1)[0].isBlank();
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
