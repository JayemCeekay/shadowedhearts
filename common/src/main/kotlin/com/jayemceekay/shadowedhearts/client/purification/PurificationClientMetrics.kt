package com.jayemceekay.shadowedhearts.client.purification

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.storage.purification.PurificationMath
import kotlin.math.ceil

/**
 * Client-side helpers to compute Purification Chamber flow/tempo percentages
 * for the vertical bars in the GUI from the currently visible set.
 */
object PurificationClientMetrics {

    data class Metrics(val flowPct: Float, val tempoPct: Float)

    /** Extract a stable list of types for equality checks. */
    private fun pokemonTypes(p: Pokemon?): List<ElementalType> = p?.types?.toList() ?: emptyList()

    /** True if the shadow shares any type with any support in-ring. */
    private fun shadowSharesType(shadow: Pokemon?, supports: List<Pokemon>): Boolean {
        if (shadow == null) return false
        val st = pokemonTypes(shadow).toSet()
        if (st.isEmpty()) return false
        for (p in supports) {
            for (t in pokemonTypes(p)) if (t in st) return true
        }
        return false
    }

    /**
     * Computes flow percent [0,1] based on clockwise matchups among supports.
     * We score edges: SE=3, Neutral=2, NVE=1 and normalize by max (3 per edge).
     * If the shadow shares a type with any support, we cap flow at 0.5 to reflect the
     * "drops to four bars" penalty from the design doc in a continuous way.
     */
    private fun computeFlow(
        shadow: Pokemon?,
        supports: List<Pokemon>,
        // Cross-set, display-side adjustments
        globalPerfectSets: Int,
        anySetMissingMember: Boolean
    ): Float {
        if (supports.isEmpty()) return 0f
        val edges = PurificationMath.clockwiseSupportMatchups(supports)
        if (edges.isEmpty()) return 0f
        var score = 0
        for (m in edges) {
            score += when (m) {
                PurificationMath.Matchup.SUPER_EFFECTIVE -> 3
                PurificationMath.Matchup.NEUTRAL -> 2
                PurificationMath.Matchup.NOT_VERY_EFFECTIVE -> 1
            }
        }
        val max = edges.size * 3
        var pct = (score.toFloat() / max.toFloat()).coerceIn(0f, 1f)

        // Include center matchup influence: scale similarly to tempo step 3
        run {
            // Find the faced support index relative to a 4-slot array model; approximate using supports[0]
            // The widget uses slot 0 if present; for ring-only list, use index 0 as facing.
            val defTypes = pokemonTypes(supports.firstOrNull())
            if (defTypes.isNotEmpty() && shadow != null) {
                var best = 1.0
                for (atk in pokemonTypes(shadow)) {
                    val m = PurificationMath.effectiveness(atk, defTypes)
                    if (m > best) best = m
                }
                pct = when (PurificationMath.toMatchup(best)) {
                    PurificationMath.Matchup.SUPER_EFFECTIVE -> (pct * (4.0f / 3.0f)).coerceIn(0f, 1f)
                    PurificationMath.Matchup.NOT_VERY_EFFECTIVE -> (pct * (2.0f / 3.0f)).coerceIn(0f, 1f)
                    else -> pct
                }
            }
        }

        // Gate max flow on uniqueness when ring size is 4: allow true 100% only for perfect sets
        if (supports.size == 4 && !PurificationMath.isPerfectSet(supports)) {
            pct = pct.coerceAtMost(0.95f)
        }

        // Optional cross-set adjustments (display-side): small-bar increments/decrements
        val SMALL_BAR = 1f / 8f
        if (anySetMissingMember) {
            pct -= SMALL_BAR
        }
        if (globalPerfectSets >= 2) {
            // Add one small bar per perfect set beyond the first, clamped to 1.0
            val add = SMALL_BAR * (globalPerfectSets - 1)
            pct += add
        }

        // Apply shadow share-type cap last to enforce the "drops to four bars" rule
        if (shadowSharesType(shadow, supports)) {
            pct = pct.coerceAtMost(0.5f)
        }

        return pct.coerceIn(0f, 1f)
    }

    /**
     * Computes a tempo percent [0,1] by mapping the purification step value (without global perfect-set bonus)
     * to a theoretical min/max range for the current ring size:
     * - min assumes all edges NVE and center NVE (x2/3)
     * - max assumes all edges SE and center SE (x4/3)
     */
    private fun computeTempo(shadow: Pokemon?, supportsArray: Array<Pokemon?>): Float {
        val ring = supportsArray.filterNotNull()
        if (shadow == null || ring.isEmpty()) return 0f

        // 1) Base by ring size
        val base = when (ring.size) {
            1 -> 10
            2 -> 27
            3 -> 49
            else -> 96
        }

        // 2) Edges sum from actual ring
        val edges = PurificationMath.clockwiseSupportMatchups(ring)
        var sum = 0
        for (m in edges) {
            when (m) {
                PurificationMath.Matchup.SUPER_EFFECTIVE -> sum += 6
                PurificationMath.Matchup.NOT_VERY_EFFECTIVE -> sum -= 3
                else -> {}
            }
        }
        if (ring.size == 4) sum *= 2
        val pre = base + sum

        // 3) Center vs facing support multiplier (best of shadow types vs faced defender)
        val faceIdx = PurificationMath.facingSupportIndex(supportsArray)
        var value = pre
        if (faceIdx >= 0) {
            val defTypes = pokemonTypes(supportsArray[faceIdx])
            var best = 1.0
            for (atk in pokemonTypes(shadow)) {
                val m = PurificationMath.effectiveness(atk, defTypes)
                if (m > best) best = m
            }
            value = when (PurificationMath.toMatchup(best)) {
                PurificationMath.Matchup.SUPER_EFFECTIVE -> ceil(value * (4.0 / 3.0)).toInt()
                PurificationMath.Matchup.NOT_VERY_EFFECTIVE -> ceil(value * (2.0 / 3.0)).toInt()
                else -> value
            }
        }

        // 4) Normalize to theoretical min/max for this ring size (ignoring perfect-set global bonuses)
        val maxEdges = when (ring.size) {
            1 -> +6
            2 -> +12
            3 -> +18
            else -> +48 // 4 edges, doubled
        }
        val minEdges = when (ring.size) {
            1 -> -3
            2 -> -6
            3 -> -9
            else -> -24
        }
        val preMin = base + minEdges
        val preMax = base + maxEdges
        val minVal = ceil(preMin * (2.0 / 3.0)).toInt()
        val maxVal = ceil(preMax * (4.0 / 3.0)).toInt()

        val denom = (maxVal - minVal).coerceAtLeast(1)
        val pct = ((value - minVal).toFloat() / denom.toFloat()).coerceIn(0f, 1f)
        return pct
    }

    /** Public entry: compute metrics for a set. */
    fun compute(
        shadow: Pokemon?,
        supports: Array<Pokemon?>,
        globalPerfectSets: Int = 0,
        anySetMissingMember: Boolean = false
    ): Metrics {
        val ring = supports.filterNotNull()
        val flow = computeFlow(shadow, ring, globalPerfectSets, anySetMissingMember)
        val tempo = computeTempo(shadow, supports)
        return Metrics(flowPct = flow, tempoPct = tempo)
    }
}
