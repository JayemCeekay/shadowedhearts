package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.tms.TechnicalMachine;
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen;
import com.cobblemon.mod.common.client.gui.tmmachine.TMPartySlotWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.client.tm.ShadowTMClientEligibility;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowMoveTeachingPolicy;
import com.jayemceekay.shadowedhearts.common.shadow.tm.ShadowTMTeaching;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TMMachineScreen.class, remap = false)
public abstract class MixinTMMachineScreen {
    @Inject(method = "canLearnTMMove", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$previewShadowTMTeaching(MoveTemplate move, Pokemon pokemon,
                                                      CallbackInfoReturnable<Integer> cir) {
        if (ShadowMoveUtil.isShadowMove(move)) {
            int status = TMPartySlotWidget.CANNOT_LEARN;
            if (ShadowTMClientEligibility.isCompatible(pokemon, move)) {
                status = ShadowTMTeaching.knowsActiveMove(pokemon, move)
                        ? TMPartySlotWidget.LEARNED : TMPartySlotWidget.CAN_LEARN;
            }
            cir.setReturnValue(status);
            return;
        }
        if (ShadowMoveTeachingPolicy.isShadowBlocked(pokemon)) {
            cir.setReturnValue(TMPartySlotWidget.CANNOT_LEARN);
        }
    }

    // The machine applies a second, stricter tmMoves filter after TechnicalMachine.filterTms.
    @Inject(method = "setMoveList$lambda$0$0", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$filterShadowRecipes(Pokemon pokemon, TechnicalMachine tm,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (ShadowMoveUtil.isShadowMove(tm.getMoveName())) {
            cir.setReturnValue(ShadowTMClientEligibility.isCompatible(pokemon, tm.getMoveName()));
        }
    }
}
