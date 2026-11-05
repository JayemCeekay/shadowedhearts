package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.moves.*;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.HeartGaugeUnlockRules;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionSettings;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
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
        ShadowAspectUtil.setShadowAspect(pokemon, shadow, false);
        // Ensure required supporting aspects exist when shadowed
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon, false);
        if (live != null) {
            ShadowPokemonData.set(live, shadow, ShadowAspectUtil.getHeartGauge(pokemon));
        }
        if (sync) {
            // Proactively sync aspect changes to observing players (party/PC/chamber UI) and mark store dirty.
            // This ensures client UIs (e.g., Summary screen) update immediately without requiring a PC swap.
            ShadowAspectUtil.syncAspects(pokemon);
            ShadowAspectUtil.syncProperties(pokemon);
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
        ShadowAspectUtil.setHeartGaugeValue(pokemon, clamped, false);
        // Ensure required supporting aspects exist when shadowed (no-op if not shadowed)
        ShadowAspectUtil.ensureRequiredShadowAspects(pokemon, false);
        if (live != null)
            ShadowPokemonData.set(live, ShadowPokemonData.isShadow(live), ShadowAspectUtil.getHeartGauge(pokemon));

        applyMoveUnlocks(pokemon);

        if (sync) {
            // Proactively sync aspect changes to observing players and mark store dirty so client UI updates live.
            ShadowAspectUtil.syncAspects(pokemon);
            ShadowAspectUtil.syncProperties(pokemon);
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
        if (com.jayemceekay.shadowedhearts.config.MoveLifecycleConfig.INSTANCE.retainShadowMovesUntilPurification()) return;

        var settings = ShadowRestrictionSettings.serverCurrent();
        // Turning off battle/visibility locks must not itself replace assigned Shadow moves.
        // Automatic slot restoration still follows the configured gauge progression.
        int allowed = HeartGaugeUnlockRules.allowedNonShadowMoves(ShadowAspectUtil.getHeartGaugeMeter(pokemon), HeartGaugeConfig.getMax(pokemon),
                true, settings.firstMoveUnlockPercent(), settings.secondMoveUnlockPercent(), settings.thirdMoveUnlockPercent(),
                settings.exactThresholds());
        if (allowed < 1) return;

        // Identify shadow moves in the active moveset
        List<Move> shadowMoves = new ArrayList<>();
        for (Move move : pokemon.getMoveSet()) {
            if (ShadowMoveUtil.isShadowMove(move)) {
                shadowMoves.add(move);
            }
        }

        if (shadowMoves.isEmpty()) return;

        List<Move> candidates = ordinaryMoveReplacementCandidates(pokemon);

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
        Set<String> promotedNames = new HashSet<>();
        int candidateIdx = 0;
        for (int i = 0; i < 4; i++) {
            Move m = movesWithNulls.get(i);
            if (m != null && replacedShadowNames.contains(m.getTemplate().getName()) && candidateIdx < toReplace) {
                Move replacement = candidates.get(candidateIdx++);
                newMoveSet.setMove(i, replacement);
                promotedNames.add(replacement.getTemplate().getName());
            } else {
                newMoveSet.setMove(i, m != null ? m.copy() : null);
            }
        }

        // Remove replaced Shadow moves and any ordinary moves promoted from the bench.
        BenchedMoves newBenchedMoves = new BenchedMoves();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            if (!replacedShadowNames.contains(bm.getMoveTemplate().getName())
                    && !promotedNames.contains(bm.getMoveTemplate().getName())) {
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
        fullyPurify(pokemon, live, null);
    }

    /**
     * Purify with an owner already authorized by the calling interaction. Custom stores
     * such as the Purification Chamber cannot resolve their owner through Cobblemon.
     * Existing callers may omit this context and use the Pokemon's native owner instead.
     */
    public static void fullyPurify(Pokemon pokemon, @Nullable PokemonEntity live,
                                   @Nullable ServerPlayer authorizedOwner) {
        boolean wasShadow = ShadowAspectUtil.hasShadowAspect(pokemon);
        com.jayemceekay.shadowedhearts.common.event.player.PokemonPurificationWalking.clear(pokemon);
        // 1. Restore all moves (replace all shadow moves)
        restoreAllMoves(pokemon);

        // 2. Remove shadow aspects
        Set<String> aspects = new HashSet<>(pokemon.getAspects());
        aspects.remove(SHAspects.SHADOW);
        aspects.remove(SHAspects.HYPER_MODE);
        aspects.remove(SHAspects.REVERSE_MODE);
        pokemon.setForcedAspects(aspects);
        pokemon.updateAspects();

        MoveLifecycle.onPurified(pokemon, wasShadow);
        ShadowProgressionManager.releaseFriendship(pokemon, wasShadow);

        // 3. Apply buffered EXP/EVs and clear gauge/buffers
        int bufferedExp = ShadowAspectUtil.getBufferedExp(pokemon);
        if (bufferedExp > 0) {
            bufferedExp = Math.min(bufferedExp,
                    ShadowProgressionManager.experienceCapacity(pokemon, authorizedOwner));

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
        List<Move> candidates = ordinaryMoveReplacementCandidates(pokemon);

        // Build the desired MoveSet: replace shadow slots with candidates, preserve others
        MoveSet newMoveSet = new MoveSet();
        List<Move> movesWithNulls = pokemon.getMoveSet().getMovesWithNulls();
        Set<String> promotedNames = new HashSet<>();
        int candidateIdx = 0;
        for (int i = 0; i < 4; i++) {
            Move m = movesWithNulls.get(i);
            if (ShadowMoveUtil.isShadowMove(m)) {
                // Always remove shadow moves; replace with a candidate if available, otherwise null
                Move replacement = candidateIdx < candidates.size() ? candidates.get(candidateIdx++) : null;
                newMoveSet.setMove(i, replacement);
                if (replacement != null) promotedNames.add(replacement.getTemplate().getName());
            } else {
                newMoveSet.setMove(i, m != null ? m.copy() : null);
            }
        }

        // Exclude all Shadow moves and ordinary moves promoted from the bench.
        BenchedMoves newBenchedMoves = new BenchedMoves();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            if (!ShadowMoveUtil.isShadowMove(bm.getMoveTemplate())
                    && !promotedNames.contains(bm.getMoveTemplate().getName())) {
                newBenchedMoves.add(bm);
            }
        }

        // Apply both atomically — each copyFrom does a single update() at the end
        pokemon.getMoveSet().copyFrom(newMoveSet);
        pokemon.getBenchedMoves().copyFrom(newBenchedMoves);
    }

    private static List<Move> ordinaryMoveReplacementCandidates(Pokemon pokemon) {
        Set<String> candidateNames = new HashSet<>();
        for (Move move : pokemon.getMoveSet()) {
            if (move != null) candidateNames.add(move.getTemplate().getName());
        }
        Set<String> benchedNames = new HashSet<>();
        for (BenchedMove move : pokemon.getBenchedMoves()) {
            benchedNames.add(move.getMoveTemplate().getName());
        }

        // Keep the existing preference for unseen ordinary level-up moves, newest first.
        List<MoveTemplate> levelUpMoves =
                new ArrayList<>(pokemon.getForm().getMoves().getLevelUpMovesUpTo(pokemon.getLevel()));
        Collections.reverse(levelUpMoves);
        List<Move> candidates = new ArrayList<>();
        for (MoveTemplate template : levelUpMoves) {
            if (!ShadowMoveUtil.isShadowMove(template)
                    && !benchedNames.contains(template.getName())
                    && candidateNames.add(template.getName())) {
                candidates.add(template.create());
            }
        }

        // A form can replace every active slot with Shadow moves and bench all ordinary moves.
        // Reuse those moves when no fresh level-up candidate remains, retaining their PP raises.
        for (BenchedMove benched : pokemon.getBenchedMoves()) {
            MoveTemplate template = benched.getMoveTemplate();
            if (!ShadowMoveUtil.isShadowMove(template) && candidateNames.add(template.getName())) {
                Move move = template.create(template.getPp(), benched.getPpRaisedStages());
                move.setCurrentPp(move.getMaxPp());
                candidates.add(move);
            }
        }
        return candidates;
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
