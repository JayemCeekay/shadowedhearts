package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.capture.DarkBallCaptureRules;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.PokemonStateSync;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.EVBUF_PREFIX;
import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.METER_PREFIX;
import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.NBT_HEART_GAUGE;
import static com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties.XPBUF_PREFIX;

/** Migrates and initializes the persistent state required by Shadow Pokemon. */
public final class ShadowStateInitialization {
    private ShadowStateInitialization() { }

    public static void ensureRequiredShadowAspects(Pokemon pokemon) {
        ensureRequiredShadowAspects(pokemon, true);
    }

    /** Migrates legacy aspects and initializes missing gauge and progression properties. */
    public static void ensureRequiredShadowAspects(Pokemon pokemon, boolean sync) {
        if (pokemon == null) return;
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return;

        boolean changed = false;
        Set<String> aspects = null;

        // Migration: aspects -> properties
        for (String a : pokemon.getAspects()) {
            if (a.startsWith(METER_PREFIX) || a.startsWith(XPBUF_PREFIX) || a.startsWith(EVBUF_PREFIX)) {
                if (aspects == null) aspects = new HashSet<>(pokemon.getAspects());
                break;
            }
        }

        if (aspects != null) {
            int heartGauge = -1;
            int xpBuf = -1;
            int[] evBuf = null;

            for (Iterator<String> it = aspects.iterator(); it.hasNext(); ) {
                String a = it.next();
                if (a.startsWith(METER_PREFIX)) {
                    try {
                        heartGauge = Integer.parseInt(a.substring(METER_PREFIX.length()));
                        it.remove();
                        changed = true;
                    } catch (NumberFormatException ignored) {
                    }
                } else if (a.startsWith(XPBUF_PREFIX)) {
                    try {
                        xpBuf = Integer.parseInt(a.substring(XPBUF_PREFIX.length()));
                        it.remove();
                        changed = true;
                    } catch (NumberFormatException ignored) {
                    }
                } else if (a.startsWith(EVBUF_PREFIX)) {
                    try {
                        String csv = a.substring(EVBUF_PREFIX.length());
                        String[] parts = csv.split(",");
                        evBuf = new int[]{0, 0, 0, 0, 0, 0};
                        for (int i = 0; i < Math.min(parts.length, 6); i++) {
                            evBuf[i] = Integer.parseInt(parts[i]);
                        }
                        it.remove();
                        changed = true;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (heartGauge != -1) ShadowPokemonProperties.setHeartGaugeProperty(pokemon, heartGauge, false);
            if (xpBuf != -1) ShadowPokemonProperties.setXPBufferProperty(pokemon, xpBuf, false);
            if (evBuf != null) ShadowPokemonProperties.setEVBufferProperty(pokemon, evBuf, false);
        }

        ensureShadowMaxIVs(pokemon);

        // Restore or initialize missing gauge and buffer properties.
        changed |= initializeMissingHeartGauge(pokemon);
        if (ShadowPokemonProperties.getXPBufferFromProperty(pokemon) == -1) {
            ShadowPokemonProperties.setXPBufferProperty(pokemon, 0, false);
            changed = true;
        }
        if (ShadowPokemonProperties.getEVBufferFromProperty(pokemon) == null) {
            ShadowPokemonProperties.setEVBufferProperty(pokemon, new int[]{0, 0, 0, 0, 0, 0}, false);
            changed = true;
        }

        if (changed) {
            if (aspects != null) {
                pokemon.setForcedAspects(aspects);
                pokemon.updateAspects();
            }
            if (sync) {
                PokemonStateSync.syncAspects(pokemon);
                PokemonStateSync.syncProperties(pokemon);
            }
        }

    }

    /** Restore saved progress verbatim; create a default only after authored species/max are final. */
    static boolean initializeMissingHeartGauge(Pokemon pokemon) {
        if (ShadowPokemonProperties.getHeartGaugeValueFromProperty(pokemon) != -1) return false;
        if (pokemon.getPersistentData().contains(NBT_HEART_GAUGE, Tag.TAG_ANY_NUMERIC)
                && pokemon.getPersistentData().getInt(NBT_HEART_GAUGE) >= 0) {
            ShadowPokemonProperties.setHeartGaugeProperty(pokemon, pokemon.getPersistentData().getInt(NBT_HEART_GAUGE), false);
        } else {
            if (ShadowHeartGaugeInitialization.deferDefault(pokemon)) return false;
            ShadowPokemonProperties.setHeartGaugeProperty(pokemon, HeartGaugeConfig.getMax(pokemon), false);
        }
        return true;
    }

    private static void ensureShadowMaxIVs(Pokemon pokemon) {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        String mode = config.shadowIVMode();

        int perfectIVs = 0;
        if (mode.equalsIgnoreCase("FIXED")) {
            perfectIVs = config.shadowFixedPerfectIVs();
        } else if (mode.equalsIgnoreCase("SCALED")) {
            // Dark Ball's larger gauge is a purification cost, not an extra IV reward.
            int maxHeartGauge = pokemon.getPersistentData().getBoolean(DarkBallCaptureRules.NBT_CORRUPTED)
                    ? HeartGaugeConfig.getMax(pokemon.getSpecies()) : HeartGaugeConfig.getMax(pokemon);
            float normalized = Math.min(1.0f, (float) maxHeartGauge / (float) HeartGaugeConfig.getGlobalMax(pokemon));
            perfectIVs = Math.round((float) Mth.lerp(normalized, 1.0, config.shadowMaxPerfectIVs()));
        }

        if (perfectIVs > 0) {
            var ivs = pokemon.getIvs();
            List<Stats> stats = new ArrayList<>(List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED));

            int currentMaxIVs = 0;
            for (Stats stat : stats) {
                if (ivs.get(stat) >= 31) {
                    currentMaxIVs++;
                }
            }

            if (currentMaxIVs < perfectIVs) {
                Random rng = new Random(pokemon.getUuid().getLeastSignificantBits());
                Collections.shuffle(stats, rng);
                for (Stats stat : stats) {
                    if (ivs.get(stat) < 31) {
                        ivs.set(stat, 31);
                        currentMaxIVs++;
                    }
                    if (currentMaxIVs >= perfectIVs) break;
                }
            }
        }
    }
}
