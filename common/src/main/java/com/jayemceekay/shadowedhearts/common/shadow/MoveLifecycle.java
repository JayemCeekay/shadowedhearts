package com.jayemceekay.shadowedhearts.common.shadow;

import com.jayemceekay.shadowedhearts.network.PokemonStateSync;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.config.MoveLifecycleConfig;
import com.jayemceekay.shadowedhearts.pokemon.properties.MoveLifecycleProperty;

import java.util.ArrayList;
import java.util.List;

import static com.jayemceekay.shadowedhearts.pokemon.properties.MoveLifecycleProperty.Kind.*;

/** Move provenance is attached to the Pokémon, so an event ending or a reconnect cannot reset it. */
public final class MoveLifecycle {
    private static final String KEY = "shadowedhearts:move_lifecycle";
    private MoveLifecycle() { }

    public static MoveLifecycleProperty state(Pokemon pokemon) {
        if (pokemon == null) return null;
        for (var property : pokemon.getCustomProperties()) {
            if (property instanceof MoveLifecycleProperty lifecycle) return lifecycle;
        }
        return MoveLifecycleProperty.parse(pokemon.getPersistentData().getString(KEY));
    }

    public static void setState(Pokemon pokemon, MoveLifecycleProperty state) {
        pokemon.getCustomProperties().removeIf(property -> property instanceof MoveLifecycleProperty);
        if (state == null) pokemon.getPersistentData().remove(KEY);
        else {
            pokemon.getCustomProperties().add(state);
            pokemon.getPersistentData().putString(KEY, state.encode());
        }
    }

    /** True means this cycle is handled by the GO assignment policy, including a previously removed move. */
    public static boolean assignGoIfEnabled(Pokemon pokemon) {
        var existing = state(pokemon);
        if (existing != null && existing.kind() == GO_SHADOW) return true;
        if (!MoveLifecycleConfig.INSTANCE.goMoves()) return false;
        MoveTemplate frustration = Moves.INSTANCE.getByName("frustration");
        if (frustration == null) throw new IllegalStateException("GO move policy requires the Frustration move");
        installFirstMove(pokemon, frustration);
        setState(pokemon, new MoveLifecycleProperty(GO_SHADOW, List.of("frustration")));
        PokemonStateSync.syncProperties(pokemon);
        return true;
    }

    public static boolean blocksReplacement(Pokemon pokemon, MoveTemplate oldMove) {
        var state = state(pokemon);
        return oldMove != null && state != null && state.kind() == GO_SHADOW
                && "frustration".equals(oldMove.getName()) && ShadowAspectUtil.hasShadowAspect(pokemon)
                && !(pokemon.isClient$common() ? MoveLifecycleClientState.INSTANCE.allowed()
                : MoveLifecycleConfig.INSTANCE.frustrationRemovalAllowed());
    }

    public static boolean blocksLearning(Pokemon pokemon, MoveTemplate newMove) {
        var state = state(pokemon);
        if (newMove == null || state == null || !state.goOrigin()) return false;
        // GO-origin moves are acquired by the lifecycle, never by ordinary teaching or relearning.
        return "frustration".equals(newMove.getName()) || "return".equals(newMove.getName());
    }

    public static void afterExchange(Pokemon pokemon, MoveTemplate oldMove) {
        var state = state(pokemon);
        if (oldMove == null || state == null || !state.goOrigin()) return;
        String name = oldMove.getName();
        if (!name.equals("frustration") && !name.equals("return")) return;
        if (pokemon.getMoveSet().getMoves().stream().anyMatch(move -> move.getTemplate().getName().equals(name))) return;
        pokemon.getBenchedMoves().remove(oldMove);
        setState(pokemon, new MoveLifecycleProperty(state.kind(), state.moves().stream().filter(move -> !move.equals(name)).toList()));
        PokemonStateSync.syncProperties(pokemon);
        PokemonStateSync.syncBenchedMoves(pokemon);
    }

    /** Apply GO conversion after native restoration, before deferred level-ups validate the moveset. */
    public static void onPurified(Pokemon pokemon, boolean wasShadow) {
        if (!wasShadow) return;
        var state = state(pokemon);
        if (MoveLifecycleConfig.INSTANCE.goMoves() || (state != null && state.kind() == GO_SHADOW)) {
            MoveTemplate returnMove = Moves.INSTANCE.getByName("return");
            if (returnMove == null) throw new IllegalStateException("GO move policy requires the Return move");
            // Remove every Frustration occurrence, including a bench entry left by earlier external editing.
            var frustration = Moves.INSTANCE.getByName("frustration");
            if (frustration != null) pokemon.getBenchedMoves().remove(frustration);
            for (int slot = 0; slot < 4; slot++) {
                Move move = pokemon.getMoveSet().get(slot);
                if (move != null && move.getTemplate().getName().equals("frustration")) pokemon.getMoveSet().setMove(slot, null);
            }
            installFirstMove(pokemon, returnMove);
            setState(pokemon, new MoveLifecycleProperty(GO_PURIFIED, List.of("return")));
        }
    }

    private static void installFirstMove(Pokemon pokemon, MoveTemplate template) {
        var moves = pokemon.getMoveSet();
        Move kept = null;
        for (int slot = 0; slot < 4; slot++) {
            Move move = moves.get(slot);
            if (move != null && move.getTemplate().getName().equals(template.getName())) {
                kept = move.copy();
                moves.setMove(slot, null);
            }
        }
        Move old = moves.get(0);
        if (old != null && !ShadowMoveUtil.isShadowMove(old)) {
            pokemon.getBenchedMoves().add(new BenchedMove(old.getTemplate(), old.getRaisedPpStages()));
        }
        pokemon.getBenchedMoves().remove(template);
        moves.setMove(0, kept == null ? template.create(template.getPp(), 0) : kept);
    }

    /** Preserve earned event moves through automatic validation/rebuilds, while allowing explicit replacement. */
    public static void preserveEarnedMoves(Pokemon pokemon, Runnable action) {
        var state = state(pokemon);
        if (state == null) { action.run(); return; }
        Move[] saved = new Move[4];
        List<BenchedMove> bench = new ArrayList<>();
        for (int slot = 0; slot < 4; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move != null && state.moves().contains(move.getTemplate().getName())) saved[slot] = move.copy();
        }
        for (BenchedMove move : pokemon.getBenchedMoves()) {
            if (state.moves().contains(move.getMoveTemplate().getName())) bench.add(move);
        }
        action.run();
        for (int slot = 0; slot < 4; slot++) {
            Move move = saved[slot];
            if (move == null) continue;
            Move displaced = pokemon.getMoveSet().get(slot);
            if (displaced != null && !displaced.getTemplate().equals(move.getTemplate()) && !ShadowMoveUtil.isShadowMove(displaced)) {
                boolean present = false;
                for (BenchedMove current : pokemon.getBenchedMoves()) {
                    if (current.getMoveTemplate().equals(displaced.getTemplate())) present = true;
                }
                if (!present) pokemon.getBenchedMoves().add(new BenchedMove(displaced.getTemplate(), displaced.getRaisedPpStages()));
            }
            for (int other = 0; other < 4; other++) {
                Move current = pokemon.getMoveSet().get(other);
                if (other != slot && current != null && current.getTemplate().equals(move.getTemplate())) pokemon.getMoveSet().setMove(other, null);
            }
            pokemon.getMoveSet().setMove(slot, move);
            pokemon.getBenchedMoves().remove(move.getTemplate());
        }
        for (BenchedMove move : bench) {
            boolean active = pokemon.getMoveSet().getMoves().stream().anyMatch(current -> current.getTemplate().equals(move.getMoveTemplate()));
            boolean present = false;
            for (BenchedMove current : pokemon.getBenchedMoves()) if (current.getMoveTemplate().equals(move.getMoveTemplate())) present = true;
            if (!active && !present) pokemon.getBenchedMoves().add(move);
        }
    }
}
