package com.jayemceekay.shadowedhearts.storage.purification

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemon.mod.common.pokemon.Pokemon
import kotlin.math.ceil

/**
 * Server-side helper for Purification Chamber flow/tempo and purification delta math.
 * Implements mechanics described in docs: clockwise adjacency matchups, perfect-set checks,
 * and the 161-steps purification formula with bonuses.
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption terms are gameplay mechanics.
 */
object PurificationMath {
    /** Result classes for effectiveness bucketing. */
    enum class Matchup { SUPER_EFFECTIVE, NEUTRAL, NOT_VERY_EFFECTIVE }

    /** Returns the two-element list of a Pokémon's types (size 1 or 2). */
    private fun pokemonTypes(p: Pokemon): List<ElementalType> = p.types.toList()

    /**
     * Effectiveness from attacker to defender following special rules:
     * - Normal vs Normal is treated as super effective
     * - Immunities (0x) are treated as resistances (0.5x)
     * - If dual-type defender has one weakness and one resistance (net 1x), treat as weak (2x)
     */
    fun effectiveness(attacker: ElementalType, defenderTypes: List<ElementalType>): Double {
        var product = 1.0
        var hasWeak = false
        var hasResist = false
        for (def in defenderTypes) {
            var m = AIUtility.getDamageMultiplier(attacker, def)
            
            // Special rule: Normal vs Normal is treated as super effective (2x)
            if (attacker == ElementalTypes.NORMAL && def == ElementalTypes.NORMAL) {
                m = 2.0
            }

            when {
                m == 0.0 -> hasResist = true // treat as resist later
                m > 1.0 -> hasWeak = true
                m < 1.0 -> hasResist = true
            }
            product *= if (m == 0.0) 0.5 else m
        }
        // Weakness prioritized over resistance when net neutral
        if (product == 1.0 && hasWeak && hasResist) return 2.0
        return product
    }

    /** Buckets numeric effectiveness multiplier into Matchup per spec. */
    fun toMatchup(mult: Double): Matchup = when {
        mult > 1.0 -> Matchup.SUPER_EFFECTIVE
        mult < 1.0 -> Matchup.NOT_VERY_EFFECTIVE
        else -> Matchup.NEUTRAL
    }

    /**
     * Computes clockwise matchups among supports; returns list of four (or fewer) edges.
     * For n supports, edges are i -> (i+1)%n using primary attacking type as support i primary (first type),
     * though we evaluate with both attacker types by using the more effective of the attacker's two STAB types.
     */
    fun clockwiseSupportMatchups(supports: List<Pokemon>): List<Matchup> {
        if (supports.size < 2) return emptyList()
        val res = ArrayList<Matchup>(supports.size)
        for (i in supports.indices) {
            val a = supports[i]
            val b = supports[(i + 1) % supports.size]
            val atkTypes = pokemonTypes(a)
            val defTypes = pokemonTypes(b)
            // Use the better of the attacker's available types (best-case STAB for the chamber flow rules)
            var best = 0.0
            for (atk in atkTypes) {
                val m = effectiveness(atk, defTypes)
                if (m > best) best = m
            }
            res.add(toMatchup(best))
        }
        return res
    }

    /** Returns true if a set is "perfect": four supports present, no overlapping types, all four edges are SE. */
    fun isPerfectSet(supports: List<Pokemon>): Boolean {
        if (supports.size != 4) return false
        // No overlapping types across supports
        val seen = mutableSetOf<ElementalType>()
        for (p in supports) {
            for (t in pokemonTypes(p)) {
                if (!seen.add(t)) return false
            }
        }
        val edges = clockwiseSupportMatchups(supports)
        return edges.size == 4 && edges.all { it == Matchup.SUPER_EFFECTIVE }
    }

    /** Facing index for the center shadow vs support: choose slot 0 if present, else the first available. */
    fun facingSupportIndex(supports: Array<Pokemon?>): Int {
        if (supports.isNotEmpty() && supports[0] != null) return 0
        for (i in supports.indices) if (supports[i] != null) return i
        return -1
    }

    /**
     * Computes the purification delta for a single set over a 161-step tick.
     * Returns 0 if no shadow present or no supports.
     */
    fun computePurificationDeltaForSet(shadow: Pokemon?, supports: Array<Pokemon?>, globalPerfectSets: Int): Int {
        val ring = supports.filterNotNull()
        if (shadow == null || ring.isEmpty()) return 0

        // 1) Base value by ring size
        val base = when (ring.size) {
            1 -> 10
            2 -> 27
            3 -> 49
            else -> 96 // 4 supports
        }

        // 2) Sum +/- by clockwise edges
        val edges = clockwiseSupportMatchups(ring)
        var sum = 0
        for (m in edges) {
            when (m) {
                Matchup.SUPER_EFFECTIVE -> sum += 6
                Matchup.NOT_VERY_EFFECTIVE -> sum -= 3
                else -> {}
            }
        }
        if (ring.size == 4) sum *= 2

        var value = base + sum

        // 3) Apply center vs facing support multiplier
        val faceIdx = facingSupportIndex(supports)
        if (faceIdx >= 0) {
            val shadowTypes = pokemonTypes(shadow)
            val defTypes = pokemonTypes(supports[faceIdx]!!)
            // Use best of shadow types
            var best = 0.0
            for (atk in shadowTypes) {
                val m = effectiveness(atk, defTypes)
                if (m > best) best = m
            }
            value = when (toMatchup(best)) {
                Matchup.SUPER_EFFECTIVE -> ceil(value * (4.0 / 3.0)).toInt()
                Matchup.NOT_VERY_EFFECTIVE -> ceil(value * (2.0 / 3.0)).toInt()
                else -> value
            }
        }

        // 4) Perfect-set cumulative bonus
        val isPerfect = isPerfectSet(ring)
        if (globalPerfectSets >= 2 && isPerfect) {
            val bonus = when (globalPerfectSets) {
                2 -> 1
                3 -> 5
                4 -> 10
                5 -> 15
                6 -> 25
                7 -> 35
                8 -> 50
                else -> 100 // 9
            }
            value += bonus
        }

        // Negative values reduce the heart gauge (open heart)
        return -value
    }
}
