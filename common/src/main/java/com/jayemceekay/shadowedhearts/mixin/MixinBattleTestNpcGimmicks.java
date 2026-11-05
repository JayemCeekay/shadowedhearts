package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.jayemceekay.shadowedhearts.npc.BattleTestNpcGimmickActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cobblemon's ordinary AI never requests gimmicks; only the opt-in test classes use this override. */
@Mixin(value = AIBattleActor.class, remap = false)
public abstract class MixinBattleTestNpcGimmicks {
    @Inject(method = "onChoiceRequested$lambda$0$0", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$chooseTestGimmick(AIBattleActor actor, ActiveBattlePokemon active,
                                                        ShowdownMoveset moveset, boolean forceSwitch,
                                                        CallbackInfoReturnable<ShowdownActionResponse> cir) {
        var response = BattleTestNpcGimmickActions.tryChoose(active, moveset, forceSwitch);
        if (response != null) cir.setReturnValue(response);
    }
}
