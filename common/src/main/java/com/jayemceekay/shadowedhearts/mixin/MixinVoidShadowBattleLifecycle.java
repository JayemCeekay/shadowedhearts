package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PokemonBattle.class, remap = false)
public abstract class MixinVoidShadowBattleLifecycle {
    @Inject(method = "end()V", at = @At("HEAD"), remap = false, require = 1)
    private void shadowedhearts$rememberVoidShadowHealth(CallbackInfo ci) {
        VoidShadowService.captureBattleHealth((PokemonBattle) (Object) this);
    }
}
