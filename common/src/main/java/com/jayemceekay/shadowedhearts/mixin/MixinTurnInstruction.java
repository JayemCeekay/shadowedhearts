package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.interpreter.instructions.TurnInstruction;
import com.jayemceekay.shadowedhearts.showdown.MicroDebug;
import com.jayemceekay.shadowedhearts.showdown.OneTurnMicroController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fallback safety for one-turn micro battles: at the start of a turn, immediately
 * prompt any AI actors that already have a request to choose their action.
 * This complements the RequestInstruction hook and guarantees both sides submit
 * choices during turn 1 even if Showdown delivers requests unevenly.
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = TurnInstruction.class, remap = false)
public abstract class MixinTurnInstruction {

    @Inject(method = "invoke", at = @At("TAIL"))
    private void shadowedhearts$promptPendingAI(PokemonBattle battle, CallbackInfo ci) {
        if (battle == null) return;
        if (!OneTurnMicroController.isOneTurn(battle.getBattleId())) return;
        try {
            battle.doWhenClear(() -> {
                try {
                    for (BattleActor actor : battle.getActors()) {
                        if (actor instanceof AIBattleActor ai) {
                            if (ai.getRequest() != null) {
                                MicroDebug.log("Turn start prompt for %s (battle %s): triggering AI choice", actor.getShowdownId(), battle.getBattleId());
                                ai.onChoiceRequested();
                            }
                        }
                    }
                } catch (Throwable inner) {
                    // ignore
                }
                return kotlin.Unit.INSTANCE;
            });
        } catch (Throwable ignored) {
            // Avoid crashing interpreter on unexpected issues
        }
    }
}
