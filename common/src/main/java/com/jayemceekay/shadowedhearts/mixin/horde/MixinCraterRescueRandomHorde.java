package com.jayemceekay.shadowedhearts.mixin.horde;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueActors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** When herd encounters are disabled or only one attacker remains, let the normal single battle proceed. */
@Pseudo
@Mixin(targets = "com.necro.horde.encounters.common.config.ConfigCache", remap = false)
public abstract class MixinCraterRescueRandomHorde {
    @Inject(method = "canHorde", at = @At("HEAD"), cancellable = true, require = 0)
    private static void shadowedhearts$keepAuthoredCast(PokemonEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (CraterRescueActors.site(target.getPokemon()) != null) cir.setReturnValue(false);
    }
}
