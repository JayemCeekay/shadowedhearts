package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.jayemceekay.shadowedhearts.showdown.AutoBattleController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Delays the dispatch check at the end of BattleActor#setActionResponses for one-turn micro battles.
 * We avoid blocking the battle processing loop by re-posting a doWhenClear callback until ~2 seconds have elapsed,
 * then calling battle.checkForInputDispatch().
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = BattleActor.class, remap = false)
public abstract class MixinBattleActor_SetActionResponses {

    @Redirect(
        method = "setActionResponses",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;checkForInputDispatch()V"
        )
    )
    private void shadowedhearts$delayCheckForInputDispatch(PokemonBattle battle) {
        try {
            if (battle != null && AutoBattleController.isOneTurn(battle.getBattleId())) {
                if(AutoBattleController.getFlag(battle.getBattleId())) {
                   // battle.doWhenClear(() -> {
                        battle.checkForInputDispatch();
                        AutoBattleController.setFlag(battle.getBattleId(), false);
                      //  return Unit.INSTANCE;
                 //   });
                } else {
                    AutoBattleController.setFlag(battle.getBattleId(), true);
                }
            }
        } catch (Throwable ignored) {
            // Fail-open to original behavior if something goes wrong
            if (battle != null) battle.checkForInputDispatch();
        }
    }
}
