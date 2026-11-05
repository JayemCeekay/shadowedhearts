package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.common.shadow.HeartGaugeClientState
import com.jayemceekay.shadowedhearts.network.restrictions.HeartGaugeSettingsS2CPacket
import net.minecraft.client.Minecraft

object HeartGaugeSettingsClientHandler : ClientNetworkPacketHandler<HeartGaugeSettingsS2CPacket> {
    override fun handle(packet: HeartGaugeSettingsS2CPacket, client: Minecraft) {
        val connection = client.connection ?: return
        if (client.isSameThread) {
            HeartGaugeClientState.INSTANCE.receive(connection, packet.overrides)
        } else {
            client.execute { HeartGaugeClientState.INSTANCE.receive(connection, packet.overrides) }
        }
    }
}
