package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.util.Mth;

import java.util.Iterator;
import java.util.Set;

/**
 * Utility for manipulating Cobblemon Pokemon aspects and derived values used by this mod.
 */
public final class PokemonAspectUtil {

    private static final String METER_PREFIX = "shadowedhearts:heartgauge:"; // 0..20000
    private static final String XPBUF_PREFIX = "shadowedhearts:xpbuf:"; // pending exp integer
    private static final String EVBUF_PREFIX = "shadowedhearts:evbuf:"; // pending EVs csv hp,atk,def,spa,spd,spe

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
     * Persist corruption scalar [0..1] by mapping to 0..20000 meter and storing as an aspect.
     */
    public static void setHeartGauge(Pokemon pokemon, float value) {
        int meter = Math.round(Mth.clamp(value, 0f, 20000f));
        setHeartGaugeValue(pokemon, meter);
    }

    /**
     * Persist corruption meter [0..20000] as a unique aspect "shadowedhearts:meter:NN".
     * Any previous meter aspects are removed to keep only one stored value.
     */
    public static void setHeartGaugeValue(Pokemon pokemon, int meter) {
        int clamped = Math.max(0, Math.min(20000, meter));
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
    public static float getHeartGauge(Pokemon pokemon) {
        int meter = getHeartGaugeValue(pokemon);
        if (meter >= 0) return meter / 200f;
        // Fallback: Shadow -> full intensity, Non-shadow -> none
        return hasShadowAspect(pokemon) ? 1f : 0f;
    }

    /**
     * @return the stored meter [0..20000], or -1 if no meter aspect is present.
     */
    public static int getHeartGaugeValue(Pokemon pokemon) {
        for (String a : pokemon.getAspects()) {
            if (a.startsWith(METER_PREFIX)) {
                try {
                    String num = a.substring(METER_PREFIX.length());
                    int parsed = Integer.parseInt(num);
                    int value = Math.max(0, Math.min(20000, parsed))/200;
                    return value;
                } catch (NumberFormatException ignored) { }
            }
        }
        return -1;
    }

    // ---------------- Pending EXP buffer helpers ----------------

    /** Returns buffered experience stored on the Pokemon (>=0). */
    public static int getBufferedExp(Pokemon pokemon) {
        for (String a : pokemon.getAspects()) {
            if (a.startsWith(XPBUF_PREFIX)) {
                try {
                    String num = a.substring(XPBUF_PREFIX.length());
                    long parsed = Long.parseLong(num);
                    if (parsed < 0) return 0;
                    return (int) Math.min(parsed, Integer.MAX_VALUE);
                } catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }

    /** Replaces buffered experience with the provided non-negative amount. */
    public static void setBufferedExp(Pokemon pokemon, int amount) {
        int clamped = Math.max(0, amount);
        Set<String> aspects = pokemon.getAspects();
        for (Iterator<String> it = aspects.iterator(); it.hasNext();) {
            String a = it.next();
            if (a.startsWith(XPBUF_PREFIX)) it.remove();
        }
        if (clamped > 0) aspects.add(XPBUF_PREFIX + clamped);
        pokemon.setForcedAspects(aspects);
        pokemon.updateAspects();
    }

    /** Adds to buffered experience and persists. */
    public static void addBufferedExp(Pokemon pokemon, int delta) {
        if (delta <= 0) return;
        int current = getBufferedExp(pokemon);
        long sum = (long) current + (long) delta;
        setBufferedExp(pokemon, (int) Math.min(sum, Integer.MAX_VALUE));
    }

    // ---------------- Pending EV buffer helpers ----------------

    /**
     * Returns a 6-length array of pending EVs in order: HP, ATK, DEF, SPA, SPD, SPE.
     */
    public static int[] getBufferedEvs(Pokemon pokemon) {
        for (String a : pokemon.getAspects()) {
            if (a.startsWith(EVBUF_PREFIX)) {
                try {
                    String csv = a.substring(EVBUF_PREFIX.length());
                    String[] parts = csv.split(",");
                    int[] out = new int[]{0,0,0,0,0,0};
                    for (int i = 0; i < Math.min(parts.length, 6); i++) {
                        int v = Integer.parseInt(parts[i]);
                        out[i] = Math.max(0, v);
                    }
                    return out;
                } catch (Exception ignored) { }
            }
        }
        return new int[]{0,0,0,0,0,0};
    }

    /** Replace pending EVs with provided values. Array must be length 6. */
    public static void setBufferedEvs(Pokemon pokemon, int[] values) {
        if (values == null || values.length != 6) values = new int[]{0,0,0,0,0,0};
        // sanitize
        for (int i = 0; i < 6; i++) {
            if (values[i] < 0) values[i] = 0;
        }
        String csv = values[0] + "," + values[1] + "," + values[2] + "," + values[3] + "," + values[4] + "," + values[5];
        Set<String> aspects = pokemon.getAspects();
        for (Iterator<String> it = aspects.iterator(); it.hasNext();) {
            String a = it.next();
            if (a.startsWith(EVBUF_PREFIX)) it.remove();
        }
        // Only persist if any non-zero exists
        boolean any = false;
        for (int v : values) { if (v > 0) { any = true; break; } }
        if (any) aspects.add(EVBUF_PREFIX + csv);
        pokemon.setForcedAspects(aspects);
        pokemon.updateAspects();
    }

    /** Adds pending EV to a given stat. */
    public static void addBufferedEv(Pokemon pokemon, Stat stat, int delta) {
        if (delta <= 0) return;
        int[] buf = getBufferedEvs(pokemon);
        int idx = statToIndex(stat);
        if (idx < 0) return; // ignore non-permanent stats
        long sum = (long) buf[idx] + (long) delta;
        buf[idx] = (int) Math.min(sum, Integer.MAX_VALUE);
        setBufferedEvs(pokemon, buf);
    }

    /** Clears all pending EV/EXP buffers. */
    public static void clearAllBuffers(Pokemon pokemon) {
        setBufferedExp(pokemon, 0);
        setBufferedEvs(pokemon, new int[]{0,0,0,0,0,0});
    }

    private static int statToIndex(Stat stat) {
        if (!(stat instanceof Stats)) return -1;
        Stats s = (Stats) stat;
        return switch (s) {
            case HP -> 0;
            case ATTACK -> 1;
            case DEFENCE -> 2;
            case SPECIAL_ATTACK -> 3;
            case SPECIAL_DEFENCE -> 4;
            case SPEED -> 5;
            default -> -1;
        };
    }
}
