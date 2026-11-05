package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraStyleDefault;

import java.util.Objects;
import java.util.function.Supplier;

/** Live server defaults belong to a connection, independently of its current dimension or player. */
public final class AuraStyleClientState {
    public static final AuraStyleClientState INSTANCE = new AuraStyleClientState();

    private Supplier<?> connectionSupplier;
    private Object connection;
    private AuraStyleDefault receivedDefault;

    /** Installed only by client initialization; native server callers retain the config fallback. */
    public synchronized void initialize(Supplier<?> connectionSupplier) {
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier");
        connection = null;
        receivedDefault = null;
    }

    /** Rejects a packet captured on a connection which has since closed or been replaced. */
    public synchronized boolean receive(Object packetConnection, AuraStyleDefault style) {
        if (connectionSupplier == null) return false;
        refreshConnection();
        if (connection == null || connection != packetConnection) return false;
        receivedDefault = Objects.requireNonNull(style, "style");
        return true;
    }

    public synchronized AuraStyleDefault currentDefault(Supplier<AuraStyleDefault> configFallback) {
        Objects.requireNonNull(configFallback, "configFallback");
        if (connectionSupplier == null) return fallback(configFallback);
        refreshConnection();
        // Some loaders retain their last remote config snapshot after disconnect.
        if (connection == null) return AuraStyleDefault.SIGNATURE;
        return receivedDefault != null ? receivedDefault : fallback(configFallback);
    }

    /** Also called each client tick so disconnect releases the previous connection promptly. */
    public synchronized void refreshConnection() {
        Object current = connectionSupplier == null ? null : connectionSupplier.get();
        if (current != connection) {
            connection = current;
            receivedDefault = null;
        }
    }

    private static AuraStyleDefault fallback(Supplier<AuraStyleDefault> supplier) {
        AuraStyleDefault style = supplier.get();
        return style == null ? AuraStyleDefault.SIGNATURE : style;
    }
}
