package com.jayemceekay.shadowedhearts.common.event.battle

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleEndedEvent
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeEvents
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Awards battle Heart Gauge progress once per player-owned Shadow Pokémon per battle,
 * including active Pokémon that were already sent out before the battle began.
 */
object BattleSentOnceListener {
    // battleId -> set of pokemon UUIDs that already fired
    private val seenByBattle: MutableMap<UUID, MutableSet<UUID>> = ConcurrentHashMap()

    fun init() {
        CobblemonEvents.POKEMON_SENT_POST.subscribe { ev: PokemonSentEvent.Post ->
            val entity = ev.pokemonEntity
            val battleId = entity.battleId ?: return@subscribe
            val battle = BattleRegistry.getBattle(battleId) ?: return@subscribe
            applyOnce(battle, entity)
        }

        // Includes captures, forfeits and stopped battles as well as victory/flee.
        CobblemonEvents.BATTLE_ENDED.subscribe { ev: BattleEndedEvent ->
            seenByBattle.remove(ev.battle.battleId)
        }
    }

    /** Existing entities emit no send event when assigned to an initial or switched-in slot. */
    @JvmStatic
    fun onBattlePokemonActivated(active: ActiveBattlePokemon) {
        val entity = active.battlePokemon?.entity ?: return
        applyOnce(active.battle, entity)
    }

    private fun applyOnce(battle: PokemonBattle, entity: PokemonEntity) {
        if (battle.ended || entity.battleId != battle.battleId) return
        val pokemon = entity.pokemon
        if (!pokemon.isPlayerOwned() || !ShadowAspectUtil.hasShadowAspect(pokemon)) return
        // A pre-battle send animation can finish after battle IDs were assigned to the
        // whole party. Its POST event must not award progress to a benched Pokémon.
        if (battle.activePokemon.none {
            it.actor.type == ActorType.PLAYER && it.isAlive() && !it.isGone()
                    && it.battlePokemon?.effectedPokemon === pokemon
        }) return

        val seen = seenByBattle.computeIfAbsent(battle.battleId) { ConcurrentHashMap.newKeySet() }
        if (seen.add(pokemon.uuid)) {
            HeartGaugeEvents.onBattleSentOut(entity)
        }
    }
}
