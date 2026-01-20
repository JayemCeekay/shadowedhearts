package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.gui.AuraScannerHUD
import com.jayemceekay.shadowedhearts.network.AuraScannerS2CPacket
import net.minecraft.client.Minecraft

object AuraScannerClientHandler : ClientNetworkPacketHandler<AuraScannerS2CPacket> {
    override fun handle(packet: AuraScannerS2CPacket, client: Minecraft) {
        AuraScannerHUD.setActive(packet.active)
    }
}
