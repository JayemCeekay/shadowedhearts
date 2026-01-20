package com.jayemceekay.shadowedhearts;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.jayemceekay.shadowedhearts.property.EVBufferProperty;
import com.jayemceekay.shadowedhearts.property.HeartGaugeProperty;
import com.jayemceekay.shadowedhearts.property.XPBufferProperty;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * Service façade for synchronizing shadow state between stored Cobblemon Pokemon and live entities.
 */
public final class ShadowService {

    /**
     * Make a Pokemon (and its live entity if present) shadow/non-shadow.
     * @param pokemon stored Pokemon object
     * @param live currently loaded entity for that Pokemon (nullable)
     * @param shadow true to set shadow, false to clear
     */
    public static void setShadow(Pokemon pokemon, @Nullable PokemonEntity live, boolean shadow) {
        PokemonAspectUtil.setShadowAspect(pokemon, shadow);
        // Ensure required supporting aspects exist when shadowed
        PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);
        if (live != null) {
            ShadowPokemonData.set(live, shadow, PokemonAspectUtil.getHeartGauge(pokemon));
        }
        // Proactively sync aspect changes to observing players (party/PC/chamber UI) and mark store dirty.
        // This ensures client UIs (e.g., Summary screen) update immediately without requiring a PC swap.
        PokemonAspectUtil.syncAspects(pokemon);
        PokemonAspectUtil.syncBenchedMoves(pokemon);
        PokemonAspectUtil.syncMoveSet(pokemon);
        // If live == null but is in world somewhere, optionally locate it to sync (handled by store observers above).
    }

    /** Set heart gauge absolute meter [0..speciesMax from config]. */
    public static void setHeartGauge(Pokemon pokemon, @Nullable PokemonEntity live, int meter) {
        int max = HeartGaugeConfig.getMax(pokemon);
        int clamped = Math.max(0, Math.min(max, meter));
        PokemonAspectUtil.setHeartGaugeValue(pokemon, clamped);
        // Ensure required supporting aspects exist when shadowed (no-op if not shadowed)
        PokemonAspectUtil.ensureRequiredShadowAspects(pokemon);
        if (live != null) ShadowPokemonData.set(live, ShadowPokemonData.isShadow(live), PokemonAspectUtil.getHeartGauge(pokemon));

        applyMoveUnlocks(pokemon);

        // Proactively sync aspect changes to observing players and mark store dirty so client UI updates live.
        PokemonAspectUtil.syncAspects(pokemon);
        PokemonAspectUtil.syncBenchedMoves(pokemon);
        PokemonAspectUtil.syncMoveSet(pokemon);
    }

    private static void applyMoveUnlocks(Pokemon pokemon) {
        if (!ShadowGate.isShadowLocked(pokemon)) return;

        int allowed = PokemonAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        if (allowed < 1) return;

        // Count how many shadow moves we have
        List<Move> shadowMoves = new ArrayList<>();
        for (Move move : pokemon.getMoveSet()) {
            if (move != null && ShadowGate.isShadowMoveId(move.getName())) {
                shadowMoves.add(move);
            }
        }

        if (shadowMoves.isEmpty()) return;

        // Get all legal level-up moves up to current level
        List<MoveTemplate> levelUpMoves =
            new ArrayList<>(pokemon.getForm().getMoves().getLevelUpMovesUpTo(pokemon.getLevel()));

        // Reverse to get "most recently unlocked" first
        Collections.reverse(levelUpMoves);

        // Filter out moves already in the moveset or benched
        Set<String> currentMoveNames = new HashSet<>();
        for (Move m : pokemon.getMoveSet()) {
            if (m != null) currentMoveNames.add(m.getTemplate().getName());
        }
        // Use a snapshot of benched moves to avoid ConcurrentModificationException
        List<BenchedMove> benchedSnapshot = new ArrayList<>();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            benchedSnapshot.add(bm);
        }
        for (BenchedMove bm : benchedSnapshot) {
            currentMoveNames.add(bm.getMoveTemplate().getName());
        }

        List<MoveTemplate> candidates = new ArrayList<>();
        for (MoveTemplate template : levelUpMoves) {
            if (!currentMoveNames.contains(template.getName())) {
                candidates.add(template);
            }
        }

        if (candidates.isEmpty()) return;

        // Thresholds for replacing shadow moves:
        //XD: <2 bars (2 moves unlocked) => replace 1st shadow move?
        //Actually our system is:
        // < 4 bars => 1st non-shadow unlocked
        // < 2 bars => 2nd non-shadow unlocked
        // < 1 bar => 3rd non-shadow unlocked
        // 0% => 4th non-shadow unlocked

        // The prompt says "if they have unlocked them by the heart gauge lowering"
        // and "When a pokemon is purified... All its moves should me undisabled."

        // For partial unlock during gauge lowering, we might want to replace shadow moves
        // if we have enough allowed slots.

        int shadowMovesToReplace = 0;
        if (allowed >= 4) shadowMovesToReplace = 4;
        else if (allowed >= 3) shadowMovesToReplace = 1; // At 3 moves allowed, usually one shadow move is replaced in XD

        if (shadowMovesToReplace <= 0) return;

        // Replace shadow moves with candidates
        int toReplace = Math.min(Math.min(shadowMoves.size(), candidates.size()), shadowMovesToReplace);
        for (int i = 0; i < toReplace; i++) {
            Move shadowMove = shadowMoves.get(i);
            MoveTemplate newMoveTemplate = candidates.get(i);

            if (pokemon.exchangeMove(shadowMove.getTemplate(), newMoveTemplate)) {
                // Forget shadow move: remove from benched moves too
                // Use a safe removal approach to avoid CME if this triggers a sync
                pokemon.getBenchedMoves().remove(shadowMove.getTemplate());
            }
        }
    }

    /** Convenience: fully purified (0) and not shadow. */
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
        int bufferedExp = PokemonAspectUtil.getBufferedExp(pokemon);
        if (bufferedExp > 0) {
            pokemon.addExperience(new SidemodExperienceSource("shadowedhearts"), bufferedExp);
        }

        int[] bufferedEvs = PokemonAspectUtil.getBufferedEvs(pokemon);
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
            if (live.getOwner() instanceof ServerPlayer player) {
                ModCriteriaTriggers.triggerShadowPurified(player);
            }
        } else if (pokemon.getOwnerPlayer() instanceof ServerPlayer player) {
            ModCriteriaTriggers.triggerShadowPurified(player);
        }

        PokemonAspectUtil.syncAspects(pokemon);
        PokemonAspectUtil.syncProperties(pokemon);
        PokemonAspectUtil.syncBenchedMoves(pokemon);
        PokemonAspectUtil.syncMoveSet(pokemon);

    }

    private static void restoreAllMoves(Pokemon pokemon) {

        // Identify all current shadow moves in the moveset to be replaced
        List<Move> shadowMoves = new ArrayList<>();
        for (Move move : pokemon.getMoveSet()) {
            if (move != null && ShadowGate.isShadowMoveId(move.getName())) {
                shadowMoves.add(move);
            }
        }

        // Get candidates from level-up moves that the Pokemon has already qualified for
        List<MoveTemplate> levelUpMoves = 
            new ArrayList<>(pokemon.getForm().getMoves().getLevelUpMovesUpTo(pokemon.getLevel()));
        // Reverse to prioritize the most recently learned moves
        Collections.reverse(levelUpMoves);


        // Track names of moves already in moveset or benched to avoid duplicates
        Set<String> currentMoveNames = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            Move m = pokemon.getMoveSet().getMovesWithNulls().get(i);
            if (m != null) currentMoveNames.add(m.getTemplate().getName());
        }

        // Use a snapshot of benched moves to avoid ConcurrentModificationException
        List<BenchedMove> benchedSnapshotRestore = new ArrayList<>();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            benchedSnapshotRestore.add(bm);
        }

        // Filter level-up moves to create a list of valid replacement candidates
        List<MoveTemplate> candidates = new ArrayList<>();
        for (MoveTemplate template : levelUpMoves) {
            if (!currentMoveNames.contains(template.getName())) {
                candidates.add(template);
            }
        }

        // Replace each shadow move with a candidate from the qualified moves list
        int toReplace = Math.min(shadowMoves.size(), candidates.size());
        for (int i = 0; i < toReplace; i++) {
            pokemon.exchangeMove(shadowMoves.get(i).getTemplate(), candidates.get(i));
        }

        // Aggressively remove all shadow moves from benched moves as well
        // Use a snapshot to avoid ConcurrentModificationException during iteration
        List<BenchedMove> snapshot = new ArrayList<>();
        for (BenchedMove bm : pokemon.getBenchedMoves()) {
            snapshot.add(bm);
        }

        for (BenchedMove bm : snapshot) {
            if (ShadowGate.isShadowMoveId(bm.getMoveTemplate().getName())) {
                pokemon.getBenchedMoves().remove(bm.getMoveTemplate());
            }
        }
    }

    /** Convenience: corrupted and shadow with specific heart gauge value. */
    public static void corrupt(Pokemon pokemon, @Nullable PokemonEntity live, int value) {
        setShadow(pokemon, live, true);
        setHeartGauge(pokemon, live, value);
    }

    /** Convenience: fully corrupted (speciesMax) and shadow. */
    public static void fullyCorrupt(Pokemon pokemon, @Nullable PokemonEntity live) {
        corrupt(pokemon, live, HeartGaugeConfig.getMax(pokemon));
    }
}
