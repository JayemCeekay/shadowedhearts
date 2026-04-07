package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.trail.TrailClientState
import com.jayemceekay.shadowedhearts.network.trail.NodeEventSyncS2CPacket
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes

/**
 * Applies NodeEventSyncS2CPacket to client state for HUD rendering.
 */
object NodeEventSyncClientHandler : ClientNetworkPacketHandler<NodeEventSyncS2CPacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: NodeEventSyncS2CPacket, client: Minecraft) {
        client.execute {
            LOG.debug("[ShadowHunt][Client] NodeEventSync: type={}, phase={}, elapsed={}/{}, wrongGuesses={}, signal={}",
                packet.eventType, packet.phase, packet.ticksElapsed, packet.maxTicks, packet.wrongGuesses, packet.signalBuildup)
            TrailClientState.syncNodeEvent(
                eventType = packet.eventType,
                phase = packet.phase,
                ticksElapsed = packet.ticksElapsed,
                maxTicks = packet.maxTicks,
                cluePositions = packet.cluePositions,
                wrongGuesses = packet.wrongGuesses,
                requiredValidCount = packet.requiredValidCount,
                foundValidCount = packet.foundValidCount,
                selectedClueIndices = packet.selectedClueIndices,
                searchCenter = packet.searchCenter,
                searchRadius = packet.searchRadius,
                searchSignalStrength = packet.searchSignalStrength,
                wildResolveTimer = packet.wildResolveTimer,
                wildsResolved = packet.wildsResolved,
                signalBuildup = packet.signalBuildup,
                provocationRequiredTicks = packet.provocationRequiredTicks
            )

            // Spawn placeholder cloud particles at evidence clue positions when event starts
            if (packet.eventType == com.jayemceekay.shadowedhearts.common.tracking.NodeEventType.EVIDENCE_INTERPRETATION
                && packet.phase == 1 // ACTIVE
                && packet.cluePositions.isNotEmpty()) {
                val level = client.level
                if (level != null) {
                    for ((idx, pos) in packet.cluePositions.withIndex()) {
                        val alreadySelected = packet.selectedClueIndices.contains(idx)
                        if (!alreadySelected) {
                            // Spawn cloud particles as placeholder indicator at each unselected clue
                            repeat(5) {
                                level.addParticle(
                                    ParticleTypes.CLOUD,
                                    pos.x + 0.5 + (Math.random() - 0.5) * 0.6,
                                    pos.y + 0.8 + Math.random() * 0.5,
                                    pos.z + 0.5 + (Math.random() - 0.5) * 0.6,
                                    0.0, 0.02, 0.0
                                )
                            }
                        }
                    }
                }
            }

            // Trigger grade flash on completion or failure
            val completedPhase = 2 // NodeEventState.Phase.COMPLETED.ordinal
            val failedPhase = 3    // NodeEventState.Phase.FAILED.ordinal
            if (packet.phase == completedPhase) {
                // Grade based on wrong guesses for evidence, time for others
                val grade = when {
                    packet.eventType == com.jayemceekay.shadowedhearts.common.tracking.NodeEventType.EVIDENCE_INTERPRETATION ->
                        if (packet.wrongGuesses == 0) "PERFECT" else if (packet.wrongGuesses >= 2) "SLOPPY" else "STANDARD"
                    packet.maxTicks > 0 -> {
                        val ratio = packet.ticksElapsed.toFloat() / packet.maxTicks
                        if (ratio < 0.5f) "PERFECT" else if (ratio > 0.8f) "SLOPPY" else "STANDARD"
                    }
                    else -> "STANDARD"
                }
                val color = when (grade) {
                    "PERFECT" -> 0x00FF00
                    "SLOPPY" -> 0xFF8800
                    else -> 0x00FFFF
                }
                TrailClientState.showGradeFlash(grade, color)
            } else if (packet.phase == failedPhase) {
                TrailClientState.showGradeFlash("FAILED", 0xFF2200)
            }
        }
    }
}
