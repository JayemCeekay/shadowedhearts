package com.jayemceekay.shadowedhearts.client.purification

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.storage.purification.PurificationMath

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
                var best = 0.0
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
     * Computes a tempo percent [0,1] based on the number of support Pokémon and their effectiveness.
     * Mapping per design guidance:
     * - 0: 0%
     * - 1: 10-15%
     * - 2: 25-35%
     * - 3: 50-65%
     * - 4 (non-perfect): 70-85%
     * - 4 (perfect flow): 100%
     */
    private fun computeTempo(shadow: Pokemon?, supportsArray: Array<Pokemon?>): Float {
        val ring = supportsArray.filterNotNull()
        if (shadow == null || ring.isEmpty()) return 0f

        val baseRange = when (ring.size) {
            1 -> 0.10f..0.15f
            2 -> 0.25f..0.35f
            3 -> 0.50f..0.65f
            else -> 0.70f..0.85f // 4 supports
        }

        // Within each tier, we vary the tempo based on clockwise flow and center matchup.
        // For 1 Pokémon, clockwise flow is 0, so it depends only on center matchup.
        
        // Use the same score logic as computeFlow but without normalization to [0,1] yet.
        val edges = PurificationMath.clockwiseSupportMatchups(ring)
        var edgeScore = 0
        for (m in edges) {
            edgeScore += when (m) {
                PurificationMath.Matchup.SUPER_EFFECTIVE -> 3
                PurificationMath.Matchup.NEUTRAL -> 2
                PurificationMath.Matchup.NOT_VERY_EFFECTIVE -> 1
            }
        }
        val maxEdgeScore = edges.size * 3
        var edgePct = if (maxEdgeScore > 0) edgeScore.toFloat() / maxEdgeScore.toFloat() else 0.5f

        // Center matchup influence
        val defTypes = pokemonTypes(supportsArray[PurificationMath.facingSupportIndex(supportsArray)])
        if (defTypes.isNotEmpty()) {
            var best = 0.0
            for (atk in pokemonTypes(shadow)) {
                val m = PurificationMath.effectiveness(atk, defTypes)
                if (m > best) best = m
            }
            edgePct = when (PurificationMath.toMatchup(best)) {
                PurificationMath.Matchup.SUPER_EFFECTIVE -> (edgePct * (4.0f / 3.0f)).coerceIn(0f, 1f)
                PurificationMath.Matchup.NOT_VERY_EFFECTIVE -> (edgePct * (2.0f / 3.0f)).coerceIn(0f, 1f)
                else -> edgePct
            }
        }

        // Interpolate within the range
        var tempo = baseRange.start + (baseRange.endInclusive - baseRange.start) * edgePct

        // Special case: 4 supports + Perfect Flow = 100%
        if (ring.size == 4 && PurificationMath.isPerfectSet(ring)) {
            tempo = 1.0f
        }

        return tempo.coerceIn(0f, 1.0f)
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
