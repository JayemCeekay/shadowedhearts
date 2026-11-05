package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundEncounters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Battle controls must not leave registered participants exposed to unrelated field damage. */
@Mixin(ServerPlayer.class)
public abstract class MixinMoonwoundBattleProtection {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$protect(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && MoonwoundEncounters.isMoonwoundBattle((ServerPlayer) (Object) this)) cir.setReturnValue(false);
    }
}
