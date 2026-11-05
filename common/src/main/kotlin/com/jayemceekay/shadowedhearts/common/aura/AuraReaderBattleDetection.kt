package com.jayemceekay.shadowedhearts.common.aura

import com.cobblemon.mod.common.battles.BattleRegistry
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderBattleDetectionS2CPacket
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderPulseResultS2CPacket
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object AuraReaderBattleDetection {
    private val observing = mutableSetOf<UUID>()

    @JvmStatic fun init() {
        TickEvent.PLAYER_POST.register { player ->
            if (player is ServerPlayer && player.tickCount % 10 == 0) update(player)
        }
        PlayerEvent.PLAYER_QUIT.register { observing.remove(it.uuid) }
        LifecycleEvent.SERVER_STOPPED.register { observing.clear() }
    }

    /** Only participants receive analysis of their opposite side's active, sent-out Pokémon. */
    @JvmStatic fun update(player: ServerPlayer) {
        val battle = if (player.isAlive && SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(player))
            BattleRegistry.getBattleByParticipatingPlayer(player) else null
        val actor = battle?.getActor(player)
        if (battle == null || actor == null) {
            if (observing.remove(player.uuid)) send(player, "", emptyList())
            return
        }
        val readings = actor.getSide().getOppositeSide().activePokemon
            .filter { it.isAlive() && !it.isGone() }
            .mapNotNull { it.battlePokemon?.effectedPokemon?.entity }
            .distinctBy { it.uuid }
            .sortedBy { it.distanceToSqr(player) }
            .mapNotNull { ShadowPokemonReadingProvider.battleAnalysis(player, it) }
        observing.add(player.uuid)
        send(player, battle.battleId.toString(), readings)
    }

    /** Action eligibility follows the actual battle, never a client claim or cached observation. */
    @JvmStatic fun isDetecting(player: ServerPlayer): Boolean {
        if (!player.isAlive || !SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(player)) return false
        val battle = BattleRegistry.getBattleByParticipatingPlayer(player) ?: return false
        val actor = battle.getActor(player) ?: return false
        return actor.getSide().getOppositeSide().activePokemon.any {
            it.isAlive() && !it.isGone() && it.battlePokemon?.effectedPokemon?.entity?.let { target ->
                ShadowPokemonReadingProvider.battleAnalysis(player, target) != null
            } == true
        }
    }

    private fun send(player: ServerPlayer, battleId: String, readings: List<AuraReading>) {
        ShadowedHeartsNetwork.sendToPlayer(player, AuraReaderBattleDetectionS2CPacket(battleId,
            AuraReaderPulseResultS2CPacket.fromReadings(readings, false, true,
                ShadowedHeartsConfigs.getInstance().shadowConfig.auraScannerShadowRange().coerceAtLeast(1))))
    }
}
