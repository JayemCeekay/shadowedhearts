package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderClientState
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderPulseResultS2CPacket
import net.minecraft.client.Minecraft

object AuraReaderPulseResultClientHandler : ClientNetworkPacketHandler<AuraReaderPulseResultS2CPacket> {
    override fun handle(packet: AuraReaderPulseResultS2CPacket, client: Minecraft) {
        val readings = packet.readings.map {
            AuraReaderClientState.ReadingSnapshot(
                it.id,
                it.type,
                it.source,
                it.label,
                it.status,
                it.directionX,
                it.directionY,
                it.directionZ,
                it.bearingUncertainty,
                it.distanceBand,
                it.verticalHint,
                it.strength,
                it.confidence,
                it.interference,
                it.priority,
                it.expiryTick,
                it.selected,
                it.locked,
                it.outOfDimension
            )
        }

        if (!packet.pulseScan) {
            AuraReaderClientState.applyReadings(readings, false)
        } else if (packet.pulseAccepted) {
            AuraReaderClientState.applyPulseResult(readings)
            client.player?.let { player ->
                if (AuraReaderClientState.isActive()) {
                    AuraReaderPulseRenderer.spawnPulse(player.position(), 0.0f, 0.95f, 1.0f, 128.0f)
                }
            }
        }
    }
}
