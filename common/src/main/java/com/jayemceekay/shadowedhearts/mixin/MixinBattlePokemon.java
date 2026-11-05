package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BattlePokemon.class, remap = false)
public abstract class MixinBattlePokemon {
    @Shadow
    public abstract boolean getGone();

    @WrapMethod(method = "canBeSentOut()Z", remap = false, require = 1)
    private boolean shadowedhearts$excludeCapturedPokemon(Operation<Boolean> original) {
        // Captures retain their persistent HP and roster entry, but cannot rejoin this battle.
        return !getGone() && original.call();
    }
}
