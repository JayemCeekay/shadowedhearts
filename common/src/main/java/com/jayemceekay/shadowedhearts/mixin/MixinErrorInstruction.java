package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.interpreter.instructions.ErrorInstruction;
import com.jayemceekay.shadowedhearts.showdown.AutoBattleController;
import com.jayemceekay.shadowedhearts.showdown.MicroDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Extra debug: log any Showdown error lines for one-turn micro battles with actor context.
 */
@Mixin(value = ErrorInstruction.class, remap = false)
public abstract class MixinErrorInstruction {
    @Shadow private BattleActor battleActor;
    @Shadow private BattleMessage message;

    @Inject(method = "invoke", at = @At("HEAD"))
    private void shadowedhearts$debugError(PokemonBattle battle, CallbackInfo ci) {
        if (battle == null) return;
        if (!AutoBattleController.isOneTurn(battle.getBattleId())) return;
        try {
            String sid = battleActor != null ? battleActor.getShowdownId() : "<no-actor>";
            String raw = message != null ? message.getRawMessage() : "<null>";
            MicroDebug.log("Error for %s (battle %s): %s", sid, battle.getBattleId(), raw);
        } catch (Throwable ignored) {}
    }
}
