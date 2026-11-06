package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderClientState
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderBattleDetectionS2CPacket
import net.minecraft.client.Minecraft

object AuraReaderBattleDetectionClientHandler : ClientNetworkPacketHandler<AuraReaderBattleDetectionS2CPacket> {
    override fun handle(packet: AuraReaderBattleDetectionS2CPacket, client: Minecraft) {
        AuraReaderClientState.setScanRange(packet.analysis.scanRange)
        AuraReaderClientState.applyBattleReadings(packet.battleId, packet.analysis.readings.map {
            AuraReaderPulseResultClientHandler.snapshot(it)
        })
    }
}
