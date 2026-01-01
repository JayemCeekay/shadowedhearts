package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.jayemceekay.shadowedhearts.PokemonAspectUtil
import com.jayemceekay.shadowedhearts.SHAspects

/**
 * Custom instruction emitted by our Showdown patches to persist Reverse Mode outside battle.
 * Format: |reverse|start|PNX  or  |reverse|end|PNX
 */
class ReverseInstruction(private val message: BattleMessage) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        val action = message.argumentAt(0) ?: return

        // Locate actor + active slot using the next token (index 1) after the id
        val (_, active) = battle.getActorAndActiveSlotFromPNX(message.argumentAt(1) ?: return)
        val bp = active.battlePokemon ?: return

        val effected = bp.effectedPokemon
        val original = bp.originalPokemon

        when (action) {
            "start" -> {
                // Add aspect to both effected and original to ensure persistence
                val aspectsEff = effected.aspects.toMutableSet()
                if (!aspectsEff.contains(SHAspects.REVERSE_MODE)) {
                    aspectsEff.add(SHAspects.REVERSE_MODE)
                    effected.forcedAspects = aspectsEff
                    effected.updateAspects()
                    PokemonAspectUtil.syncAspects(effected)
                }
                val aspectsOrig = original.aspects.toMutableSet()
                if (!aspectsOrig.contains(SHAspects.REVERSE_MODE)) {
                    aspectsOrig.add(SHAspects.REVERSE_MODE)
                    original.forcedAspects = aspectsOrig
                    original.updateAspects()
                    PokemonAspectUtil.syncAspects(original)
                }
            }
            "end" -> {
                val aspectsEff = effected.aspects.toMutableSet()
                if (aspectsEff.remove(SHAspects.REVERSE_MODE)) {
                    effected.forcedAspects = aspectsEff
                    effected.updateAspects()
                    PokemonAspectUtil.syncAspects(effected)
                }
                val aspectsOrig = original.aspects.toMutableSet()
                if (aspectsOrig.remove(SHAspects.REVERSE_MODE)) {
                    original.forcedAspects = aspectsOrig
                    original.updateAspects()
                    PokemonAspectUtil.syncAspects(original)
                }
            }
        }
    }
}
