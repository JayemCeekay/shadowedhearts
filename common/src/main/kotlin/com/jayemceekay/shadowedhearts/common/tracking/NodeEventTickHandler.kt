package com.jayemceekay.shadowedhearts.common.tracking

import com.jayemceekay.shadowedhearts.Shadowedhearts
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork
import com.jayemceekay.shadowedhearts.network.trail.EvidenceScanCompleteHandler
import com.jayemceekay.shadowedhearts.network.trail.ManifestationSyncS2CPacket
import com.jayemceekay.shadowedhearts.network.trail.NodeEventSyncS2CPacket
import com.jayemceekay.shadowedhearts.network.trail.TrailSyncS2CPacket
import com.jayemceekay.shadowedhearts.registry.ModSounds
import dev.architectury.event.events.common.TickEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import kotlin.math.min

/**
 * Server-side tick handler for active node events.
 * Ticks environmental search proximity, provocation hold, wild interruption timers,
 * and syncs state changes to the client.
 */
object NodeEventTickHandler {
    private val LOG = Shadowedhearts.LOGGER
    private const val SYNC_INTERVAL = 5 // sync every 5 ticks to avoid spam

    fun init() {
        TickEvent.SERVER_POST.register { server ->
            for (session in TrailManager.getAllSessions()) {
                val player = server.playerList.getPlayer(session.playerId) ?: continue

                // Tick signal blackout
                session.tickSignalBlackout()

                // Tick manifestation buildup
                if (session.state == TrailSession.State.MANIFESTATION_BUILDUP) {
                    tickManifestationBuildup(player, session)
                    continue
                }

                if (session.state != TrailSession.State.NODE_EVENT_ACTIVE) continue
                val event = session.activeNodeEvent ?: continue

                val playerPos = player.blockPosition()
                val changed = session.tickNodeEvent(playerPos)

                if (event.phase == NodeEventState.Phase.COMPLETED) {
                    // Event completed — grade based on performance
                    val grade = gradeNodeEvent(event)
                    LOG.debug("[ShadowHunt] NodeEvent COMPLETED: type={}, grade={}, elapsed={}/{}, player={}",
                        event.eventType, grade, event.ticksElapsed, event.maxTicks, player.name.string)
                    session.lastNodeGrade = grade

                    // Play event-specific completion sound
                    when (event.eventType) {
                        NodeEventType.EVIDENCE_INTERPRETATION ->
                            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_EVIDENCE_CORRECT.get(), SoundSource.PLAYERS, 0.9f, 1.0f)
                        NodeEventType.ENVIRONMENTAL_SEARCH ->
                            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_NODE_SCAN.get(), SoundSource.PLAYERS, 0.9f, 1.0f)
                        NodeEventType.PROVOCATION ->
                            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_PROVOCATION_BUILDUP.get(), SoundSource.PLAYERS, 0.8f, 1.2f)
                        else -> {}
                    }
                    // Play grade-specific sound
                    when (grade) {
                        "PERFECT" -> player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_GRADE_PERFECT.get(), SoundSource.PLAYERS, 1.0f, 1.0f)
                        "SLOPPY" -> player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_GRADE_SLOPPY.get(), SoundSource.PLAYERS, 0.8f, 1.0f)
                        else -> {}
                    }
                    // Play trail reveal sound
                    player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_TRAIL_REVEAL.get(), SoundSource.PLAYERS, 0.8f, 1.0f)

                    // Apply outcome branching
                    applyNodeEventBranching(grade, event.eventType, session)

                    session.completeNodeEvent()
                    advanceAndSync(session, player)
                    // Clear node event on client
                    ShadowedHeartsNetwork.sendToPlayer(player, NodeEventSyncS2CPacket(
                        eventType = event.eventType,
                        phase = NodeEventState.Phase.COMPLETED.ordinal,
                        ticksElapsed = 0, maxTicks = 0
                    ))
                } else if (event.phase == NodeEventState.Phase.FAILED) {
                    // Event failed — apply penalty
                    LOG.debug("[ShadowHunt] NodeEvent FAILED: type={}, elapsed={}/{}, player={}",
                        event.eventType, event.ticksElapsed, event.maxTicks, player.name.string)
                    session.lastNodeGrade = "FAILED"
                    player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_GRADE_FAILED.get(), SoundSource.PLAYERS, 0.7f, 1.0f)

                    // Failure branching: signal blackout + tension spike
                    session.beginSignalBlackout(40) // 2 seconds blackout
                    session.addTension(0.1f)
                    player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_SIGNAL_BLACKOUT.get(), SoundSource.PLAYERS, 0.6f, 1.0f)

                    // Evidence wrong choice → increased tension + trail noise
                    if (event.eventType == NodeEventType.EVIDENCE_INTERPRETATION) {
                        session.trailQuality = kotlin.math.max(0f, session.trailQuality - 0.15f)
                    }
                    // Provocation timeout → stronger tension for next node
                    if (event.eventType == NodeEventType.PROVOCATION) {
                        session.addTension(0.1f)
                        session.setPendingWildConsequence(true)
                    }

                    session.failNodeEvent()
                    ShadowedHeartsNetwork.sendToPlayer(player, NodeEventSyncS2CPacket(
                        eventType = event.eventType,
                        phase = NodeEventState.Phase.FAILED.ordinal,
                        ticksElapsed = event.ticksElapsed, maxTicks = event.maxTicks
                    ))
                } else if (event.ticksElapsed % SYNC_INTERVAL == 0) {
                    // Periodic sync for continuous feedback
                    syncNodeEvent(player, event, session)
                }
            }
        }
    }

    /**
     * Grade a completed node event based on performance metrics.
     * Returns "PERFECT", "STANDARD", or "SLOPPY".
     */
    private fun gradeNodeEvent(event: NodeEventState): String {
        return when (event.eventType) {
            NodeEventType.EVIDENCE_INTERPRETATION -> {
                // Perfect = no wrong guesses, Sloppy = 2+ wrong guesses
                when {
                    event.wrongGuesses == 0 -> "PERFECT"
                    event.wrongGuesses >= 2 -> "SLOPPY"
                    else -> "STANDARD"
                }
            }
            NodeEventType.ENVIRONMENTAL_SEARCH -> {
                // Perfect = found quickly (<50% time), Sloppy = found late (>80% time)
                val timeRatio = if (event.maxTicks > 0) event.ticksElapsed.toFloat() / event.maxTicks else 0.5f
                when {
                    timeRatio < 0.5f -> "PERFECT"
                    timeRatio > 0.8f -> "SLOPPY"
                    else -> "STANDARD"
                }
            }
            NodeEventType.PROVOCATION -> {
                // Perfect = completed with >20% time remaining, Sloppy = barely made it
                val timeRatio = if (event.maxTicks > 0) event.ticksElapsed.toFloat() / event.maxTicks else 0.5f
                when {
                    timeRatio < 0.6f -> "PERFECT"
                    timeRatio > 0.9f -> "SLOPPY"
                    else -> "STANDARD"
                }
            }
            NodeEventType.WILD_INTERRUPTION -> {
                // Always standard for wild encounters (resolved by timer)
                "STANDARD"
            }
            else -> "STANDARD"
        }
    }

    /**
     * Apply concrete gameplay consequences based on node event grade.
     */
    private fun applyNodeEventBranching(grade: String, eventType: NodeEventType, session: TrailSession) {
        when (grade) {
            "PERFECT" -> {
                // Strong success: shorter search radius, quality bonus
                session.setNextSearchRadiusModifier(0.7f)
                session.trailQuality = kotlin.math.min(1f, session.trailQuality + 0.1f)
            }
            "STANDARD" -> {
                // Normal: reset modifier
                session.setNextSearchRadiusModifier(1.0f)
            }
            "SLOPPY" -> {
                // Sloppy: wider search, noise, extra tension
                session.setNextSearchRadiusModifier(1.3f)
                session.trailQuality = kotlin.math.max(0f, session.trailQuality - 0.1f)
                session.addTension(0.05f)
            }
        }
    }

    private fun tickManifestationBuildup(player: ServerPlayer, session: TrailSession) {
        val prevPhase = session.manifestationPhase
        val phase = session.tickManifestationBuildup()

        // Play phase transition sounds
        if (phase != prevPhase) {
            LOG.debug("[ShadowHunt] Manifestation phase transition: {} -> {}, progress={}", prevPhase, phase, session.manifestationProgress)
            when (phase) {
                2 -> player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_MANIFESTATION_BUILDUP.get(), SoundSource.PLAYERS, 0.9f, 0.8f)
                3 -> player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_MANIFESTATION_BUILDUP.get(), SoundSource.PLAYERS, 1.0f, 1.0f)
            }
        }

        // Sync periodically
        if (session.manifestationProgress > 0 && (session.manifestationProgress * 100).toInt() % 5 == 0) {
            ShadowedHeartsNetwork.sendToPlayer(player, ManifestationSyncS2CPacket(
                phase = session.manifestationPhase,
                progress = session.manifestationProgress,
                convergenceTarget = session.currentHotspot?.pos(),
                tension = session.tension
            ))
        }

        // Spawn when ready
        if (session.isManifestationReady) {
            LOG.debug("[ShadowHunt] Manifestation READY — spawning shadow pokemon for player={}", player.name.string)
            // Play reveal sound
            player.serverLevel().playSound(null, player.blockPosition(), ModSounds.HUNT_MANIFESTATION_REVEAL.get(), SoundSource.PLAYERS, 1.0f, 1.0f)
            // Send final phase to client
            ShadowedHeartsNetwork.sendToPlayer(player, ManifestationSyncS2CPacket(
                phase = 4,
                progress = 1.0f,
                convergenceTarget = session.currentHotspot?.pos(),
                tension = session.tension
            ))
            // Spawn the Pokémon
            EvidenceScanCompleteHandler.spawnFinalManifestation(player, session)
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
        }
        // If no more nodes, final manifestation is handled by EvidenceScanCompleteHandler
        // when the player reaches the final hotspot
    }

    private fun syncNodeEvent(player: ServerPlayer, event: NodeEventState, session: TrailSession) {
        val playerPos = player.blockPosition()
        ShadowedHeartsNetwork.sendToPlayer(player, NodeEventSyncS2CPacket(
            eventType = event.eventType,
            phase = event.phase.ordinal,
            ticksElapsed = event.ticksElapsed,
            maxTicks = event.maxTicks,
            cluePositions = event.cluePositions,
            wrongGuesses = event.wrongGuesses,
            requiredValidCount = event.requiredValidCount,
            foundValidCount = event.foundValidCount,
            selectedClueIndices = event.selectedClueIndices,
            searchCenter = session.currentHotspot?.pos(),
            searchRadius = event.searchRadius,
            searchSignalStrength = event.getSearchSignalStrength(playerPos),
            wildResolveTimer = event.wildResolveTimer,
            wildsResolved = event.isWildsResolved,
            signalBuildup = event.signalBuildup,
            provocationRequiredTicks = event.provocationRequiredTicks
        ))
    }
}
