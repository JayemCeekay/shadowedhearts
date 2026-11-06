package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.shadow.style.AuraStyleClientState
import com.jayemceekay.shadowedhearts.network.aura.AuraStyleDefaultS2CPacket
import net.minecraft.client.Minecraft

object AuraStyleDefaultClientHandler : ClientNetworkPacketHandler<AuraStyleDefaultS2CPacket> {
    override fun handle(packet: AuraStyleDefaultS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        // Both loader registrations dispatch on the client thread. Retain the connection
        // identity if another caller needs to enqueue this handler in the future.
        if (client.isSameThread) {
            AuraStyleClientState.INSTANCE.receive(connection, packet.style)
        } else {
            client.execute { AuraStyleClientState.INSTANCE.receive(connection, packet.style) }
        }
    }
}
