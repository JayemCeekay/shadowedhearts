package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.jayemceekay.shadowedhearts.SHAspects
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.heart.HeartGaugeEvents

/**
 * Format: |call|PNX
 * Example: |call|p1a
 */
class CallInstruction(private val message: BattleMessage) :
    InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        // Locate actor + active slot using the first token (index 0) after the id
        val (_, active) = message.actorAndActivePokemon(0, battle) ?: return
        val bp = active.battlePokemon ?: return

        val effected = bp.effectedPokemon
        val original = bp.originalPokemon


        val isHyper =
            effected.aspects.contains(SHAspects.HYPER_MODE) || original.aspects.contains(
                SHAspects.HYPER_MODE
            )
        val isReverse =
            effected.aspects.contains(SHAspects.REVERSE_MODE) || original.aspects.contains(
                SHAspects.REVERSE_MODE
            )

        if (isHyper || isReverse) {
            effected.entity?.let { live ->
                // If the toggle is on, and the Pokemon is in Hyper or Reverse mode, reduce heart gauge
                if (ShadowedHeartsConfigs.getInstance().shadowConfig.callButtonReducesHeartGauge()) {
                    HeartGaugeEvents.onCalledInBattle(live)
                }
            }
        }
    }
}
