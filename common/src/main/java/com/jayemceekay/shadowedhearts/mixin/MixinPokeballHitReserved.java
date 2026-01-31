package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.jayemceekay.shadowedhearts.SHAspects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "us.timinc.mc.cobblemon.timcore.handler.PokeballHitReserved", remap = false)
public abstract class MixinPokeballHitReserved {

    @Inject(method = "handle*", at = @At("HEAD"), cancellable = true)
    private void onHandle(ThrownPokeballHitEvent evt, CallbackInfo ci) {
        if (evt.getPokemon().getAspects().contains(SHAspects.SHADOW)) {
            ci.cancel();
        }
    }
}
