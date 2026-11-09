package com.jayemceekay.shadowedhearts.mixin.sos;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueActors;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueBattles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.necro.sos.encounters.common.events.SOSBattleRollEvent", remap = false)
public abstract class MixinCraterSosRoll {
    @Shadow public abstract ServerPlayer player();
    @Shadow public abstract PokemonEntity source();

    @Inject(method = "roll", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$scheduledCall(RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        var caller = source();
        if (CraterRescueActors.site(caller.getPokemon()) != null) cir.setReturnValue(CraterRescueBattles.sosDue(player(), caller));
    }
}
