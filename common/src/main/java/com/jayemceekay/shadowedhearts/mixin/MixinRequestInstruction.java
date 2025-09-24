package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.interpreter.instructions.RequestInstruction;
import com.jayemceekay.shadowedhearts.showdown.OneTurnMicroController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For micro one-turn battles, immediately have the target AI actor choose their action as soon as a request arrives.
 * This prevents stalling due to timing mismatches between request phases and AI choice dispatch, and avoids forcing
 * other actors (who don't yet have a request) to PASS.
 */
@Mixin(value = RequestInstruction.class, remap = false)
public abstract class MixinRequestInstruction {

    @Final
    @Shadow private BattleActor battleActor;

    @Inject(method = "invoke", at = @At("TAIL"))
    private void shadowedhearts$autoChooseOnRequest(PokemonBattle battle, CallbackInfo ci) {
        if (battle == null) return;
        if (!OneTurnMicroController.isOneTurn(battle.getBattleId())) return;
        try {
            BattleActor actor = this.battleActor;
            // Log request details for the actor whose request just arrived
            try {
                com.cobblemon.mod.common.battles.ShowdownActionRequest req = actor.getRequest();
                String sid = actor.getShowdownId();
                if (req != null) {
                    int activeCount = req.getActive() == null ? 0 : req.getActive().size();
                    int fsCount = req.getForceSwitch() == null ? 0 : req.getForceSwitch().size();
                    com.jayemceekay.shadowedhearts.showdown.MicroDebug.log("Request for %s (battle %s): active=%d, forceSwitch=%d", sid, battle.getBattleId(), activeCount, fsCount);
                } else {
                    com.jayemceekay.shadowedhearts.showdown.MicroDebug.log("Request for %s (battle %s): <null>", sid, battle.getBattleId());
                }
            } catch (Throwable ignored2) {
                ignored2.printStackTrace();
            }

            if (actor instanceof AIBattleActor ai) {
                com.jayemceekay.shadowedhearts.showdown.MicroDebug.log("Trigger onChoiceRequested for %s (battle %s)", actor.getShowdownId(), battle.getBattleId());
                // Ask the AI to immediately decide given the current request just set by RequestInstruction
                ai.onChoiceRequested();
            }
        } catch (Throwable ignored) {
            ignored.printStackTrace();
            // avoid crashing the interpreter on edge cases
        }
    }
}
