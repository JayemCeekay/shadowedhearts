package com.jayemceekay.shadowedhearts.client.trail

import com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence
import com.jayemceekay.shadowedhearts.common.tracking.NodeEventType
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSignalTier
import com.jayemceekay.shadowedhearts.common.tracking.ShadowSpeciesTrait
import net.minecraft.core.BlockPos

/**
 * Holds all synced gameplay state for the trail system:
 * hunt metadata, calibration, node events, manifestation, signal blackout,
 * grade flash, false trails, and species trait.
 */
object TrailStateSync {

    // ── Hunt metadata (synced from server) ──
    var currentTier: ShadowSignalTier = ShadowSignalTier.FAINT
        private set
    var eventTypes: List<NodeEventType> = emptyList()
        private set
    var tension: Float = 0.0f
        private set
    var trailQuality: Float = 1.0f
        private set
    var currentNodeIndex: Int = 0
        private set
    var huntSeed: Long = 0L
        private set

    // ── Calibration client state ──
    var calibrationSequence: List<CalibrationSequence.Direction> = emptyList()
        private set
    var calibrationVariant: CalibrationSequence.Variant = CalibrationSequence.Variant.HARMONIC_LOCK
        private set
    var calibrationTimeLimitTicks: Int = 0
        private set
    var calibrationCurrentIndex: Int = 0
        private set
    var calibrationMistakes: Int = 0
        private set
    var calibrationElapsedTicks: Int = 0
        private set
    var calibrationCompleted: Boolean = false
        private set
    var calibrationFailed: Boolean = false
        private set
    var calibrationGrade: CalibrationSequence.Grade? = null
        private set
    var calibrationActive: Boolean = false
        private set

    // ── Node Event client state ──
    var nodeEventType: NodeEventType? = null
        private set
    var nodeEventPhase: Int = 0
        private set
    var nodeEventTicksElapsed: Int = 0
        private set
    var nodeEventMaxTicks: Int = 0
        private set
    var nodeEventActive: Boolean = false
        private set
    // Evidence Interpretation
    var nodeEventCluePositions: List<BlockPos> = emptyList()
        private set
    var nodeEventWrongGuesses: Int = 0
        private set
    var nodeEventRequiredValidCount: Int = 1
        private set
    var nodeEventFoundValidCount: Int = 0
        private set
    var nodeEventSelectedClueIndices: List<Int> = emptyList()
        private set
    // Environmental Search
    var nodeEventSearchCenter: BlockPos? = null
        private set
    var nodeEventSearchRadius: Float = 0f
        private set
    var nodeEventSearchSignalStrength: Float = 0f
        private set
    // Wild Interruption
    var nodeEventWildResolveTimer: Int = 0
        private set
    var nodeEventWildsResolved: Boolean = false
        private set
    // Provocation
    var nodeEventSignalBuildup: Float = 0f
        private set
    var nodeEventProvocationRequiredTicks: Int = 0
        private set

    // ── Manifestation buildup client state ──
    var manifestationPhase: Int = 0
        private set
    var manifestationProgress: Float = 0f
        private set
    var manifestationConvergenceTarget: BlockPos? = null
        private set
    var manifestationActive: Boolean = false
        private set

    // ── Signal blackout client state ──
    var signalBlackedOut: Boolean = false
        private set
    var signalBlackoutTicks: Int = 0
        private set

    // ── Grade flash display ──
    var lastGradeText: String? = null
        private set
    var gradeFlashTicks: Int = 0
        private set
    var gradeFlashColor: Int = 0xFFFFFF
        private set

    // ── False trails (decoy branches) ──
    var falseTrails: List<List<BlockPos>> = emptyList()
        private set

    // ── Species trait ──
    var speciesTrait: ShadowSpeciesTrait = ShadowSpeciesTrait.NEUTRAL
        private set

    fun syncHuntData(
        tier: ShadowSignalTier,
        eventTypes: List<NodeEventType>,
        tension: Float,
        trailQuality: Float,
        currentNodeIndex: Int,
        huntSeed: Long
    ) {
        this.currentTier = tier
        this.eventTypes = eventTypes
        this.tension = tension
        this.trailQuality = trailQuality
        this.currentNodeIndex = currentNodeIndex
        this.huntSeed = huntSeed
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
        this.calibrationSequence = sequence
        this.calibrationVariant = variant
        this.calibrationTimeLimitTicks = timeLimitTicks
        this.calibrationCurrentIndex = currentInputIndex
        this.calibrationMistakes = mistakes
        this.calibrationElapsedTicks = elapsedTicks
        this.calibrationCompleted = completed
        this.calibrationFailed = failed
        this.calibrationGrade = grade
        this.calibrationActive = !completed && !failed && sequence.isNotEmpty()
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
        this.nodeEventType = eventType
        this.nodeEventPhase = phase
        this.nodeEventTicksElapsed = ticksElapsed
        this.nodeEventMaxTicks = maxTicks
        this.nodeEventCluePositions = cluePositions
        this.nodeEventWrongGuesses = wrongGuesses
        this.nodeEventRequiredValidCount = requiredValidCount
        this.nodeEventFoundValidCount = foundValidCount
        this.nodeEventSelectedClueIndices = selectedClueIndices
        this.nodeEventSearchCenter = searchCenter
        this.nodeEventSearchRadius = searchRadius
        this.nodeEventSearchSignalStrength = searchSignalStrength
        this.nodeEventWildResolveTimer = wildResolveTimer
        this.nodeEventWildsResolved = wildsResolved
        this.nodeEventSignalBuildup = signalBuildup
        this.nodeEventProvocationRequiredTicks = provocationRequiredTicks
        this.nodeEventActive = (phase == 1)
    }

    fun syncManifestation(
        phase: Int,
        progress: Float,
        convergenceTarget: BlockPos?,
        tension: Float
    ) {
        this.manifestationPhase = phase
        this.manifestationProgress = progress
        this.manifestationConvergenceTarget = convergenceTarget
        this.manifestationActive = phase in 1..3
        this.tension = tension
    }

    fun showGradeFlash(grade: String, color: Int) {
        this.lastGradeText = grade
        this.gradeFlashTicks = 40
        this.gradeFlashColor = color
    }

    fun syncFalseTrails(trails: List<List<BlockPos>>) {
        this.falseTrails = trails
    }

    fun syncSpeciesTrait(traitId: Int) {
        this.speciesTrait = ShadowSpeciesTrait.fromId(traitId)
    }

    fun syncSignalBlackout(blackedOut: Boolean, ticks: Int) {
        this.signalBlackedOut = blackedOut
        this.signalBlackoutTicks = ticks
    }

    fun tickTimers() {
        if (gradeFlashTicks > 0) gradeFlashTicks--
        if (gradeFlashTicks <= 0) lastGradeText = null
        if (signalBlackoutTicks > 0) {
            signalBlackoutTicks--
            signalBlackedOut = signalBlackoutTicks > 0
        }
    }

    fun clear() {
        calibrationSequence = emptyList()
        calibrationVariant = CalibrationSequence.Variant.HARMONIC_LOCK
        calibrationTimeLimitTicks = 0
        calibrationCurrentIndex = 0
        calibrationMistakes = 0
        calibrationElapsedTicks = 0
        calibrationCompleted = false
        calibrationFailed = false
        calibrationGrade = null
        calibrationActive = false
        nodeEventType = null
        nodeEventPhase = 0
        nodeEventTicksElapsed = 0
        nodeEventMaxTicks = 0
        nodeEventActive = false
        nodeEventCluePositions = emptyList()
        nodeEventWrongGuesses = 0
        nodeEventRequiredValidCount = 1
        nodeEventFoundValidCount = 0
        nodeEventSelectedClueIndices = emptyList()
        nodeEventSearchCenter = null
        nodeEventSearchRadius = 0f
        nodeEventSearchSignalStrength = 0f
        nodeEventWildResolveTimer = 0
        nodeEventWildsResolved = false
        nodeEventSignalBuildup = 0f
        nodeEventProvocationRequiredTicks = 0
        currentTier = ShadowSignalTier.FAINT
        eventTypes = emptyList()
        tension = 0f
        trailQuality = 1f
        currentNodeIndex = 0
        huntSeed = 0L
        manifestationPhase = 0
        manifestationProgress = 0f
        manifestationConvergenceTarget = null
        manifestationActive = false
        signalBlackedOut = false
        signalBlackoutTicks = 0
        lastGradeText = null
        gradeFlashTicks = 0
        gradeFlashColor = 0xFFFFFF
        falseTrails = emptyList()
        speciesTrait = ShadowSpeciesTrait.NEUTRAL
    }
}
