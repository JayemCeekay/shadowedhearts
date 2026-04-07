package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.moves.*;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.config.IShadowConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTBridgeHolder;
import com.jayemceekay.shadowedhearts.pokemon.properties.EVBufferProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.HeartGaugeProperty;
import com.jayemceekay.shadowedhearts.pokemon.properties.XPBufferProperty;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * Service façade for synchronizing shadow state between stored Cobblemon Pokemon and live entities.
 */
public final class ShadowService {

    /**
     * Make a Pokemon (and its live entity if present) shadow/non-shadow.
     *
     * @param pokemon stored Pokemon object
     * @param live    currently loaded entity for that Pokemon (nullable)
     * @param shadow  true to set shadow, false to clear
     */
    public static void setShadow(Pokemon pokemon, @Nullable PokemonEntity live, boolean shadow) {
        setShadow(pokemon, live, shadow, true);
    }

    public static void setShadow(Pokemon pokemon, @Nullable PokemonEntity live, boolean shadow, boolean sync) {
        ShadowAspectUtil.setShadowAspect(pokemon, shadow, sync);
        // Ensure required supporting aspects exist when shadowed
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon);
        if (live != null) {
            ShadowPokemonData.set(live, shadow, ShadowAspectUtil.getHeartGauge(pokemon));
        }
        if (sync) {
            // Proactively sync aspect changes to observing players (party/PC/chamber UI) and mark store dirty.
            // This ensures client UIs (e.g., Summary screen) update immediately without requiring a PC swap.
            ShadowAspectUtil.syncAspects(pokemon);
            ShadowAspectUtil.syncBenchedMoves(pokemon);
            ShadowAspectUtil.syncMoveSet(pokemon);
        }
    }

    /**
     * Set heart gauge absolute meter [0..speciesMax from config].
     */
    public static void setHeartGauge(Pokemon pokemon, @Nullable PokemonEntity live, int meter) {
        setHeartGauge(pokemon, live, meter, true);
    }

    public static void setHeartGauge(Pokemon pokemon, @Nullable PokemonEntity live, int meter, boolean sync) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int clamped = Math.max(0, Math.min(max, meter));
        ShadowAspectUtil.setHeartGaugeValue(pokemon, clamped, sync);
        // Ensure required supporting aspects exist when shadowed (no-op if not shadowed)
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon);
        if (live != null)
            ShadowPokemonData.set(live, ShadowPokemonData.isShadow(live), ShadowAspectUtil.getHeartGauge(pokemon));

        applyMoveUnlocks(pokemon);

        if (sync) {
            // Proactively sync aspect changes to observing players and mark store dirty so client UI updates live.
            ShadowAspectUtil.syncAspects(pokemon);
            ShadowAspectUtil.syncBenchedMoves(pokemon);
            ShadowAspectUtil.syncMoveSet(pokemon);
        }
    }

    public static void syncAll(Pokemon pokemon) {
        ShadowAspectUtil.syncAspects(pokemon);
        ShadowAspectUtil.syncProperties(pokemon);
        ShadowAspectUtil.syncBenchedMoves(pokemon);
        ShadowAspectUtil.syncMoveSet(pokemon);
    }

    private static void applyMoveUnlocks(Pokemon pokemon) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return;

        int allowed = ShadowAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        if (allowed < 1) return;

        // Identify shadow moves in the active moveset
        List<Move> shadowMoves = new ArrayList<>();
        for (Move move : pokemon.getMoveSet()) {
            if (move != null && move.getType() == Shadowedhearts.SH_SHADOW_TYPE) {
                shadowMoves.add(move);
            }
        }

        if (shadowMoves.isEmpty()) return;

        // Get level-up move candidates (most recently learned first)
        List<MoveTemplate> levelUpMoves =
                new ArrayList<>(pokemon.getForm().getMoves().getLevelUpMovesUpTo(pokemon.getLevel()));
        Collections.reverse(levelUpMoves);

        // Exclude moves already in the moveset or benched
        Set<String> occupiedNames = new HashSet<>();
        for (Move m : pokemon.getMoveSet()) {
            if (m != null) occupiedNames.add(m.getTemplate().getName());
        }
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            occupiedNames.add(bm.getMoveTemplate().getName());
        }

        List<MoveTemplate> candidates = new ArrayList<>();
        for (MoveTemplate template : levelUpMoves) {
            if (!occupiedNames.contains(template.getName())) {
                candidates.add(template);
            }
        }

        if (candidates.isEmpty()) return;

        int shadowMovesToReplace = 0;
        if (allowed >= 3) shadowMovesToReplace = shadowMoves.size() - 1;

        if (shadowMovesToReplace <= 0) return;

        int toReplace = Math.min(Math.min(shadowMoves.size(), candidates.size()), shadowMovesToReplace);

        // Record which shadow move names are being replaced
        Set<String> replacedShadowNames = new HashSet<>();
        for (int i = 0; i < toReplace; i++) {
            replacedShadowNames.add(shadowMoves.get(i).getTemplate().getName());
        }

        // Build the desired MoveSet: swap replaced shadow slots for candidates, preserve others
        MoveSet newMoveSet = new MoveSet();
        List<Move> movesWithNulls = pokemon.getMoveSet().getMovesWithNulls();
        int candidateIdx = 0;
        for (int i = 0; i < 4; i++) {
            Move m = movesWithNulls.get(i);
            if (m != null && replacedShadowNames.contains(m.getTemplate().getName()) && candidateIdx < candidates.size()) {
                newMoveSet.setMove(i, candidates.get(candidateIdx++).create());
            } else {
                newMoveSet.setMove(i, m != null ? m.copy() : null);
            }
        }

        // Build the desired BenchedMoves: remove the replaced shadow moves
        BenchedMoves newBenchedMoves = new BenchedMoves();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            if (!replacedShadowNames.contains(bm.getMoveTemplate().getName())) {
                newBenchedMoves.add(bm);
            }
        }

        // Apply both atomically — each copyFrom does a single update() at the end
        pokemon.getMoveSet().copyFrom(newMoveSet);
        pokemon.getBenchedMoves().copyFrom(newBenchedMoves);
    }

    /**
     * Convenience: fully purified (0) and not shadow.
     */
    public static void fullyPurify(Pokemon pokemon, @Nullable PokemonEntity live) {
        // 1. Restore all moves (replace all shadow moves)
        restoreAllMoves(pokemon);

        // 2. Remove shadow aspects
        Set<String> aspects = new HashSet<>(pokemon.getAspects());
        aspects.remove(SHAspects.SHADOW);
        aspects.remove(SHAspects.HYPER_MODE);
        aspects.remove(SHAspects.REVERSE_MODE);
        pokemon.setForcedAspects(aspects);
        pokemon.updateAspects();

        // 3. Apply buffered EXP/EVs and clear gauge/buffers
        int bufferedExp = ShadowAspectUtil.getBufferedExp(pokemon);
        if (bufferedExp > 0) {
            IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
            if (config.rctIntegrationEnabled() && config.limitExpToRCTCap()) {
                ServerPlayer player = pokemon.getOwnerPlayer();
                if (player != null) {
                    int levelCap = RCTBridgeHolder.INSTANCE.getLevelCap(player);
                    if (pokemon.getLevel() >= levelCap) {
                        bufferedExp = 0;
                    } else {
                        int xpToCap = pokemon.getExperienceToLevel(levelCap + 1) - 1;
                        if (xpToCap > 0) {
                            bufferedExp = Math.min(bufferedExp, xpToCap);
                        } else {
                            bufferedExp = 0;
                        }
                    }
                }
            }

            if (bufferedExp > 0) {
                pokemon.addExperience(new SidemodExperienceSource("shadowedhearts"), bufferedExp);
            }
        }

        int[] bufferedEvs = ShadowAspectUtil.getBufferedEvs(pokemon);
        Stats[] stats = {
                Stats.HP,
                Stats.ATTACK,
                Stats.DEFENCE,
                Stats.SPECIAL_ATTACK,
                Stats.SPECIAL_DEFENCE,
                Stats.SPEED
        };
        for (int i = 0; i < 6; i++) {
            if (bufferedEvs[i] > 0) {
                pokemon.getEvs().add(stats[i], bufferedEvs[i]);
            }
        }

        // 4. Remove custom properties and NBT data
        pokemon.getCustomProperties().removeIf(p ->
                p instanceof HeartGaugeProperty ||
                        p instanceof XPBufferProperty ||
                        p instanceof EVBufferProperty
        );
        pokemon.getPersistentData().remove("shadowedhearts:heartgauge");
        pokemon.getPersistentData().remove("shadowedhearts:xpbuf");
        pokemon.getPersistentData().remove("shadowedhearts:evbuf");

        if (live != null) {
            ShadowPokemonData.set(live, false, 0);
        }

        syncAll(pokemon);
    }

    private static void restoreAllMoves(Pokemon pokemon) {
        // Get level-up move candidates (most recently learned first)
        List<MoveTemplate> levelUpMoves =
                new ArrayList<>(pokemon.getForm().getMoves().getLevelUpMovesUpTo(pokemon.getLevel()));
        Collections.reverse(levelUpMoves);

        // Exclude non-shadow moves already in the moveset or benched (shadow ones are being replaced)
        Set<String> occupiedNames = new HashSet<>();
        for (Move m : pokemon.getMoveSet()) {
            if (m != null && m.getType() != Shadowedhearts.SH_SHADOW_TYPE) {
                occupiedNames.add(m.getTemplate().getName());
            }
        }
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            if (bm.getMoveTemplate().getElementalType() != Shadowedhearts.SH_SHADOW_TYPE) {
                occupiedNames.add(bm.getMoveTemplate().getName());
            }
        }

        List<MoveTemplate> candidates = new ArrayList<>();
        for (MoveTemplate template : levelUpMoves) {
            if (!occupiedNames.contains(template.getName())) {
                candidates.add(template);
            }
        }

        // Build the desired MoveSet: replace shadow slots with candidates, preserve others
        MoveSet newMoveSet = new MoveSet();
        List<Move> movesWithNulls = pokemon.getMoveSet().getMovesWithNulls();
        int candidateIdx = 0;
        for (int i = 0; i < 4; i++) {
            Move m = movesWithNulls.get(i);
            if (m != null && m.getType() == Shadowedhearts.SH_SHADOW_TYPE && candidateIdx < candidates.size()) {
                newMoveSet.setMove(i, candidates.get(candidateIdx++).create());
            } else {
                newMoveSet.setMove(i, m != null ? m.copy() : null);
            }
        }

        // Build the desired BenchedMoves: exclude all shadow-type moves
        BenchedMoves newBenchedMoves = new BenchedMoves();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            if (bm.getMoveTemplate().getElementalType() != Shadowedhearts.SH_SHADOW_TYPE) {
                newBenchedMoves.add(bm);
            }
        }

        // Apply both atomically — each copyFrom does a single update() at the end
        pokemon.getMoveSet().copyFrom(newMoveSet);
        pokemon.getBenchedMoves().copyFrom(newBenchedMoves);
    }

    /**
     * Convenience: corrupted and shadow with specific heart gauge value.
     */
    public static void corrupt(Pokemon pokemon, @Nullable PokemonEntity live, int value) {
        corrupt(pokemon, live, value, true);
    }

    public static void corrupt(Pokemon pokemon, @Nullable PokemonEntity live, int value, boolean sync) {
        setShadow(pokemon, live, true, sync);
        setHeartGauge(pokemon, live, value, sync);
    }

    /**
     * Convenience: fully corrupted (speciesMax) and shadow.
     */
    public static void fullyCorrupt(Pokemon pokemon, @Nullable PokemonEntity live) {
        fullyCorrupt(pokemon, live, true);
    }

    public static void fullyCorrupt(Pokemon pokemon, @Nullable PokemonEntity live, boolean sync) {
        corrupt(pokemon, live, HeartGaugeConfig.getMax(pokemon), sync);
    }
}
