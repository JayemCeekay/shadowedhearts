package com.jayemceekay.shadowedhearts.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import net.minecraft.client.Minecraft

object SnagResultHandler : ClientNetworkPacketHandler<SnagResultPacket> {
    override fun handle(packet: SnagResultPacket, client: Minecraft) {
        // currently no-op on client as per ModNetworking.java
    }
}
