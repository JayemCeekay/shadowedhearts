package com.jayemceekay.shadowedhearts.server

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent
import com.jayemceekay.shadowedhearts.heart.HeartGaugeEvents
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Ensures `onBattleSentOut` applies only once per Pokémon per battle.
 * Tracks which Pokémon UUIDs have already triggered within a given battleId
 * and suppresses further triggers from switches.
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption terms are gameplay mechanics.
 */
object BattleSentOnceListener {
    // battleId -> set of pokemon UUIDs that already fired
    private val seenByBattle: MutableMap<UUID, MutableSet<UUID>> = ConcurrentHashMap()

    fun init() {
        // When a Pokémon is sent out (party send), fire once per battle
        CobblemonEvents.POKEMON_SENT_POST.subscribe { ev: PokemonSentEvent.Post ->
            val entity = ev.pokemonEntity
            val battleId = entity.battleId ?: return@subscribe
            val pokemonUUID = ev.pokemon.uuid

            val set = seenByBattle.computeIfAbsent(battleId) { ConcurrentHashMap.newKeySet() }
            val firstTime = set.add(pokemonUUID)
            if (firstTime) {
                HeartGaugeEvents.onBattleSentOut(entity)
            }
        }

        // Cleanup when battles end (victory or flee)
        CobblemonEvents.BATTLE_VICTORY.subscribe { ev: BattleVictoryEvent ->
            seenByBattle.remove(ev.battle.battleId)
        }
        CobblemonEvents.BATTLE_FLED.subscribe { ev: BattleFledEvent ->
            seenByBattle.remove(ev.battle.battleId)
        }
    }
}
