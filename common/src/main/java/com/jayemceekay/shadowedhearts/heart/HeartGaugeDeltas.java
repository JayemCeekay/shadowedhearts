package com.jayemceekay.shadowedhearts.heart;

import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Nature-based Heart Gauge delta table using Pokémon XD values from the design.
 * Negative values reduce the meter (open the heart). All values are JOY SCENT baseline for SCENT event.
 */
public final class HeartGaugeDeltas {
    private HeartGaugeDeltas() {}

    public enum EventType { BATTLE, CALL, PARTY, CHAMBER, SCENT }

    // Map key: nature id path (e.g., "hardy"). Value: deltas by event.
    private static final Map<String, int[]> TABLE = new HashMap<>();

    static {
        // Order: BATTLE, CALL, PARTY, CHAMBER, SCENT
        put("hardy",   -110, -300, -100, -240, -90);
        put("lonely",   -70, -330, -100, -240, -130);
        put("brave",    -130, -270, -90,  -270, -80);
        put("adamant",  -110, -270, -110, -300, -80);
        put("naughty",  -120, -270, -110, -270, -70);
        put("bold",     -110, -270, -90,  -300, -100);
        put("docile",   -100, -360, -80,  -270, -120);
        put("relaxed",  -90,  -270, -110, -360, -100);
        put("impish",   -120, -300, -100, -240, -80);
        put("lax",      -100, -270, -90,  -270, -110);
        put("timid",    -70,  -330, -110, -360, -120);
        put("hasty",    -130, -300, -70,  -240, -100);
        put("serious",  -100, -330, -110, -300, -90);
        put("jolly",    -120, -300, -90,  -240, -90);
        put("naive",    -100, -300, -120, -270, -80);
        put("modest",   -70,  -300, -120, -360, -110);
        put("mild",     -80,  -270, -100, -330, -120);
        put("quiet",    -100, -300, -100, -300, -100);
        put("bashful",  -80,  -300, -90,  -330, -130);
        put("rash",     -90,  -300, -90,  -300, -120);
        put("calm",     -80,  -300, -110, -330, -110);
        put("gentle",   -70,  -300, -130, -360, -100);
        put("sassy",    -130, -240, -100, -270, -70);
        put("careful",  -90,  -300, -100, -330, -110);
        put("quirky",   -130, -270, -80,  -360, -90);
    }

    private static void put(String nature, int battle, int call, int party, int chamber, int scent) {
        TABLE.put(nature.toLowerCase(Locale.ROOT), new int[]{ battle, call, party, chamber, scent });
    }

    /** Returns the JOY SCENT baseline delta for this Pokemon's nature and event. */
    public static int getDelta(Pokemon pokemon, EventType type) {
        Nature n = pokemon.getEffectiveNature();
        if (n == null) return 0;
        ResourceLocation id = n.getName();
        String key = id == null ? null : id.getPath();
        if (key == null) return 0;
        int[] arr = TABLE.get(key.toLowerCase(Locale.ROOT));
        if (arr == null) return 0;
        return switch (type) {
            case BATTLE -> arr[0];
            case CALL -> arr[1];
            case PARTY -> arr[2];
            case CHAMBER -> arr[3];
            case SCENT -> arr[4];
        };
    }
}
