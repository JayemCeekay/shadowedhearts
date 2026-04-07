package com.jayemceekay.shadowedhearts.client.network

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.client.trail.TrailClientState
import com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence
import com.jayemceekay.shadowedhearts.network.trail.CalibrationSyncS2CPacket
import net.minecraft.client.Minecraft

/**
 * Applies CalibrationSyncS2CPacket to client state for HUD rendering.
 */
object CalibrationSyncClientHandler : ClientNetworkPacketHandler<CalibrationSyncS2CPacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: CalibrationSyncS2CPacket, client: Minecraft) {
        client.execute {
            LOG.debug("[ShadowHunt][Client] CalibrationSync: variant={}, index={}/{}, completed={}, failed={}, grade={}",
                packet.variant, packet.currentInputIndex, packet.sequence.size, packet.completed, packet.failed, packet.grade)
            TrailClientState.syncCalibration(
                sequence = packet.sequence,
                variant = packet.variant,
                timeLimitTicks = packet.timeLimitTicks,
                currentInputIndex = packet.currentInputIndex,
                mistakes = packet.mistakes,
                elapsedTicks = packet.elapsedTicks,
                completed = packet.completed,
                failed = packet.failed,
                grade = packet.grade
            )

            // Trigger grade flash when calibration completes with a grade
            if ((packet.completed || packet.failed) && packet.grade != null) {
                val gradeText = packet.grade.name
                val color = when (packet.grade) {
                    CalibrationSequence.Grade.PERFECT -> 0x00FF00
                    CalibrationSequence.Grade.STANDARD -> 0x00FFFF
                    CalibrationSequence.Grade.SLOPPY -> 0xFF8800
                    CalibrationSequence.Grade.FAILED -> 0xFF2200
                }
                TrailClientState.showGradeFlash(gradeText, color)
            }
        }
    }
}
