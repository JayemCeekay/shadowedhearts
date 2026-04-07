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
        SPEC = builder.build();
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
        public ModConfigSpec.BooleanValue shadowfallActive;
        public ModConfigSpec.IntValue impactChanceOneInTicks;
        public ModConfigSpec.IntValue civilizedHeatmapThreshold;
        public ModConfigSpec.IntValue heatmapDecayTicks;
        public ModConfigSpec.DoubleValue heatmapDecayAmount;
        public ModConfigSpec.IntValue minImpactDistanceToPlayer;
        public ModConfigSpec.IntValue maxImpactDistanceToPlayer;
        public ModConfigSpec.IntValue minImpactDistanceToStructures;
        public ModConfigSpec.IntValue minImpactDistanceToSpawn;
        public ModConfigSpec.IntValue minCraterRadius;
        public ModConfigSpec.IntValue maxCraterRadius;
        public ModConfigSpec.IntValue heatmapPresenceRadius;
        public ModConfigSpec.IntValue heatmapFlushIntervalTicks;
        public ModConfigSpec.IntValue meteoroidImpactBroadcastRadius;
        public ModConfigSpec.BooleanValue meteoroidShadowTransformationEnabled;
        public ModConfigSpec.IntValue meteoroidShadowTransformationRadius;
        public ModConfigSpec.IntValue meteoroidShadowTransformationCheckIntervalTicks;
        public ModConfigSpec.DoubleValue meteoroidShadowTransformationChancePerInterval;
        public ModConfigSpec.DoubleValue meteoroidShadowTransformationExposureIncrease;
        public ModConfigSpec.DoubleValue meteoroidShadowTransformationExposureDecay;
        public ModConfigSpec.DoubleValue meteoroidShadowSpawnChanceMultiplier;
        public ModConfigSpec.BooleanValue meteoroidWorldGenEnabled;
        public ModConfigSpec.IntValue meteoroidSpacing;
        public ModConfigSpec.IntValue meteoroidSeparation;
        public ModConfigSpec.ConfigValue<List<? extends String>> meteoroidBiomeBlacklist;
        public ModConfigSpec.ConfigValue<List<? extends String>> meteoroidBiomeWhitelist;

        private void build(ModConfigSpec.Builder builder) {
            shadowfallActive = builder
                    .comment("Whether the Shadowfall event is active, enabling meteoroid impacts.")
                    .define("shadowfallActive", false);
            
            impactChanceOneInTicks = builder
                    .comment("The average number of ticks between impact attempts. (e.g., 12000 ticks = 10 minutes)")
                    .defineInRange("impactChanceOneInTicks", 12000, 1, Integer.MAX_VALUE);
            
            civilizedHeatmapThreshold = builder
                    .comment("Chunks with activity heatmap above this value are considered 'civilized' and protected from impacts.")
                    .defineInRange("civilizedHeatmapThreshold", 100, 0, Integer.MAX_VALUE);
            
            heatmapDecayTicks = builder
                    .comment("How often (in ticks) the player activity heatmap decays.")
                    .defineInRange("heatmapDecayTicks", 1200, 1, Integer.MAX_VALUE);
            
            heatmapDecayAmount = builder
                    .comment("The amount of activity that decays from each chunk every decay cycle.")
                    .defineInRange("heatmapDecayAmount", 1.0, 0.0, 1000.0);
            
            minImpactDistanceToPlayer = builder
                    .comment("Minimum distance (in blocks) from any player for an impact to occur.")
                    .defineInRange("minImpactDistanceToPlayer", 64, 0, Integer.MAX_VALUE);
            
            maxImpactDistanceToPlayer = builder
                    .comment("Maximum distance (in blocks) from a player to consider for an impact.")
                    .defineInRange("maxImpactDistanceToPlayer", 256, 1, Integer.MAX_VALUE);
            
            minImpactDistanceToStructures = builder
                    .comment("Minimum distance (in blocks) from any generated structures for an impact to occur.")
                    .defineInRange("minImpactDistanceToStructures", 64, 0, Integer.MAX_VALUE);
            
            minImpactDistanceToSpawn = builder
                    .comment("Minimum distance (in blocks) from the world spawn point for an impact to occur.")
                    .defineInRange("minImpactDistanceToSpawn", 128, 0, Integer.MAX_VALUE);
            
            minCraterRadius = builder
                    .comment("Minimum radius of generated craters.")
                    .defineInRange("minCraterRadius", 8, 1, 100);
            
            maxCraterRadius = builder
                    .comment("Maximum radius of generated craters.")
                    .defineInRange("maxCraterRadius", 16, 1, 100);
            
            heatmapPresenceRadius = builder
                    .comment("Radius (in chunks) around players where activity heatmap is increased.")
                    .defineInRange("heatmapPresenceRadius", 2, 0, 16);
            
            heatmapFlushIntervalTicks = builder
                    .comment("How often (in ticks) the activity heatmap is flushed to disk.")
                    .defineInRange("heatmapFlushIntervalTicks", 1200, 1, Integer.MAX_VALUE);
            
            meteoroidImpactBroadcastRadius = builder
                    .comment("Radius (in blocks) within which players will receive a message and hear a sound when a meteoroid impacts.")
                    .defineInRange("meteoroidImpactBroadcastRadius", 256, 0, Integer.MAX_VALUE);
            
            meteoroidShadowTransformationEnabled = builder
                    .comment("Whether wild Pokemon near shadowfall meteoroids can become Shadow Pokemon over time.")
                    .define("meteoroidShadowTransformationEnabled", true);
            
            meteoroidShadowTransformationRadius = builder
                    .comment("Radius around meteoroids to check for wild Pokemon.")
                    .defineInRange("meteoroidShadowTransformationRadius", 16, 1, 64);
            
            meteoroidShadowTransformationCheckIntervalTicks = builder
                    .comment("How often (in ticks) to check for wild Pokemon near meteoroids.")
                    .defineInRange("meteoroidShadowTransformationCheckIntervalTicks", 100, 20, 12000);
            
            meteoroidShadowTransformationChancePerInterval = builder
                    .comment("The base chance per check interval for a wild Pokemon near a meteoroid to become a Shadow Pokemon.")
                    .defineInRange("meteoroidShadowTransformationChancePerInterval", 0.05, 0.0, 1.0);
            
            meteoroidShadowTransformationExposureIncrease = builder
                    .comment("The amount of exposure a wild Pokemon gains per check interval when near a meteoroid.")
                    .defineInRange("meteoroidShadowTransformationExposureIncrease", 1.0, 0.0, 100.0);
            
            meteoroidShadowTransformationExposureDecay = builder
                    .comment("The amount of exposure a wild Pokemon loses per check interval when NOT near a meteoroid.")
                    .defineInRange("meteoroidShadowTransformationExposureDecay", 0.5, 0.0, 100.0);
            
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
        public boolean shadowfallActive() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.shadowfallActive();
            return shadowfallActive.get();
        }

        @Override
        public int impactChanceOneInTicks() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.impactChanceOneInTicks();
            return impactChanceOneInTicks.get();
        }

        @Override
        public int civilizedHeatmapThreshold() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.civilizedHeatmapThreshold();
            return civilizedHeatmapThreshold.get();
        }

        @Override
        public int heatmapDecayTicks() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.heatmapDecayTicks();
            return heatmapDecayTicks.get();
        }

        @Override
        public double heatmapDecayAmount() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.heatmapDecayAmount();
            return heatmapDecayAmount.get();
        }

        @Override
        public int minImpactDistanceToPlayer() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.minImpactDistanceToPlayer();
            return minImpactDistanceToPlayer.get();
        }

        @Override
        public int maxImpactDistanceToPlayer() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.maxImpactDistanceToPlayer();
            return maxImpactDistanceToPlayer.get();
        }

        @Override
        public int minImpactDistanceToStructures() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.minImpactDistanceToStructures();
            return minImpactDistanceToStructures.get();
        }

        @Override
        public int minImpactDistanceToSpawn() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.minImpactDistanceToSpawn();
            return minImpactDistanceToSpawn.get();
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
        public int heatmapPresenceRadius() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.heatmapPresenceRadius();
            return heatmapPresenceRadius.get();
        }

        @Override
        public int heatmapFlushIntervalTicks() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.heatmapFlushIntervalTicks();
            return heatmapFlushIntervalTicks.get();
        }

        @Override
        public int meteoroidImpactBroadcastRadius() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidImpactBroadcastRadius();
            return meteoroidImpactBroadcastRadius.get();
        }

        @Override
        public boolean meteoroidShadowTransformationEnabled() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowTransformationEnabled();
            return meteoroidShadowTransformationEnabled.get();
        }

        @Override
        public int meteoroidShadowTransformationRadius() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowTransformationRadius();
            return meteoroidShadowTransformationRadius.get();
        }

        @Override
        public int meteoroidShadowTransformationCheckIntervalTicks() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowTransformationCheckIntervalTicks();
            return meteoroidShadowTransformationCheckIntervalTicks.get();
        }

        @Override
        public double meteoroidShadowTransformationChancePerInterval() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowTransformationChancePerInterval();
            return meteoroidShadowTransformationChancePerInterval.get();
        }

        @Override
        public double meteoroidShadowTransformationExposureIncrease() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowTransformationExposureIncrease();
            return meteoroidShadowTransformationExposureIncrease.get();
        }

        @Override
        public double meteoroidShadowTransformationExposureDecay() {
            if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowTransformationExposureDecay();
            return meteoroidShadowTransformationExposureDecay.get();
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

        private void build(ModConfigSpec.Builder builder) {
            enabled = builder
                    .comment("Whether Shadow Pokemon do 20% more and take 20% more damage like in Pokemon GO.")
                    .define("enabled", false);
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
        public ModConfigSpec.IntValue trailMinNodeDistance;
        public ModConfigSpec.IntValue trailMaxNodeDistance;
        public ModConfigSpec.BooleanValue auraReaderRequiredForAura;
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

            trailMinNodeDistance = builder
                    .comment("The minimum distance (in blocks) between evidence nodes in a trail.")
                    .defineInRange("trailMinNodeDistance", 8, 1, 64);

            trailMaxNodeDistance = builder
                    .comment("The maximum distance (in blocks) between evidence nodes in a trail.")
                    .defineInRange("trailMaxNodeDistance", 18, 1, 128);

            auraReaderRequiredForAura = builder
                    .comment("Whether the Aura Reader is required to see Shadow Auras in the overworld.")
                    .define("auraReaderRequiredForAura", false);

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

        private void build(ModConfigSpec.Builder builder) {
            maxOverrides = builder
                    .comment("List of species-specific maximum Heart Gauge values. Format: species=max")
                    .defineList("maxOverrides", List.of(), o -> o instanceof String && ((String) o).contains("="));
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
        Shadowedhearts.LOGGER.info("ModConfig loaded via Forge Config API Port.");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
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
