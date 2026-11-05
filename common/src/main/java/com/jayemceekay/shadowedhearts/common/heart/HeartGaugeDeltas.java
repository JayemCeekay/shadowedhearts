package com.jayemceekay.shadowedhearts.common.heart;

import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.ProgressionEconomyConfig;
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
    private static final Map<String, int[]> COLOSSEUM = new HashMap<>();

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

        // Colosseum's distinct BATTLE/CALL/PARTY/JOY SCENT values. The fourth
        // column remains the existing chamber value: Colosseum had no chamber.
        putColosseum("hardy", 150, 300, 150, 100);
        putColosseum("lonely", 50, 450, 150, 200);
        putColosseum("brave", 200, 225, 150, 75);
        putColosseum("adamant", 150, 225, 150, 75);
        putColosseum("naughty", 150, 225, 200, 75);
        putColosseum("bold", 150, 225, 200, 50);
        putColosseum("docile", 75, 600, 100, 100);
        putColosseum("relaxed", 75, 225, 75, 150);
        putColosseum("impish", 200, 300, 150, 75);
        putColosseum("lax", 100, 225, 150, 150);
        putColosseum("timid", 50, 450, 50, 150);
        putColosseum("hasty", 200, 300, 75, 150);
        putColosseum("serious", 100, 450, 100, 75);
        putColosseum("jolly", 150, 300, 100, 150);
        putColosseum("naive", 100, 300, 150, 100);
        putColosseum("modest", 75, 300, 75, 100);
        putColosseum("mild", 75, 225, 75, 200);
        putColosseum("quiet", 100, 300, 100, 100);
        putColosseum("bashful", 50, 300, 75, 200);
        putColosseum("rash", 75, 300, 100, 150);
        putColosseum("calm", 50, 300, 100, 150);
        putColosseum("gentle", 50, 300, 75, 150);
        putColosseum("sassy", 200, 150, 150, 100);
        putColosseum("careful", 75, 300, 75, 150);
        putColosseum("quirky", 200, 225, 50, 75);
    }

    private static void put(String nature, int battle, int call, int party, int chamber, int scent) {
        TABLE.put(nature.toLowerCase(Locale.ROOT), new int[]{ battle, call, party, chamber, scent });
    }

    private static void putColosseum(String nature, int battle, int call, int party, int scent) {
        COLOSSEUM.put(nature, new int[]{-battle, -call, -party, TABLE.get(nature)[3], -scent});
    }

    /** Returns the JOY SCENT baseline delta for this Pokemon's nature and event. */
    public static int getDelta(Pokemon pokemon, EventType type) {
        Nature n = pokemon.getEffectiveNature();
        if (n == null) return 0;
        ResourceLocation id = n.getName();
        String key = id == null ? null : id.getPath();
        if (key == null) return 0;
        return getDelta(key, type, ProgressionEconomyConfig.INSTANCE.careTable());
    }

    static int getDelta(String nature, EventType type, ProgressionEconomyConfig.CareTable table) {
        int[] arr = (table == ProgressionEconomyConfig.CareTable.COLOSSEUM ? COLOSSEUM : TABLE)
                .get(nature.toLowerCase(Locale.ROOT));
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
