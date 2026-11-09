package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueBattles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Pokemon.class, remap = false)
public abstract class MixinCraterHowlStatus {
    @Inject(method = "applyStatus", at = @At("TAIL"))
    private void shadowedhearts$interruptHowl(PersistentStatus status, CallbackInfo ci) {
        CraterRescueBattles.howlStatus((Pokemon) (Object) this);
    }
}
