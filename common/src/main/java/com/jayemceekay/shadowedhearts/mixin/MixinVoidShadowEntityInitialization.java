package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.voidshadow.VoidShadowService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers native /spawnpokemon, which does not emit the natural-spawn event. */
@Mixin(value = PokemonEntity.class, remap = false)
public abstract class MixinVoidShadowEntityInitialization {
    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lcom/cobblemon/mod/common/pokemon/Pokemon;Lnet/minecraft/world/entity/EntityType;)V",
            at = @At("RETURN"), remap = false, require = 1)
    private void shadowedhearts$initializeVoidShadowIdentity(CallbackInfo ci) {
        VoidShadowService.initializeEntity((PokemonEntity) (Object) this);
    }
}
