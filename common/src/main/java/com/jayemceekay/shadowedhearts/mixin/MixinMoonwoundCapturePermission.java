package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueBattles;
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEncounters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EmptyPokeBallEntity.class)
public abstract class MixinMoonwoundCapturePermission {
    @Inject(method = "tick", at = @At("HEAD"))
    private void shadowedhearts$recheckCapturePermission(CallbackInfo ci) {
        // Cobblemon's ball scheduler runs at the end of tick, including the native
        // ownership commit. Revocation completes its future before that callback.
        EmptyPokeBallEntity ball = (EmptyPokeBallEntity) (Object) this;
        MoonwoundEncounters.recheckCapture(ball);
        CraterRescueBattles.recheckCapture(ball);
    }
}
