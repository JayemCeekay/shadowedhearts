package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.gui.AuraScannerHUD
import com.jayemceekay.shadowedhearts.network.MeteoroidScanResultPacket
import net.minecraft.client.Minecraft

object MeteoroidScanResultClientHandler : ClientNetworkPacketHandler<MeteoroidScanResultPacket> {
    override fun handle(packet: MeteoroidScanResultPacket, client: Minecraft) {
        AuraScannerHUD.enqueueMeteoroidCenters(packet.centers)
    }
}
