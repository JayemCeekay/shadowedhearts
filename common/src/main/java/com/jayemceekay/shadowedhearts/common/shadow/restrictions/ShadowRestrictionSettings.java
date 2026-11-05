package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;

import java.util.Objects;

/** Immutable, server-authoritative Heart Gauge rules shared with client presentation. */
public record ShadowRestrictionSettings(
        boolean bufferExperience,
        boolean bufferEVs,
        boolean blockFriendshipChanges,
        boolean blockEvolution,
        boolean blockNicknaming,
        boolean blockTrading,
        boolean blockMoveChanges,
        boolean blockMoveTeaching,
        boolean hideNature,
        boolean hideLevelAndExperience,
        boolean hideEVs,
        boolean hideIVs,
        boolean restrictNonShadowMoves,
        int natureRevealPercent,
        int levelExpRevealPercent,
        int evRevealPercent,
        int ivRevealPercent,
        int firstMoveUnlockPercent,
        int secondMoveUnlockPercent,
        int thirdMoveUnlockPercent,
        boolean exactThresholds,
        boolean blockHyperTraining
) {
    private static final ShadowRestrictionSettings DEFAULTS = new ShadowRestrictionSettings(
            true, true, true, true, true, true, true, true, true, true, true, true, true,
            40, 40, 40, 40, 79, 40, 20);

    /** Existing callers retain rounded thresholds and unrestricted Hyper Training. */
    public ShadowRestrictionSettings(boolean bufferExperience, boolean bufferEVs, boolean blockFriendshipChanges,
                                     boolean blockEvolution, boolean blockNicknaming, boolean blockTrading,
                                     boolean blockMoveChanges, boolean blockMoveTeaching, boolean hideNature,
                                     boolean hideLevelAndExperience, boolean hideEVs, boolean hideIVs,
                                     boolean restrictNonShadowMoves, int natureRevealPercent, int levelExpRevealPercent,
                                     int evRevealPercent, int ivRevealPercent, int firstMoveUnlockPercent,
                                     int secondMoveUnlockPercent, int thirdMoveUnlockPercent) {
        this(bufferExperience, bufferEVs, blockFriendshipChanges, blockEvolution, blockNicknaming, blockTrading,
                blockMoveChanges, blockMoveTeaching, hideNature, hideLevelAndExperience, hideEVs, hideIVs,
                restrictNonShadowMoves, natureRevealPercent, levelExpRevealPercent, evRevealPercent, ivRevealPercent,
                firstMoveUnlockPercent, secondMoveUnlockPercent, thirdMoveUnlockPercent, false, false);
    }

    public ShadowRestrictionSettings {
        requirePercent(natureRevealPercent);
        requirePercent(levelExpRevealPercent);
        requirePercent(evRevealPercent);
        requirePercent(ivRevealPercent);
        requirePercent(firstMoveUnlockPercent);
        requirePercent(secondMoveUnlockPercent);
        requirePercent(thirdMoveUnlockPercent);
    }

    public static ShadowRestrictionSettings defaults() {
        return DEFAULTS;
    }

    public static ShadowRestrictionSettings fromConfig(IShadowConfig config) {
        Objects.requireNonNull(config, "config");
        return new ShadowRestrictionSettings(
                config.heartGaugeBufferExperience(),
                config.heartGaugeBufferEVs(),
                config.heartGaugeBlockFriendshipChanges(),
                config.heartGaugeBlockEvolution(),
                config.heartGaugeBlockNicknaming(),
                config.heartGaugeBlockTrading(),
                config.heartGaugeBlockMoveChanges(),
                config.heartGaugeBlockMoveTeaching(),
                config.heartGaugeHideNature(),
                config.heartGaugeHideLevelAndExperience(),
                config.heartGaugeHideEVs(),
                config.heartGaugeHideIVs(),
                config.heartGaugeRestrictNonShadowMoves(),
                config.heartGaugeNatureRevealPercent(),
                config.heartGaugeLevelExpRevealPercent(),
                config.heartGaugeEvRevealPercent(),
                config.heartGaugeIvRevealPercent(),
                config.heartGaugeFirstMoveUnlockPercent(),
                config.heartGaugeSecondMoveUnlockPercent(),
                config.heartGaugeThirdMoveUnlockPercent(),
                config.heartGaugeExactThresholds(),
                config.heartGaugeBlockHyperTraining());
    }

    /** Enforcement always reads server config, including an integrated server in the client JVM. */
    public static ShadowRestrictionSettings serverCurrent() {
        return fromConfig(ShadowedHeartsConfigs.getInstance().getShadowConfig());
    }

    public static ShadowRestrictionSettings forPokemon(Pokemon pokemon) {
        return pokemon != null && pokemon.isClient$common()
                ? ShadowRestrictionClientState.INSTANCE.currentSettings(ShadowRestrictionSettings::serverCurrent)
                : serverCurrent();
    }

    private static void requirePercent(int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Heart Gauge percentage must be between 0 and 100: " + value);
        }
    }
}
