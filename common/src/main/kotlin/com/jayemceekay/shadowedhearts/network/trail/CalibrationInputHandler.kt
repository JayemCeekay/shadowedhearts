package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.tracking.TrailManager
import com.jayemceekay.shadowedhearts.common.tracking.TrailSession
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import com.jayemceekay.shadowedhearts.registry.ModSounds
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource

/**
 * Server handler for calibration directional inputs from the client.
 */
object CalibrationInputHandler : ServerNetworkPacketHandler<CalibrationInputC2SPacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: CalibrationInputC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val session = TrailManager.get(player.uuid).orElse(null) ?: return
        if (session.state != TrailSession.State.CALIBRATION_ACTIVE) {
            LOG.debug("[ShadowHunt] CalibrationInput: ignored, state={} for player={}", session.state, player.name.string)
            return
        }

        val correct = session.submitCalibrationInput(packet.direction)
        val calibration = session.currentCalibration
        LOG.debug("[ShadowHunt] CalibrationInput: dir={}, correct={}, index={}/{}, state={}",
            packet.direction, correct, calibration?.currentInputIndex, calibration?.sequence?.size, session.state)

        // Play correct/wrong feedback sounds
        if (correct) {
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_CALIBRATION_CORRECT.get(), SoundSource.PLAYERS, 0.7f, 1.0f)
        } else {
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_CALIBRATION_WRONG.get(), SoundSource.PLAYERS, 0.7f, 1.0f)
        }

        // If calibration completed (via submitCalibrationInput -> markScanned), send updated trail
        if (session.state == TrailSession.State.SEGMENT_REVEALED || session.state == TrailSession.State.FINAL_LOCK) {
            LOG.debug("[ShadowHunt] Calibration complete: grade={}, state={}, hasMore={}",
                session.lastCalibrationGrade, session.state, session.hasMore())
            // Play calibration complete + grade sound
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_CALIBRATION_COMPLETE.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
            when (session.lastCalibrationGrade) {
                com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence.Grade.PERFECT ->
                    player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_GRADE_PERFECT.get(), SoundSource.PLAYERS, 1.0f, 1.0f)
                com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence.Grade.SLOPPY ->
                    player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_GRADE_SLOPPY.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
                com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence.Grade.FAILED ->
                    player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_GRADE_FAILED.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
                else -> {} // STANDARD — no extra sound
            }
            // Node was completed — advance to next hotspot and sync trail
            val hotspot = if (session.hasMore()) session.advanceToNextHotspot(2.5f) else null
            val nodes = session.nodes.subList(session.index, session.nodes.size).map { it.pos() }
            val eventTypes = session.nodes.subList(session.index, session.nodes.size).map { it.eventType() }
            ShadowedHeartsNetwork.sendToPlayer(
                player,
                TrailSyncS2CPacket(
                    nodes = nodes,
                    hotspot = hotspot?.pos(),
                    tier = session.tier,
                    eventTypes = eventTypes,
                    tension = session.tension,
                    trailQuality = session.trailQuality,
                    currentNodeIndex = session.index,
                    huntSeed = session.huntSeed
                )
            )
            // Also send calibration result
            val grade = session.lastCalibrationGrade
            if (calibration != null) {
                ShadowedHeartsNetwork.sendToPlayer(
                    player,
                    CalibrationSyncS2CPacket.fromSequence(calibration, grade)
                )
            }
        } else if (calibration != null) {
            // Still in calibration — sync progress
            ShadowedHeartsNetwork.sendToPlayer(
                player,
                CalibrationSyncS2CPacket.fromSequence(calibration)
            )
        }
    }
}
