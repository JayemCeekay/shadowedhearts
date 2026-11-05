package com.jayemceekay.shadowedhearts.network.trainer

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorMessages
import net.minecraft.network.chat.Component
import com.jayemceekay.shadowedhearts.common.editor.TrainerEditorService
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.WeakHashMap

object TrainerEditorRequestHandler : ServerNetworkPacketHandler<TrainerEditorRequestPacket> {
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

    override fun handle(packet: TrainerEditorRequestPacket, server: MinecraftServer, player: ServerPlayer) {
        val allowed = admit(player)
        // Keep world access on the server thread, including direct invocations outside packet dispatch.
        server.execute {
            if (server.playerList.getPlayer(player.uuid) !== player) return@execute
            val response = if (!player.hasPermissions(2)) {
                failure(packet.json, TrainerEditorMessages.message("permission_required"))
            } else if (!allowed) {
                failure(packet.json, TrainerEditorMessages.message("rate_limited"))
            } else try {
                TrainerEditorService.handle(player, packet.json)
            } catch (exception: RuntimeException) {
                Shadowedhearts.LOGGER.warn("Trainer editor request failed for {}", player.uuid, exception)
                failure(packet.json, TrainerEditorMessages.message("process_failed"))
            }
            val bounded = if (response.length <= TrainerEditorResponsePacket.MAX_JSON_LENGTH) response
                else failure(packet.json, TrainerEditorMessages.message("definitions_too_large"))
            TrainerEditorResponsePacket(bounded).sendToPlayer(player)
        }
    }

    private fun failure(request: String, message: Component): String {
        val result = JsonObject()
        try {
            TrainerEditorService.validateEnvelope(request)
            val input = JsonParser.parseString(request).asJsonObject
            for (key in listOf("requestId", "action", "category")) {
                input.get(key)?.takeIf { it.isJsonPrimitive }?.let { result.add(key, it) }
            }
        } catch (_: RuntimeException) { /* Malformed input still receives a bounded error. */ }
        result.addProperty("ok", false)
        result.add("message", TrainerEditorMessages.json(message))
        return result.toString()
    }
}
