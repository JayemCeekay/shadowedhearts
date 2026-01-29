package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.api.moves.*;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.AspectsUpdatePacket;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.BenchedMovesUpdatePacket;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.MoveSetUpdatePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ModConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.network.PokemonPropertyUpdatePacket;
import com.jayemceekay.shadowedhearts.pokemon.properties.*;
import kotlin.jvm.functions.Function0;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * Utility for manipulating Cobblemon Pokemon aspects and derived values used by this mod.
 */
public final class PokemonAspectUtil {

    private static final String METER_PREFIX = "shadowedhearts:heartgauge:"; // 0..speciesMax
    private static final String XPBUF_PREFIX = "shadowedhearts:xpbuf:"; // pending exp integer
    private static final String EVBUF_PREFIX = "shadowedhearts:evbuf:"; // pending EVs csv hp,atk,def,spa,spd,spe

    private static final String NBT_HEART_GAUGE = "shadowedhearts:heartgauge";
    private static final String NBT_EXPOSURE = "shadowedhearts:exposure";
    private static final String NBT_IMMUNIZED = "shadowedhearts:immunized";
    private static final String NBT_XP_BUF = "shadowedhearts:xpbuf";
    private static final String NBT_EV_BUF = "shadowedhearts:evbuf";
    private static final String NBT_SCENT_COOLDOWN = "shadowedhearts:scent_cooldown";

    /**
     * Add/remove the Shadow aspect on the Pokemon’s stored data.
     */
    public static void setShadowAspect(Pokemon pokemon, boolean isShadow) {
        setShadowAspect(pokemon, isShadow, true);
    }

    public static void setShadowAspect(Pokemon pokemon, boolean isShadow, boolean sync) {
        // Work on a defensive copy to avoid mutating the live Set while it may be iterated elsewhere
        Set<String> aspectsCopy = new HashSet<>(pokemon.getAspects());
        if (isShadow) {
            aspectsCopy.add(SHAspects.SHADOW);
            replaceMovesWithShadowMoves(pokemon);
        } else {
            aspectsCopy.remove(SHAspects.SHADOW);
        }
        pokemon.setForcedAspects(aspectsCopy);
        pokemon.updateAspects();
        if (sync) {
            syncAspects(pokemon);
            syncBenchedMoves(pokemon);
            syncMoveSet(pokemon);
        }
    }

    private static void replaceMovesWithShadowMoves(Pokemon pokemon) {
        long salt = pokemon.getUuid().getLeastSignificantBits();
        Random rng = new Random(salt);
        int replaceCount = ModConfig.resolveReplaceCount(rng);
        if (replaceCount <= 0) return;

        MoveSet moveSet = pokemon.getMoveSet();
        for (int i = 0; i < Math.min(replaceCount, 4); i++) {
            Move oldMove = moveSet.get(i);
            if (oldMove == null) continue;

            // Don't replace if it's already a shadow move
            if (ShadowGate.isShadowMoveId(oldMove.getTemplate().getName()))
                continue;

            // Move old move to bench
            BenchedMove benched = new BenchedMove(oldMove.getTemplate(), oldMove.getRaisedPpStages());
            pokemon.getBenchedMoves().add(benched);

            // Replace with shadow move
            String shadowMoveName;
            IShadowConfig cfg = ShadowedHeartsConfigs.getInstance().getShadowConfig();
            if (cfg.shadowMovesOnlyShadowRush()) {
                if (i == 0) {
                    shadowMoveName = "shadowrush";
                } else {
                    continue; // Only allow shadowrush in the first slot if onlyShadowRush is enabled
                }
            } else {
                shadowMoveName = getRandomShadowMove(pokemon, i);
            }

            var template = Moves.getByName(shadowMoveName);
            if (template != null) {
                moveSet.setMove(i, template.create(template.getPp(), 0));
            }
        }


    }

    private static String getRandomShadowMove(Pokemon pokemon, int seed) {
        String[] shadowMoves = {
                "shadowblast", "shadowblitz", "shadowbolt", "shadowbreak", "shadowchill",
                "shadowdown", "shadowend", "shadowfire", "shadowhalf", "shadowhold",
                "shadowmist", "shadowpanic", "shadowrave", "shadowrush", "shadowshed",
                "shadowsky", "shadowstorm", "shadowwave"
        };
        long salt = pokemon.getUuid().getLeastSignificantBits() ^ seed;
        Random rng = new Random(salt);
        return shadowMoves[rng.nextInt(shadowMoves.length)];
    }

    public static boolean hasShadowAspect(Pokemon pokemon) {
        if (pokemon != null) {
            return pokemon.getAspects().contains(SHAspects.SHADOW);
        }
        return false;
    }

    /**
     * Ensures that a Shadow Pokémon has all required supporting aspects.
     * If the Pokémon has the shadow aspect, this will:
     * - Create the heart gauge aspect if missing, initializing to the species default max.
     * - Create XP and EV buffer aspects if missing, initialized to 0 and 0,0,0,0,0,0 respectively.
     * This method is idempotent and safe to call occasionally (e.g., on send-out or periodic validations).
     */
    public static void ensureRequiredShadowAspects(Pokemon pokemon) {
        if (pokemon == null) return;
        if (!hasShadowAspect(pokemon)) return;

        boolean changed = false;
        // Copy before modifications to avoid concurrent modification of the live set
        Set<String> aspects = new HashSet<>(pokemon.getAspects());

        // Migration: aspects -> properties
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
                } catch (NumberFormatException ignored) {}
            } else if (a.startsWith(XPBUF_PREFIX)) {
                try {
                    xpBuf = Integer.parseInt(a.substring(XPBUF_PREFIX.length()));
                    it.remove();
                    changed = true;
                } catch (NumberFormatException ignored) {}
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
                } catch (NumberFormatException ignored) {}
            }
        }

        if (heartGauge != -1) setHeartGaugeProperty(pokemon, heartGauge);
        if (xpBuf != -1) setXPBufferProperty(pokemon, xpBuf);
        if (evBuf != null) setEVBufferProperty(pokemon, evBuf);
        
        ensureShadowMaxIVs(pokemon);

        // Ensure defaults if missing both in properties and aspects
        if (getHeartGaugeValueFromProperty(pokemon) == -1) {
            setHeartGaugeProperty(pokemon, HeartGaugeConfig.getMax(pokemon));
        }
        if (getXPBufferFromProperty(pokemon) == -1) {
            setXPBufferProperty(pokemon, 0);
        }
        if (getEVBufferFromProperty(pokemon) == null) {
            setEVBufferProperty(pokemon, new int[]{0, 0, 0, 0, 0, 0});
        }

        if (changed) {
            pokemon.setForcedAspects(aspects);
            pokemon.updateAspects();
            syncAspects(pokemon);
            syncProperties(pokemon);
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
            pokemon.onChange(new AspectsUpdatePacket(supplier, new HashSet<>(pokemon.getAspects())));
        } catch (Throwable t) {
            // Fallback
        } finally {
            pokemon.onChange(null);
        }
    }

    public static void syncProperties(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            pokemon.onChange(new PokemonPropertyUpdatePacket(supplier, new ArrayList<>(pokemon.getCustomProperties())));
        } catch (Throwable t) {
            // Fallback
        } finally {
            pokemon.onChange(null);
        }
    }

    public static void syncBenchedMoves(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            BenchedMoves snapshot = new BenchedMoves();
            snapshot.doWithoutEmitting(() -> {
                for (BenchedMove bm : pk.getBenchedMoves()) {
                    snapshot.add(bm);
                }
                return null;
            });
            pokemon.onChange(new BenchedMovesUpdatePacket(supplier, snapshot));
        } catch (Throwable t) {
            // Fallback
        } finally {
            pokemon.onChange(null);
        }
    }

    public static void syncMoveSet(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            // Create a snapshot of the MoveSet to avoid race conditions during Netty encoding.
            MoveSet snapshot = new MoveSet();
            snapshot.doWithoutEmitting(() -> {
                for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
                    Move move = pk.getMoveSet().get(i);
                    if (move != null) {
                        snapshot.setMove(i, move);
                    }
                }
                return null;
            });
            pokemon.onChange(new MoveSetUpdatePacket(supplier, snapshot));
        } catch (Throwable t) {
            // Fallback
        } finally {
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
        setHeartGaugeValue(pokemon, meter, true);
    }

    public static void setHeartGaugeValue(Pokemon pokemon, int meter, boolean sync) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int clamped = Math.max(0, Math.min(max, meter));
        setHeartGaugeProperty(pokemon, clamped, sync);
    }

    public static void setHeartGaugeProperty(Pokemon pokemon, int value) {
        setHeartGaugeProperty(pokemon, value, true);
    }

    public static void setHeartGaugeProperty(Pokemon pokemon, int value, boolean sync) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof HeartGaugeProperty);
        pokemon.getCustomProperties().add(new HeartGaugeProperty(value));
        pokemon.getPersistentData().putInt(NBT_HEART_GAUGE, value);
        if (sync) syncProperties(pokemon);
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
        syncProperties(pokemon);
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
        syncProperties(pokemon);
    }

    public static boolean isImmunized(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof ImmunizedProperty)
                .map(p -> ((ImmunizedProperty) p).getValue())
                .findFirst()
                .orElseGet(() -> pokemon.getPersistentData().getBoolean(NBT_IMMUNIZED));
    }

    public static void setXPBufferProperty(Pokemon pokemon, int value) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof XPBufferProperty);
        pokemon.getCustomProperties().add(new XPBufferProperty(value));
        pokemon.getPersistentData().putInt(NBT_XP_BUF, value);
        syncProperties(pokemon);
    }

    public static int getXPBufferFromProperty(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof XPBufferProperty)
                .map(p -> ((XPBufferProperty) p).getValue())
                .findFirst()
                .orElse(-1);
    }

    public static void setEVBufferProperty(Pokemon pokemon, int[] values) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof EVBufferProperty);
        pokemon.getCustomProperties().add(new EVBufferProperty(values));
        pokemon.getPersistentData().putIntArray(NBT_EV_BUF, values);
        syncProperties(pokemon);
    }

    public static int[] getEVBufferFromProperty(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof EVBufferProperty)
                .map(p -> ((EVBufferProperty) p).getValues())
                .findFirst()
                .orElse(null);
    }

    public static void setScentCooldown(Pokemon pokemon, long value) {
        pokemon.getCustomProperties().removeIf(p -> p instanceof ScentCooldownProperty);
        pokemon.getCustomProperties().add(new ScentCooldownProperty(value));
        pokemon.getPersistentData().putLong(NBT_SCENT_COOLDOWN, value);
        syncProperties(pokemon);
    }

    public static long getScentCooldown(Pokemon pokemon) {
        return pokemon.getCustomProperties().stream()
                .filter(p -> p instanceof ScentCooldownProperty)
                .map(p -> ((ScentCooldownProperty) p).getLastUseTime())
                .findFirst()
                .orElseGet(() -> pokemon.getPersistentData().getLong(NBT_SCENT_COOLDOWN));
    }

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
        int propertyValue = getHeartGaugeValueFromProperty(pokemon);
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

    /**
     * Returns the stored heart gauge absolute meter [0..speciesMax]. If absent, returns
     * speciesMax when Shadow, else 0. This is useful for applying absolute deltas.
     */
    public static int getHeartGaugeMeter(Pokemon pokemon) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int propertyValue = getHeartGaugeValueFromProperty(pokemon);
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
        return hasShadowAspect(pokemon) ? max : 0;
    }

    // ---------------- Heart Gauge visibility helpers ----------------

    /**
     * Returns the heart gauge percent [0..100]. If absent, returns 100 when Shadow, else 0.
     */
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

    /**
     * Nature is hidden while bars > 40% (i.e., 3+ bars remaining).
     */
    public static boolean isNatureHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /**
     * Level/EXP are hidden while bars > 40% (revealed at < 3 bars).
     */
    public static boolean isLevelExpHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /**
     * EVs are hidden until the gauge reaches 2 bars or fewer (revealed at 2 bars).
     */
    public static boolean isEVHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /**
     * IVs are hidden until the gauge reaches 2 bars or fewer (revealed at 2 bars).
     */
    public static boolean isIVHiddenByGauge(Pokemon pokemon) {
        return getBarsRemaining(pokemon) > 2;
    }

    /**
     * Determines how many non-Shadow moves may be visibly revealed by the current gauge state.
     * Thresholds (from design doc):
     * - < 4 bars (<= 80%) => 1st non-Shadow move
     * - < 2 bars (<= 40%) => 2nd non-Shadow move
     * - < 1 bar  (<= 20%) => 3rd non-Shadow move
     * - 0%               => all
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

    /**
     * Returns buffered experience stored on the Pokemon (>=0).
     */
    public static int getBufferedExp(Pokemon pokemon) {
        int propertyValue = getXPBufferFromProperty(pokemon);
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
        setXPBufferProperty(pokemon, Math.max(0, amount));
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
        int[] propertyValues = getEVBufferFromProperty(pokemon);
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
        if (values == null || values.length != 6)
            values = new int[]{0, 0, 0, 0, 0, 0};
        // sanitize
        for (int i = 0; i < 6; i++) {
            if (values[i] < 0) values[i] = 0;
        }
        setEVBufferProperty(pokemon, values);
    }

    /**
     * Adds pending EV to a given stat.
     */
    public static void addBufferedEv(Pokemon pokemon, Stat stat, int delta) {
        if (delta <= 0) return;
        int[] buf = getBufferedEvs(pokemon);
        int idx = statToIndex(stat);
        if (idx < 0) return; // ignore non-permanent stats
        long sum = (long) buf[idx] + (long) delta;
        buf[idx] = (int) Math.min(sum, Integer.MAX_VALUE);
        setBufferedEvs(pokemon, buf);
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
            syncAspects(pokemon);
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

    private static void ensureShadowMaxIVs(Pokemon pokemon) {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        String mode = config.shadowIVMode();

        int perfectIVs = 0;
        if (mode.equalsIgnoreCase("FIXED")) {
            perfectIVs = config.shadowFixedPerfectIVs();
        } else if (mode.equalsIgnoreCase("SCALED")) {
            int maxHeartGauge = HeartGaugeConfig.getMax(pokemon);
            float normalized = Math.min(1.0f, (float) maxHeartGauge / (float) HeartGaugeConfig.getGlobalMax());
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
