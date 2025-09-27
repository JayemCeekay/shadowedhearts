package com.jayemceekay.shadowedhearts.showdown;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Cobblemon battles that should be force-ended after exactly one turn.
 * Also provides a simple per-battle boolean flag that callers can set/get during
 * the lifetime of the one-turn battle.
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class AutoBattleController {
    private AutoBattleController() {}

    private static final Set<UUID> ONE_TURN = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLOSED = ConcurrentHashMap.newKeySet();
    // Arbitrary boolean flag per battle (defaults to false when marked)
    private static final Map<UUID, Boolean> AWAITING_INPUT = new ConcurrentHashMap<>();

    /** Mark a battle as a one-turn micro battle. Initializes its flag to false. */
    public static void mark(UUID id) {
        if (id != null) {
            ONE_TURN.add(id);
            AWAITING_INPUT.putIfAbsent(id, Boolean.FALSE);
        }
    }

    /** Returns true if the battle is a tracked one-turn battle. */
    public static boolean isOneTurn(UUID id) { return id != null && ONE_TURN.contains(id); }

    /** Marks as closed; returns true only the first time for the given id. */
    public static boolean tryMarkClosed(UUID id) { return id != null && CLOSED.add(id); }

    /** Clears all tracking for the given battle id. */
    public static void clear(UUID id) { if (id != null) { ONE_TURN.remove(id); CLOSED.remove(id); AWAITING_INPUT.remove(id); } }

    /** Sets the arbitrary boolean flag for this one-turn battle. */
    public static void setFlag(UUID id, boolean value) {
        if (id != null) {
            AWAITING_INPUT.put(id, value);
        }
    }

    /** Gets the arbitrary boolean flag for this one-turn battle (defaults to false). */
    public static boolean getFlag(UUID id) {
        return id != null && Boolean.TRUE.equals(AWAITING_INPUT.get(id));
    }
}
