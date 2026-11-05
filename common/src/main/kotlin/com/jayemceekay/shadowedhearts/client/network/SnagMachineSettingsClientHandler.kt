package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.snag.SnagMachineClientState
import com.jayemceekay.shadowedhearts.network.items.SnagMachineSettingsS2CPacket
import net.minecraft.client.Minecraft

object SnagMachineSettingsClientHandler : ClientNetworkPacketHandler<SnagMachineSettingsS2CPacket> {
    override fun handle(packet: SnagMachineSettingsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        if (client.isSameThread) {
            SnagMachineClientState.INSTANCE.receive(connection, packet.settings)
        } else {
            client.execute { SnagMachineClientState.INSTANCE.receive(connection, packet.settings) }
        }
    }
}
