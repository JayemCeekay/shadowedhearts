package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.common.event.player.PokemonPurificationWalking;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;
import com.jayemceekay.shadowedhearts.network.PokemonStateSync;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallProximity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * Owns Shadow aspect lifecycle and aura eligibility.
 * Existing initialization, gauge, property, buffer, unlock, synchronization, and world-query
 * methods remain as forwarding entry points; new callers should use the focused owners directly.
 */
public final class ShadowAspectUtil {

    /**
     * Add/remove the Shadow aspect on the Pokemon’s stored data.
     */
    public static void setShadowAspect(Pokemon pokemon, boolean isShadow) {
        setShadowAspect(pokemon, isShadow, true);
    }

    public static void setShadowAspect(Pokemon pokemon, boolean isShadow, boolean sync) {
        if (hasShadowAspect(pokemon) != isShadow) {
            PokemonPurificationWalking.clear(pokemon);
        }
        // Work on a defensive copy to avoid mutating the live Set while it may be iterated elsewhere
        Set<String> aspectsCopy = new HashSet<>(pokemon.getAspects());
        if (isShadow) {
            aspectsCopy.add(SHAspects.SHADOW);
            replaceMovesWithShadowMoves(pokemon);
        } else {
            aspectsCopy.remove(SHAspects.SHADOW);
        }
        pokemon.setForcedAspects(aspectsCopy);
        pokemon.updateAspects();
        if (sync) {
            PokemonStateSync.syncAspects(pokemon);
            PokemonStateSync.syncBenchedMoves(pokemon);
            PokemonStateSync.syncMoveSet(pokemon);
        }
    }

    private static void replaceMovesWithShadowMoves(Pokemon pokemon) {
        ShadowMoveUtil.assignShadowMoves(pokemon);
    }

    public static boolean hasShadowAspect(Pokemon pokemon) {
        if (pokemon != null) {
            return pokemon.getAspects().contains(SHAspects.SHADOW);
        }
        return false;
    }

    public static boolean shouldHaveShadowAura(Pokemon pokemon) {
        if (pokemon == null) return false;
        return hasShadowAspect(pokemon) || isHoldingShadowShard(pokemon);
    }

    public static boolean isHoldingShadowShard(Pokemon pokemon) {
        if (pokemon == null) return false;
        return pokemon.cosmeticItem().is(ModItems.SHADOW_SHARD.get());
    }

    public static boolean shouldHaveShadowAura(RenderablePokemon pokemon) {
        if (pokemon == null) return false;
        return pokemon.getAspects().contains(SHAspects.SHADOW) || pokemon.getAspects().contains("cosmetic_item-shadow_shard");
    }

    public static void ensureRequiredShadowAspects(Pokemon pokemon) {
        ShadowStateInitialization.ensureRequiredShadowAspects(pokemon);
    }

    /**
     * Migrates legacy aspects into custom properties and initializes missing Shadow state.
     * Saved Heart Gauge progress is restored before creating a default from the configured maximum.
     * Missing XP and EV buffers begin empty; their properties retain persistent-data mirrors.
     */
    public static void ensureRequiredShadowAspects(Pokemon pokemon, boolean sync) {
        ShadowStateInitialization.ensureRequiredShadowAspects(pokemon, sync);
    }

    /** Restore saved progress verbatim; create a default only after authored species/max are final. */
    static boolean initializeMissingHeartGauge(Pokemon pokemon) {
        return ShadowStateInitialization.initializeMissingHeartGauge(pokemon);
    }

    /**
     * Sends an aspects update for the given Pokemon to clients and marks it dirty for persistence.
     * Safe to call even on the server only; will fall back to onChange(null) if packet creation fails.
     */
    public static void syncAspects(Pokemon pokemon) {
        PokemonStateSync.syncAspects(pokemon);
    }

    public static void syncProperties(Pokemon pokemon) {
        PokemonStateSync.syncProperties(pokemon);
    }

    public static void syncBenchedMoves(Pokemon pokemon) {
        PokemonStateSync.syncBenchedMoves(pokemon);
    }

    public static void syncMoveSet(Pokemon pokemon) {
        PokemonStateSync.syncMoveSet(pokemon);
    }

    /**
     * Persist a clamped absolute Heart Gauge value through its property and NBT mirror.
     */
    public static void setHeartGauge(Pokemon pokemon, float value) {
        ShadowHeartGauge.setHeartGauge(pokemon, value);
    }

    /**
     * Persist an absolute Heart Gauge meter clamped to the configured maximum.
     */
    public static void setHeartGaugeValue(Pokemon pokemon, int meter) {
        ShadowHeartGauge.setHeartGaugeValue(pokemon, meter);
    }

    public static void setHeartGaugeValue(Pokemon pokemon, int meter, boolean sync) {
        ShadowHeartGauge.setHeartGaugeValue(pokemon, meter, sync);
    }

    public static void setHeartGaugeProperty(Pokemon pokemon, int value) {
        ShadowPokemonProperties.setHeartGaugeProperty(pokemon, value);
    }

    public static void setHeartGaugeProperty(Pokemon pokemon, int value, boolean sync) {
        ShadowPokemonProperties.setHeartGaugeProperty(pokemon, value, sync);
    }

    public static int getHeartGaugeValueFromProperty(Pokemon pokemon) {
        return ShadowPokemonProperties.getHeartGaugeValueFromProperty(pokemon);
    }

    public static void setExposureProperty(Pokemon pokemon, double value) {
        ShadowPokemonProperties.setExposureProperty(pokemon, value);
    }

    public static double getExposure(Pokemon pokemon) {
        return ShadowPokemonProperties.getExposure(pokemon);
    }

    public static void setImmunizedProperty(Pokemon pokemon, boolean value) {
        ShadowPokemonProperties.setImmunizedProperty(pokemon, value);
    }

    public static boolean isImmunized(Pokemon pokemon) {
        return ShadowPokemonProperties.isImmunized(pokemon);
    }

    public static void setXPBufferProperty(Pokemon pokemon, int value) {
        ShadowPokemonProperties.setXPBufferProperty(pokemon, value);
    }

    public static void setXPBufferProperty(Pokemon pokemon, int value, boolean sync) {
        ShadowPokemonProperties.setXPBufferProperty(pokemon, value, sync);
    }

    public static int getXPBufferFromProperty(Pokemon pokemon) {
        return ShadowPokemonProperties.getXPBufferFromProperty(pokemon);
    }

    public static void setEVBufferProperty(Pokemon pokemon, int[] values) {
        ShadowPokemonProperties.setEVBufferProperty(pokemon, values);
    }

    public static void setEVBufferProperty(Pokemon pokemon, int[] values, boolean sync) {
        ShadowPokemonProperties.setEVBufferProperty(pokemon, values, sync);
    }

    public static int[] getEVBufferFromProperty(Pokemon pokemon) {
        return ShadowPokemonProperties.getEVBufferFromProperty(pokemon);
    }

    public static void setHeartGaugeMaxProperty(Pokemon pokemon, int value) {
        ShadowPokemonProperties.setHeartGaugeMaxProperty(pokemon, value);
    }

    public static void setHeartGaugeMaxProperty(Pokemon pokemon, int value, boolean sync) {
        ShadowPokemonProperties.setHeartGaugeMaxProperty(pokemon, value, sync);
    }

    public static int getHeartGaugeMaxFromProperty(Pokemon pokemon) {
        return ShadowPokemonProperties.getHeartGaugeMaxFromProperty(pokemon);
    }

    public static void setScentCooldown(Pokemon pokemon, long value) {
        ShadowPokemonProperties.setScentCooldown(pokemon, value);
    }

    /** Last successful scent-use tick, or -1 when never used. Tick zero is a valid saved use. */
    public static long getScentCooldown(Pokemon pokemon) {
        return ShadowPokemonProperties.getScentCooldown(pokemon);
    }

    /** Rounded presentation intensity, not a purification eligibility check. */
    public static float getHeartGauge(Pokemon pokemon) {
        return ShadowHeartGauge.getHeartGauge(pokemon);
    }

    /**
     * @return the stored heart gauge scaled to 0..100 based on the configured maximum, or -1 if no stored value is present.
     */
    public static int getHeartGaugeValue(Pokemon pokemon) {
        return ShadowHeartGauge.getHeartGaugeValue(pokemon);
    }

    /**
     * Returns the stored heart gauge absolute meter [0..configured maximum]. If absent, returns
     * the configured maximum when Shadow, else 0. This is useful for applying absolute deltas.
     */
    public static int getHeartGaugeMeter(Pokemon pokemon) {
        return ShadowHeartGauge.getHeartGaugeMeter(pokemon);
    }

    /** A rounded zero percent is not enough: the absolute meter must be empty. */
    public static boolean isReadyForPurification(Pokemon pokemon) {
        return ShadowHeartGauge.isReadyForPurification(pokemon);
    }

    // ---------------- Heart Gauge visibility helpers ----------------

    /**
     * Returns the heart gauge percent [0..100]. If absent, returns 100 when Shadow, else 0.
     */
    public static int getHeartGaugePercent(Pokemon pokemon) {
        return ShadowHeartGauge.getHeartGaugePercent(pokemon);
    }

    /**
     * Returns how many of the 5 bars are still filled (0..5) based on percent.
     * 100% => 5 bars, 81..100 => 5, 61..80 => 4, 41..60 => 3, 21..40 => 2, 1..20 => 1, 0 => 0.
     */
    public static int getBarsRemaining(Pokemon pokemon) {
        return ShadowHeartGauge.getBarsRemaining(pokemon);
    }

    /** Nature visibility follows the server's reveal threshold (40% by default). */
    public static boolean isNatureHiddenByGauge(Pokemon pokemon) {
        return ShadowUnlockPolicy.isNatureHiddenByGauge(pokemon);
    }

    /**
     * Level/EXP visibility is independent of EXP buffering.
     */
    public static boolean isLevelExpHiddenByGauge(Pokemon pokemon) {
        return ShadowUnlockPolicy.isLevelExpHiddenByGauge(pokemon);
    }

    /**
     * EV visibility is independent of EV buffering.
     */
    public static boolean isEVHiddenByGauge(Pokemon pokemon) {
        return ShadowUnlockPolicy.isEVHiddenByGauge(pokemon);
    }

    /**
     * IV visibility follows the server's reveal threshold.
     */
    public static boolean isIVHiddenByGauge(Pokemon pokemon) {
        return ShadowUnlockPolicy.isIVHiddenByGauge(pokemon);
    }

    /**
     * Shared visibility and battle-use gate. Defaults preserve the original 79/40/20 thresholds.
     * Disabling the restriction allows all ordinary moves without removing assigned Shadow moves.
     */
    public static int getAllowedVisibleNonShadowMoves(Pokemon pokemon) {
        return ShadowUnlockPolicy.getAllowedVisibleNonShadowMoves(pokemon);
    }

    // ---------------- Pending EXP buffer helpers ----------------

    /**
     * Returns buffered experience stored on the Pokemon (>=0).
     */
    public static int getBufferedExp(Pokemon pokemon) {
        return ShadowProgressionBuffers.getBufferedExp(pokemon);
    }

    /**
     * Replaces buffered experience with the provided non-negative amount.
     */
    public static void setBufferedExp(Pokemon pokemon, int amount) {
        ShadowProgressionBuffers.setBufferedExp(pokemon, amount);
    }

    /**
     * Adds to buffered experience and persists.
     */
    public static void addBufferedExp(Pokemon pokemon, int delta) {
        ShadowProgressionBuffers.addBufferedExp(pokemon, delta);
    }

    // ---------------- Pending EV buffer helpers ----------------

    /**
     * Returns a 6-length array of pending EVs in order: HP, ATK, DEF, SPA, SPD, SPE.
     */
    public static int[] getBufferedEvs(Pokemon pokemon) {
        return ShadowProgressionBuffers.getBufferedEvs(pokemon);
    }

    /**
     * Replace pending EVs with provided values. Array must be length 6.
     */
    public static void setBufferedEvs(Pokemon pokemon, int[] values) {
        ShadowProgressionBuffers.setBufferedEvs(pokemon, values);
    }

    /**
     * Adds pending EV to a given stat.
     */
    public static void addBufferedEv(Pokemon pokemon, Stat stat, int delta) {
        ShadowProgressionBuffers.addBufferedEv(pokemon, stat, delta);
    }

    /** Returns the actual accepted gain for native item success/consumption and POST events. */
    public static int acceptBufferedEv(Pokemon pokemon, Stat stat, int delta) {
        return ShadowProgressionBuffers.acceptBufferedEv(pokemon, stat, delta);
    }

    /**
     * Clears all pending EV/EXP buffers.
     */
    public static void clearAllBuffers(Pokemon pokemon) {
        ShadowProgressionBuffers.clearAllBuffers(pokemon);
    }

    public static boolean shouldMaskMove(Pokemon pokemon, Move move) {
        return ShadowUnlockPolicy.shouldMaskMove(pokemon, move);
    }

    public static boolean shouldMaskMove(Pokemon pokemon, int indexInMoveSet) {
        return ShadowUnlockPolicy.shouldMaskMove(pokemon, indexInMoveSet);
    }

    public static boolean shouldMaskMove(Pokemon pokemon, String moveId) {
        return ShadowUnlockPolicy.shouldMaskMove(pokemon, moveId);
    }

    public static boolean isNearMeteoroid(ServerLevel level, BlockPos pos, int radius, int verticalRadius) {
        return UmbrafallProximity.isNearActiveCore(level, pos, radius, verticalRadius);
    }
}
