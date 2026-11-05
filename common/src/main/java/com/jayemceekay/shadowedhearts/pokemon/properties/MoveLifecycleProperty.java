package com.jayemceekay.shadowedhearts.pokemon.properties;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycle;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Persisted and synchronized provenance for event-locked and special purification moves. */
public record MoveLifecycleProperty(Kind kind, List<String> moves) implements CustomPokemonProperty {
    public enum Kind {
        GO_SHADOW, GO_PURIFIED,
        /** Read compatibility for previously earned moves; no new XD reward sets are granted. */
        XD_PURIFIED
    }

    public MoveLifecycleProperty {
        if (kind == null || moves == null || moves.size() > 4
                || moves.stream().anyMatch(move -> move == null || !move.matches("[a-z0-9]{1,64}"))
                || moves.stream().distinct().count() != moves.size()) {
            throw new IllegalArgumentException("Invalid move provenance");
        }
        moves = List.copyOf(moves);
    }

    public boolean goOrigin() { return kind != Kind.XD_PURIFIED; }
    public String encode() { return kind.name().toLowerCase(Locale.ROOT) + ":" + String.join(",", moves); }
    @Override public String asString() { return "sh_move_lifecycle=" + encode(); }
    @Override public void apply(Pokemon pokemon) { MoveLifecycle.setState(pokemon, this); }
    @Override public boolean matches(Pokemon pokemon) { return equals(MoveLifecycle.state(pokemon)); }

    public static MoveLifecycleProperty parse(String text) {
        if (text == null || text.length() > 300) return null;
        try {
            String[] parts = text.split(":", -1);
            if (parts.length != 2) return null;
            Kind kind = Kind.valueOf(parts[0].toUpperCase(Locale.ROOT));
            return new MoveLifecycleProperty(kind, parts[1].isEmpty() ? List.of() : Arrays.asList(parts[1].split(",", -1)));
        } catch (IllegalArgumentException malformed) { return null; }
    }
}
