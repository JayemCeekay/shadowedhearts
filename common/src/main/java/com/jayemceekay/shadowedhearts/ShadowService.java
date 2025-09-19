package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.jetbrains.annotations.Nullable;


/**
 * Service façade for synchronizing shadow state between stored Cobblemon Pokemon and live entities.
 */
public final class ShadowService {

    /**
     * Make a Pokemon (and its live entity if present) shadow/non-shadow.
     * @param pokemon stored Pokemon object
     * @param live currently loaded entity for that Pokemon (nullable)
     * @param shadow true to set shadow, false to clear
     */
    public static void setShadow(Pokemon pokemon, @Nullable PokemonEntity live, boolean shadow) {
        PokemonAspectUtil.setShadowAspect(pokemon, shadow);
        if (live != null) {
            ShadowPokemonData.set(live, shadow, PokemonAspectUtil.getCorruption(pokemon));
        }
        // If live == null but is in world somewhere, optionally locate it to sync.
    }

    /** Set corruption-purity meter [0..100]. */
    public static void setCorruptionMeter(Pokemon pokemon, @Nullable PokemonEntity live, int meter) {
        int clamped = Math.max(0, Math.min(100, meter));
        float scalar = clamped / 100f;
        PokemonAspectUtil.setCorruptionMeter(pokemon, clamped);
        if (live != null) ShadowPokemonData.set(live, ShadowPokemonData.isShadow(live), scalar);
    }

    /** Convenience: fully purified (0) and not shadow. */
    public static void fullyPurify(Pokemon pokemon, @Nullable PokemonEntity live) {
        setShadow(pokemon, live, false);
        setCorruptionMeter(pokemon, live, 0);
    }

    /** Convenience: fully corrupted (100) and shadow. */
    public static void fullyCorrupt(Pokemon pokemon, @Nullable PokemonEntity live) {
        setShadow(pokemon, live, true);
        setCorruptionMeter(pokemon, live, 100);
    }
}
