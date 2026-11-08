package com.jayemceekay.shadowedhearts.common.shadow;

import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.data.ShadowLearnset;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Individual moves earned at purification, shared by full party snapshots and live property updates. */
public record RetainedShadowMoves(Set<String> moves) implements CustomPokemonProperty {
    public static final String KEY = "sh_retained_moves";
    private static final String NBT_KEY = "shadowedhearts:retained_moves";
    private static final Pattern MOVE_ID = Pattern.compile("[a-z0-9]+");

    public RetainedShadowMoves {
        moves = Set.copyOf(moves);
    }

    public static RetainedShadowMoves parse(String value) {
        if (value == null) return null;
        Set<String> ids = new HashSet<>();
        for (String id : value.split(",", -1)) {
            if (!MOVE_ID.matcher(id).matches()) return null;
            ids.add(id);
        }
        return new RetainedShadowMoves(ids);
    }

    public static Set<String> ids(Pokemon pokemon) {
        for (var property : pokemon.getCustomProperties()) {
            if (property instanceof RetainedShadowMoves retained) return retained.moves();
        }
        // Cobblemon's S2C party and PC codecs include persistentData and omit customProperties.
        if (!pokemon.getPersistentData().contains(NBT_KEY)) return Set.of();
        var retained = parse(pokemon.getPersistentData().getString(NBT_KEY));
        return retained == null ? Set.of() : retained.moves();
    }

    public static boolean knows(Pokemon pokemon, String move) {
        return ids(pokemon).contains(move);
    }

    static void onPurification(Pokemon pokemon, ShadowLearnset profile) {
        if (profile == null) return;
        Set<String> previous = ids(pokemon);
        Set<String> earned = new HashSet<>(previous);
        for (var move : pokemon.getMoveSet()) {
            if (profile.retains(move.getName())) earned.add(move.getName());
        }
        for (var benched : pokemon.getBenchedMoves()) {
            String id = benched.getMoveTemplate().getName();
            if (profile.retains(id)) earned.add(id);
        }
        if (!previous.equals(earned)) new RetainedShadowMoves(earned).apply(pokemon);
    }

    @Override public String asString() {
        return KEY + "=" + encode();
    }

    @Override public void apply(Pokemon pokemon) {
        pokemon.getCustomProperties().removeIf(property -> property instanceof RetainedShadowMoves);
        pokemon.getCustomProperties().add(this);
        pokemon.getPersistentData().putString(NBT_KEY, encode());
    }

    private String encode() { return String.join(",", moves.stream().sorted().toList()); }

    @Override public boolean matches(Pokemon pokemon) {
        return ids(pokemon).containsAll(moves);
    }
}
