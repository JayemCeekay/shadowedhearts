package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.SHAspects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Pokemon.class, remap = false)
public abstract class MixinPokemon {

    @Shadow
    public abstract boolean isPlayerOwned();

    @Shadow
    public abstract boolean isNPCOwned();

    @Inject(method = "isWild", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$isWildShadowCheck(CallbackInfoReturnable<Boolean> cir) {
        if(((Pokemon)(Object)this).getAspects().contains(SHAspects.SHADOW)) {
            if(isPlayerOwned()) {
                cir.setReturnValue(false);
            } else if(isNPCOwned()) {
                cir.setReturnValue(true);
            }
        }
    }

}
