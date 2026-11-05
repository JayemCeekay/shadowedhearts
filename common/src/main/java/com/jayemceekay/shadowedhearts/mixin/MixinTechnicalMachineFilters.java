package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.tms.TechnicalMachine;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.client.tm.ShadowTMClientEligibility;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Keep the native search, type and discovery filters; change only Shadow learnability. */
@Mixin(value = TechnicalMachine.Companion.class, remap = false)
public abstract class MixinTechnicalMachineFilters {
    @WrapOperation(method = "filterTms", at = @At(value = "INVOKE",
            target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private boolean shadowedhearts$includeCompatibleShadowTM(List<?> learnset, Object candidate,
                                                            Operation<Boolean> original,
                                                            @Local(argsOnly = true) Pokemon pokemon) {
        if (candidate instanceof MoveTemplate move && ShadowMoveUtil.isShadowMove(move)) {
            return ShadowTMClientEligibility.isCompatible(pokemon, move);
        }
        return original.call(learnset, candidate);
    }
}
