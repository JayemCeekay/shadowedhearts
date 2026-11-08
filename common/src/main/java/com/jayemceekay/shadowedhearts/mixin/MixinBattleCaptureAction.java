package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.snag.SnagCaptureProvenance;
import com.jayemceekay.shadowedhearts.integration.mega_showdown.MegaShowdownBridgeHolder;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BattleCaptureAction.class, remap = false)
public class MixinBattleCaptureAction {

    @Inject(method = "attach", at = @At("HEAD"))
    private void shadowedhearts$rememberSnagOrigin(CallbackInfo ci) {
        SnagCaptureProvenance.recordAttempt((BattleCaptureAction) (Object) this);
    }

    @Inject(method = "attach$lambda$3", remap = false, at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;writeShowdownAction([Ljava/lang/String;)V", shift = At.Shift.AFTER), require = 0)
    private static void shadowedhearts$clearCapturedAction(BattleCaptureAction action, Boolean successful, CallbackInfoReturnable<Unit> cir) {
        var target = action.getTargetPokemon();
        var member = target.getBattlePokemon();
        if (!successful || member == null || !member.getEffectedPokemon().getAspects().contains(SHAspects.SHADOW)) return;
        MegaShowdownBridgeHolder.INSTANCE.revertMegaShowdownAspects(member.getEffectedPokemon());

        // Responses are indexed within this actor. A late-joining actor may have no choice queued yet.
        BattleActor actor = target.getActor();
        int index = actor.getActivePokemon().indexOf(target);
        if (index >= 0 && index < actor.getResponses().size()) {
            actor.getResponses().set(index, PassActionResponse.INSTANCE);
        }
    }
}
