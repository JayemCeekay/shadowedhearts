package com.jayemceekay.shadowedhearts.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState;
/** Effective battle PP presentation from the connected server's settings. */
public final class ShadowBattleMoveDetails {
    private ShadowBattleMoveDetails() {}

    public static boolean unlimited(MoveTemplate move, Pokemon pokemon) {
        var settings = ShadowBattleClientState.INSTANCE.current();
        return settings != null && settings.unlimitedShadowPP()
                && ShadowAspectUtil.hasShadowAspect(pokemon) && ShadowMoveUtil.isShadowMove(move);
    }
}
