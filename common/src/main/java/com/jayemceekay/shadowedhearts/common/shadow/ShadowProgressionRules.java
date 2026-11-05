package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.pokemon.EVs;

/** Budget calculations shared by native gain handling and saved-buffer recovery. */
public final class ShadowProgressionRules {
    private ShadowProgressionRules() {}

    /** Strict milestone comparison without rounded UI percentages or integer overflow. */
    public static boolean belowThreshold(int meter, int maximum, int threshold) {
        if (threshold >= 101) return true;
        if (threshold <= 0 || maximum <= 0) return false;
        return (long) Math.max(0, Math.min(meter, maximum)) * 100L < (long) maximum * threshold;
    }

    /** EXP is integral in the source games; small fractional awards round down. */
    public static int scaledExperience(int requested, double multiplier) {
        if (requested <= 0 || !Double.isFinite(multiplier) || multiplier <= 0.0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(requested * multiplier));
    }

    /** Retains fractional friendship so repeated one-point walking gains still earn at 80%. */
    public static FriendshipGain friendshipGain(int requested, double multiplier, double remainder, int room) {
        if (requested <= 0 || room <= 0 || !Double.isFinite(multiplier) || multiplier <= 0.0) return new FriendshipGain(0, 0);
        double previous = Double.isFinite(remainder) && remainder >= 0 && remainder < 1 ? remainder : 0;
        double total = requested * multiplier + previous;
        if (!Double.isFinite(total) || total >= room) return new FriendshipGain(Math.max(0, room), 0);
        int whole = (int) Math.floor(total + 1.0e-9);
        return new FriendshipGain(whole, Math.max(0, total - whole));
    }

    public record FriendshipGain(int accepted, double remainder) {}

    public static int acceptedExperience(int requested, int buffered, long capacity) {
        long room = Math.max(0L, Math.min(capacity, Integer.MAX_VALUE)) - Math.max(0, buffered);
        return (int) Math.max(0L, Math.min(Math.max(0, requested), room));
    }

    /**
     * Old buffers did not record earning order. Preserve their previous purification outcome
     * by allocating in HP/ATK/DEF/SPA/SPD/SPE order; valid buffers are unchanged.
     */
    public static int[] normalizeEvs(int[] existing, int[] pending) {
        int[] result = new int[6];
        long total = 0;
        for (int i = 0; i < 6; i++) total += value(existing, i);
        int room = (int) Math.max(0L, EVs.MAX_TOTAL_VALUE - total);
        for (int i = 0; i < 6; i++) {
            int statRoom = Math.max(0, EVs.MAX_STAT_VALUE - value(existing, i));
            result[i] = Math.min(value(pending, i), Math.min(statRoom, room));
            room -= result[i];
        }
        return result;
    }

    public static EvGain addEvs(int[] existing, int[] pending, int index, int requested) {
        int[] result = normalizeEvs(existing, pending);
        if (index < 0 || index >= 6 || requested <= 0) return new EvGain(result, 0);
        long total = 0;
        for (int i = 0; i < 6; i++) total += (long) value(existing, i) + result[i];
        long room = Math.min(EVs.MAX_TOTAL_VALUE - total,
                (long) EVs.MAX_STAT_VALUE - value(existing, index) - result[index]);
        int accepted = (int) Math.max(0L, Math.min(requested, room));
        result[index] += accepted;
        return new EvGain(result, accepted);
    }

    private static int value(int[] values, int index) {
        return values != null && index < values.length ? Math.max(0, values[index]) : 0;
    }

    public record EvGain(int[] buffer, int accepted) {}
}
