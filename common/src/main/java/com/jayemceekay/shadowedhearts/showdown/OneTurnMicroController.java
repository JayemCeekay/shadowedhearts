package com.jayemceekay.shadowedhearts.showdown;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Cobblemon battles that should be force-ended after exactly one turn.
 *
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public final class OneTurnMicroController {
    private OneTurnMicroController() {}

    private static final Set<UUID> ONE_TURN = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLOSED = ConcurrentHashMap.newKeySet();

    public static void mark(UUID id) { if (id != null) ONE_TURN.add(id); }
    public static boolean isOneTurn(UUID id) { return id != null && ONE_TURN.contains(id); }
    /** Marks as closed; returns true only the first time for the given id. */
    public static boolean tryMarkClosed(UUID id) { return id != null && CLOSED.add(id); }
    public static void clear(UUID id) { if (id != null) { ONE_TURN.remove(id); CLOSED.remove(id); } }
}
