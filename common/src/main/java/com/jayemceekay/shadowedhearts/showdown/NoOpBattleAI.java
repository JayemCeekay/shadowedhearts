package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal AI that never selects an action. Used for one-sided micro battles
 * where the defender should not act (e.g., player proxy).
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class NoOpBattleAI implements BattleAI {
    @Override
    public @NotNull ShowdownActionResponse choose(ActiveBattlePokemon activeBattlePokemon, PokemonBattle battle, BattleSide aiSide, ShowdownMoveset moveset, boolean forceSwitch) {
        // Defer to Showdown's default behavior (usually do nothing / struggle if required)
        return new DefaultActionResponse();
    }
}
