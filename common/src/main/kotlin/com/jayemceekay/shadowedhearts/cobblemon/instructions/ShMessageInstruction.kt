package com.jayemceekay.shadowedhearts.cobblemon.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import net.minecraft.network.chat.Component

/**
 * Custom instruction to display translated battle messages.
 * Format: |sh_message|LANG_KEY|PNX|...EXTRA_ARGS
 */
class ShMessageInstruction(private val message: BattleMessage) : InterpreterInstruction {
    companion object {
        private const val DISOBEY_KEY = "cobblemon.battle.hypermode.disobey"
        private val MOVE_ID = Regex("[a-z0-9]+")

        /** Only the disobey message's explicitly typed move argument is translated. */
        @JvmStatic
        fun localizeExtraArgument(key: String, index: Int, argument: String): Any {
            if (key == DISOBEY_KEY && index == 0 && argument.startsWith("move:")) {
                val moveId = argument.removePrefix("move:")
                if (MOVE_ID.matches(moveId)) return Component.translatable("cobblemon.move.$moveId")
            }
            // Includes legacy in-flight move display names and arbitrary authored text.
            return argument
        }
    }

    override fun invoke(battle: PokemonBattle) {
        val key = message.argumentAt(0) ?: return
        val pnxRaw = message.argumentAt(1) ?: return

        // Extract pnx (e.g., p1a) from ident (e.g., p1a: Bulbasaur)
        val pnx = if (pnxRaw.contains(":")) pnxRaw.split(":")[0].trim() else pnxRaw

        val (_, active) = battle.getActorAndActiveSlotFromPNX(pnx) ?: return

        val pokemonName = active.battlePokemon?.getName() ?: Component.translatable("battle.shadowedhearts.unknown_pokemon")
        
        // Extract extra arguments starting from index 2
        val extraArgs = mutableListOf<Any>()
        var i = 2
        while (true) {
            val arg = message.argumentAt(i) ?: break
            extraArgs.add(localizeExtraArgument(key, i - 2, arg))
            i++
        }
        
        // We use Component.translatable which will use the Minecraft lang system
        // We pass the pokemonName as the first argument as it's the most common case
        val langArgs = mutableListOf<Any>(pokemonName)
        langArgs.addAll(extraArgs)
        
        battle.broadcastChatMessage(Component.translatable(key, *langArgs.toTypedArray()))
    }
}
