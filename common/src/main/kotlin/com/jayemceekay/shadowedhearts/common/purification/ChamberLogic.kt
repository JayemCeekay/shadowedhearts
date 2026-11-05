package com.jayemceekay.shadowedhearts.common.purification

import com.cobblemon.mod.common.pokemon.Pokemon
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeProgressionMath
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore

object ChamberLogic {

    fun advanceSteps(store: PurificationChamberStore, steps: Int, stepAccumulator: Int): Int {
        if (steps <= 0 || !GameplayTuningConfig.INSTANCE.chamberPurificationEnabled()
            || GameplayTuningConfig.INSTANCE.chamberMultiplier() <= 0.0) return stepAccumulator
        val stepRequirement = ShadowedHeartsConfigs.getInstance().shadowConfig.purificationChamberStepRequirement()
        val accumulated = stepAccumulator.toLong() + steps.toLong()
        val intervals = accumulated / stepRequirement
        if (intervals > 0) applyChamberTicks(store, intervals)
        return (accumulated % stepRequirement).toInt()
    }

    private fun applyChamberTicks(store: PurificationChamberStore, intervals: Long) {
        val affectedPokemon = mutableSetOf<Pokemon>()
        val setsCount = PurificationChamberStore.SETS
        // Migrate old meter aspects before reading the gauge; otherwise the setter's
        // compatibility repair could overwrite the newly applied batch reduction.
        for (setIndex in 0 until setsCount) {
            if (store.getSupports(setIndex).any { it != null }) {
                store.getShadow(setIndex)?.let { ShadowAspectUtil.ensureRequiredShadowAspects(it, false) }
            }
        }
        val perfectCount = (0 until setsCount).count { setIndex ->
            val ring = store.getSupportRing(setIndex)
            PurificationMath.isPerfectSet(ring)
        }

        for (setIndex in 0 until setsCount) {
            val shadow = store.getShadow(setIndex)
            val supports = store.getSupports(setIndex)
            val delta = HeartGaugeProgressionMath.scaleReduction(
                PurificationMath.computePurificationDeltaForSet(shadow, supports, perfectCount),
                GameplayTuningConfig.INSTANCE.chamberMultiplier())
            
            if (delta != 0 && shadow != null) {
                val cur = ShadowAspectUtil.getHeartGaugeMeter(shadow)
                // Supports and types are fixed during this call. Batch completed intervals
                // so a large saved remainder or /sh debug steps input cannot monopolize the server.
                val next = (cur.toLong() + delta.toLong() * intervals).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                ShadowService.setHeartGauge(shadow, null, next, false)
                affectedPokemon.add(shadow)
            }
        }
        affectedPokemon.forEach { ShadowService.syncAll(it) }
        if (affectedPokemon.isNotEmpty()) {
            store.touch()
        }
    }
}
