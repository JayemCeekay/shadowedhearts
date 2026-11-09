package com.jayemceekay.shadowedhearts.network.craterrescue

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.craterrescue.CraterRescueService
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

data class CraterRescueResponsePacket(val siteId: UUID, val accept: Boolean, val offeredBall: ResourceLocation) :
    NetworkPacket<CraterRescueResponsePacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(siteId)
        buffer.writeBoolean(accept)
        buffer.writeResourceLocation(offeredBall)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "crater_rescue_response")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            CraterRescueResponsePacket(buffer.readUUID(), buffer.readBoolean(), buffer.readResourceLocation())
    }
}

object CraterRescueResponseHandler : ServerNetworkPacketHandler<CraterRescueResponsePacket> {
    override fun handle(packet: CraterRescueResponsePacket, server: MinecraftServer, player: ServerPlayer) {
        server.execute {
            if (server.playerList.getPlayer(player.uuid) === player) {
                CraterRescueService.respond(player, packet.siteId, packet.accept, packet.offeredBall)
            }
        }
    }
}
