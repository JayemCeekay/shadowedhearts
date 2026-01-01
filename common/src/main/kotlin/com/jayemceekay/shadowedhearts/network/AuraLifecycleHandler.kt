package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters
import net.minecraft.client.Minecraft

object AuraLifecycleHandler : ClientNetworkPacketHandler<AuraLifecyclePacket> {
    override fun handle(packet: AuraLifecyclePacket, client: Minecraft) {
        AuraEmitters.receiveLifecycle(packet)
    }
}
