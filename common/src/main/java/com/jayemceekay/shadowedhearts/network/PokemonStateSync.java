package com.jayemceekay.shadowedhearts.network;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.BenchedMoves;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.AspectsUpdatePacket;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.BenchedMovesUpdatePacket;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.MoveSetUpdatePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import java.util.ArrayList;
import java.util.HashSet;

/** Publishes Pokemon state snapshots through Cobblemon's update and persistence hooks. */
public final class PokemonStateSync {
    private PokemonStateSync() { }

    /**
     * Sends an aspects update for the given Pokemon to clients and marks it dirty for persistence.
     * Safe to call even on the server only; will fall back to onChange(null) if packet creation fails.
     */
    public static void syncAspects(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            pokemon.onChange(new AspectsUpdatePacket(supplier, new HashSet<>(pokemon.getAspects())));
        } catch (Throwable t) {
            // Fallback
        } finally {
            pokemon.onChange(null);
        }
    }

    public static void syncProperties(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            pokemon.onChange(new PokemonPropertyUpdatePacket(supplier, new ArrayList<>(pokemon.getCustomProperties())));
        } catch (Throwable t) {
            // Fallback
        } finally {
            pokemon.onChange(null);
        }
    }

    public static void syncBenchedMoves(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            BenchedMoves snapshot = new BenchedMoves();
            snapshot.doWithoutEmitting(() -> {
                for (BenchedMove bm : pk.getBenchedMoves()) {
                    snapshot.add(bm);
                }
                return null;
            });
            pokemon.onChange(new BenchedMovesUpdatePacket(supplier, snapshot));
        } catch (Throwable t) {
            // Fallback
        } finally {
            pokemon.onChange(null);
        }
    }

    public static void syncMoveSet(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            final Pokemon pk = pokemon;
            Function0<Pokemon> supplier = () -> pk;
            // Create a snapshot of the MoveSet to avoid race conditions during Netty encoding.
            MoveSet snapshot = new MoveSet();
            snapshot.doWithoutEmitting(() -> {
                for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
                    Move move = pk.getMoveSet().get(i);
                    if (move != null) {
                        snapshot.setMove(i, move);
                    }
                }
                return Unit.INSTANCE;
            });
            pokemon.onChange(new MoveSetUpdatePacket(supplier, snapshot));
        } catch (Throwable t) {
            // Fallback
        } finally {
            pokemon.onChange(null);
        }
    }
}
