package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.jayemceekay.shadowedhearts.SHAspects
import com.jayemceekay.shadowedhearts.config.ModConfig
import com.jayemceekay.shadowedhearts.heart.HeartGaugeEvents

/**
 * Format: |call|PNX
 * Example: |call|p1a
 */
class CallInstruction(private val message: BattleMessage) : InterpreterInstruction {
    override fun invoke(battle: PokemonBattle) {
        // Locate actor + active slot using the first token (index 0) after the id
        val (_, active) = message.actorAndActivePokemon(0, battle) ?: return
        val bp = active.battlePokemon ?: return

        val effected = bp.effectedPokemon
        val original = bp.originalPokemon

        // If the toggle is on, and the Pokemon is in Hyper or Reverse mode, reduce heart gauge
        if (ModConfig.get().callButton.reducesHeartGauge) {
            val isHyper = effected.aspects.contains(SHAspects.HYPER_MODE) || original.aspects.contains(SHAspects.HYPER_MODE)
            val isReverse = effected.aspects.contains(SHAspects.REVERSE_MODE) || original.aspects.contains(SHAspects.REVERSE_MODE)

            if (isHyper || isReverse) {
                effected.entity?.let { live ->
                    if (live is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                        HeartGaugeEvents.onCalledInBattle(live)
                    }
                }
            }
        }

        // If the toggle is on, and the Pokemon is asleep, we heal it if config allows.
        // Actually, Showdown side already calls pokemon.cureStatus() if config allows.
        // But we must make sure the Minecraft side reflects this.
        // Standard Cobblemon StatusInstruction should handle the |curestatus message that Showdown emits.
        println("[DEBUG_LOG] Processing |call| instruction for ${bp.getName().string} in battle ${battle.battleId}")
        battle.log("Call instruction processed for ${bp.getName().string}")
    }
}
