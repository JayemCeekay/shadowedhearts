package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.aura.AuraLockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A bounded natural-despawn guard; never marks an entity persistent or recreates an unloaded one. */
@Mixin(PokemonEntity.class)
public abstract class MixinAuraLockedPokemonDespawn {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$keepValidAuraLock(CallbackInfo ci) {
        if (AuraLockManager.INSTANCE.preventsNaturalDespawn((PokemonEntity) (Object) this)) ci.cancel();
    }
}
