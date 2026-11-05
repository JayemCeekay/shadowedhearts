package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.moonwound.raid.RaidDensMoonwoundAdapter;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs after Raid Dens' priority-1000 mixin has contributed its accessor method. */
@Mixin(value = PokemonEntity.class, priority = 900)
public abstract class MixinMoonwoundRaidDefinition {
    @Inject(method = "crd_getRaidBoss", at = @At("HEAD"), cancellable = true, remap = false)
    private void shadowedhearts$frozenDefinition(CallbackInfoReturnable<RaidBoss> cir) {
        RaidBoss definition = RaidDensMoonwoundAdapter.definitionFor((PokemonEntity) (Object) this);
        if (definition != null) cir.setReturnValue(definition);
    }
}
