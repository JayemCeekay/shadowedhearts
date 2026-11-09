package com.jayemceekay.shadowedhearts.mixin.sos;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueActors;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueBattles;
import com.jayemceekay.shadowedhearts.integration.CraterSosCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.necro.sos.encounters.common.config.ConfigCache", remap = false)
public abstract class MixinCraterSosEligibility {
    @Inject(method = "canSOS(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void shadowedhearts$authoredSlot(PokemonEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (CraterRescueActors.site(target.getPokemon()) != null) {
            cir.setReturnValue(CraterSosCompatibility.available() && CraterRescueBattles.allowsSos(target));
        }
    }
}
