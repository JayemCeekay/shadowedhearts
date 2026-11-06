package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.jayemceekay.shadowedhearts.network.PokemonStateSync

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects

/**
 * Custom instruction emitted by our Showdown patches to persist Hyper Mode outside battle.
 * Format: |hyper|start|PNX  or  |hyper|end|PNX
 */
class HyperInstruction(private val message: BattleMessage) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        val action = message.argumentAt(0) ?: return

        val pnx = message.argumentAt(1) ?: return

        // Keep mode changes and Call in the same order as the battle actions.
        battle.dispatchGo {
            val (_, active) = battle.getActorAndActiveSlotFromPNX(pnx)
            val bp = active.battlePokemon ?: return@dispatchGo

            val effected = bp.effectedPokemon
            val original = bp.originalPokemon

            when (action) {
                "start" -> {
                    // Add aspect to both effected and original to ensure persistence
                    val aspectsEff = effected.aspects.toMutableSet()
                    if (!aspectsEff.contains(SHAspects.HYPER_MODE)) {
                        aspectsEff.add(SHAspects.HYPER_MODE)
                        effected.forcedAspects = aspectsEff
                        effected.updateAspects()
                        PokemonStateSync.syncAspects(effected)
                    }
                    val aspectsOrig = original.aspects.toMutableSet()
                    if (!aspectsOrig.contains(SHAspects.HYPER_MODE)) {
                        aspectsOrig.add(SHAspects.HYPER_MODE)
                        original.forcedAspects = aspectsOrig
                        original.updateAspects()
                        PokemonStateSync.syncAspects(original)
                    }
                }
                "end" -> {
                    val aspectsEff = effected.aspects.toMutableSet()
                    if (aspectsEff.remove(SHAspects.HYPER_MODE)) {
                        effected.forcedAspects = aspectsEff
                        effected.updateAspects()
                        PokemonStateSync.syncAspects(effected)
                    }
                    val aspectsOrig = original.aspects.toMutableSet()
                    if (aspectsOrig.remove(SHAspects.HYPER_MODE)) {
                        original.forcedAspects = aspectsOrig
                        original.updateAspects()
                        PokemonStateSync.syncAspects(original)
                    }
                }
            }
        }
    }
}
