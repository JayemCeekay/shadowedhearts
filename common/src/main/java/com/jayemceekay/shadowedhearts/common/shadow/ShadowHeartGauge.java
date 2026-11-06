package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import net.minecraft.util.Mth;

import java.util.ArrayList;

import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.METER_PREFIX;
import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.NBT_HEART_GAUGE;

/** Interprets stored Heart Gauge values for progression, presentation, and purification. */
public final class ShadowHeartGauge {
    private ShadowHeartGauge() { }

    /** Persists a clamped absolute Heart Gauge through its property and NBT mirror. */
    public static void setHeartGauge(Pokemon pokemon, float value) {
        int meter = Math.round(Mth.clamp(value, 0f, HeartGaugeConfig.getMax(pokemon)));
        setHeartGaugeValue(pokemon, meter);
    }

    /** Persists an absolute Heart Gauge meter clamped to the configured maximum. */
    public static void setHeartGaugeValue(Pokemon pokemon, int meter) {
        setHeartGaugeValue(pokemon, meter, true);
    }

    public static void setHeartGaugeValue(Pokemon pokemon, int meter, boolean sync) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int clamped = Math.max(0, Math.min(max, meter));
        ShadowPokemonProperties.setHeartGaugeProperty(pokemon, clamped, sync);
    }

    /** Rounded presentation intensity, not a purification eligibility check. */
    public static float getHeartGauge(Pokemon pokemon) {
        int percent = getHeartGaugeValue(pokemon);
        if (percent >= 0) return percent / 100f;
        // Fallback: Shadow or Shard -> full intensity, else none
        return ShadowAspectUtil.shouldHaveShadowAura(pokemon) ? 1f : 0f;
    }

    /** Returns a rounded percentage of the configured maximum, or -1 if no stored value is present. */
    public static int getHeartGaugeValue(Pokemon pokemon) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int propertyValue = ShadowPokemonProperties.getHeartGaugeValueFromProperty(pokemon);
        if (propertyValue >= 0) {
            int clamped = Math.max(0, Math.min(max, propertyValue));
            if (max <= 0) return 0;
            int percent = Math.round((clamped * 100f) / max);
            return Math.max(0, Math.min(100, percent));
        }

        // Fallback to NBT for initial client-side sync
        if (pokemon.getPersistentData().contains(NBT_HEART_GAUGE)) {
            int nbtValue = pokemon.getPersistentData().getInt(NBT_HEART_GAUGE);
            int clamped = Math.max(0, Math.min(max, nbtValue));
            if (max <= 0) return 0;
            int percent = Math.round((clamped * 100f) / max);
            return Math.max(0, Math.min(100, percent));
        }

        // Iterate over a snapshot to avoid CME if aspects mutate during iteration
        for (String a : new ArrayList<>(pokemon.getAspects())) {
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

    /** Returns the absolute meter; absent data defaults to the maximum for Shadows and zero otherwise. */
    public static int getHeartGaugeMeter(Pokemon pokemon) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int propertyValue = ShadowPokemonProperties.getHeartGaugeValueFromProperty(pokemon);
        if (propertyValue >= 0) {
            return Math.max(0, Math.min(max, propertyValue));
        }

        if (pokemon.getPersistentData().contains(NBT_HEART_GAUGE)) {
            return Math.max(0, Math.min(max, pokemon.getPersistentData().getInt(NBT_HEART_GAUGE)));
        }

        for (String a : new ArrayList<>(pokemon.getAspects())) {
            if (a.startsWith(METER_PREFIX)) {
                try {
                    String num = a.substring(METER_PREFIX.length());
                    int parsed = Integer.parseInt(num);
                    return Math.max(0, Math.min(max, parsed));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // If no explicit meter stored, infer from shadow aspect.
        return ShadowAspectUtil.hasShadowAspect(pokemon) ? max : 0;
    }

    /** A rounded zero percent is not enough: the absolute meter must be empty. */
    public static boolean isReadyForPurification(Pokemon pokemon) {
        return ShadowAspectUtil.hasShadowAspect(pokemon) && getHeartGaugeMeter(pokemon) == 0;
    }

    /** Returns the presentation percentage, defaulting to 100 for Shadows and zero otherwise. */
    public static int getHeartGaugePercent(Pokemon pokemon) {
        int percent = getHeartGaugeValue(pokemon);
        if (percent >= 0) return percent;
        return ShadowAspectUtil.hasShadowAspect(pokemon) ? 100 : 0;
    }

    /** Returns how many of the five 20-percent gauge bars remain filled. */
    public static int getBarsRemaining(Pokemon pokemon) {
        int p = Math.max(0, Math.min(100, getHeartGaugePercent(pokemon)));
        if (p == 0) return 0;
        return (int) Math.ceil(p / 20.0);
    }
}
