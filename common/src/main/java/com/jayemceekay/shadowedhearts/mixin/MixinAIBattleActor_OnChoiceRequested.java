package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.jayemceekay.shadowedhearts.showdown.AutoBattleController;
import com.jayemceekay.shadowedhearts.showdown.MicroDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Log whenever an AI actor is asked to choose, to verify choice flow during one-turn micro battles.
 */
@Mixin(value = AIBattleActor.class, remap = false)
public abstract class MixinAIBattleActor_OnChoiceRequested {

    @Inject(method = "onChoiceRequested", at = @At("HEAD"))
    private void shadowedhearts$debugOnChoiceRequested(CallbackInfo ci) {
        try {
            AIBattleActor self = (AIBattleActor)(Object)this;
            PokemonBattle battle = self.getBattle();
            if (battle == null || !AutoBattleController.isOneTurn(battle.getBattleId())) return;
            String sid = self.getShowdownId();
            ShowdownActionRequest req = self.getRequest();
            int activeCount = (req != null && req.getActive() != null) ? req.getActive().size() : 0;
            MicroDebug.log("onChoiceRequested for %s (battle %s): hasRequest=%s active=%d", sid, battle.getBattleId(), req != null, activeCount);
        } catch (Throwable ignored) {}
    }
}
