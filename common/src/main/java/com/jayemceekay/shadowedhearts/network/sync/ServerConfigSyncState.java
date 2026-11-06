package com.jayemceekay.shadowedhearts.network.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-thread delivery bookkeeping for immutable configuration snapshots. */
final class ServerConfigSyncState<S, V> {
    private S activeServer;
    private V lastTickValue;
    private final Map<UUID, V> delivered = new HashMap<>();

    boolean beginTick(S server, V value) {
        Objects.requireNonNull(value, "value");
        useServer(server);
        if (Objects.equals(lastTickValue, value)) return false;
        lastTickValue = value;
        return true;
    }

    void playerJoined(S server, UUID player, V value) {
        Objects.requireNonNull(value, "value");
        useServer(server);
        delivered.put(player, value);
        // Reconcile next tick even if a live edit changes and then reverts before that tick.
        // A join must not consume a pending change for players already online.
        if (!Objects.equals(lastTickValue, value)) lastTickValue = null;
    }

    boolean needsUpdate(UUID player, V value) {
        return !Objects.equals(delivered.get(player), value);
    }

    void recordDelivery(UUID player, V value) {
        delivered.put(player, Objects.requireNonNull(value, "value"));
    }

    void playerLeft(S server, UUID player) {
        if (activeServer == server) delivered.remove(player);
    }

    void serverStopped(S server) {
        if (activeServer == server) {
            activeServer = null;
            lastTickValue = null;
            delivered.clear();
        }
    }

    private void useServer(S server) {
        if (activeServer != server) {
            activeServer = server;
            lastTickValue = null;
            delivered.clear();
        }
    }
}
