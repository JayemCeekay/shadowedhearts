package com.jayemceekay.shadowedhearts.network.trail

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.common.tracking.NodeEventState
import com.jayemceekay.shadowedhearts.common.tracking.TrailManager
import com.jayemceekay.shadowedhearts.common.tracking.TrailSession
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import com.jayemceekay.shadowedhearts.registry.ModSounds
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import kotlin.math.min

/**
 * Server handler for node event interactions from the client.
 * Currently handles Evidence Interpretation clue selection (action=0).
 */
object NodeEventInputHandler : ServerNetworkPacketHandler<NodeEventInputC2SPacket> {
    private val LOG = Shadowedhearts.LOGGER

    override fun handle(packet: NodeEventInputC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        val session = TrailManager.get(player.uuid).orElse(null) ?: return
        if (session.state != TrailSession.State.NODE_EVENT_ACTIVE) return
        val event = session.activeNodeEvent ?: return

        when (packet.action) {
            0 -> handleClueSelection(packet.selectedIndex, session, event, player)
        }
    }

    private fun handleClueSelection(index: Int, session: TrailSession, event: NodeEventState, player: ServerPlayer) {
        if (event.phase != NodeEventState.Phase.ACTIVE) return

        val correct = event.selectClue(index)
        LOG.debug("[ShadowHunt] ClueSelection: index={}, correct={}, wrongGuesses={}, phase={}, player={}",
            index, correct, event.wrongGuesses, event.phase, player.name.string)

        if (event.phase == NodeEventState.Phase.COMPLETED) {
            // Correct clue selected — complete the node event
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_EVIDENCE_CORRECT.get(), SoundSource.PLAYERS, 0.9f, 1.0f)
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_TRAIL_REVEAL.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
            session.completeNodeEvent()
            advanceAndSync(session, player)
        } else if (event.phase == NodeEventState.Phase.FAILED) {
            // Too many wrong guesses — fail with penalty
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_GRADE_FAILED.get(), SoundSource.PLAYERS, 0.7f, 1.0f)
            session.failNodeEvent()
            syncNodeEvent(session, event, player)
        } else {
            // Wrong guess but not failed yet — play wrong sound and sync updated state
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_EVIDENCE_WRONG.get(), SoundSource.PLAYERS, 0.6f, 1.0f)
            syncNodeEvent(session, event, player)
        }
    }

    private fun advanceAndSync(session: TrailSession, player: ServerPlayer) {
        if (session.hasMore()) {
            val next = session.advanceToNextHotspot(2.5f)
            val nextSegmentEnd = min(session.index + 2, session.nodes.size)
            val segmentNodes = session.nodes.subList(session.index, nextSegmentEnd).map { it.pos() }
            val segmentEventTypes = session.nodes.subList(session.index, nextSegmentEnd).map { it.eventType() }
            ShadowedHeartsNetwork.sendToPlayer(player, TrailSyncS2CPacket(
                nodes = segmentNodes,
                hotspot = next?.pos(),
                tier = session.tier,
                eventTypes = segmentEventTypes,
                tension = session.tension,
                trailQuality = session.trailQuality,
                currentNodeIndex = session.index,
                huntSeed = session.huntSeed
            ))
            // Clear node event on client
            ShadowedHeartsNetwork.sendToPlayer(player, NodeEventSyncS2CPacket(
                eventType = session.currentEventType,
                phase = NodeEventState.Phase.COMPLETED.ordinal,
                ticksElapsed = 0, maxTicks = 0
            ))
        } else {
            // Final manifestation
            EvidenceScanCompleteHandler.handle(
                EvidenceScanCompleteC2SPacket(), player.server!!, player
            )
        }
    }

    private fun syncNodeEvent(session: TrailSession, event: NodeEventState, player: ServerPlayer) {
        ShadowedHeartsNetwork.sendToPlayer(player, NodeEventSyncS2CPacket(
            eventType = event.eventType,
            phase = event.phase.ordinal,
            ticksElapsed = event.ticksElapsed,
            maxTicks = event.maxTicks,
            cluePositions = event.cluePositions,
            wrongGuesses = event.wrongGuesses,
            requiredValidCount = event.requiredValidCount,
            foundValidCount = event.foundValidCount,
            selectedClueIndices = event.selectedClueIndices
        ))
    }
}
