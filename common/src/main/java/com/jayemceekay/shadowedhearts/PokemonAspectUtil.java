package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.AspectsUpdatePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.jvm.functions.Function0;
import net.minecraft.util.Mth;

import java.util.Iterator;
import java.util.Set;

/**
 * Utility for manipulating Cobblemon Pokemon aspects and derived values used by this mod.
 */
public final class PokemonAspectUtil {

    private static final String METER_PREFIX = "shadowedhearts:heartgauge:"; // 0..speciesMax
    private static final String XPBUF_PREFIX = "shadowedhearts:xpbuf:"; // pending exp integer
    private static final String EVBUF_PREFIX = "shadowedhearts:evbuf:"; // pending EVs csv hp,atk,def,spa,spd,spe

    /** Add/remove the Shadow aspect on the Pokemon’s stored data. */
    public static void setShadowAspect(Pokemon pokemon, boolean isShadow) {
        // Work on a defensive copy to avoid mutating the live Set while it may be iterated elsewhere
        Set<String> aspectsCopy = new java.util.HashSet<>(pokemon.getAspects());
        if (isShadow) aspectsCopy.add(SHAspects.SHADOW);
        else aspectsCopy.remove(SHAspects.SHADOW);
        pokemon.setForcedAspects(aspectsCopy);
        pokemon.updateAspects();
    }

    public static boolean hasShadowAspect(Pokemon pokemon) {
        if(pokemon != null) {
            return pokemon.getAspects().contains(SHAspects.SHADOW);
        }
        return false;
    }

    /**
     * Ensures that a Shadow Pokémon has all required supporting aspects.
     * If the Pokémon has the shadow aspect, this will:
     *  - Create the heart gauge aspect if missing, initializing to the species default max.
     *  - Create XP and EV buffer aspects if missing, initialized to 0 and 0,0,0,0,0,0 respectively.
     * This method is idempotent and safe to call occasionally (e.g., on send-out or periodic validations).
     */
    public static void ensureRequiredShadowAspects(Pokemon pokemon) {
        if (pokemon == null) return;
        if (!hasShadowAspect(pokemon)) return;

        boolean changed = false;
        // Copy before modifications to avoid concurrent modification of the live set
        Set<String> aspects = new java.util.HashSet<>(pokemon.getAspects());

        // Ensure heart gauge aspect exists
        int percent = getHeartGaugeValue(pokemon);
        if (percent < 0) { // missing aspect
            int max = HeartGaugeConfig.getMax(pokemon);
            // store absolute meter (not percent) as aspect value
            // setHeartGaugeValue will also clean any previous meter aspects
            setHeartGaugeValue(pokemon, max);
            // setHeartGaugeValue already persists + updates
            // Re-fetch and copy aspects after update for subsequent checks
            aspects = new java.util.HashSet<>(pokemon.getAspects());
        }

        // Ensure XP buffer aspect exists (store explicit zero if missing)
        boolean hasXpBuf = false;
        for (String a : aspects) {
            if (a.startsWith(XPBUF_PREFIX)) { hasXpBuf = true; break; }
        }
        if (!hasXpBuf) {
            aspects.add(XPBUF_PREFIX + "0");
            changed = true;
        }

        // Ensure EV buffer aspect exists (store explicit zeros if missing)
        boolean hasEvBuf = false;
        for (String a : aspects) {
            if (a.startsWith(EVBUF_PREFIX)) { hasEvBuf = true; break; }
        }
        if (!hasEvBuf) {
            aspects.add(EVBUF_PREFIX + "0,0,0,0,0,0");
            changed = true;
        }

        if (changed) {
            pokemon.setForcedAspects(aspects);
            pokemon.updateAspects();

            try {
                final Pokemon pk = pokemon;
                Function0<Pokemon> supplier = () -> pk;
                // Send a snapshot of aspects to avoid races
                pokemon.onChange(new AspectsUpdatePacket(supplier, new java.util.HashSet<>(pokemon.getAspects())));
            } catch (Throwable t) {
                // Fallback: at least mark store changed so persistence occurs even if packet construction fails
                pokemon.onChange(null);
            }
        }
    }

    /**
     * Sends an aspects update for the given Pokemon to clients and marks it dirty for persistence.
     * Safe to call even on the server only; will fall back to onChange(null) if packet creation fails.
     */
    public static void syncAspects(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            pokemon.onChange(new AspectsUpdatePacket(supplier, new java.util.HashSet<>(pokemon.getAspects())));
        } catch (Throwable t) {
            pokemon.onChange(null);
        }
    }

    /**
     * Persist heart gauge absolute value [0..speciesMax] into aspects.
     */
    public static void setHeartGauge(Pokemon pokemon, float value) {
        int meter = Math.round(Mth.clamp(value, 0f, HeartGaugeConfig.getMax(pokemon)));
        setHeartGaugeValue(pokemon, meter);
    }

    /**
     * Persist heart gauge absolute meter [0..speciesMax] as a unique aspect "shadowedhearts:heartgauge:NN".
     * Any previous meter aspects are removed to keep only one stored value.
     */
    public static void setHeartGaugeValue(Pokemon pokemon, int meter) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int clamped = Math.max(0, Math.min(max, meter));
        // Work on a copy to avoid modifying a Set that might be iterated by listeners
        Set<String> aspectsCopy = new java.util.HashSet<>(pokemon.getAspects());
        // Remove previous meter aspects
        for (Iterator<String> it = aspectsCopy.iterator(); it.hasNext();) {
            String a = it.next();
            if (a.startsWith(METER_PREFIX)) it.remove();
        }
        // Add the new meter aspect
        aspectsCopy.add(METER_PREFIX + clamped);
        pokemon.setForcedAspects(aspectsCopy);
        pokemon.updateAspects();
    }

    /**
     * Read heart gauge as a 0..100 scalar based on species-specific maximum; fallback based on shadow aspect.
     */
    public static float getHeartGauge(Pokemon pokemon) {
        int percent = getHeartGaugeValue(pokemon);
        if (percent >= 0) return percent / 100f;
        // Fallback: Shadow -> full intensity, Non-shadow -> none
        return hasShadowAspect(pokemon) ? 1f : 0f;
    }

    /**
     * @return the stored heart gauge scaled to 0..100 based on species max, or -1 if no aspect is present.
     */
    public static int getHeartGaugeValue(Pokemon pokemon) {
        int max = HeartGaugeConfig.getMax(pokemon);
        // Iterate over a snapshot to avoid CME if aspects mutate during iteration
        for (String a : new java.util.ArrayList<>(pokemon.getAspects())) {
            if (a.startsWith(METER_PREFIX)) {
                try {
                    String num = a.substring(METER_PREFIX.length());
                    int parsed = Integer.parseInt(num);
                    int clamped = Math.max(0, Math.min(max, parsed));
                    // Scale absolute to 0..100 percent
                    if (max <= 0) return 0;
                    int percent = Math.round((clamped * 100f) / max);
                    return Math.max(0, Math.min(100, percent));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /**
     * Returns the stored heart gauge absolute meter [0..speciesMax]. If absent, returns
     * speciesMax when Shadow, else 0. This is useful for applying absolute deltas.
     */
    public static int getHeartGaugeMeter(Pokemon pokemon) {
        int max = HeartGaugeConfig.getMax(pokemon);
        for (String a : new java.util.ArrayList<>(pokemon.getAspects())) {
            if (a.startsWith(METER_PREFIX)) {
                try {
                    String num = a.substring(METER_PREFIX.length());
                    int parsed = Integer.parseInt(num);
                    return Math.max(0, Math.min(max, parsed));
                } catch (NumberFormatException ignored) { }
            }
        }
        // If no explicit meter stored, infer from shadow aspect.
        return hasShadowAspect(pokemon) ? max : 0;
    }

    // ---------------- Heart Gauge visibility helpers ----------------

    /** Returns the heart gauge percent [0..100]. If absent, returns 100 when Shadow, else 0. */
    public static int getHeartGaugePercent(Pokemon pokemon) {
        int percent = getHeartGaugeValue(pokemon);
        if (percent >= 0) return percent;
        return hasShadowAspect(pokemon) ? 100 : 0;
    }

    /**
     * Returns how many of the 5 bars are still filled (0..5) based on percent.
     * 100% => 5 bars, 81..100 => 5, 61..80 => 4, 41..60 => 3, 21..40 => 2, 1..20 => 1, 0 => 0.
     */
    public static int getBarsRemaining(Pokemon pokemon) {
        int p = Math.max(0, Math.min(100, getHeartGaugePercent(pokemon)));
        if (p == 0) return 0;
        return (int) Math.ceil(p / 20.0);
    }

    /** Nature is hidden while bars > 40% (i.e., 3+ bars remaining). */
    public static boolean isNatureHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /** Level/EXP are hidden while bars > 40% (revealed at < 3 bars). */
    public static boolean isLevelExpHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /** EVs are hidden until the gauge reaches 2 bars or fewer (revealed at 2 bars). */
    public static boolean isEVHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /** IVs are hidden until the gauge reaches 2 bars or fewer (revealed at 2 bars). */
    public static boolean isIVHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /**
     * Determines how many non-Shadow moves may be visibly revealed by the current gauge state.
     * Thresholds (from design doc):
     *  - < 4 bars (<= 80%) => 1st non-Shadow move
     *  - < 2 bars (<= 40%) => 2nd non-Shadow move
     *  - < 1 bar  (<= 20%) => 3rd non-Shadow move
     *  - 0%               => all
     */
    public static int getAllowedVisibleNonShadowMoves(Pokemon pokemon) {
        int percent = Math.max(0, Math.min(100, getHeartGaugePercent(pokemon)));
        if (percent == 0) return 4; // all non-Shadow moves
        if (percent <= 20) return 3;
        if (percent <= 40) return 2;
        if (percent < 80) return 1; // 41..79
        return 0; // 80..100
    }

    // ---------------- Pending EXP buffer helpers ----------------

    /** Returns buffered experience stored on the Pokemon (>=0). */
    public static int getBufferedExp(Pokemon pokemon) {
        for (String a : new java.util.ArrayList<>(pokemon.getAspects())) {
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
        Set<String> aspectsCopy = new java.util.HashSet<>(pokemon.getAspects());
        for (Iterator<String> it = aspectsCopy.iterator(); it.hasNext();) {
            String a = it.next();
            if (a.startsWith(XPBUF_PREFIX)) it.remove();
        }
        if (clamped > 0) aspectsCopy.add(XPBUF_PREFIX + clamped);
        pokemon.setForcedAspects(aspectsCopy);
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
        for (String a : new java.util.ArrayList<>(pokemon.getAspects())) {
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
        Set<String> aspectsCopy = new java.util.HashSet<>(pokemon.getAspects());
        for (Iterator<String> it = aspectsCopy.iterator(); it.hasNext();) {
            String a = it.next();
            if (a.startsWith(EVBUF_PREFIX)) it.remove();
        }
        // Only persist if any non-zero exists
        boolean any = false;
        for (int v : values) { if (v > 0) { any = true; break; } }
        if (any) aspectsCopy.add(EVBUF_PREFIX + csv);
        pokemon.setForcedAspects(aspectsCopy);
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
