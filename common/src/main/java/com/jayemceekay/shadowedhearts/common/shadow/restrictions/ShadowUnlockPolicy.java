package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowStartingMoves;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;

/** Heart Gauge unlock policy shared by authoritative gameplay and client presentation. */
public final class ShadowUnlockPolicy {
    private ShadowUnlockPolicy() { }

    /** Nature visibility follows the server's reveal threshold (40% by default). */
    public static boolean isNatureHiddenByGauge(Pokemon pokemon) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return false;
        var settings = ShadowRestrictionSettings.forPokemon(pokemon);
        return HeartGaugeUnlockRules.isHidden(ShadowHeartGauge.getHeartGaugeMeter(pokemon), HeartGaugeConfig.getMax(pokemon),
                settings.hideNature(), settings.natureRevealPercent(), settings.exactThresholds());
    }

    /**
     * Level/EXP visibility is independent of EXP buffering.
     */
    public static boolean isLevelExpHiddenByGauge(Pokemon pokemon) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return false;
        var settings = ShadowRestrictionSettings.forPokemon(pokemon);
        return HeartGaugeUnlockRules.isHidden(ShadowHeartGauge.getHeartGaugeMeter(pokemon), HeartGaugeConfig.getMax(pokemon),
                settings.hideLevelAndExperience(), settings.levelExpRevealPercent(), settings.exactThresholds());
    }

    /**
     * EV visibility is independent of EV buffering.
     */
    public static boolean isEVHiddenByGauge(Pokemon pokemon) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return false;
        var settings = ShadowRestrictionSettings.forPokemon(pokemon);
        return HeartGaugeUnlockRules.isHidden(ShadowHeartGauge.getHeartGaugeMeter(pokemon), HeartGaugeConfig.getMax(pokemon),
                settings.hideEVs(), settings.evRevealPercent(), settings.exactThresholds());
    }

    /**
     * IV visibility follows the server's reveal threshold.
     */
    public static boolean isIVHiddenByGauge(Pokemon pokemon) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return false;
        var settings = ShadowRestrictionSettings.forPokemon(pokemon);
        return HeartGaugeUnlockRules.isHidden(ShadowHeartGauge.getHeartGaugeMeter(pokemon), HeartGaugeConfig.getMax(pokemon),
                settings.hideIVs(), settings.ivRevealPercent(), settings.exactThresholds());
    }

    /**
     * Shared visibility and battle-use gate for ordinary moves.
     * Disabling the restriction allows all ordinary moves without removing assigned Shadow moves.
     */
    public static int getAllowedVisibleNonShadowMoves(Pokemon pokemon) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return MoveSet.MOVE_COUNT;
        var settings = ShadowRestrictionSettings.forPokemon(pokemon);
        int allowed = HeartGaugeUnlockRules.allowedNonShadowMoves(ShadowHeartGauge.getHeartGaugeMeter(pokemon), HeartGaugeConfig.getMax(pokemon),
                settings.restrictNonShadowMoves(), settings.firstMoveUnlockPercent(),
                settings.secondMoveUnlockPercent(), settings.thirdMoveUnlockPercent(), settings.exactThresholds());
        if (allowed == 0 && pokemon.getMoveSet().getMoves().stream().noneMatch(ShadowMoveUtil::isShadowMove)
                && (ShadowLearnsets.get(pokemon) != null || ShadowStartingMoves.hasExactSet(pokemon))) {
            // A pack can defer its first Shadow move or author an encounter with ordinary moves only.
            return 1;
        }
        return allowed;
    }

    public static boolean shouldMaskMove(Pokemon pokemon, Move move) {
        if (move == null || pokemon == null) return false;
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return false;
        if (ShadowMoveUtil.isShadowMove(move)) return false;

        int nonShadowIndex = 0;
        int allowed = getAllowedVisibleNonShadowMoves(pokemon);
        for (var mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null) continue;
            if (ShadowMoveUtil.isShadowMove(mv)) continue;
            if (mv == move) return nonShadowIndex >= allowed;
            nonShadowIndex++;
        }
        return false;
    }

    public static boolean shouldMaskMove(Pokemon pokemon, int indexInMoveSet) {
        if (pokemon == null) return false;
        var moves = pokemon.getMoveSet().getMovesWithNulls();
        if (indexInMoveSet < 0 || indexInMoveSet >= moves.size()) return false;
        var move = moves.get(indexInMoveSet);
        return shouldMaskMove(pokemon, move);
    }

    public static boolean shouldMaskMove(Pokemon pokemon, String moveId) {
        if (pokemon == null || moveId == null) return false;
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return false;
        var template = Moves.INSTANCE.getByNameOrDummy(moveId);
        if (ShadowMoveUtil.isShadowMove(template)) return false;

        int nonShadowIndex = 0;
        int allowed = getAllowedVisibleNonShadowMoves(pokemon);
        for (var mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null) {
                continue;
            }
            if (ShadowMoveUtil.isShadowMove(mv)) continue;
            if (mv.getName().equalsIgnoreCase(moveId)) return nonShadowIndex >= allowed;
            nonShadowIndex++;
        }
        return false;
    }
}
