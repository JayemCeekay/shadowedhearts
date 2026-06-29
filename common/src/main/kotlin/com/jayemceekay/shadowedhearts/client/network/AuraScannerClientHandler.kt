package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.network.aura.AuraScannerS2CPacket
import net.minecraft.client.Minecraft

object AuraScannerClientHandler : ClientNetworkPacketHandler<AuraScannerS2CPacket> {
    override fun handle(packet: AuraScannerS2CPacket, client: Minecraft) {
        // Aura Reader HUD removed — will be reworked
    }
}
