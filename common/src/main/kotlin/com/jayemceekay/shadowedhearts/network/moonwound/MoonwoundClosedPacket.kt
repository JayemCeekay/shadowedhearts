package com.jayemceekay.shadowedhearts.network.moonwound

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundClient
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/** Invalidates one event, without closing a replacement lobby at the same site. */
data class MoonwoundClosedPacket(val eventId: UUID) : NetworkPacket<MoonwoundClosedPacket> {
    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(eventId)
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "moonwound_closed")
        fun decode(buffer: RegistryFriendlyByteBuf) = MoonwoundClosedPacket(buffer.readUUID())
    }
}

object MoonwoundClosedHandler : ClientNetworkPacketHandler<MoonwoundClosedPacket> {
    override fun handle(packet: MoonwoundClosedPacket, client: Minecraft) {
        val connection = client.connection
        client.execute {
            if (connection != null && client.connection === connection) MoonwoundClient.close(packet.eventId)
        }
    }
}
