package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.BenchedMoves;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;
import com.jayemceekay.shadowedhearts.network.PokemonStateSync;
import com.jayemceekay.shadowedhearts.pokemon.properties.ShadowEncounterMoves;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installs a species or encounter starting set once per corruption cycle. */
public final class ShadowStartingMoves {
    private static final String ASSIGNED = "shadowedhearts:starting_moves_assigned";
    private static final String ENCOUNTER = "shadowedhearts:encounter_moves";

    private ShadowStartingMoves() { }

    public static void endCycle(Pokemon pokemon) {
        pokemon.getPersistentData().remove(ASSIGNED);
        pokemon.getPersistentData().remove(ENCOUNTER);
        if (pokemon.getCustomProperties().removeIf(ShadowEncounterMoves.class::isInstance) && !pokemon.isClient$common()) {
            PokemonStateSync.syncProperties(pokemon);
        }
    }

    /** Native party snapshots carry persistentData; live property updates carry the installed encounter property. */
    public static void updateClientEncounter(Pokemon pokemon) {
        for (var property : pokemon.getCustomProperties()) {
            if (property instanceof ShadowEncounterMoves encounter) {
                pokemon.getPersistentData().putString(ENCOUNTER, String.join(",", encounter.moves()));
                pokemon.getPersistentData().putBoolean(ASSIGNED, true);
                return;
            }
        }
        if (pokemon.getPersistentData().contains(ENCOUNTER)) {
            pokemon.getPersistentData().remove(ASSIGNED);
            pokemon.getPersistentData().remove(ENCOUNTER);
        }
    }

    /** An NPC copy or newly generated encounter can receive a new level after its template was initialized. */
    public static void setNewEncounterLevel(Pokemon pokemon, int level) {
        boolean changed = pokemon.getLevel() != level;
        pokemon.setLevel(level);
        if (changed && !hasExactSet(pokemon)) pokemon.getPersistentData().remove(ASSIGNED);
    }

    public static boolean assigned(Pokemon pokemon) {
        return pokemon.getPersistentData().getBoolean(ASSIGNED);
    }

    /** A native NPC moveset builder may run after properties.create() has finished. */
    public static boolean hasExactSet(Pokemon pokemon) {
        return assigned(pokemon) && pokemon.getPersistentData().contains(ENCOUNTER);
    }

    /** Keep the Pokémon's current Shadow slots (including a deliberately empty selection)
     * while letting a native NPC or form builder regenerate its ordinary moves. */
    public static boolean preserveDuringBuilder(Pokemon pokemon, Runnable build) {
        if (!assigned(pokemon)) return false;
        if (hasExactSet(pokemon)) return true;
        Move[] before = pokemon.getMoveSet().getMovesWithNulls().toArray(Move[]::new);
        build.run();
        MoveSet active = pokemon.getMoveSet();
        Map<String, Move> ordinary = new LinkedHashMap<>();
        for (Move move : active) if (!ShadowMoveUtil.isShadowMove(move)) ordinary.put(move.getName(), move);
        for (Move move : before) {
            if (move != null && !ShadowMoveUtil.isShadowMove(move)) ordinary.putIfAbsent(move.getName(), move);
        }
        Move[] result = new Move[MoveSet.MOVE_COUNT];
        for (int slot = 0; slot < result.length; slot++) {
            if (ShadowMoveUtil.isShadowMove(before[slot])) {
                result[slot] = before[slot];
            } else if (active.get(slot) != null) {
                result[slot] = ordinary.remove(active.get(slot).getName());
            }
        }
        var remaining = ordinary.values().iterator();
        for (int slot = 0; slot < result.length && remaining.hasNext(); slot++) {
            if (result[slot] == null) result[slot] = remaining.next();
        }
        var bench = pokemon.getBenchedMoves();
        bench.doWithoutEmitting(() -> {
            remaining.forEachRemaining(move -> {
                bench.remove(move.getTemplate());
                bench.add(new BenchedMove(move.getTemplate(), move.getRaisedPpStages()));
            });
            for (Move move : result) if (move != null) bench.remove(move.getTemplate());
            return kotlin.Unit.INSTANCE;
        });
        bench.update();
        active.doWithoutEmitting(() -> {
            for (int slot = 0; slot < result.length; slot++) {
                active.setMove(slot, result[slot] == null ? null : result[slot].copy());
            }
            return kotlin.Unit.INSTANCE;
        });
        active.update();
        return true;
    }

    public static String encounter(Pokemon pokemon) {
        return pokemon.getPersistentData().getString(ENCOUNTER);
    }

    public static void setEncounter(Pokemon pokemon, ShadowEncounterMoves request) {
        if (pokemon.isClient$common()) return;
        try {
            request.validateRegistered();
        } catch (IllegalArgumentException invalid) {
            Shadowedhearts.LOGGER.warn("Ignoring {}: {}", request.asString(), invalid.getMessage());
            return;
        }
        String encoded = String.join(",", request.moves());
        if (!encoded.equals(encounter(pokemon))) {
            pokemon.getPersistentData().putString(ENCOUNTER, encoded);
            pokemon.getPersistentData().remove(ASSIGNED);
        }
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) ShadowMoveUtil.assignShadowMoves(pokemon);
    }

    static boolean assignIfAuthored(Pokemon pokemon, boolean newCycle) {
        String encounter = encounter(pokemon);
        if (!encounter.isEmpty()) {
            // An addon may have been removed since this encounter was authored or saved.
            try {
                ShadowEncounterMoves.parse(encounter).validateRegistered();
            } catch (IllegalArgumentException invalid) {
                Shadowedhearts.LOGGER.warn("Ignoring saved sh_shadow_moves '{}' for {}: {}", encounter,
                        pokemon.getSpecies().getResourceIdentifier(), invalid.getMessage());
                pokemon.getPersistentData().remove(ENCOUNTER);
                encounter = "";
            }
        }
        var profile = ShadowLearnsets.get(pokemon);
        if (profile == null && encounter.isEmpty()) return false;
        // Refreshing a pre-feature saved Shadow must preserve the actual Pokémon's moves.
        if (!newCycle && encounter.isEmpty()) return true;

        boolean exact = !encounter.isEmpty();
        List<String> desired = exact ? List.of(encounter.split(",")) : profile.startingMoves(pokemon.getLevel());
        List<MoveTemplate> templates = new ArrayList<>();
        for (String id : desired) {
            MoveTemplate move = Moves.getByName(id);
            if (move == null) {
                Shadowedhearts.LOGGER.warn("Ignoring unavailable starting move '{}' for {}", id, pokemon.getSpecies().getResourceIdentifier());
                continue;
            }
            templates.add(move);
        }
        if (!exact && pokemon.getMoveSet().getMoves().isEmpty()) pokemon.initializeMovesetFromDefault();
        install(pokemon, templates, exact);
        pokemon.getPersistentData().putBoolean(ASSIGNED, true);
        if (exact) {
            pokemon.getCustomProperties().removeIf(ShadowEncounterMoves.class::isInstance);
            pokemon.getCustomProperties().add(new ShadowEncounterMoves(desired));
            PokemonStateSync.syncProperties(pokemon);
        }
        return true;
    }

    private static void install(Pokemon pokemon, List<MoveTemplate> desired, boolean exact) {
        Map<String, Move> old = new LinkedHashMap<>();
        for (Move move : pokemon.getMoveSet()) old.put(move.getName(), move);
        Map<String, BenchedMove> bench = new LinkedHashMap<>();
        for (BenchedMove move : pokemon.getBenchedMoves()) bench.put(move.getMoveTemplate().getName(), move);

        MoveSet active = new MoveSet();
        int slot = 0;
        for (MoveTemplate template : desired) {
            Move existing = old.remove(template.getName());
            BenchedMove learned = bench.remove(template.getName());
            Move move = existing != null ? existing.copy() : template.create(template.getPp(),
                    learned == null ? 0 : learned.getPpRaisedStages());
            if (existing == null) move.setCurrentPp(move.getMaxPp());
            active.setMove(slot++, move);
        }
        if (!exact) {
            // Prefer the ordinary moves beyond the slots displaced by the Shadow set.
            List<Move> ordinary = old.values().stream().filter(move -> !ShadowMoveUtil.isShadowMove(move)).toList();
            int start = Math.max(0, ordinary.size() - (MoveSet.MOVE_COUNT - slot));
            for (Move move : ordinary.subList(start, ordinary.size())) {
                active.setMove(slot++, move.copy());
                old.remove(move.getName());
                bench.remove(move.getName());
            }
        }
        for (Move move : old.values()) {
            bench.put(move.getName(), new BenchedMove(move.getTemplate(), move.getRaisedPpStages()));
        }
        BenchedMoves benched = new BenchedMoves();
        for (BenchedMove move : bench.values()) benched.add(move);
        pokemon.getMoveSet().copyFrom(active);
        pokemon.getBenchedMoves().copyFrom(benched);
    }
}
