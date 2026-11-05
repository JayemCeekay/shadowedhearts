package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MoveActionResponse.class, remap = false)
public class MixinMoveActionResponse {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$blockNonShadowMoves(
            ActiveBattlePokemon activeBattlePokemon, ShowdownMoveset showdownMoveSet, boolean forceSwitch, CallbackInfoReturnable<Boolean> cir
    ) {
        if (forceSwitch || showdownMoveSet == null || activeBattlePokemon == null
                || activeBattlePokemon.getActor().getType() != ActorType.PLAYER) return;

        // Heart Gauge move locks govern player commands. Opponent AI keeps its full moveset,
        // including battle clones whose original Pokemon may still have a player owner.
        var battlePokemon = activeBattlePokemon.getBattlePokemon();
        if (battlePokemon == null) return;
        String moveId = ((AccessorMoveActionResponse) this).shadowedhearts$getMoveName();
        // Showdown owns forced continuation turns (for example, Thrash) and Struggle.
        // Keep those usable if a live config change locks the original ordinary move.
        if ("struggle".equalsIgnoreCase(moveId)) return;
        for (var move : showdownMoveSet.getMoves()) {
            if (move.getId().equalsIgnoreCase(moveId) && move.mustBeUsed()) return;
        }
        if (ShadowAspectUtil.shouldMaskMove(battlePokemon.getEffectedPokemon(), moveId)) {
            cir.setReturnValue(false);
        }
    }
}
