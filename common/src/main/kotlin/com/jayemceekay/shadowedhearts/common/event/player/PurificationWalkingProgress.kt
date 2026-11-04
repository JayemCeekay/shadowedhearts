package com.jayemceekay.shadowedhearts.common.event.player

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Per-player movement accounting shared by chamber and party purification. */
internal class PurificationWalkingProgress {
    private var previous: Position? = null
    private var fractionalDistance = 0.0
    private var partySteps = 0
    private var partyInterval = PARTY_STEP_INTERVAL

    /** Rebase after a dimension change or excluded movement, without losing earned whole steps. */
    fun resetPosition(x: Double, y: Double, z: Double) {
        previous = Position(x, y, z)
        fractionalDistance = 0.0
    }

    @JvmOverloads
    fun advance(x: Double, y: Double, z: Double, countMovement: Boolean, partyStepInterval: Int = PARTY_STEP_INTERVAL): Credit {
        val interval = partyStepInterval.coerceIn(1, 1_000_000)
        if (interval != partyInterval) {
            // Keep the completed fraction of this cycle, without a large retroactive payout.
            partySteps = (partySteps.toLong() * interval / partyInterval).toInt()
            partyInterval = interval
        }
        val last = previous
        previous = Position(x, y, z)
        if (last == null || !countMovement) {
            fractionalDistance = 0.0
            return Credit.NONE
        }

        val dx = x - last.x
        val dy = y - last.y
        val dz = z - last.z
        // Include vertical displacement in teleport rejection, but never count it as walking.
        val displacement = abs(dx) + abs(dy) + abs(dz)
        if (!displacement.isFinite() || displacement > TELEPORT_THRESHOLD_BLOCKS) {
            fractionalDistance = 0.0
            return Credit.NONE
        }

        val distance = fractionalDistance + hypot(dx, dz)
        // Tolerate floating-point error at whole-block boundaries (e.g. ten 0.1-block ticks).
        val wholeSteps = floor(distance + 1.0e-9).toInt()
        val steps = min(wholeSteps, MAX_STEPS_PER_TICK)
        // Excess movement must not be banked and paid out over later stationary ticks.
        fractionalDistance = if (distance >= MAX_STEPS_PER_TICK) 0.0 else max(0.0, distance - steps)
        if (steps == 0) return Credit.NONE

        val total = partySteps + steps
        partySteps = total % interval
        return Credit(steps, total / interval)
    }

    data class Credit(val chamberSteps: Int, val partyIntervals: Int) {
        companion object {
            val NONE = Credit(0, 0)
        }
    }

    private data class Position(val x: Double, val y: Double, val z: Double)

    private companion object {
        const val TELEPORT_THRESHOLD_BLOCKS = 64.0
        const val MAX_STEPS_PER_TICK = 8
        const val PARTY_STEP_INTERVAL = 256
    }
}
