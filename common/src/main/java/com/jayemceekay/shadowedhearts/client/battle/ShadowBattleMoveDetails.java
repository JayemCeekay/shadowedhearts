package com.jayemceekay.shadowedhearts.client.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;

public final class ShadowBattleMoveDetails {
    private ShadowBattleMoveDetails() {}

    public static boolean masked(BattleMoveSelection.MoveTile tile) {
        var move = tile.getMove();
        if (move == null || !move.getDisabled()) return false;
        // Cobblemon uses 100/100 PP for a forced continuation such as Thrash.
        if (move.getPp() == 100 && move.getMaxpp() == 100) return false;
        return ShadowUnlockPolicy.shouldMaskMove(tile.getPokemon(), move.getId());
    }

    public static boolean unlimited(MoveTemplate move, Pokemon pokemon) {
        var settings = ShadowBattleClientState.INSTANCE.current();
        return settings != null && settings.unlimitedShadowPP()
                && ShadowAspectUtil.hasShadowAspect(pokemon) && ShadowMoveUtil.isShadowMove(move);
    }
}
