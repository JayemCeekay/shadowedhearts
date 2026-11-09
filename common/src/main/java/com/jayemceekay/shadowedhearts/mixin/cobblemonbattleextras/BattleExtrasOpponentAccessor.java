package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "name.modid.client.CustomBattleController$OpponentMatchupEntry", remap = false)
public interface BattleExtrasOpponentAccessor {
    @Accessor("battlePokemon")
    ClientBattlePokemon shadowedhearts$getBattlePokemon();
}
