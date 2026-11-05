package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.capture.DarkBallClientState
import com.jayemceekay.shadowedhearts.network.items.DarkBallSettingsS2CPacket
import net.minecraft.client.Minecraft

object DarkBallSettingsClientHandler : ClientNetworkPacketHandler<DarkBallSettingsS2CPacket> {
    override fun handle(packet: DarkBallSettingsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        if (client.isSameThread) {
            DarkBallClientState.INSTANCE.receive(connection, packet.settings)
        } else {
            client.execute { DarkBallClientState.INSTANCE.receive(connection, packet.settings) }
        }
    }
}
