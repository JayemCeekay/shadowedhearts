package com.jayemceekay.shadowedhearts.client.trail

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.util.*
import kotlin.math.abs

/**
 * Client-only A* pathfinding, neighbor generation, path simplification,
 * and forward-biased trimming for the trail system.
 */
object TrailPathSolver {

    const val MAX_SEARCH_NODES = 4000
    const val MAX_PATH_RANGE = 96
    const val FULL_RECOMPUTE_DIST = 13
    const val MICRO_A_STAR_BUDGET = 200
    const val MICRO_A_STAR_CONNECT_DIST = 8
    const val HYSTERESIS_THRESHOLD = 5.0

    fun computePath(level: Level, start: BlockPos, goal: BlockPos, maxNodes: Int): MutableList<BlockPos> {
        val open = PriorityQueue(compareBy<Pair<BlockPos, Int>> { it.second })
        val startStand = findStandableNear(level, start)
        val goalStand = findStandableNear(level, goal)
        if (startStand == null || goalStand == null) return mutableListOf()

        val startKey = startStand.asLong()
        val goalKey = goalStand.asLong()

        val cameFrom = HashMap<Long, Long>(1024)
        val gScore = HashMap<Long, Int>(1024)
        val fScore = HashMap<Long, Int>(1024)

        fun h(a: BlockPos, b: BlockPos): Int = a.distManhattan(b)

        gScore[startKey] = 0
        fScore[startKey] = h(startStand, goalStand)
        open.add(startStand to fScore[startKey]!!)

        var processed = 0
        val visited = HashSet<Long>(2048)
        while (open.isNotEmpty() && processed < maxNodes) {
            val current = open.poll().first
            val currKey = current.asLong()
            if (!visited.add(currKey)) continue
            processed++

            if (currKey == goalKey) {
                return reconstructPath(cameFrom, currKey)
            }

            for (nbr in neighbors(level, current)) {
                val nk = nbr.asLong()
                val tentativeG = (gScore[currKey] ?: Int.MAX_VALUE - 1) + 1
                if (tentativeG < (gScore[nk] ?: Int.MAX_VALUE)) {
                    cameFrom[nk] = currKey
                    gScore[nk] = tentativeG
                    val f = tentativeG + h(nbr, goalStand)
                    fScore[nk] = f
                    open.add(nbr to f)
                }
            }
        }
        var bestKey: Long? = null
        var bestF = Int.MAX_VALUE
        for ((k, f) in fScore) {
            if (f < bestF) { bestF = f; bestKey = k }
        }
        return if (bestKey != null) reconstructPath(cameFrom, bestKey!!) else mutableListOf()
    }

    fun simplifyPath(path: List<BlockPos>): MutableList<BlockPos> {
        if (path.size <= 2) return path.toMutableList()
        val simplified = mutableListOf<BlockPos>()
        simplified.add(path[0])
        var lastAdded = path[0]

        val minNodeDistSq = 4.0 * 4.0

        for (i in 1 until path.size - 1) {
            val current = path[i]
            val heightChange = abs(current.y - lastAdded.y) > 0

            if (current.distSqr(lastAdded) >= minNodeDistSq || heightChange) {
                simplified.add(current)
                lastAdded = current
            }
        }

        if (simplified.last() != path.last()) {
            simplified.add(path.last())
        }
        return simplified
    }

    fun findClosestNodeIndex(pos: BlockPos, path: List<BlockPos>): Int {
        if (path.isEmpty()) return -1
        var minDestSq = Double.MAX_VALUE
        var index = -1
        for (i in path.indices) {
            val d2 = path[i].distSqr(pos)
            if (d2 < minDestSq) {
                minDestSq = d2
                index = i
            }
        }
        return index
    }

    /**
     * Find the best trim index: the furthest-along node (highest index) that the player
     * has effectively "passed" even if they're not on the exact path.
     */
    fun findForwardTrimIndex(player: BlockPos, goal: BlockPos, path: List<BlockPos>, closestIndex: Int): Int {
        val playerToGoalSq = player.distSqr(goal).toDouble()
        var bestTrim = closestIndex

        val searchLimit = (closestIndex + 15).coerceAtMost(path.size)
        for (i in closestIndex until searchLimit) {
            val nodeToGoalSq = path[i].distSqr(goal).toDouble()
            if (playerToGoalSq < nodeToGoalSq - 4.0) {
                bestTrim = i
            } else {
                break
            }
        }
        return bestTrim
    }

    private fun neighbors(level: Level, pos: BlockPos): List<BlockPos> {
        val result = ArrayList<BlockPos>(8)
        val deltas = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1)
        )
        for (d in deltas) {
            val nx = pos.x + d[0]
            val nz = pos.z + d[1]
            val candidate = findStandableNear(level, BlockPos(nx, pos.y, nz)) ?: continue
            if (abs(candidate.y - pos.y) <= 1) {
                result.add(candidate)
            }
        }
        val diagonals = arrayOf(
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)
        )
        for (d in diagonals) {
            val nx = pos.x + d[0]
            val nz = pos.z + d[1]
            val candidate = findStandableNear(level, BlockPos(nx, pos.y, nz)) ?: continue
            if (abs(candidate.y - pos.y) <= 1) {
                val side1 = findStandableNear(level, BlockPos(pos.x + d[0], pos.y, pos.z))
                val side2 = findStandableNear(level, BlockPos(pos.x, pos.y, pos.z + d[1]))
                if (side1 != null || side2 != null) {
                    result.add(candidate)
                }
            }
        }
        return result
    }

    private fun findStandableNear(level: Level, base: BlockPos): BlockPos? {
        val order = intArrayOf(0, -1, 1)
        for (dy in order) {
            val p = BlockPos(base.x, base.y + dy, base.z)
            if (canStandAt(level, p)) return p
        }
        return null
    }

    private fun canStandAt(level: Level, pos: BlockPos): Boolean {
        val belowPos = pos.below()
        val below = level.getBlockState(belowPos)
        if (!below.isFaceSturdy(level, belowPos, Direction.UP)) return false

        val feet = level.getBlockState(pos)
        val headPos = pos.above()
        val head = level.getBlockState(headPos)
        if (!isPassable(level, feet, pos)) return false
        if (!isPassable(level, head, headPos)) return false
        return true
    }

    private fun isPassable(level: Level, state: BlockState, pos: BlockPos): Boolean {
        if (state.isAir) return true
        val shape = state.getCollisionShape(level, pos)
        return shape.isEmpty
    }

    private fun reconstructPath(cameFrom: Map<Long, Long>, goalKey: Long): MutableList<BlockPos> {
        val path = ArrayList<BlockPos>()
        var currentKey: Long? = goalKey
        while (currentKey != null) {
            val bp = BlockPos.of(currentKey)
            path.add(bp)
            currentKey = cameFrom[currentKey]
        }
        path.reverse()
        return path
    }

    private fun BlockPos.distManhattan(other: BlockPos): Int =
        abs(this.x - other.x) + abs(this.y - other.y) + abs(this.z - other.z)
}
