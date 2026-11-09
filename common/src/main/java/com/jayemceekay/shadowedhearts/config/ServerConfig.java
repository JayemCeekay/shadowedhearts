package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundDifficulty;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.List;

/** Per-world generation, Umbrafall and Moonwound settings stored in server.toml. */
public final class ServerConfig implements IWorldAlterationConfig {
    public static final ServerConfig INSTANCE = new ServerConfig();
    public static final ModConfigSpec SPEC;
    private boolean loaded;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("World generation settings apply to future placement attempts; existing terrain is unchanged.")
                .translation("shadowedhearts.configuration.worldAlteration").push("worldAlteration");
        INSTANCE.build(builder);
        builder.pop();
        builder.comment("Moonwound quotas and opponent levels are saved when a lobby is created. Recovery uses running server time; server downtime does not consume these timers.")
                .translation("shadowedhearts.configuration.moonwound").push("moonwound");
        INSTANCE.newEventsEnabled = builder
                .comment("Allow creation of new Moonwound lobbies. Disabling this does not close existing lobbies or stop them from starting, and does not interrupt started events or rewards. Use operator cancellation to close an existing event.")
                .translation("shadowedhearts.configuration.moonwound.newEventsEnabled")
                .define("newEventsEnabled", true);
        INSTANCE.shellExcavationTarget = builder
                .comment("Shared original-block target for the outer shell. Saved when the lobby is created and capped to available originals at Start.")
                .translation("shadowedhearts.configuration.moonwound.shellExcavationTarget")
                .defineInRange("shellExcavationTarget", 8, 1, 750);
        INSTANCE.mantleExcavationTarget = builder
                .comment("Shared original-block target for the lattice mantle. Saved when the lobby is created and capped to available originals at Start.")
                .translation("shadowedhearts.configuration.moonwound.mantleExcavationTarget")
                .defineInRange("mantleExcavationTarget", 6, 1, 750);
        INSTANCE.casingExcavationTarget = builder
                .comment("Shared original-block target for the core casing. Saved when the lobby is created and capped to available originals at Start.")
                .translation("shadowedhearts.configuration.moonwound.casingExcavationTarget")
                .defineInRange("casingExcavationTarget", 4, 1, 750);
        INSTANCE.shellWaveOpponentsPerMember = builder
                .comment("Shell-wave opponents per member of the roster frozen at Start. Saved when the lobby is created; the total does not shrink when members leave.")
                .translation("shadowedhearts.configuration.moonwound.shellWaveOpponentsPerMember")
                .defineInRange("shellWaveOpponentsPerMember", 2, 1, 16);
        INSTANCE.mantleWaveOpponentsPerMember = builder
                .comment("Mantle-wave opponents per member of the roster frozen at Start. Saved when the lobby is created; the total does not shrink when members leave.")
                .translation("shadowedhearts.configuration.moonwound.mantleWaveOpponentsPerMember")
                .defineInRange("mantleWaveOpponentsPerMember", 3, 1, 16);
        INSTANCE.casingWaveOpponentsPerMember = builder
                .comment("Casing-wave opponents per member of the roster frozen at Start. Saved when the lobby is created; the total does not shrink when members leave.")
                .translation("shadowedhearts.configuration.moonwound.casingWaveOpponentsPerMember")
                .defineInRange("casingWaveOpponentsPerMember", 4, 1, 16);
        INSTANCE.strongWaveLevel = builder
                .comment("Void Shadow level for Strong-site waves. Saved when the lobby is created.")
                .translation("shadowedhearts.configuration.moonwound.strongWaveLevel")
                .defineInRange("strongWaveLevel", 40, 1, 100);
        INSTANCE.resonantWaveLevel = builder
                .comment("Void Shadow level for Resonant-site waves. Saved when the lobby is created.")
                .translation("shadowedhearts.configuration.moonwound.resonantWaveLevel")
                .defineInRange("resonantWaveLevel", 55, 1, 100);
        INSTANCE.strongAlphaLevel = builder
                .comment("Initial Alpha finale level at Strong sites. Saved when the lobby is created. Native Alpha party-level matching can subsequently change it.")
                .translation("shadowedhearts.configuration.moonwound.strongAlphaLevel")
                .defineInRange("strongAlphaLevel", 50, 1, 100);
        INSTANCE.resonantAlphaLevel = builder
                .comment("Initial Alpha fallback level at Resonant sites without the supported Raid Dens integration. Saved when the lobby is created. Native Alpha party-level matching can subsequently change it.")
                .translation("shadowedhearts.configuration.moonwound.resonantAlphaLevel")
                .defineInRange("resonantAlphaLevel", 65, 1, 100);
        INSTANCE.raidBossLevel = builder
                .comment("Resonant raid boss level override, saved when the lobby is created. Zero keeps the datapack-authored boss level (65 in the bundled definition); 1-100 overrides it. Already issued bosses retain their saved state.")
                .translation("shadowedhearts.configuration.moonwound.raidBossLevel")
                .defineInRange("raidBossLevel", 0, 0, 100);
        INSTANCE.missingMemberGraceSeconds = builder
                .comment("Seconds a member may be offline or away before leaving a lobby or temporarily leaving an active event's voting electorate. Active members keep their participation and rewards; returning restores their vote.")
                .translation("shadowedhearts.configuration.moonwound.missingMemberGraceSeconds")
                .defineInRange("missingMemberGraceSeconds", 120, 10, 3600);
        INSTANCE.lobbyIdleTimeoutSeconds = builder
                .comment("Close an unstarted lobby after this many seconds without a successful party action. Nearby idle players and screen refreshes do not extend it. The core is left untouched.")
                .translation("shadowedhearts.configuration.moonwound.lobbyIdleTimeoutSeconds")
                .defineInRange("lobbyIdleTimeoutSeconds", 300, 30, 7200);
        INSTANCE.excavationPaceSeconds = builder
                .comment("Soft bonus target for each excavation stage. Completing in time heals conscious party Pokemon by 25% of maximum HP before combat. Missing the target never fails the event. Captured when the event starts.")
                .translation("shadowedhearts.configuration.moonwound.excavationPaceSeconds")
                .defineInRange("excavationPaceSeconds", 90, 10, 3600);
        INSTANCE.wavePaceSecondsPerOpponent = builder
                .comment("Soft bonus target per opponent per original member, using the event's saved wave sizes. Fast waves grant 60 seconds of Haste I for excavation; the final wave prepares a 25% party heal if Overload is chosen. Captured when the event starts.")
                .translation("shadowedhearts.configuration.moonwound.wavePaceSecondsPerOpponent")
                .defineInRange("wavePaceSecondsPerOpponent", 90, 10, 3600);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {
    }

    public void load() {
        loaded = true;
        Shadowedhearts.LOGGER.info("ServerConfig loaded via Forge Config API Port.");
    }

    public boolean isLoaded() {
        return loaded && SPEC.isLoaded();
    }

    public ModConfigSpec.DoubleValue meteoroidShadowSpawnChanceMultiplier;
    public ModConfigSpec.BooleanValue meteoroidWorldGenEnabled;
    public ModConfigSpec.BooleanValue umbrafallSignalsEnabled;
    public ModConfigSpec.DoubleValue craterRescueChance;
    public ModConfigSpec.EnumValue<CraterCastSource> craterCastSource;
    public ModConfigSpec.IntValue craterRescueWeight;
    public ModConfigSpec.IntValue craterOutbreakWeight;
    public ModConfigSpec.IntValue craterHowlWeight;
    public ModConfigSpec.IntValue craterHowlScentReward;
    public ModConfigSpec.IntValue craterRescueAttackers;
    public ModConfigSpec.IntValue craterOutbreakPackSize;
    public ModConfigSpec.IntValue craterOutbreakScatterDistance;
    public ModConfigSpec.IntValue craterOutbreakDustReward;
    public ModConfigSpec.IntValue craterRescueInterventionSeconds;
    public ModConfigSpec.EnumValue<UmbrafallSignalMode> umbrafallSignalMode;
    public ModConfigSpec.IntValue umbrafallSignalLocateMinDistance;
    public ModConfigSpec.IntValue umbrafallSignalLocateMaxDistance;
    public ModConfigSpec.IntValue umbrafallSignalLifetimeSeconds;
    public ModConfigSpec.ConfigValue<List<? extends String>> umbrafallSignalAllowedTiers;
    public ModConfigSpec.IntValue umbrafallWorkPerTick;
    public ModConfigSpec.IntValue meteoroidSpacing;
    public ModConfigSpec.IntValue meteoroidSeparation;
    public ModConfigSpec.ConfigValue<List<? extends String>> meteoroidBiomeBlacklist;
    public ModConfigSpec.ConfigValue<List<? extends String>> meteoroidBiomeWhitelist;
    public ModConfigSpec.BooleanValue newEventsEnabled;
    public ModConfigSpec.IntValue shellExcavationTarget;
    public ModConfigSpec.IntValue mantleExcavationTarget;
    public ModConfigSpec.IntValue casingExcavationTarget;
    public ModConfigSpec.IntValue shellWaveOpponentsPerMember;
    public ModConfigSpec.IntValue mantleWaveOpponentsPerMember;
    public ModConfigSpec.IntValue casingWaveOpponentsPerMember;
    public ModConfigSpec.IntValue strongWaveLevel;
    public ModConfigSpec.IntValue resonantWaveLevel;
    public ModConfigSpec.IntValue strongAlphaLevel;
    public ModConfigSpec.IntValue resonantAlphaLevel;
    public ModConfigSpec.IntValue raidBossLevel;
    public ModConfigSpec.IntValue missingMemberGraceSeconds;
    public ModConfigSpec.IntValue lobbyIdleTimeoutSeconds;
    public ModConfigSpec.IntValue excavationPaceSeconds;
    public ModConfigSpec.IntValue wavePaceSecondsPerOpponent;

    public boolean newEventsEnabled() {
        return !isLoaded() || newEventsEnabled.get();
    }

    public double craterRescueChance() {
        return isLoaded() ? craterRescueChance.get() : 0.35;
    }

    public CraterCastSource craterCastSource() {
        return isLoaded() ? craterCastSource.get() : CraterCastSource.DATAPACK;
    }

    public int craterRescueWeight() {
        return isLoaded() ? craterRescueWeight.get() : 1;
    }

    public int craterOutbreakWeight() {
        return isLoaded() ? craterOutbreakWeight.get() : 1;
    }

    public int craterHowlWeight() {
        return isLoaded() ? craterHowlWeight.get() : 1;
    }

    public int craterHowlScentReward() {
        return isLoaded() ? craterHowlScentReward.get() : 1;
    }

    public int craterRescueAttackers() {
        return isLoaded() ? craterRescueAttackers.get() : 3;
    }

    public int craterOutbreakPackSize() {
        return isLoaded() ? craterOutbreakPackSize.get() : 3;
    }

    public int craterOutbreakScatterDistance() {
        return isLoaded() ? craterOutbreakScatterDistance.get() : 4;
    }

    public int craterOutbreakDustReward() {
        return isLoaded() ? craterOutbreakDustReward.get() : 3;
    }

    public int craterRescueInterventionSeconds() {
        return isLoaded() ? craterRescueInterventionSeconds.get() : 0;
    }

    /** A fresh shell/mantle/casing array for each new lobby. */
    public int[] excavationTargets() {
        return isLoaded() ? new int[]{shellExcavationTarget.get(), mantleExcavationTarget.get(), casingExcavationTarget.get()}
                : new int[]{8, 6, 4};
    }

    /** A fresh shell/mantle/casing array, before multiplication by the frozen roster. */
    public int[] waveOpponentsPerMember() {
        return isLoaded() ? new int[]{shellWaveOpponentsPerMember.get(), mantleWaveOpponentsPerMember.get(), casingWaveOpponentsPerMember.get()}
                : new int[]{2, 3, 4};
    }

    public MoonwoundDifficulty moonwoundDifficulty() {
        return isLoaded() ? new MoonwoundDifficulty(strongWaveLevel.get(), resonantWaveLevel.get(),
                strongAlphaLevel.get(), resonantAlphaLevel.get(), raidBossLevel.get()) : MoonwoundDifficulty.DEFAULT;
    }

    public int missingMemberGraceSeconds() {
        return isLoaded() ? missingMemberGraceSeconds.get() : 120;
    }

    public int lobbyIdleTimeoutSeconds() {
        return isLoaded() ? lobbyIdleTimeoutSeconds.get() : 300;
    }

    public int excavationPaceSeconds() {
        return isLoaded() ? excavationPaceSeconds.get() : 90;
    }

    public int wavePaceSecondsPerOpponent() {
        return isLoaded() ? wavePaceSecondsPerOpponent.get() : 90;
    }

    private void build(ModConfigSpec.Builder builder) {
        craterRescueChance = builder
                .comment("Chance of any field event when a trainer first approaches a ready Weak or Moderate site. Each site rolls once, then selects one event by weight. Keeps the original craterRescueChance key for existing worlds.")
                .translation("shadowedhearts.configuration.worldAlteration.craterRescueChance")
                .defineInRange("craterRescueChance", 0.35, 0.0, 1.0);
        craterRescueWeight = builder
                .comment("Relative weight of Cornered at the Crater on a successful field event roll. Zero disables new rescues.")
                .translation("shadowedhearts.configuration.worldAlteration.craterRescueWeight")
                .defineInRange("craterRescueWeight", 1, 0, 10000);
        craterCastSource = builder
                .comment("Species source for new crater field events. DATAPACK uses authored casts. LOCAL_SPAWNS samples eligible local dry-land Cobblemon spawns and leaves the site quiet when none qualify. LOCAL_OR_DATAPACK tries local spawns first and uses an authored cast if needed. Existing events keep their saved Pokemon.")
                .translation("shadowedhearts.configuration.worldAlteration.craterCastSource")
                .defineEnum("craterCastSource", CraterCastSource.DATAPACK);
        craterOutbreakWeight = builder
                .comment("Relative weight of The Outbreak on a successful field event roll. Zero disables new outbreaks.")
                .translation("shadowedhearts.configuration.worldAlteration.craterOutbreakWeight")
                .defineInRange("craterOutbreakWeight", 1, 0, 10000);
        craterHowlWeight = builder
                .comment("Relative weight of Answering the Howl on a successful field event roll. Zero disables new howls.")
                .translation("shadowedhearts.configuration.worldAlteration.craterHowlWeight")
                .defineInRange("craterHowlWeight", 1, 0, 10000);
        craterHowlScentReward = builder
                .comment("Joy Scents awarded once when the trainer settles a new howl encounter. Zero disables the item reward.")
                .translation("shadowedhearts.configuration.worldAlteration.craterHowlScentReward")
                .defineInRange("craterHowlScentReward", 1, 0, 64);
        craterRescueAttackers = builder
                .comment("Number of Shadow attackers guarding the ordinary victim. Saved when a rescue is created.")
                .translation("shadowedhearts.configuration.worldAlteration.craterRescueAttackers")
                .defineInRange("craterRescueAttackers", 3, 1, 6);
        craterOutbreakPackSize = builder
                .comment("Total Shadow Pokémon in a new outbreak, including its single fragment carrier.")
                .translation("shadowedhearts.configuration.worldAlteration.craterOutbreakPackSize")
                .defineInRange("craterOutbreakPackSize", 3, 2, 6);
        craterOutbreakScatterDistance = builder
                .comment("Distance in blocks from a visible member of the gathered outbreak that triggers immediate scatter. Saved when an outbreak is created.")
                .translation("shadowedhearts.configuration.worldAlteration.craterOutbreakScatterDistance")
                .defineInRange("craterOutbreakScatterDistance", 4, 1, 8);
        craterOutbreakDustReward = builder
                .comment("Shadow Dust awarded once for containing a new outbreak. Zero disables the item reward.")
                .translation("shadowedhearts.configuration.worldAlteration.craterOutbreakDustReward")
                .defineInRange("craterOutbreakDustReward", 3, 0, 64);
        craterRescueInterventionSeconds = builder
                .comment("Seconds to challenge the first attacker in Cornered at the Crater while trainers are nearby. Zero disables the timer. Battles and reward decisions have no deadline.")
                .translation("shadowedhearts.configuration.worldAlteration.craterRescueInterventionSeconds")
                .defineInRange("craterRescueInterventionSeconds", 0, 0, 3600);
        meteoroidShadowSpawnChanceMultiplier = builder
                .comment("Multiplier applied to the base Shadow spawn chance near active Umbrafall cores.")
                .translation("shadowedhearts.configuration.worldAlteration.meteoroidShadowSpawnChanceMultiplier")
                .defineInRange("meteoroidShadowSpawnChanceMultiplier", 5.0, 0.0, 100.0);

        meteoroidWorldGenEnabled = builder
                .comment("Whether natural Umbrafall sites are placed during new chunk generation. Shadow Signal Data generation is controlled separately.")
                .translation("shadowedhearts.configuration.worldAlteration.meteoroidWorldGenEnabled")
                .define("meteoroidWorldGenEnabled", true);

        umbrafallSignalsEnabled = builder
                .comment("Whether Shadow Signal Data can locate or generate Umbrafall sites, independently of natural chunk generation.")
                .translation("shadowedhearts.configuration.worldAlteration.umbrafallSignalsEnabled")
                .define("umbrafallSignalsEnabled", true);

        umbrafallSignalMode = builder
                .comment("LOCATE_OR_GENERATE finds a nearby matching site or prepares a new site. LOCATE_ONLY only finds existing sites and never consumes a signal when none is available. Restricting this cancels uncommitted searches; already reserved terrain placement finishes normally.")
                .translation("shadowedhearts.configuration.worldAlteration.umbrafallSignalMode")
                .defineEnum("umbrafallSignalMode", UmbrafallSignalMode.LOCATE_OR_GENERATE);
        umbrafallSignalLocateMinDistance = builder
                .comment("Minimum distance in blocks to locate an existing site. Values above the maximum are limited to that maximum. Does not change new-site generation distance.")
                .translation("shadowedhearts.configuration.worldAlteration.umbrafallSignalLocateMinDistance")
                .defineInRange("umbrafallSignalLocateMinDistance", 64, 0, 32768);
        umbrafallSignalLocateMaxDistance = builder
                .comment("Maximum distance in blocks to locate an existing site. Sites still require a safe, loaded footprint; this never loads distant terrain. Does not change new-site generation distance.")
                .translation("shadowedhearts.configuration.worldAlteration.umbrafallSignalLocateMaxDistance")
                .defineInRange("umbrafallSignalLocateMaxDistance", 640, 1, 32768);
        umbrafallSignalLifetimeSeconds = builder
                .comment("Guidance lifetime in seconds of world game time when a site is assigned or finishes placement. Existing guidance keeps its saved expiry.")
                .translation("shadowedhearts.configuration.worldAlteration.umbrafallSignalLifetimeSeconds")
                .defineInRange("umbrafallSignalLifetimeSeconds", 3600, 1, 604800);
        umbrafallSignalAllowedTiers = builder
                .comment("Signal tiers allowed to locate or generate sites: faint, weak, moderate, strong, resonant. Empty disables all signal tiers. Existing assigned sites and reserved placement are unaffected.")
                .translation("shadowedhearts.configuration.worldAlteration.umbrafallSignalAllowedTiers")
                .defineListAllowEmpty("umbrafallSignalAllowedTiers", List.of("faint", "weak", "moderate", "strong", "resonant"),
                        () -> "weak", value -> value instanceof String tier && List.of("faint", "weak", "moderate", "strong", "resonant").contains(tier));

        umbrafallWorkPerTick = builder
                .comment("Maximum Umbrafall site search, validation, and placement work units per server tick.")
                .translation("shadowedhearts.configuration.worldAlteration.umbrafallWorkPerTick")
                .defineInRange("umbrafallWorkPerTick", 4096, 128, 32768);

        meteoroidSpacing = builder
                .comment("Average distance (in chunks) between natural Umbrafall structure placement attempts.")
                .translation("shadowedhearts.configuration.worldAlteration.meteoroidSpacing")
                .defineInRange("meteoroidSpacing", 26, 1, 4096);

        meteoroidSeparation = builder
                .comment("Minimum distance (in chunks) between natural Umbrafall structure placement attempts. Values at or above spacing are limited to spacing minus one.")
                .translation("shadowedhearts.configuration.worldAlteration.meteoroidSeparation")
                .defineInRange("meteoroidSeparation", 11, 0, 4096);

        meteoroidBiomeBlacklist = builder
                .comment("Biomes excluded from natural Umbrafall generation. Biome tags such as #minecraft:is_forest are also accepted.")
                .translation("shadowedhearts.configuration.worldAlteration.meteoroidBiomeBlacklist")
                .defineListAllowEmpty("meteoroidBiomeBlacklist", Collections.emptyList(),
                        () -> "minecraft:plains", value -> value instanceof String text && !text.isBlank());

        meteoroidBiomeWhitelist = builder
                .comment("Biomes allowed for natural Umbrafall generation. If nonempty, only these biomes are allowed; the blacklist still applies. Biome tags such as #minecraft:is_forest are also accepted.")
                .translation("shadowedhearts.configuration.worldAlteration.meteoroidBiomeWhitelist")
                .defineListAllowEmpty("meteoroidBiomeWhitelist", Collections.emptyList(),
                        () -> "minecraft:plains", value -> value instanceof String text && !text.isBlank());
    }
    @Override
    public double meteoroidShadowSpawnChanceMultiplier() {
        if (!isLoaded()) return IWorldAlterationConfig.super.meteoroidShadowSpawnChanceMultiplier();
        return meteoroidShadowSpawnChanceMultiplier.get();
    }

    @Override
    public boolean meteoroidWorldGenEnabled() {
        if (!isLoaded()) return IWorldAlterationConfig.super.meteoroidWorldGenEnabled();
        return meteoroidWorldGenEnabled.get();
    }

    @Override
    public boolean umbrafallSignalsEnabled() {
        if (!isLoaded()) return IWorldAlterationConfig.super.umbrafallSignalsEnabled();
        return umbrafallSignalsEnabled.get();
    }

    @Override
    public UmbrafallSignalMode umbrafallSignalMode() {
        return isLoaded() ? umbrafallSignalMode.get() : IWorldAlterationConfig.super.umbrafallSignalMode();
    }

    @Override
    public int umbrafallSignalLocateMinDistance() {
        return isLoaded() ? Math.min(umbrafallSignalLocateMinDistance.get(), umbrafallSignalLocateMaxDistance())
                : IWorldAlterationConfig.super.umbrafallSignalLocateMinDistance();
    }

    @Override
    public int umbrafallSignalLocateMaxDistance() {
        return isLoaded() ? umbrafallSignalLocateMaxDistance.get() : IWorldAlterationConfig.super.umbrafallSignalLocateMaxDistance();
    }

    @Override
    public int umbrafallSignalLifetimeSeconds() {
        return isLoaded() ? umbrafallSignalLifetimeSeconds.get() : IWorldAlterationConfig.super.umbrafallSignalLifetimeSeconds();
    }

    @Override
    public List<? extends String> umbrafallSignalAllowedTiers() {
        return isLoaded() ? umbrafallSignalAllowedTiers.get() : IWorldAlterationConfig.super.umbrafallSignalAllowedTiers();
    }

    @Override
    public int umbrafallWorkPerTick() {
        if (!isLoaded()) return IWorldAlterationConfig.super.umbrafallWorkPerTick();
        return umbrafallWorkPerTick.get();
    }

    @Override
    public int meteoroidSpacing() {
        if (!isLoaded()) return IWorldAlterationConfig.super.meteoroidSpacing();
        return meteoroidSpacing.get();
    }

    @Override
    public int meteoroidSeparation() {
        if (!isLoaded()) return IWorldAlterationConfig.super.meteoroidSeparation();
        return Math.min(meteoroidSeparation.get(), meteoroidSpacing() - 1);
    }

    @Override
    public List<? extends String> meteoroidBiomeBlacklist() {
        if (!isLoaded()) return IWorldAlterationConfig.super.meteoroidBiomeBlacklist();
        return meteoroidBiomeBlacklist.get();
    }

    @Override
    public List<? extends String> meteoroidBiomeWhitelist() {
        if (!isLoaded()) return IWorldAlterationConfig.super.meteoroidBiomeWhitelist();
        return meteoroidBiomeWhitelist.get();
    }
}
