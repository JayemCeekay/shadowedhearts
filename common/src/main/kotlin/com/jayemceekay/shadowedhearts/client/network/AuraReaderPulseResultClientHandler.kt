package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderClientState
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderPulseRenderer
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.network.aura.AuraReaderPulseResultS2CPacket
import com.jayemceekay.shadowedhearts.registry.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance

object AuraReaderPulseResultClientHandler : ClientNetworkPacketHandler<AuraReaderPulseResultS2CPacket> {
    override fun handle(packet: AuraReaderPulseResultS2CPacket, client: Minecraft) {
        AuraReaderClientState.setScanRange(packet.scanRange)
        val readings = packet.readings.map(::snapshot)

        if (!packet.pulseScan) {
            AuraReaderClientState.applyReadings(readings, false)
        } else if (packet.pulseAccepted) {
            AuraReaderClientState.applyPulseResult(readings)
            client.player?.let { player ->
                if (AuraReaderClientState.isActive()) {
                    client.soundManager.play(SimpleSoundInstance.forUI(
                        ModSounds.AURA_SCANNER_BEEP.get(), 1.0f,
                        ShadowedHeartsConfigs.getInstance().clientConfig.soundConfig().auraScannerBeepVolume()
                    ))
                    AuraReaderPulseRenderer.spawnPulse(player.position(), 0.0f, 0.95f, 1.0f, packet.scanRange.coerceAtLeast(1).toFloat())
                }
            }
        }
    }

    fun snapshot(it: AuraReaderPulseResultS2CPacket.ReadingSummary) =
            AuraReaderClientState.ReadingSnapshot(
                it.id,
                it.type,
                it.source,
                it.labelText,
                it.statusText,
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
                it.outOfDimension,
                it.heartGauge
            )
}
