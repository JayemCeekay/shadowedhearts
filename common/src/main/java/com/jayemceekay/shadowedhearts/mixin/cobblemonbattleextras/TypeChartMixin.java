package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasMovePresentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "name.modid.client.TypeChart", remap = false)
public abstract class TypeChartMixin {
    @Inject(method = "getEffectiveness", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$shadowMove(MoveTemplate move, ElementalType primary, ElementalType secondary,
                                                 CallbackInfoReturnable<Float> cir) {
        if (ShadowMoveUtil.isShadowMove(move)) cir.setReturnValue(BattleExtrasMovePresentation.effectiveness(null));
    }

    @Inject(method = "getEffectivenessAgainstTypes", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$shadowType(String type, String primary, String secondary,
                                                 CallbackInfoReturnable<Float> cir) {
        if ("shadow".equalsIgnoreCase(type)) cir.setReturnValue(BattleExtrasMovePresentation.effectiveness(null));
    }
}
