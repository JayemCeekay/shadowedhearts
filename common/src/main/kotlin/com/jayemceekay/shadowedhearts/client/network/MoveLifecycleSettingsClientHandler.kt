package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycleClientState
import com.jayemceekay.shadowedhearts.network.items.MoveLifecycleSettingsS2CPacket
import net.minecraft.client.Minecraft

object MoveLifecycleSettingsClientHandler : ClientNetworkPacketHandler<MoveLifecycleSettingsS2CPacket> {
    override fun handle(packet: MoveLifecycleSettingsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        client.execute { MoveLifecycleClientState.INSTANCE.receive(connection, packet.removalAllowed) }
    }
}
