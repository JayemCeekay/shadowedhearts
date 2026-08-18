package com.jayemceekay.shadowedhearts.network.aura

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.ShadowAuraEmitters
import net.minecraft.client.Minecraft

object AuraLifecycleHandler : ClientNetworkPacketHandler<AuraLifecyclePacket> {
    override fun handle(packet: AuraLifecyclePacket, client: Minecraft) {
        ShadowAuraEmitters.receiveLifecycle(packet)
    }
}
