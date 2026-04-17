package com.jayemceekay.shadowedhearts.client.trail

import com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence
import com.jayemceekay.shadowedhearts.common.tracking.NodeEventType
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalTier
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSpeciesTrait
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * Client-only trail system for visualizing an active trail.
 *
 * Delegates to:
 * - [TrailStateSync] for synced hunt/event/calibration data
 * - [TrailPathSolver] for A* + trimming + micro-splicing
 * - [TrailPathPresenter] for smoothing / crossfade / inertial lag
 * - [TrailEvidenceRenderer] for evidence node markers
 * - [TrailRibbonRenderer] for the ribbon trail itself
 */
object TrailClientState {
    private val nodes: MutableList<BlockPos> = mutableListOf()
    private var hotspot: BlockPos? = null
    private var active: Boolean = false

    private var tickCounter: Int = 0

    // Pathfinder cache/state
    private var cachedPath: MutableList<BlockPos> = mutableListOf()
    private var lastStart: BlockPos? = null
    private var lastGoal: BlockPos? = null
    private var recomputeCooldown: Int = 0

    // Tuning
    private const val RECOMPUTE_INTERVAL_TICKS = 100

    // Pre-smoothed false trail paths
    private var falseTrailSmoothedPaths: List<List<Vec3>> = emptyList()

    // ── Progress-based Clairvoyance reveal ──
    private var pathProgress: Double = 0.0
    private const val BEHIND_BUFFER = 6.0
    private const val AHEAD_WINDOW = 48.0
    private const val FADE_EDGE = 4.0

    // ── Delegated state accessors (preserve existing public API) ──

    var currentTier: ShadowSignalTier
        get() = TrailStateSync.currentTier
        private set(value) {} // read-through only
    var eventTypes: List<NodeEventType>
        get() = TrailStateSync.eventTypes
        private set(value) {}
    var tension: Float
        get() = TrailStateSync.tension
        private set(value) {}
    var trailQuality: Float
        get() = TrailStateSync.trailQuality
        private set(value) {}
    var currentNodeIndex: Int
        get() = TrailStateSync.currentNodeIndex
        private set(value) {}
    var huntSeed: Long
        get() = TrailStateSync.huntSeed
        private set(value) {}

    var calibrationSequence: List<CalibrationSequence.Direction>
        get() = TrailStateSync.calibrationSequence
        private set(value) {}
    var calibrationVariant: CalibrationSequence.Variant
        get() = TrailStateSync.calibrationVariant
        private set(value) {}
    var calibrationTimeLimitTicks: Int
        get() = TrailStateSync.calibrationTimeLimitTicks
        private set(value) {}
    var calibrationCurrentIndex: Int
        get() = TrailStateSync.calibrationCurrentIndex
        private set(value) {}
    var calibrationMistakes: Int
        get() = TrailStateSync.calibrationMistakes
        private set(value) {}
    var calibrationElapsedTicks: Int
        get() = TrailStateSync.calibrationElapsedTicks
        private set(value) {}
    var calibrationCompleted: Boolean
        get() = TrailStateSync.calibrationCompleted
        private set(value) {}
    var calibrationFailed: Boolean
        get() = TrailStateSync.calibrationFailed
        private set(value) {}
    var calibrationGrade: CalibrationSequence.Grade?
        get() = TrailStateSync.calibrationGrade
        private set(value) {}
    var calibrationActive: Boolean
        get() = TrailStateSync.calibrationActive
        private set(value) {}

    var nodeEventType: NodeEventType?
        get() = TrailStateSync.nodeEventType
        private set(value) {}
    var nodeEventPhase: Int
        get() = TrailStateSync.nodeEventPhase
        private set(value) {}
    var nodeEventTicksElapsed: Int
        get() = TrailStateSync.nodeEventTicksElapsed
        private set(value) {}
    var nodeEventMaxTicks: Int
        get() = TrailStateSync.nodeEventMaxTicks
        private set(value) {}
    var nodeEventActive: Boolean
        get() = TrailStateSync.nodeEventActive
        private set(value) {}
    var nodeEventCluePositions: List<BlockPos>
        get() = TrailStateSync.nodeEventCluePositions
        private set(value) {}
    var nodeEventWrongGuesses: Int
        get() = TrailStateSync.nodeEventWrongGuesses
        private set(value) {}
    var nodeEventRequiredValidCount: Int
        get() = TrailStateSync.nodeEventRequiredValidCount
        private set(value) {}
    var nodeEventFoundValidCount: Int
        get() = TrailStateSync.nodeEventFoundValidCount
        private set(value) {}
    var nodeEventSelectedClueIndices: List<Int>
        get() = TrailStateSync.nodeEventSelectedClueIndices
        private set(value) {}
    var nodeEventSearchCenter: BlockPos?
        get() = TrailStateSync.nodeEventSearchCenter
        private set(value) {}
    var nodeEventSearchRadius: Float
        get() = TrailStateSync.nodeEventSearchRadius
        private set(value) {}
    var nodeEventSearchSignalStrength: Float
        get() = TrailStateSync.nodeEventSearchSignalStrength
        private set(value) {}
    var nodeEventWildResolveTimer: Int
        get() = TrailStateSync.nodeEventWildResolveTimer
        private set(value) {}
    var nodeEventWildsResolved: Boolean
        get() = TrailStateSync.nodeEventWildsResolved
        private set(value) {}
    var nodeEventSignalBuildup: Float
        get() = TrailStateSync.nodeEventSignalBuildup
        private set(value) {}
    var nodeEventProvocationRequiredTicks: Int
        get() = TrailStateSync.nodeEventProvocationRequiredTicks
        private set(value) {}

    var manifestationPhase: Int
        get() = TrailStateSync.manifestationPhase
        private set(value) {}
    var manifestationProgress: Float
        get() = TrailStateSync.manifestationProgress
        private set(value) {}
    var manifestationConvergenceTarget: BlockPos?
        get() = TrailStateSync.manifestationConvergenceTarget
        private set(value) {}
    var manifestationActive: Boolean
        get() = TrailStateSync.manifestationActive
        private set(value) {}

    var signalBlackedOut: Boolean
        get() = TrailStateSync.signalBlackedOut
        private set(value) {}
    var signalBlackoutTicks: Int
        get() = TrailStateSync.signalBlackoutTicks
        private set(value) {}

    var lastGradeText: String?
        get() = TrailStateSync.lastGradeText
        private set(value) {}
    var gradeFlashTicks: Int
        get() = TrailStateSync.gradeFlashTicks
        private set(value) {}
    var gradeFlashColor: Int
        get() = TrailStateSync.gradeFlashColor
        private set(value) {}

    var falseTrails: List<List<BlockPos>>
        get() = TrailStateSync.falseTrails
        private set(value) {}

    var speciesTrait: ShadowSpeciesTrait
        get() = TrailStateSync.speciesTrait
        private set(value) {}

    // ── Public API (unchanged signatures) ──

    @JvmStatic
    fun sync(newNodes: List<BlockPos>, hotspotPos: BlockPos?) {
        nodes.clear()
        nodes.addAll(newNodes)
        hotspot = hotspotPos
        active = nodes.isNotEmpty()
        tickCounter = 0
        recomputeCooldown = 0
    }

    fun syncHuntData(
        tier: ShadowSignalTier,
        eventTypes: List<NodeEventType>,
        tension: Float,
        trailQuality: Float,
        currentNodeIndex: Int,
        huntSeed: Long
    ) {
        TrailStateSync.syncHuntData(tier, eventTypes, tension, trailQuality, currentNodeIndex, huntSeed)
    }

    fun syncCalibration(
        sequence: List<CalibrationSequence.Direction>,
        variant: CalibrationSequence.Variant,
        timeLimitTicks: Int,
        currentInputIndex: Int,
        mistakes: Int,
        elapsedTicks: Int,
        completed: Boolean,
        failed: Boolean,
        grade: CalibrationSequence.Grade?
    ) {
        TrailStateSync.syncCalibration(sequence, variant, timeLimitTicks, currentInputIndex, mistakes, elapsedTicks, completed, failed, grade)
    }

    fun syncNodeEvent(
        eventType: NodeEventType,
        phase: Int,
        ticksElapsed: Int,
        maxTicks: Int,
        cluePositions: List<BlockPos>,
        wrongGuesses: Int,
        requiredValidCount: Int = 1,
        foundValidCount: Int = 0,
        selectedClueIndices: List<Int> = emptyList(),
        searchCenter: BlockPos?,
        searchRadius: Float,
        searchSignalStrength: Float,
        wildResolveTimer: Int,
        wildsResolved: Boolean,
        signalBuildup: Float,
        provocationRequiredTicks: Int
    ) {
        TrailStateSync.syncNodeEvent(eventType, phase, ticksElapsed, maxTicks, cluePositions, wrongGuesses,
            requiredValidCount, foundValidCount, selectedClueIndices, searchCenter, searchRadius,
            searchSignalStrength, wildResolveTimer, wildsResolved, signalBuildup, provocationRequiredTicks)
    }

    fun syncManifestation(
        phase: Int,
        progress: Float,
        convergenceTarget: BlockPos?,
        tension: Float
    ) {
        TrailStateSync.syncManifestation(phase, progress, convergenceTarget, tension)
    }

    fun showGradeFlash(grade: String, color: Int) {
        TrailStateSync.showGradeFlash(grade, color)
    }

    fun syncFalseTrails(trails: List<List<BlockPos>>) {
        TrailStateSync.syncFalseTrails(trails)
        if (trails.isNotEmpty()) {
            falseTrailSmoothedPaths = trails.map { trail ->
                if (trail.size >= 2) TrailPathPresenter.generateSmoothedPath(trail) else emptyList()
            }
        } else {
            falseTrailSmoothedPaths = emptyList()
        }
    }

    fun syncSpeciesTrait(traitId: Int) {
        TrailStateSync.syncSpeciesTrait(traitId)
    }

    fun syncSignalBlackout(blackedOut: Boolean, ticks: Int) {
        TrailStateSync.syncSignalBlackout(blackedOut, ticks)
    }

    fun clear() {
        nodes.clear()
        hotspot = null
        active = false
        tickCounter = 0
        cachedPath.clear()
        lastStart = null
        lastGoal = null
        recomputeCooldown = 0
        pathProgress = 0.0
        falseTrailSmoothedPaths = emptyList()
        TrailPathPresenter.clear()
        TrailStateSync.clear()
    }

    fun tick() {
        if (!active) return
        if (nodes.isEmpty()) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        val goal = hotspot ?: return
        val start = player.blockPosition()

        val goalChanged = lastGoal != goal
        var needsFullRecompute = goalChanged || cachedPath.isEmpty()

        if (!needsFullRecompute) {
            val closestIndex = TrailPathSolver.findClosestNodeIndex(start, cachedPath)
            if (closestIndex == -1 || start.distSqr(cachedPath[closestIndex]) > TrailPathSolver.FULL_RECOMPUTE_DIST.toLong() * TrailPathSolver.FULL_RECOMPUTE_DIST) {
                needsFullRecompute = true
            } else {
                val trimIndex = TrailPathSolver.findForwardTrimIndex(start, goal, cachedPath, closestIndex)
                if (trimIndex > 0) {
                    cachedPath = cachedPath.subList(trimIndex, cachedPath.size).toMutableList()
                }

                val spliceTarget = cachedPath.firstOrNull { bp ->
                    start.distSqr(bp) <= TrailPathSolver.MICRO_A_STAR_CONNECT_DIST.toLong() * TrailPathSolver.MICRO_A_STAR_CONNECT_DIST
                } ?: cachedPath.firstOrNull()

                if (spliceTarget != null && start != spliceTarget) {
                    val playerToGoal = start.distSqr(goal)
                    val spliceToGoal = spliceTarget.distSqr(goal)
                    if (playerToGoal < spliceToGoal) {
                        val spliceIdx = cachedPath.indexOf(spliceTarget)
                        if (spliceIdx >= 0 && spliceIdx + 1 < cachedPath.size) {
                            cachedPath = cachedPath.subList(spliceIdx + 1, cachedPath.size).toMutableList()
                        }
                    } else {
                        val microPath = TrailPathSolver.computePath(level, start, spliceTarget, TrailPathSolver.MICRO_A_STAR_BUDGET)
                        if (microPath.isNotEmpty() && microPath.size >= 2) {
                            val microLen = microPath.size.toDouble()
                            val directDist = Math.sqrt(start.distSqr(spliceTarget).toDouble())
                            val currentFrontLen = if (cachedPath.size > 1) {
                                Math.sqrt(start.distSqr(cachedPath[0]).toDouble()) + 1.0
                            } else directDist + TrailPathSolver.HYSTERESIS_THRESHOLD + 1.0

                            if (microLen < currentFrontLen + TrailPathSolver.HYSTERESIS_THRESHOLD || cachedPath.size <= 1) {
                                val spliceIdx = cachedPath.indexOf(spliceTarget)
                                val tail = if (spliceIdx >= 0 && spliceIdx + 1 < cachedPath.size) {
                                    cachedPath.subList(spliceIdx + 1, cachedPath.size)
                                } else emptyList()
                                cachedPath = (microPath + tail).toMutableList()
                            }
                        }
                    }
                }

                TrailPathPresenter.setSmoothedPath(
                    TrailPathPresenter.generateSmoothedPath(cachedPath),
                    crossfade = false
                )
            }
        }

        if (needsFullRecompute) {
            if (start.distManhattan(goal) <= TrailPathSolver.MAX_PATH_RANGE) {
                val fullPath = TrailPathSolver.computePath(level, start, goal, TrailPathSolver.MAX_SEARCH_NODES)
                cachedPath = TrailPathSolver.simplifyPath(fullPath)
                val newSmoothed = TrailPathPresenter.generateSmoothedPath(cachedPath)
                TrailPathPresenter.setSmoothedPath(newSmoothed, crossfade = TrailPathPresenter.smoothedPath.isNotEmpty())
            } else {
                cachedPath.clear()
                TrailPathPresenter.setSmoothedPath(emptyList(), crossfade = false)
            }
            lastStart = start
            lastGoal = goal
            recomputeCooldown = RECOMPUTE_INTERVAL_TICKS
        } else if (recomputeCooldown > 0) {
            recomputeCooldown--
        }

        TrailPathPresenter.tick(forceUpdate = needsFullRecompute)

        tickCounter++

        TrailStateSync.tickTimers()
    }

    fun getSmoothedPath(): List<Vec3> = TrailPathPresenter.currDisplayPath

    fun isActive(): Boolean = active

    fun getHotspot(): BlockPos? = hotspot

    /**
     * Called from the world render event (AFTER_TRANSLUCENT) to draw the shadow aura tube trail.
     * Implements progress-based Clairvoyance reveal: only a moving window of the trail
     * is visible, advancing with the player. Trail fades behind and ahead at the edges.
     */
    fun render(partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, hudAlpha: Float = 1.0f) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val playerPos = player.getPosition(partialTicks)

        val prevPath = TrailPathPresenter.prevDisplayPath
        val currPath = TrailPathPresenter.currDisplayPath

        // Interpolate between previous tick and current tick using partialTicks
        val interpPath = mutableListOf<Vec3>()
        val count = minOf(prevPath.size, currPath.size)
        for (i in 0 until count) {
            val prev = prevPath[i]
            val curr = currPath[i]
            interpPath.add(Vec3(
                prev.x + (curr.x - prev.x) * partialTicks,
                prev.y + (curr.y - prev.y) * partialTicks,
                prev.z + (curr.z - prev.z) * partialTicks
            ))
        }
        for (i in count until currPath.size) {
            interpPath.add(currPath[i])
        }

        if (interpPath.size < 2) return

        // Compute cumulative distances along the path
        val cumDist = DoubleArray(interpPath.size)
        cumDist[0] = 0.0
        for (i in 1 until interpPath.size) {
            cumDist[i] = cumDist[i - 1] + interpPath[i].distanceTo(interpPath[i - 1])
        }

        // Project the player onto the closest segment of the path for stable progress
        val playerPathDist = TrailPathPresenter.projectOntoPath(interpPath, cumDist, playerPos)

        // Advance progress monotonically (never goes backward) for stable Clairvoyance feel
        if (playerPathDist > pathProgress) {
            pathProgress = playerPathDist
        }

        // Quality affects the visible window: lower quality = shorter, noisier
        val qualityMult = 0.6 + TrailStateSync.trailQuality * 0.4
        val effectiveAhead = AHEAD_WINDOW * qualityMult

        val windowStart = pathProgress - BEHIND_BUFFER
        val windowEnd = pathProgress + effectiveAhead

        val visiblePath = mutableListOf<Vec3>()
        for (i in interpPath.indices) {
            val d = cumDist[i]
            if (d < windowStart - FADE_EDGE || d > windowEnd + FADE_EDGE) continue
            visiblePath.add(interpPath[i])
        }

        if (visiblePath.size < 2) return

        val maxTrailDist = 64.0f

        TrailRibbonRenderer.render(
            visiblePath,
            playerPos,
            maxTrailDist,
            partialTicks,
            poseStack,
            buffer,
            hudAlpha
        )

        // Render false/decoy trails with reduced opacity
        if (falseTrailSmoothedPaths.isNotEmpty()) {
            for (falsePath in falseTrailSmoothedPaths) {
                if (falsePath.size < 2) continue
                val nearestDist = falsePath.minOf { it.distanceToSqr(playerPos) }
                if (nearestDist > 64.0 * 64.0) continue
                TrailRibbonRenderer.render(
                    falsePath,
                    playerPos,
                    maxTrailDist,
                    partialTicks,
                    poseStack,
                    buffer,
                    hudAlpha * 0.4f
                )
            }
        }

        TrailEvidenceRenderer.render(
            mc.level ?: return,
            poseStack,
            buffer,
            mc.gameRenderer.mainCamera,
            nodes,
            hotspot,
            TrailStateSync.currentNodeIndex,
            tickCounter,
            TrailStateSync.signalBlackedOut
        )
    }

    private fun BlockPos.distManhattan(other: BlockPos): Int =
        kotlin.math.abs(this.x - other.x) + kotlin.math.abs(this.y - other.y) + kotlin.math.abs(this.z - other.z)
}
