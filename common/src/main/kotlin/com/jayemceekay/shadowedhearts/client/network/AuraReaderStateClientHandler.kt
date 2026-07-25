package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderClientState
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderStateS2CPacket
import net.minecraft.client.Minecraft

object AuraReaderStateClientHandler : ClientNetworkPacketHandler<AuraReaderStateS2CPacket> {
    override fun handle(packet: AuraReaderStateS2CPacket, client: Minecraft) {
        AuraReaderClientState.applyServerState(
            packet.active,
            packet.mode,
            packet.charge,
            packet.maxCharge,
            packet.tracking,
            packet.selectedSignal,
            packet.lockType,
            packet.pulseCooldownRemainingTicks,
            packet.pulseCooldownMaxTicks
        )
    }
}
