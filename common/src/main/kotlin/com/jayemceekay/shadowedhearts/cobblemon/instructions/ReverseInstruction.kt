package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil

/**
 * Custom instruction emitted by our Showdown patches to persist Reverse Mode outside battle.
 * Format: |reverse|start|PNX  or  |reverse|end|PNX[|UUID]
 */
class ReverseInstruction(private val message: BattleMessage) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        val action = message.argumentAt(0) ?: return
        val pnx = message.argumentAt(1) ?: return
        val endedPokemonId = if (action == "end") message.argumentAt(2)?.takeIf { it.isNotBlank() } else null

        // Follow the move/switch dispatches preceding this message. Applying the
        // aspect during parsing would show Reverse Mode before the move plays.
        battle.dispatchGo {
            // FaintInstruction empties the active slot before this queued end runs.
            // Resolve its explicit identity within the original actor's roster so
            // a later occupant can never receive the fainted Pokemon's cleanup.
            val bp = if (endedPokemonId != null) {
                try {
                    battle.getBattlePokemon(pnx, endedPokemonId)
                } catch (_: IllegalStateException) {
                    return@dispatchGo
                }
            } else {
                val (_, active) = battle.getActorAndActiveSlotFromPNX(pnx)
                active.battlePokemon ?: return@dispatchGo
            }

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
                        ShadowAspectUtil.syncAspects(effected)
                    }
                    val aspectsOrig = original.aspects.toMutableSet()
                    if (!aspectsOrig.contains(SHAspects.REVERSE_MODE)) {
                        aspectsOrig.add(SHAspects.REVERSE_MODE)
                        original.forcedAspects = aspectsOrig
                        original.updateAspects()
                        ShadowAspectUtil.syncAspects(original)
                    }
                }
                "end" -> {
                    val aspectsEff = effected.aspects.toMutableSet()
                    if (aspectsEff.remove(SHAspects.REVERSE_MODE)) {
                        effected.forcedAspects = aspectsEff
                        effected.updateAspects()
                        ShadowAspectUtil.syncAspects(effected)
                    }
                    val aspectsOrig = original.aspects.toMutableSet()
                    if (aspectsOrig.remove(SHAspects.REVERSE_MODE)) {
                        original.forcedAspects = aspectsOrig
                        original.updateAspects()
                        ShadowAspectUtil.syncAspects(original)
                    }
                }
            }
        }
    }
}
