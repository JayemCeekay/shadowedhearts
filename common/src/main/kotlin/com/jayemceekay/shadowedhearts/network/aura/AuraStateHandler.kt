package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.ShadowAuraEmitters
import net.minecraft.client.Minecraft

object AuraStateHandler : ClientNetworkPacketHandler<AuraStatePacket> {
    override fun handle(packet: AuraStatePacket, client: Minecraft) {
        ShadowAuraEmitters.receiveState(packet)
    }
}
