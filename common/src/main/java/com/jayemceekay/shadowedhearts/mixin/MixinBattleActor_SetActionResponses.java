package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Delays the dispatch check at the end of BattleActor#setActionResponses for one-turn micro battles.
 * We avoid blocking the battle processing loop by re-posting a doWhenClear callback until ~2 seconds have elapsed,
 * then calling battle.checkForInputDispatch().
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = BattleActor.class, remap = false)
public abstract class MixinBattleActor_SetActionResponses {

    /*@WrapOperation(method = "setActionResponses", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;checkForInputDispatch()V"))
    public void shadowedhearts$wrapCheckForInputDispatch(PokemonBattle battle, Operation<Void> original) {
        if (battle != null && AutoBattleController.isOneTurn(battle.getBattleId())) {
            if(AutoBattleController.getFlag(battle.getBattleId())) {
                battle.checkForInputDispatch();
                AutoBattleController.setFlag(battle.getBattleId(), false);
            } else {
                AutoBattleController.setFlag(battle.getBattleId(), true);
            }
        } else {
            original.call(battle);
        }
    }*/
}
