package com.jayemceekay.shadowedhearts.common.shadow;

import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Resolved server overrides belong to a connection, never to the shared local config cache. */
public final class HeartGaugeClientState {
    public static final HeartGaugeClientState INSTANCE = new HeartGaugeClientState();

    private Supplier<?> connectionSupplier;
    private BooleanSupplier integratedServer;
    private Object connection;
    private Map<String, Integer> receivedOverrides;
    private int receivedGlobalMax;

    public synchronized void initialize(Supplier<?> connectionSupplier, BooleanSupplier integratedServer) {
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier");
        this.integratedServer = Objects.requireNonNull(integratedServer, "integratedServer");
        connection = null;
        receivedOverrides = null;
    }

    /** Reject packets queued by a connection which has since closed or been replaced. */
    public synchronized boolean receive(Object packetConnection, Map<String, Integer> overrides) {
        if (connectionSupplier == null) return false;
        refreshConnection();
        if (connection == null || connection != packetConnection) return false;
        Map<String, Integer> snapshot = Map.copyOf(overrides);
        int maximum = HeartGaugeConfig.defaultGlobalMax();
        for (int value : snapshot.values()) {
            if (value <= 0) throw new IllegalArgumentException("Heart Gauge maxima must be positive");
            maximum = Math.max(maximum, value);
        }
        receivedOverrides = snapshot;
        receivedGlobalMax = maximum;
        return true;
    }

    public synchronized Map<String, Integer> currentOverrides(Supplier<Map<String, Integer>> local) {
        Objects.requireNonNull(local, "local");
        if (connectionSupplier == null) return local.get();
        refreshConnection();
        if (connection == null) return Map.of();
        if (receivedOverrides != null) return receivedOverrides;
        // A remote server's empty TOML list must not enable this client's legacy JSON fallback.
        return integratedServer.getAsBoolean() ? local.get() : Map.of();
    }

    public synchronized int currentGlobalMax(IntSupplier local) {
        Objects.requireNonNull(local, "local");
        if (connectionSupplier == null) return local.getAsInt();
        refreshConnection();
        if (connection == null) return HeartGaugeConfig.defaultGlobalMax();
        if (receivedOverrides != null) return receivedGlobalMax;
        return integratedServer.getAsBoolean() ? local.getAsInt() : HeartGaugeConfig.defaultGlobalMax();
    }

    /** Dimension and player replacements keep the same connection; only reconnects clear it. */
    public synchronized void refreshConnection() {
        Object current = connectionSupplier == null ? null : connectionSupplier.get();
        if (current != connection) {
            connection = current;
            receivedOverrides = null;
        }
    }
}
