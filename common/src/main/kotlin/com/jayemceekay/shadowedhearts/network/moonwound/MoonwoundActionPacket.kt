package com.jayemceekay.shadowedhearts.network.moonwound

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

data class MoonwoundActionPacket(
    val siteId: UUID,
    val eventId: UUID,
    val action: Action,
    val target: UUID?
) : NetworkPacket<MoonwoundActionPacket> {
    enum class Action {
        JOIN, LEAVE, READY, ACCEPT_JOIN, DECLINE_JOIN, START,
        VOTE_HARVEST, VOTE_SEAL, VOTE_OVERLOAD, REFRESH
    }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(siteId)
        buffer.writeUUID(eventId)
        buffer.writeEnum(action)
        buffer.writeBoolean(target != null)
        target?.let(buffer::writeUUID)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "moonwound_action")
        fun decode(buffer: RegistryFriendlyByteBuf) = MoonwoundActionPacket(
            buffer.readUUID(), buffer.readUUID(), buffer.readEnum(Action::class.java),
            if (buffer.readBoolean()) buffer.readUUID() else null
        )
    }
}

object MoonwoundActionHandler : ServerNetworkPacketHandler<MoonwoundActionPacket> {
    override fun handle(packet: MoonwoundActionPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!server.isSameThread) {
            server.execute { handle(packet, server, player) }
            return
        }
        MoonwoundService.handleAction(player, packet)
    }
}
