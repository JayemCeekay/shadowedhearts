package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.util.Mth;

import java.util.Iterator;
import java.util.Set;

/**
 * Utility for manipulating Cobblemon Pokemon aspects and derived values used by this mod.
 */
public final class PokemonAspectUtil {

    private static final String METER_PREFIX = "shadowedhearts:meter:"; // 0..100

    /** Add/remove the Shadow aspect on the Pokemon’s stored data. */
    public static void setShadowAspect(Pokemon pokemon, boolean isShadow) {
        var aspects = pokemon.getAspects(); // Set<String> maintained by Cobblemon
        if (isShadow) aspects.add(SHAspects.SHADOW);
        else aspects.remove(SHAspects.SHADOW);
        pokemon.setForcedAspects(aspects);
        pokemon.updateAspects();
    }

    public static boolean hasShadowAspect(Pokemon pokemon) {
        return pokemon.getAspects().contains(SHAspects.SHADOW);
    }

    /**
     * Persist corruption scalar [0..1] by mapping to 0..100 meter and storing as an aspect.
     */
    public static void setCorruption(Pokemon pokemon, float value) {
        int meter = Math.round(Mth.clamp(value, 0f, 1f) * 100f);
        setCorruptionMeter(pokemon, meter);
    }

    /**
     * Persist corruption meter [0..100] as a unique aspect "shadowedhearts:meter:NN".
     * Any previous meter aspects are removed to keep only one stored value.
     */
    public static void setCorruptionMeter(Pokemon pokemon, int meter) {
        int clamped = Math.max(0, Math.min(100, meter));
        Set<String> aspects = pokemon.getAspects();
        // Remove previous meter aspects
        for (Iterator<String> it = aspects.iterator(); it.hasNext();) {
            String a = it.next();
            if (a.startsWith(METER_PREFIX)) it.remove();
        }
        // Add the new meter aspect if non-zero or if you want explicit zero persistence
        aspects.add(METER_PREFIX + clamped);
        pokemon.setForcedAspects(aspects);
        pokemon.updateAspects();
    }

    /**
     * Read corruption scalar from the stored meter aspect if present; fallback based on shadow aspect.
     */
    public static float getCorruption(Pokemon pokemon) {
        int meter = getCorruptionMeter(pokemon);
        if (meter >= 0) return meter / 100f;
        // Fallback: Shadow -> full intensity, Non-shadow -> none
        return hasShadowAspect(pokemon) ? 1f : 0f;
    }

    /**
     * @return the stored meter [0..100], or -1 if no meter aspect is present.
     */
    public static int getCorruptionMeter(Pokemon pokemon) {
        for (String a : pokemon.getAspects()) {
            if (a.startsWith(METER_PREFIX)) {
                try {
                    String num = a.substring(METER_PREFIX.length());
                    int parsed = Integer.parseInt(num);
                    return Math.max(0, Math.min(100, parsed));
                } catch (NumberFormatException ignored) { }
            }
        }
        return -1;
    }
}
