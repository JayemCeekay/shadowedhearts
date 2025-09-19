package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction

/**
 * A no-op instruction used to acknowledge and ignore unrecognized or benign tokens
 * coming from the Showdown log stream (e.g., custom debug lines like "sending request").
 *
 * This prevents the interpreter from complaining about "Missing interpretation" while
 * keeping the battle state unchanged.
 */
class NoOpInstruction(
    private val message: BattleMessage? = null
) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        // Intentionally do nothing; optionally, keep a tiny trace for debugging.
        battle.log("NoOp Instruction")
    }
}