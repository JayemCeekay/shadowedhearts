package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters
import net.minecraft.client.Minecraft

object AuraStateHandler : ClientNetworkPacketHandler<AuraStatePacket> {
    override fun handle(packet: AuraStatePacket, client: Minecraft) {
        AuraEmitters.receiveState(packet)
    }
}
