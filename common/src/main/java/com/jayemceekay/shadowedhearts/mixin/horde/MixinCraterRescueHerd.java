package com.jayemceekay.shadowedhearts.mixin.horde;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueActors;
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueBattles;
import com.necro.asymmetric.battles.common.api.BattleParticipant;
import com.necro.asymmetric.battles.common.api.actor.HordeBattleActor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The add-on's enabled herd route still owns pre/post events, the battle format and normal error handling. */
@Mixin(value = BattleParticipant.class, remap = false)
public interface MixinCraterRescueHerd {
    @Inject(method = "horde(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;)Lcom/necro/asymmetric/battles/common/api/BattleParticipant;",
            at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$useRescuePack(PokemonEntity target, CallbackInfoReturnable<BattleParticipant<HordeBattleActor>> cir) {
        if (CraterRescueActors.site(target.getPokemon()) == null) return;
        var pack = CraterRescueBattles.gatherPack(target);
        cir.setReturnValue(pack.size() >= 2 ? BattleParticipant.horde(pack) : null);
    }
}
