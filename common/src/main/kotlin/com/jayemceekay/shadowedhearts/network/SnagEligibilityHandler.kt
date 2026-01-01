package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.snag.ClientSnagState
import net.minecraft.client.Minecraft

object SnagEligibilityHandler : ClientNetworkPacketHandler<SnagEligibilityPacket> {
    override fun handle(packet: SnagEligibilityPacket, client: Minecraft) {
        ClientSnagState.setEligible(packet.eligible)
    }
}
