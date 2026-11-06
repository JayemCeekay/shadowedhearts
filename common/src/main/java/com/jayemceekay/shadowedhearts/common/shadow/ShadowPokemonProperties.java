package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeBattleSync;
import com.jayemceekay.shadowedhearts.network.PokemonStateSync;
import com.jayemceekay.shadowedhearts.pokemon.properties.EVBufferProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.ExposureProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.HeartGaugeMaxProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.HeartGaugeProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.ImmunizedProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.ScentCooldownProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.XPBufferProperty;
import net.minecraft.nbt.Tag;

/** Raw Shadow properties and their persistent-data mirrors, without gameplay clamping. */
public final class ShadowPokemonProperties {
    private ShadowPokemonProperties() { }

    static final String METER_PREFIX = "shadowedhearts:heartgauge:"; // 0..speciesMax
    static final String XPBUF_PREFIX = "shadowedhearts:xpbuf:"; // pending exp integer
    static final String EVBUF_PREFIX = "shadowedhearts:evbuf:"; // pending EVs csv hp,atk,def,spa,spd,spe

    static final String NBT_HEART_GAUGE = "shadowedhearts:heartgauge";
    static final String NBT_EXPOSURE = "shadowedhearts:exposure";
    static final String NBT_IMMUNIZED = "shadowedhearts:immunized";
    static final String NBT_XP_BUF = "shadowedhearts:xpbuf";
    static final String NBT_EV_BUF = "shadowedhearts:evbuf";
    static final String NBT_SCENT_COOLDOWN = "shadowedhearts:scent_cooldown";
    static final String NBT_HEART_GAUGE_MAX = "shadowedhearts:heartgaugemax";

    public static void setHeartGaugeProperty(Pokemon pokemon, int value) {
        setHeartGaugeProperty(pokemon, value, true);
    }

    public static void setHeartGaugeProperty(Pokemon pokemon, int value, boolean sync) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof HeartGaugeProperty);
        pokemon.getCustomProperties().add(new HeartGaugeProperty(value));
        pokemon.getPersistentData().putInt(NBT_HEART_GAUGE, value);
        // Keep simulator state current even when Minecraft property notifications are batched.
        HeartGaugeBattleSync.sync(pokemon, null);
        if (sync) PokemonStateSync.syncProperties(pokemon);
    }

    public static int getHeartGaugeValueFromProperty(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof HeartGaugeProperty)
                .map(p -> ((HeartGaugeProperty) p).getValue())
                .findFirst()
                .orElse(-1);
    }

    public static void setExposureProperty(Pokemon pokemon, double value) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof ExposureProperty);
        pokemon.getCustomProperties().add(new ExposureProperty(value));
        pokemon.getPersistentData().putDouble(NBT_EXPOSURE, value);
        PokemonStateSync.syncProperties(pokemon);
    }

    public static double getExposure(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof ExposureProperty)
                .map(p -> ((ExposureProperty) p).getValue())
                .findFirst()
                .orElseGet(() -> pokemon.getPersistentData().getDouble(NBT_EXPOSURE));
    }

    public static void setImmunizedProperty(Pokemon pokemon, boolean value) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof ImmunizedProperty);
        pokemon.getCustomProperties().add(new ImmunizedProperty(value));
        pokemon.getPersistentData().putBoolean(NBT_IMMUNIZED, value);
        PokemonStateSync.syncProperties(pokemon);
    }

    public static boolean isImmunized(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof ImmunizedProperty)
                .map(p -> ((ImmunizedProperty) p).getValue())
                .findFirst()
                .orElseGet(() -> pokemon.getPersistentData().getBoolean(NBT_IMMUNIZED));
    }

    public static void setXPBufferProperty(Pokemon pokemon, int value) {
        setXPBufferProperty(pokemon, value, true);
    }

    public static void setXPBufferProperty(Pokemon pokemon, int value, boolean sync) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof XPBufferProperty);
        pokemon.getCustomProperties().add(new XPBufferProperty(value));
        pokemon.getPersistentData().putInt(NBT_XP_BUF, value);
        if (sync) PokemonStateSync.syncProperties(pokemon);
    }

    public static int getXPBufferFromProperty(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof XPBufferProperty)
                .map(p -> ((XPBufferProperty) p).getValue())
                .findFirst()
                .orElse(-1);
    }

    public static void setEVBufferProperty(Pokemon pokemon, int[] values) {
        setEVBufferProperty(pokemon, values, true);
    }

    public static void setEVBufferProperty(Pokemon pokemon, int[] values, boolean sync) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof EVBufferProperty);
        pokemon.getCustomProperties().add(new EVBufferProperty(values));
        pokemon.getPersistentData().putIntArray(NBT_EV_BUF, values);
        if (sync) PokemonStateSync.syncProperties(pokemon);
    }

    public static int[] getEVBufferFromProperty(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof EVBufferProperty)
                .map(p -> ((EVBufferProperty) p).getValues())
                .findFirst()
                .orElse(null);
    }

    public static void setHeartGaugeMaxProperty(Pokemon pokemon, int value) {
        setHeartGaugeMaxProperty(pokemon, value, true);
    }

    public static void setHeartGaugeMaxProperty(Pokemon pokemon, int value, boolean sync) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof HeartGaugeMaxProperty);
        pokemon.getCustomProperties().add(new HeartGaugeMaxProperty(value));
        pokemon.getPersistentData().putInt(NBT_HEART_GAUGE_MAX, value);
        HeartGaugeBattleSync.sync(pokemon, null);
        if (sync) PokemonStateSync.syncProperties(pokemon);
    }

    public static int getHeartGaugeMaxFromProperty(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof HeartGaugeMaxProperty)
                .map(p -> ((HeartGaugeMaxProperty) p).getValue())
                .findFirst()
                .orElseGet(() -> {
                    if (pokemon.getPersistentData().contains(NBT_HEART_GAUGE_MAX)) {
                        return pokemon.getPersistentData().getInt(NBT_HEART_GAUGE_MAX);
                    }
                    return -1;
                });
    }

    public static void setScentCooldown(Pokemon pokemon, long value) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof ScentCooldownProperty);
        pokemon.getCustomProperties().add(new ScentCooldownProperty(value));
        pokemon.getPersistentData().putLong(NBT_SCENT_COOLDOWN, value);
        PokemonStateSync.syncProperties(pokemon);
    }

    /** Last successful scent-use tick, or -1 when never used. Tick zero is a valid saved use. */
    public static long getScentCooldown(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof ScentCooldownProperty)
                .map(p -> ((ScentCooldownProperty) p).getLastUseTime())
                .findFirst()
                .orElseGet(() -> pokemon.getPersistentData().contains(NBT_SCENT_COOLDOWN, Tag.TAG_ANY_NUMERIC)
                        ? pokemon.getPersistentData().getLong(NBT_SCENT_COOLDOWN) : -1L);
    }

}
