package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.data.ShadowLearnsetClientState
import com.jayemceekay.shadowedhearts.network.items.ShadowLearnsetsS2CPacket
import net.minecraft.client.Minecraft

object ShadowLearnsetsClientHandler : ClientNetworkPacketHandler<ShadowLearnsetsS2CPacket> {
    override fun handle(packet: ShadowLearnsetsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        client.execute { ShadowLearnsetClientState.INSTANCE.receive(connection, packet.first, packet.last, packet.profiles) }
    }
}
