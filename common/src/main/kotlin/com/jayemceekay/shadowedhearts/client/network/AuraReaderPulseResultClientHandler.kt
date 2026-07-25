package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.AuraReaderClientState
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderPulseResultS2CPacket
import net.minecraft.client.Minecraft

object AuraReaderPulseResultClientHandler : ClientNetworkPacketHandler<AuraReaderPulseResultS2CPacket> {
    override fun handle(packet: AuraReaderPulseResultS2CPacket, client: Minecraft) {
        if (!packet.pulseScan) {
            AuraReaderClientState.applyReadings(
                packet.readings.map {
                    AuraReaderClientState.ReadingSnapshot(
                        it.id,
                        it.type,
                        it.label,
                        it.status,
                        it.distanceBand,
                        it.verticalHint,
                        it.strength,
                        it.confidence
                    )
                },
                false
            )
        }

        if (packet.pulseScan && packet.pulseAccepted) {
            client.player?.let { player ->
                if (AuraReaderClientState.isActive()) {
                    AuraPulseRenderer.spawnPulse(player.position(), 0.0f, 0.95f, 1.0f, 128.0f)
                }
            }
        }
    }
}
