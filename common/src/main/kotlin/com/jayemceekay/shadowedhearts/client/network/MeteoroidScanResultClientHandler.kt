package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.network.aura.MeteoroidScanResultPacket
import net.minecraft.client.Minecraft

object MeteoroidScanResultClientHandler : ClientNetworkPacketHandler<MeteoroidScanResultPacket> {
    override fun handle(packet: MeteoroidScanResultPacket, client: Minecraft) {
        // Aura Reader HUD removed — will be reworked
    }
}
