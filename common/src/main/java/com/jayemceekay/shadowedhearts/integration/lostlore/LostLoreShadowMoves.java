package com.jayemceekay.shadowedhearts.integration.lostlore;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.BenchedMoves;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import dev.architectury.platform.Platform;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Uses Lost Lore's loaded form data without linking its optional classes. */
public final class LostLoreShadowMoves {
    private LostLoreShadowMoves() {}

    public static boolean isEnabledFor(Pokemon pokemon) {
        if (pokemon == null || !ShadowedHeartsConfigs.getInstance().getShadowConfig().preferLostLoreShadowMoves()
                || !Platform.isModLoaded("lost_lore")) return false;
        var species = pokemon.getSpecies().getResourceIdentifier();
        return "cobblemon".equals(species.getNamespace())
                && ("lugia".equals(species.getPath()) || "mewtwo".equals(species.getPath()))
                && pokemon.getForm().getAspects().contains("shadows")
                && ShadowMoveUtil.isShadowType(pokemon.getForm().getPrimaryType());
    }

    public static List<MoveTemplate> getPreferredMoves(Pokemon pokemon) {
        if (!isEnabledFor(pokemon)) return List.of();
        Map<String, MoveTemplate> moves = new LinkedHashMap<>();
        for (MoveTemplate move : pokemon.getForm().getMoves().getLevelUpMovesUpTo(pokemon.getLevel())) {
            if (ShadowMoveUtil.isShadowMove(move)) moves.putIfAbsent(move.getName(), move);
            if (moves.size() == MoveSet.MOVE_COUNT) break;
        }
        return List.copyOf(moves.values());
    }

    /** Called only by explicit Shadow assignment, never by config reload or save loading. */
    public static boolean assignIfEnabled(Pokemon pokemon) {
        List<MoveTemplate> preferred = getPreferredMoves(pokemon);
        if (preferred.isEmpty()) return false;

        Set<String> desiredIds = new LinkedHashSet<>();
        preferred.forEach(move -> desiredIds.add(move.getName()));
        Set<String> presentIds = new LinkedHashSet<>();
        Map<String, BenchedMove> bench = new LinkedHashMap<>();
        for (BenchedMove move : pokemon.getBenchedMoves()) bench.put(move.getMoveTemplate().getName(), move);
        Move[] slots = new Move[MoveSet.MOVE_COUNT];
        boolean removedOtherShadow = false;
        for (int slot = 0; slot < slots.length; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null) continue;
            String id = move.getTemplate().getName();
            if (desiredIds.contains(id)) {
                slots[slot] = move.copy();
                presentIds.add(id);
            } else if (ShadowMoveUtil.isShadowMove(move)) {
                bench.put(id, new BenchedMove(move.getTemplate(), move.getRaisedPpStages()));
                removedOtherShadow = true;
            } else {
                slots[slot] = move.copy();
            }
        }
        if (presentIds.containsAll(desiredIds) && !removedOtherShadow) return true;

        for (MoveTemplate template : preferred) {
            if (presentIds.contains(template.getName())) continue;
            for (int slot = 0; slot < slots.length; slot++) {
                Move displaced = slots[slot];
                if (displaced != null && desiredIds.contains(displaced.getTemplate().getName())) continue;
                if (displaced != null) {
                    bench.put(displaced.getTemplate().getName(),
                            new BenchedMove(displaced.getTemplate(), displaced.getRaisedPpStages()));
                }
                BenchedMove benched = bench.get(template.getName());
                Move move = template.create(template.getPp(), benched == null ? 0 : benched.getPpRaisedStages());
                move.setCurrentPp(move.getMaxPp());
                slots[slot] = move;
                presentIds.add(template.getName());
                break;
            }
        }

        MoveSet active = pokemon.getMoveSet();
        active.doWithoutEmitting(() -> {
            for (int slot = 0; slot < slots.length; slot++) {
                Move move = slots[slot];
                active.setMove(slot, move);
                if (move != null) bench.remove(move.getTemplate().getName());
            }
            return null;
        });
        BenchedMoves benched = new BenchedMoves();
        for (BenchedMove move : bench.values()) benched.add(move);
        active.update();
        pokemon.getBenchedMoves().copyFrom(benched);
        return true;
    }
}
