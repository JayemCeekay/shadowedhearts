package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.data.ShadowLearnset;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Integrates per-Pokémon learnability without modifying Cobblemon's shared species and form objects. */
public final class ShadowLearnsetMoves {
    private ShadowLearnsetMoves() {}

    public static Set<MoveTemplate> levelMoves(Pokemon pokemon, int level, Set<MoveTemplate> nativeMoves) {
        ShadowLearnset profile = ShadowLearnsets.get(pokemon);
        if (profile == null) return nativeMoves;
        Set<MoveTemplate> result = new LinkedHashSet<>(nativeMoves);
        result.removeIf(ShadowMoveUtil::isShadowMove);
        addRegistered(result, profile.levelMoves(level, ShadowAspectUtil.hasShadowAspect(pokemon)));
        return result;
    }

    public static Set<MoveTemplate> accessibleMoves(Pokemon pokemon, Set<MoveTemplate> nativeMoves) {
        ShadowLearnset profile = ShadowLearnsets.get(pokemon);
        Set<String> retained = RetainedShadowMoves.ids(pokemon);
        if (profile == null && retained.isEmpty()) return nativeMoves;
        Set<MoveTemplate> result = new LinkedHashSet<>(nativeMoves);
        if (profile != null) {
            // A TM entry is permission to teach. Only an already learned TM belongs in the picker.
            result.removeIf(ShadowMoveUtil::isShadowMove);
            boolean shadow = ShadowAspectUtil.hasShadowAspect(pokemon);
            addRegistered(result, profile.levelMoves(pokemon.getLevel(), shadow));
            for (var benched : pokemon.getBenchedMoves()) {
                var move = benched.getMoveTemplate();
                if (profile.permits(move.getName(), pokemon.getLevel(), shadow)) result.add(move);
            }
        }
        addRegistered(result, retained);
        return result;
    }

    private static void addRegistered(Set<MoveTemplate> moves, Collection<String> ids) {
        for (String id : ids) {
            MoveTemplate move = Moves.getByName(id);
            if (ShadowMoveUtil.isShadowMove(move)) moves.add(move);
        }
    }

    /** Called by native validation for both active and benched moves. */
    public static boolean isLegal(Pokemon pokemon, MoveTemplate move, boolean nativeLegal) {
        if (!ShadowMoveUtil.isShadowMove(move)) return nativeLegal;
        if (RetainedShadowMoves.knows(pokemon, move.getName())) return true;
        ShadowLearnset profile = ShadowLearnsets.get(pokemon);
        return profile == null ? nativeLegal
                : profile.permits(move.getName(), pokemon.getLevel(), ShadowAspectUtil.hasShadowAspect(pokemon));
    }

    /** On an authored species, an equipped TM remains learned when another disc or Scale changes its slot. */
    public static void updateShadowSlotBench(Pokemon pokemon, Move outgoing, MoveTemplate incoming) {
        var profile = ShadowLearnsets.get(pokemon);
        var bench = pokemon.getBenchedMoves();
        bench.doWithoutEmitting(() -> {
            bench.remove(outgoing.getTemplate());
            bench.remove(incoming);
            if (profile != null && profile.permits(outgoing.getName(), pokemon.getLevel(), true)) {
                bench.add(new BenchedMove(outgoing.getTemplate(), outgoing.getRaisedPpStages()));
            }
            return Unit.INSTANCE;
        });
    }

    /** A selector sees the same ordinary-state level and TM sources as the finished Pokémon. */
    public static Learnset ordinaryBuilderLearnset(Learnset nativeMoves, ShadowLearnset profile) {
        Learnset combined = new Learnset();
        nativeMoves.getLevelUpMoves().forEach((level, moves) -> {
            var ordinary = new ArrayList<>(moves);
            ordinary.removeIf(ShadowMoveUtil::isShadowMove);
            if (!ordinary.isEmpty()) combined.getLevelUpMoves().put(level, ordinary);
        });
        for (var entry : profile.levelUp()) {
            if (entry.requiresShadow()) continue;
            MoveTemplate move = Moves.getByName(entry.move());
            if (ShadowMoveUtil.isShadowMove(move)) {
                combined.getLevelUpMoves().computeIfAbsent(entry.level(), ignored -> new ArrayList<>()).add(move);
            }
        }
        copyOrdinary(nativeMoves.getTmMoves(), combined.getTmMoves());
        for (var entry : profile.tm()) {
            if (entry.requiresShadow()) continue;
            MoveTemplate move = Moves.getByName(entry.move());
            if (ShadowMoveUtil.isShadowMove(move)) combined.getTmMoves().add(move);
        }
        copyOrdinary(nativeMoves.getEggMoves(), combined.getEggMoves());
        copyOrdinary(nativeMoves.getTutorMoves(), combined.getTutorMoves());
        copyOrdinary(nativeMoves.getSpecialMoves(), combined.getSpecialMoves());
        copyOrdinary(nativeMoves.getLegacyMoves(), combined.getLegacyMoves());
        copyOrdinary(nativeMoves.getEvolutionMoves(), combined.getEvolutionMoves());
        copyOrdinary(nativeMoves.getFormChangeMoves(), combined.getFormChangeMoves());
        return combined;
    }

    private static void copyOrdinary(Collection<MoveTemplate> from, Collection<MoveTemplate> to) {
        for (MoveTemplate move : from) if (!ShadowMoveUtil.isShadowMove(move)) to.add(move);
    }
}
