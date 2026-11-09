package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = BattleRegistry.class, remap = false)
public abstract class MixinRaidDensEmptyTeam {
    // Wrap outside Raid Dens 0.13.5's default-order injector. Its transform lookup reads
    // the first Pokémon even for an empty partner slot reserved by SOS Encounters.
    @WrapOperation(method = "startShowdown(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;)V",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/battles/BattleRegistry;packTeam(Ljava/util/List;)Ljava/lang/String;"),
            order = 1001)
    private String shadowedhearts$packEmptyTeam(BattleRegistry registry, List<BattlePokemon> team, Operation<String> original) {
        return team.isEmpty() ? registry.packTeam(team) : original.call(registry, team);
    }
}
