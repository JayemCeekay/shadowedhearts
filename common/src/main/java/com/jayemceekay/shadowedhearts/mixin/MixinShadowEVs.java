package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.pokemon.stats.EvSource;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.pokemon.EVs;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowProgressionManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EVs.class, remap = false)
public abstract class MixinShadowEVs {
    @WrapOperation(method = "add(Lcom/cobblemon/mod/common/api/pokemon/stats/Stat;ILcom/cobblemon/mod/common/api/pokemon/stats/EvSource;)I",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokemon/EVs;performAdd(Lcom/cobblemon/mod/common/api/pokemon/stats/Stat;I)I"),
            require = 1, allow = 1)
    private int shadowedhearts$bufferAcceptedEvs(EVs evs, Stat stat, int amount, Operation<Integer> original,
                                               @Local(argsOnly = true) EvSource source) {
        return switch (ShadowProgressionManager.evPolicy(source)) {
            case BUFFER -> ShadowAspectUtil.acceptBufferedEv(source.getPokemon(), stat, amount);
            case DISCARD -> 0;
            default -> original.call(evs, stat, amount);
        };
    }
}
