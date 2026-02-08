package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = ShowdownMoveset.class, remap = false)
public abstract class MixinShowdownMoveset {
    @Shadow
    public List<InBattleMove> moves;

    @Shadow
    private List<InBattleGimmickMove> canZMove;

    @Shadow
    private List<InBattleGimmickMove> maxMoves;

    @Inject(method = "setGimmickMapping", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$safeSetGimmickMapping(CallbackInfoReturnable<Unit> cir) {
        List<InBattleGimmickMove> gimmickMoves = canZMove != null ? canZMove : maxMoves;
        if (gimmickMoves != null && moves != null) {
            int gimmickIndex = 0;
            for (InBattleMove move : moves) {
                if (move == null) continue;
                if (gimmickIndex < gimmickMoves.size()) {
                    move.setGimmickMove(gimmickMoves.get(gimmickIndex));
                    gimmickIndex++;
                }
            }
        }
        cir.cancel();
    }

}
