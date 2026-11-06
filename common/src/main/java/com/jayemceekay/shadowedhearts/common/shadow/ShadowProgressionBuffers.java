package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.network.PokemonStateSync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.EVBUF_PREFIX;
import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.NBT_EV_BUF;
import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.NBT_XP_BUF;
import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.XPBUF_PREFIX;

/** Stores deferred EXP and EV gains while preserving legacy saves and native stat budgets. */
public final class ShadowProgressionBuffers {
    private ShadowProgressionBuffers() { }

    // ---------------- Pending EXP buffer helpers ----------------

    /**
     * Returns buffered experience stored on the Pokemon (>=0).
     */
    public static int getBufferedExp(Pokemon pokemon) {
        int propertyValue = ShadowPokemonProperties.getXPBufferFromProperty(pokemon);
        if (propertyValue >= 0) return propertyValue;

        if (pokemon.getPersistentData().contains(NBT_XP_BUF)) {
            return Math.max(0, pokemon.getPersistentData().getInt(NBT_XP_BUF));
        }

        for (String a : new ArrayList<>(pokemon.getAspects())) {
            if (a.startsWith(XPBUF_PREFIX)) {
                try {
                    String num = a.substring(XPBUF_PREFIX.length());
                    long parsed = Long.parseLong(num);
                    if (parsed < 0) return 0;
                    return (int) Math.min(parsed, Integer.MAX_VALUE);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    /**
     * Replaces buffered experience with the provided non-negative amount.
     */
    public static void setBufferedExp(Pokemon pokemon, int amount) {
        ShadowPokemonProperties.setXPBufferProperty(pokemon, Math.max(0, amount));
    }

    /**
     * Adds to buffered experience and persists.
     */
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
        return ShadowProgressionRules.normalizeEvs(existingEvs(pokemon), getStoredBufferedEvs(pokemon));
    }

    private static int[] existingEvs(Pokemon pokemon) {
        int[] values = new int[6];
        for (Stats stat : Stats.values()) {
            int index = statToIndex(stat);
            if (index >= 0) values[index] = pokemon.getEvs().getOrDefault(stat);
        }
        return values;
    }

    private static int[] getStoredBufferedEvs(Pokemon pokemon) {
        int[] propertyValues = ShadowPokemonProperties.getEVBufferFromProperty(pokemon);
        if (propertyValues != null) return propertyValues;

        if (pokemon.getPersistentData().contains(NBT_EV_BUF)) {
            return pokemon.getPersistentData().getIntArray(NBT_EV_BUF);
        }

        for (String a : new ArrayList<>(pokemon.getAspects())) {
            if (a.startsWith(EVBUF_PREFIX)) {
                try {
                    String csv = a.substring(EVBUF_PREFIX.length());
                    String[] parts = csv.split(",");
                    int[] out = new int[]{0, 0, 0, 0, 0, 0};
                    for (int i = 0; i < Math.min(parts.length, 6); i++) {
                        int v = Integer.parseInt(parts[i]);
                        out[i] = Math.max(0, v);
                    }
                    return out;
                } catch (Exception ignored) {
                }
            }
        }
        return new int[]{0, 0, 0, 0, 0, 0};
    }

    /**
     * Replace pending EVs with provided values. Array must be length 6.
     */
    public static void setBufferedEvs(Pokemon pokemon, int[] values) {
        ShadowPokemonProperties.setEVBufferProperty(pokemon, ShadowProgressionRules.normalizeEvs(existingEvs(pokemon), values));
    }

    /**
     * Adds pending EV to a given stat.
     */
    public static void addBufferedEv(Pokemon pokemon, Stat stat, int delta) {
        acceptBufferedEv(pokemon, stat, delta);
    }

    /** Returns the actual accepted gain for native item success/consumption and POST events. */
    public static int acceptBufferedEv(Pokemon pokemon, Stat stat, int delta) {
        if (delta <= 0) return 0;
        var gain = ShadowProgressionRules.addEvs(existingEvs(pokemon), getStoredBufferedEvs(pokemon),
                statToIndex(stat), delta);
        if (gain.accepted() > 0) ShadowPokemonProperties.setEVBufferProperty(pokemon, gain.buffer());
        return gain.accepted();
    }

    /**
     * Clears all pending EV/EXP buffers.
     */
    public static void clearAllBuffers(Pokemon pokemon) {
        setBufferedExp(pokemon, 0);
        setBufferedEvs(pokemon, new int[]{0, 0, 0, 0, 0, 0});

        pokemon.getPersistentData().remove(NBT_XP_BUF);
        pokemon.getPersistentData().remove(NBT_EV_BUF);

        Set<String> aspectsCopy = new HashSet<>(pokemon.getAspects());
        boolean changed = false;
        for (Iterator<String> it = aspectsCopy.iterator(); it.hasNext(); ) {
            String a = it.next();
            if (a.startsWith(XPBUF_PREFIX) || a.startsWith(EVBUF_PREFIX)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            pokemon.setForcedAspects(aspectsCopy);
            pokemon.updateAspects();
            PokemonStateSync.syncAspects(pokemon);
        }
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
