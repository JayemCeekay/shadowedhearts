package com.jayemceekay.shadowedhearts.common.event.player;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import net.minecraft.nbt.CompoundTag;

/** Partial walking cycles belong to a Pokemon, not the player or current party slot. */
public final class PokemonPurificationWalking {
    private static final String KEY = "shadowedhearts:party_walking";
    private static final int DEFAULT_INTERVAL = 256;
    private PokemonPurificationWalking() {}

    /** Called only with movement already accepted by the teleport-safe player tracker. */
    public static int advance(Pokemon pokemon, int acceptedSteps, int interval) {
        if (pokemon == null || acceptedSteps <= 0 || !ShadowAspectUtil.hasShadowAspect(pokemon)) return 0;
        var current = pokemon.getPersistentData().getCompound(KEY);
        var result = credit(current.getInt("steps"), current.getInt("interval"), acceptedSteps, interval);
        var updated = new CompoundTag();
        updated.putInt("steps", result.remainingSteps());
        updated.putInt("interval", result.interval());
        pokemon.getPersistentData().put(KEY, updated);
        // Mutating persistentData alone does not mark Cobblemon stores for saving.
        pokemon.onChange(null);
        return result.completedIntervals();
    }

    public static void clear(Pokemon pokemon) {
        if (pokemon != null && pokemon.getPersistentData().contains(KEY)) {
            pokemon.getPersistentData().remove(KEY);
            pokemon.onChange(null);
        }
    }

    static Credit credit(int savedSteps, int savedInterval, int acceptedSteps, int requestedInterval) {
        int interval = Math.clamp(requestedInterval, 1, 1_000_000);
        int previousInterval = savedInterval >= 1 && savedInterval <= 1_000_000
                ? savedInterval : DEFAULT_INTERVAL;
        int remaining = Math.clamp(savedSteps, 0, previousInterval - 1);
        // Keep the completed fraction when a server owner changes cadence; do not pay retroactive cycles.
        long rebased = (long) remaining * interval / previousInterval;
        long total = rebased + Math.clamp(acceptedSteps, 0, 8);
        return new Credit((int) (total % interval), interval, (int) (total / interval));
    }

    record Credit(int remainingSteps, int interval, int completedIntervals) {}
}
