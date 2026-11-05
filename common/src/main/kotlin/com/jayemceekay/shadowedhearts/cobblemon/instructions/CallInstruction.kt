package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeEvents
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs

/**
 * Format: |call|PNX
 * Example: |call|p1a
 */
class CallInstruction(private val message: BattleMessage) :
    InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        val pnx = message.argumentAt(0)?.substringBefore(':') ?: return
        // Read the mode after preceding start messages and before its queued end.
        battle.dispatchGo {
            // Our Call protocol emits a bare slot, without Cobblemon's UUID suffix.
            val (_, active) = battle.getActorAndActiveSlotFromPNX(pnx)
            val bp = active.battlePokemon ?: return@dispatchGo

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
                    // If enabled, calling the Pokemon out of either mode opens its heart.
                    if (ShadowedHeartsConfigs.getInstance().shadowConfig.callButtonReducesHeartGauge()) {
                        HeartGaugeEvents.onCalledInBattle(live)
                    }
                }
            }
        }
    }
}
