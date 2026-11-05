package com.jayemceekay.shadowedhearts.common.snag;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/** Connection-scoped settings; local config is never substituted for a remote server's values. */
public final class SnagMachineClientState {
    public static final SnagMachineClientState INSTANCE = new SnagMachineClientState();

    private Supplier<?> connectionSupplier;
    private Object connection;
    private SnagMachineSettings receivedSettings;

    public synchronized void initialize(Supplier<?> connectionSupplier) {
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier");
        connection = null;
        receivedSettings = null;
    }

    public synchronized boolean receive(Object packetConnection, SnagMachineSettings settings) {
        if (connectionSupplier == null) return false;
        refreshConnection();
        if (connection == null || connection != packetConnection) return false;
        receivedSettings = Objects.requireNonNull(settings, "settings");
        return true;
    }

    /** Null means this connection has not supplied authoritative settings yet. */
    @Nullable
    public synchronized SnagMachineSettings currentSettings(Supplier<SnagMachineSettings> serverFallback) {
        Objects.requireNonNull(serverFallback, "serverFallback");
        if (connectionSupplier == null) return serverFallback.get();
        refreshConnection();
        return receivedSettings;
    }

    public synchronized void refreshConnection() {
        Object current = connectionSupplier == null ? null : connectionSupplier.get();
        if (current != connection) {
            connection = current;
            receivedSettings = null;
        }
    }
}
