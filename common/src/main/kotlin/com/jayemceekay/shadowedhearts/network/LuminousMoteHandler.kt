package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.particle.LuminousMoteEmitters
import net.minecraft.client.Minecraft

object LuminousMoteHandler : ClientNetworkPacketHandler<LuminousMotePacket> {
    override fun handle(packet: LuminousMotePacket, client: Minecraft) {
        LuminousMoteEmitters.receivePacket(packet)
    }
}
