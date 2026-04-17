package com.jayemceekay.shadowedhearts.client.trail

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/**
 * Responsible for smoothing raw block paths, crossfading between path versions,
 * terrain-aware hover height, and segment-based player progress projection.
 */
object TrailPathPresenter {

    // Tuning
    private const val CROSSFADE_SPEED = 0.033f
    private const val PATH_JITTER_MAGNITUDE = 0.08

    // Terrain-aware height
    private const val MIN_FLOAT_HEIGHT = 0.0
    private const val MAX_FLOAT_HEIGHT = 0.0
    private const val CEILING_PROBE_RANGE = 5

    // State
    var smoothedPath: List<Vec3> = listOf()
        private set
    var prevDisplayPath: List<Vec3> = listOf()
        private set
    var currDisplayPath: MutableList<Vec3> = mutableListOf()
        private set
    private var pendingSmoothedPath: List<Vec3>? = null
    var crossfadeProgress: Float = 1.0f
        private set

    /**
     * Generate a smoothed Vec3 path from block positions using centripetal Catmull-Rom splines.
     */
    fun generateSmoothedPath(path: List<BlockPos>): List<Vec3> {
        if (path.isEmpty()) return emptyList()
        val mc = Minecraft.getInstance()
        val level = mc.level
        val points = path.map { bp ->
            val heightOffset = if (level != null) computeTerrainHeight(level, bp) else 0.25
            applyJitter(Vec3(bp.x + 0.5, bp.y + heightOffset, bp.z + 0.5), bp)
        }
        if (points.size <= 1) return points

        if (points.size < 3) {
            return interpolateLine(points[0], points.last())
        }

        val result = mutableListOf<Vec3>()
        result.add(points[0])

        for (i in 0 until points.size - 1) {
            val p0 = if (i > 0) points[i - 1] else points[i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]

            val segmentDist = p1.distanceTo(p2)
            val numSteps = (segmentDist / 0.25).toInt().coerceAtLeast(1)

            for (j in 1..numSteps) {
                val t = j.toDouble() / numSteps
                result.add(catmullRomCentripetal(p0, p1, p2, p3, t))
            }
        }

        return result
    }

    /**
     * Set a new smoothed path, optionally crossfading from the current one.
     */
    fun setSmoothedPath(newPath: List<Vec3>, crossfade: Boolean) {
        if (crossfade && smoothedPath.isNotEmpty()) {
            pendingSmoothedPath = newPath
            crossfadeProgress = 0.0f
        } else {
            smoothedPath = newPath
            pendingSmoothedPath = null
            crossfadeProgress = 1.0f
        }
    }

    /**
     * Advance crossfade and update the display path.
     * Should be called from tick().
     */
    fun tick(forceUpdate: Boolean) {
        updateDisplayPath()
    }

    /**
     * Projects the player onto the closest segment of the path and returns
     * the cumulative distance along the path at that projection point.
     */
    fun projectOntoPath(path: List<Vec3>, cumDist: DoubleArray, playerPos: Vec3): Double {
        if (path.size < 2) return if (path.isNotEmpty()) cumDist[0] else 0.0

        var bestDistSq = Double.MAX_VALUE
        var bestPathDist = 0.0

        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val ax = b.x - a.x
            val ay = b.y - a.y
            val az = b.z - a.z
            val segLenSq = ax * ax + ay * ay + az * az

            val t = if (segLenSq < 1e-12) 0.0 else {
                val dot = (playerPos.x - a.x) * ax + (playerPos.y - a.y) * ay + (playerPos.z - a.z) * az
                (dot / segLenSq).coerceIn(0.0, 1.0)
            }

            val projX = a.x + ax * t
            val projY = a.y + ay * t
            val projZ = a.z + az * t
            val dx = playerPos.x - projX
            val dy = playerPos.y - projY
            val dz = playerPos.z - projZ
            val distSq = dx * dx + dy * dy + dz * dz

            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestPathDist = cumDist[i] + (cumDist[i + 1] - cumDist[i]) * t
            }
        }

        return bestPathDist
    }

    fun clear() {
        smoothedPath = emptyList()
        prevDisplayPath = listOf()
        currDisplayPath.clear()
        pendingSmoothedPath = null
        crossfadeProgress = 1.0f
    }

    // ── Internal ──

    private fun updateDisplayPath() {
        val pending = pendingSmoothedPath
        if (pending != null && crossfadeProgress < 1.0f) {
            crossfadeProgress = minOf(crossfadeProgress + CROSSFADE_SPEED, 1.0f)
            if (crossfadeProgress >= 1.0f) {
                smoothedPath = pending
                pendingSmoothedPath = null
            } else {
                val blended = mutableListOf<Vec3>()
                val blendCount = minOf(smoothedPath.size, pending.size)
                for (i in 0 until blendCount) {
                    val old = smoothedPath[i]
                    val nw = pending[i]
                    val t = crossfadeProgress.toDouble()
                    blended.add(Vec3(
                        old.x + (nw.x - old.x) * t,
                        old.y + (nw.y - old.y) * t,
                        old.z + (nw.z - old.z) * t
                    ))
                }
                if (pending.size > blendCount) {
                    for (i in blendCount until pending.size) blended.add(pending[i])
                } else if (smoothedPath.size > blendCount) {
                    for (i in blendCount until smoothedPath.size) blended.add(smoothedPath[i])
                }
                smoothedPath = blended
            }
        }

        prevDisplayPath = currDisplayPath.toList()
        currDisplayPath = smoothedPath.toMutableList()
    }

    private fun computeTerrainHeight(level: Level, pos: BlockPos): Double {
        var ceilingDist = CEILING_PROBE_RANGE
        for (dy in 1..CEILING_PROBE_RANGE) {
            val probePos = pos.above(dy)
            val state = level.getBlockState(probePos)
            if (!state.isAir && !state.getCollisionShape(level, probePos).isEmpty) {
                ceilingDist = dy
                break
            }
        }
        val openness = ((ceilingDist - 1.0) / (CEILING_PROBE_RANGE - 1.0)).coerceIn(0.0, 1.0)
        return MIN_FLOAT_HEIGHT + (MAX_FLOAT_HEIGHT - MIN_FLOAT_HEIGHT) * openness
    }

    private fun interpolateLine(a: Vec3, b: Vec3): List<Vec3> {
        val dist = a.distanceTo(b)
        val numSteps = (dist / 0.25).toInt().coerceAtLeast(1)
        val result = mutableListOf<Vec3>()
        result.add(a)
        for (i in 1..numSteps) {
            val t = i.toDouble() / numSteps
            result.add(Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t))
        }
        return result
    }

    private fun catmullRomCentripetal(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Double): Vec3 {
        fun knotInterval(a: Vec3, b: Vec3): Double {
            val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
            return Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)).coerceAtLeast(1e-6)
        }

        val t0 = 0.0
        val t1 = t0 + knotInterval(p0, p1)
        val t2 = t1 + knotInterval(p1, p2)
        val t3 = t2 + knotInterval(p2, p3)

        val u = t1 + t * (t2 - t1)

        fun lerpV(a: Vec3, b: Vec3, ta: Double, tb: Double, tv: Double): Vec3 {
            val f = if (Math.abs(tb - ta) < 1e-12) 0.5 else (tv - ta) / (tb - ta)
            return Vec3(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f)
        }

        val a1 = lerpV(p0, p1, t0, t1, u)
        val a2 = lerpV(p1, p2, t1, t2, u)
        val a3 = lerpV(p2, p3, t2, t3, u)
        val b1 = lerpV(a1, a2, t0, t2, u)
        val b2 = lerpV(a2, a3, t1, t3, u)
        return lerpV(b1, b2, t1, t2, u)
    }

    private fun applyJitter(pos: Vec3, seed: BlockPos): Vec3 {
        val rnd = Random(seed.hashCode().toLong())
        val mag = PATH_JITTER_MAGNITUDE
        return Vec3(
            pos.x + (rnd.nextDouble() - 0.5) * mag,
            pos.y + (rnd.nextDouble() - 0.5) * mag,
            pos.z + (rnd.nextDouble() - 0.5) * mag
        )
    }
}
