package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.jayemceekay.shadowedhearts.SHAspects
import com.jayemceekay.shadowedhearts.ShadowAspectUtil

/**
 * Custom instruction emitted by our Showdown patches to persist Hyper Mode outside battle.
 * Format: |hyper|start|PNX  or  |hyper|end|PNX
 */
class HyperInstruction(private val message: BattleMessage) : InterpreterInstruction {
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
                if (!aspectsEff.contains(SHAspects.HYPER_MODE)) {
                    aspectsEff.add(SHAspects.HYPER_MODE)
                    effected.forcedAspects = aspectsEff
                    effected.updateAspects()
                    ShadowAspectUtil.syncAspects(effected)
                }
                val aspectsOrig = original.aspects.toMutableSet()
                if (!aspectsOrig.contains(SHAspects.HYPER_MODE)) {
                    aspectsOrig.add(SHAspects.HYPER_MODE)
                    original.forcedAspects = aspectsOrig
                    original.updateAspects()
                    ShadowAspectUtil.syncAspects(original)
                }
            }
            "end" -> {
                val aspectsEff = effected.aspects.toMutableSet()
                if (aspectsEff.remove(SHAspects.HYPER_MODE)) {
                    effected.forcedAspects = aspectsEff
                    effected.updateAspects()
                    ShadowAspectUtil.syncAspects(effected)
                }
                val aspectsOrig = original.aspects.toMutableSet()
                if (aspectsOrig.remove(SHAspects.HYPER_MODE)) {
                    original.forcedAspects = aspectsOrig
                    original.updateAspects()
                    ShadowAspectUtil.syncAspects(original)
                }
            }
        }
    }
}
