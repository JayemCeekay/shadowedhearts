package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueBattles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PokemonBattle.class, remap = false)
public abstract class MixinCraterHowlTurn {
    @Inject(method = "turn", at = @At("TAIL"))
    private void shadowedhearts$howlTurn(int turn, CallbackInfo ci) {
        CraterRescueBattles.howlTurn((PokemonBattle) (Object) this, turn);
    }
}
