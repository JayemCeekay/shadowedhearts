package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction

/**
 * Format: |capture|PNX
 * Example: |capture|p2a  (or with ":" metadata)
 *
 * Marks the target active slot as “captured”/gone on our side and informs clients.
 */
class CaptureInstruction(
    private val message: BattleMessage
) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        // Locate actor + active slot using the first token (index 0) after the id
        val (_, active) = message.actorAndActivePokemon(0, battle) ?: return
        val bp = active.battlePokemon ?: return

        //apply operations to the captured Pokémon
    }
}