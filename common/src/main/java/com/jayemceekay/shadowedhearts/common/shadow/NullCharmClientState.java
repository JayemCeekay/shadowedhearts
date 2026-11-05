package com.jayemceekay.shadowedhearts.common.shadow;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/** Connection-scoped tooltip policy without references to client-only Minecraft classes. */
public final class NullCharmClientState {
    public static final NullCharmClientState INSTANCE = new NullCharmClientState();

    private Supplier<?> connectionSupplier;
    private Object connection;
    private NullCharmEffect receivedEffect;

    /** Installed only by client initialization; server-side callers retain their config fallback. */
    public synchronized void initialize(Supplier<?> connectionSupplier) {
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier");
        connection = null;
        receivedEffect = null;
    }

    /** Rejects updates queued for a connection that has since closed or been replaced. */
    public synchronized boolean receive(Object packetConnection, NullCharmEffect effect) {
        if (connectionSupplier == null) return false;
        refreshConnection();
        if (connection == null || connection != packetConnection) return false;
        receivedEffect = Objects.requireNonNull(effect, "effect");
        return true;
    }

    /** Null means the client has no authoritative policy yet and should show a generic tooltip. */
    @Nullable
    public synchronized NullCharmEffect currentEffect(Supplier<NullCharmEffect> serverFallback) {
        Objects.requireNonNull(serverFallback, "serverFallback");
        if (connectionSupplier == null) return serverFallback.get();
        refreshConnection();
        // Local config may differ from the remote server or retain a previous server's values.
        return receivedEffect;
    }

    /** Called each client tick as well as on access, promptly discarding stale connections. */
    public synchronized void refreshConnection() {
        Object current = connectionSupplier == null ? null : connectionSupplier.get();
        if (current != connection) {
            connection = current;
            receivedEffect = null;
        }
    }
}
