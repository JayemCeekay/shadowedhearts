package com.jayemceekay.shadowedhearts.network.preset

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.config.preset.RulesPresetService
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.WeakHashMap

object RulesPresetRequestHandler : ServerNetworkPacketHandler<RulesPresetRequestPacket> {
    private data class Budget(var tokens: Double = 8.0, var at: Long = System.nanoTime())
    private val budgets = WeakHashMap<ServerPlayer, Budget>()

    @Synchronized private fun admit(player: ServerPlayer): Boolean {
        val budget = budgets.getOrPut(player) { Budget() }
        val now = System.nanoTime()
        budget.tokens = (budget.tokens + (now - budget.at).coerceAtLeast(0) / 1_000_000_000.0 * 4).coerceAtMost(8.0)
        budget.at = now
        if (budget.tokens < 1) return false
        budget.tokens--
        return true
    }

    override fun handle(packet: RulesPresetRequestPacket, server: MinecraftServer, player: ServerPlayer) {
        val admitted = admit(player)
        server.execute {
            if (server.playerList.getPlayer(player.uuid) !== player) return@execute
            val response = if (!admitted) failure(packet.json, "rate_limited") else try {
                // The service checks current permissions and binds writes to the actual loaded server files.
                RulesPresetService.handle(player, packet.json)
            } catch (exception: RuntimeException) {
                Shadowedhearts.LOGGER.warn("Rules preset request failed for {}", player.uuid, exception)
                failure(packet.json, "process_failed")
            }
            val bounded = if (response.length <= RulesPresetResponsePacket.MAX_JSON_LENGTH) response
                else failure(packet.json, "response_too_large")
            RulesPresetResponsePacket(bounded).sendToPlayer(player)
        }
    }

    private fun failure(request: String, reason: String): String =
        RulesPresetReplies.error(request, "screen.shadowedhearts.rules_presets.$reason")
}
