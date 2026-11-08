package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowStartingMoves;

import java.util.List;
import java.util.Locale;

/** Ordered, exact initial moves for an authored encounter, including any ordinary moves. */
public record ShadowEncounterMoves(List<String> moves) implements CustomPokemonProperty {
    public ShadowEncounterMoves {
        moves = List.copyOf(moves);
        if (moves.isEmpty() || moves.size() > MoveSet.MOVE_COUNT || moves.stream().distinct().count() != moves.size()
                || moves.stream().anyMatch(id -> !id.matches("[a-z0-9]{1,64}"))) {
            throw new IllegalArgumentException("sh_shadow_moves expects one to four distinct move IDs");
        }
    }

    public static ShadowEncounterMoves parse(String value) {
        if (value == null) return null;
        return new ShadowEncounterMoves(List.of(value.toLowerCase(Locale.ROOT).split(",", -1)));
    }

    /** Checked by the pool authoring boundary after the move registry has loaded. */
    public void validateRegistered() {
        for (String move : moves) {
            if (Moves.getByName(move) == null) throw new IllegalArgumentException("Unknown sh_shadow_moves move '" + move + "'");
        }
    }

    @Override public String asString() { return "sh_shadow_moves=" + String.join(",", moves); }
    @Override public void apply(Pokemon pokemon) { ShadowStartingMoves.setEncounter(pokemon, this); }
    @Override public boolean matches(Pokemon pokemon) { return String.join(",", moves).equals(ShadowStartingMoves.encounter(pokemon)); }
}
