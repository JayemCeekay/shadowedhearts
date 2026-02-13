package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
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
        if (forceSwitch || showdownMoveSet == null) return;

        var bp = activeBattlePokemon;
        var pokemon = bp == null ? null : bp.getBattlePokemon().getEffectedPokemon();
        if (pokemon == null) return;

        if (ShadowGate.isShadowLocked(pokemon)) {
            String moveId = ((AccessorMoveActionResponse) this).shadowedhearts$getMoveName();
            if (!ShadowGate.isShadowMoveId(moveId)) {
                int allowed = ShadowAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
                int nonShadowIndex = 0;
                boolean matched = false;
                for (var mv : pokemon.getMoveSet().getMovesWithNulls()) {
                    if (mv == null) continue;
                    if (!ShadowGate.isShadowMoveId(mv.getName())) {
                        if (mv.getName().equalsIgnoreCase(moveId)) {
                            matched = true;
                            if (nonShadowIndex >= allowed) {
                                cir.setReturnValue(false);
                            }
                            break;
                        }
                        nonShadowIndex++;
                    }
                }
                // If move wasn't in moveset (e.g. Struggle), it might not be locked here, but Struggle is usually handled elsewhere.
                // If it IS in moveset but not matched (shouldn't happen), we default to previous behavior? 
                // Previous behavior was returning false if not a shadow move.
                if (!matched && !"struggle".equalsIgnoreCase(moveId)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}