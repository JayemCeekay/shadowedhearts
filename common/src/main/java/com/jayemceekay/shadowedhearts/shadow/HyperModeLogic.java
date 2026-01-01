package com.jayemceekay.shadowedhearts.shadow;

import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hyper Mode entry rate helper.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 *
 * Provides convenience methods to compute the chance that a Shadow Pokémon
 * enters Hyper Mode when ordered to use a non-Shadow move based on its Nature
 * and current Heart Gauge bars (5..0). Values are taken from Colosseum/XD tables.
 *
 * Notes: Some values vary per nature group and can increase before decreasing.
 * The table here mirrors the in-game behavior at a coarse level. It can be
 * tuned later or read from datapack/config if desired.
 */
public final class HyperModeLogic {
    private HyperModeLogic() {}

    /**
     * Chance table by normalized nature id -> array for bars [5,4,3,2,1,0].
     * Values are percentages 0..100.
     */
    private static final Map<String, int[]> TABLE = new HashMap<>();

    static {
        // Calm, Lonely, Modest, Timid — steadily decreases with purification
        putAll(new String[]{"calm","lonely","modest","timid"}, new int[]{30,25,20,15,10,5});
        // Bold, Brave, Lax, Quirky, Sassy — higher overall but decreasing
        putAll(new String[]{"bold","brave","lax","quirky","sassy"}, new int[]{50,40,30,20,10,5});
        // Hasty, Impish, Naughty, Rash — starts high then eases
        putAll(new String[]{"hasty","impish","naughty","rash"}, new int[]{50,0,40,30,25,12});
        // Careful, Docile, Quiet, Serious — mid, fairly flat
        putAll(new String[]{"careful","docile","quiet","serious"}, new int[]{0,20,0,15,10,5});
        // Gentle, Jolly, Mild, Naive — stays the same even when ready
        putAll(new String[]{"gentle","jolly","mild","naive"}, new int[]{0,0,50,0,0,0});
        // Adamant, Bashful, Hardy, Relaxed — increases before decreasing
        putAll(new String[]{"adamant","bashful","hardy","relaxed"}, new int[]{30,0,70,0,50,25});

    }

    private static void putAll(String[] natures, int[] chances) {
        for (String n : natures) TABLE.put(n, chances);
    }

    /**
     * Converts a heart gauge scalar [0..1] to visible bars 5..0.
     * 1.0 -> 5 bars, 0.0 -> 0 bars.
     */
    public static int barsFromScalar(float heartGauge) {
        float clamped = Mth.clamp(heartGauge, 0f, 1f);
        // Split into 5 equal segments. Use ceil to map (0, 0.2] => 1 bar, ..., (0.8, 1] => 5 bars
        int bars = (int)Math.ceil(clamped * 5f);
        return Mth.clamp(bars, 0, 5);
    }

    /**
     * Returns the percentage chance [0..100] to enter Hyper Mode for a given nature and bars.
     * If the nature is not in the table, uses a conservative fallback curve.
     */
    public static int chancePercent(String nature, int bars) {
        bars = Mth.clamp(bars, 0, 5);
        String key = nature == null ? "" : nature.toLowerCase(Locale.ROOT);
        int[] row = TABLE.get(key);
        if (row == null) {
            // Fallback: linear from 30% at 5 bars down to 5% at 0 bars
            int[] def = {30,25,20,15,10,5};
            return def[5 - Math.max(0, 5 - bars)];
        }
        // map bars 5..0 to indices 0..5
        int idx = 5 - bars;
        return row[idx];
    }

    /** Rolls the Hyper Mode entry check using the given RNG. */
    public static boolean roll(String nature, int bars) {
        int p = chancePercent(nature, bars);
        if (p <= 0) return false;
        if (p >= 100) return true;
        // 1..100
        int r = ThreadLocalRandom.current().nextInt(100) + 1;
        return r <= p;
    }
}
