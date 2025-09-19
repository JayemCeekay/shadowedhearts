package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.jayemceekay.shadowedhearts.SHAspects;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleCaptureAction.class, remap = false)
public class MixinBattleCaptureAction {

    @Inject(method = "attach$lambda$5", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;writeShowdownAction([Ljava/lang/String;)V", shift = At.Shift.AFTER), require = 0, remap = false)
    private static void shadowedhearts$attachLambda5Head(BattleCaptureAction this$0, Boolean successful, CallbackInfoReturnable<Unit> cir) {
        if (successful) {
            if (this$0.getTargetPokemon().getBattlePokemon().getEffectedPokemon().getAspects().contains(SHAspects.SHADOW)) {
                BattleActor npcActor = this$0.getTargetPokemon().getActor();
                int capturedIndex = this$0.getTargetPokemon().getDigit(true) - 1;
                npcActor.getResponses().set(capturedIndex, PassActionResponse.INSTANCE);
            }
        }
    }
}
