package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.widgets.common.SummaryScrollList;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SummaryScrollList.class, remap = false)
public class MixinSummaryScrollList {

   /* @Unique
    private boolean shadowedhearts$shouldMask() {
        Move m = this.getMove();
        if (m == null || pokemon == null) return false;
        if (ShadowGate.isShadowMoveId(m.getName())) return false; // Shadow moves always visible

        // Compute this move's index among non-Shadow moves in move order
        int nonShadowIndex = 0;
        int allowed = PokemonAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        for (var mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null) continue;
            if (ShadowGate.isShadowMoveId(mv.getName())) continue;
            if (mv == m) {
                // If this move's position is at or beyond allowed, mask it
                return nonShadowIndex >= allowed;
            }
            nonShadowIndex++;
        }
        return false;
    }*/


}
