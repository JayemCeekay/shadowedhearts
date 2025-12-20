package com.jayemceekay.shadowedhearts.client.storage;

import com.cobblemon.mod.common.api.storage.StorePosition;
import com.cobblemon.mod.common.client.storage.ClientStorage;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal client-side storage for the Purification Chamber UI.
 * Holds 9 sets of 5 positions each: 0 (center) and 1-4 (outer).
 * Provides a client-side currentSetIndex for paging like PC boxes.
 */
public class ClientPurificationStorage extends ClientStorage<ClientPurificationStorage.PurificationPosition> {

    public static class PurificationPosition implements StorePosition {
        public final int index; // 0..4
        public PurificationPosition(int index) { this.index = index; }
        @Override
        public boolean equals(Object o) { return o instanceof PurificationPosition pp && pp.index == index; }
        @Override
        public int hashCode() { return Objects.hash(index); }
    }

    public static final int TOTAL_SETS = 9;

    // setIndex -> (position -> pokemon)
    private final Map<Integer, Map<PurificationPosition, Pokemon>> sets = new HashMap<>();
    private int currentSetIndex = 0;

    public ClientPurificationStorage(UUID uuid) {
        super(uuid);
    }

    @Override
    public Pokemon findByUUID(UUID uuid) {
        for (Map<PurificationPosition, Pokemon> map : sets.values()) {
            for (Pokemon p : map.values()) {
                if (p != null && uuid.equals(p.getUuid())) return p;
            }
        }
        return null;
    }

    @Override
    public void set(PurificationPosition position, Pokemon pokemon) {
        Map<PurificationPosition, Pokemon> map = sets.computeIfAbsent(currentSetIndex, k -> new HashMap<>());
        if (pokemon == null) map.remove(position); else map.put(position, pokemon);
    }

    @Override
    public Pokemon get(PurificationPosition position) {
        Map<PurificationPosition, Pokemon> map = sets.get(currentSetIndex);
        return map == null ? null : map.get(position);
    }

    @Override
    public PurificationPosition getPosition(Pokemon pokemon) {
        Map<PurificationPosition, Pokemon> map = sets.get(currentSetIndex);
        if (map == null) return null;
        for (Map.Entry<PurificationPosition, Pokemon> e : map.entrySet()) {
            if (e.getValue() != null && e.getValue().getUuid().equals(pokemon.getUuid())) return e.getKey();
        }
        return null;
    }

    // Paging controls
    public int getCurrentSetIndex() { return currentSetIndex; }
    public void setCurrentSetIndex(int idx) {
        if (idx < 0) idx = 0;
        if (idx >= TOTAL_SETS) idx = TOTAL_SETS - 1;
        this.currentSetIndex = idx;
    }
    public void nextSet() { setCurrentSetIndex((currentSetIndex + 1) % TOTAL_SETS); }
    public void prevSet() { setCurrentSetIndex((currentSetIndex - 1 + TOTAL_SETS) % TOTAL_SETS); }

    // --- New helpers for network sync without mutating currentSetIndex ---
    /** Clears all client-cached sets for this storage. */
    public void clearAll() { sets.clear(); }

    /** Sets a pokemon at the given set index and position, without changing the current page. */
    public void setAt(int setIndex, PurificationPosition position, Pokemon pokemon) {
        if (setIndex < 0 || setIndex >= TOTAL_SETS) return;
        Map<PurificationPosition, Pokemon> map = sets.computeIfAbsent(setIndex, k -> new HashMap<>());
        if (pokemon == null) map.remove(position); else map.put(position, pokemon);
    }

    /** Gets a pokemon at the given set index and position, without changing the current page. */
    public Pokemon getAt(int setIndex, PurificationPosition position) {
        if (setIndex < 0 || setIndex >= TOTAL_SETS) return null;
        Map<PurificationPosition, Pokemon> map = sets.get(setIndex);
        return map == null ? null : map.get(position);
    }
}
