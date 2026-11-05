package com.jayemceekay.shadowedhearts.common.shadow.restrictions;

import java.util.Objects;
import java.util.function.Supplier;

/** Connection-scoped snapshots without references to client-only Minecraft classes. */
public final class ShadowRestrictionClientState {
    public static final ShadowRestrictionClientState INSTANCE = new ShadowRestrictionClientState();

    private Supplier<?> connectionSupplier;
    private Object connection;
    private ShadowRestrictionSettings receivedSettings;

    /** Called only during client initialization. */
    public synchronized void initialize(Supplier<?> connectionSupplier) {
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier");
        connection = null;
        receivedSettings = null;
    }

    public synchronized boolean receive(Object packetConnection, ShadowRestrictionSettings settings) {
        if (connectionSupplier == null) return false;
        refreshConnection();
        if (connection == null || connection != packetConnection) return false;
        receivedSettings = Objects.requireNonNull(settings, "settings");
        return true;
    }

    public synchronized ShadowRestrictionSettings currentSettings(Supplier<ShadowRestrictionSettings> configFallback) {
        Objects.requireNonNull(configFallback, "configFallback");
        if (connectionSupplier == null) return fallback(configFallback);
        refreshConnection();
        // A loader may retain a remote config after disconnect; do not expose it in local previews.
        if (connection == null) return ShadowRestrictionSettings.defaults();
        return receivedSettings != null ? receivedSettings : fallback(configFallback);
    }

    public synchronized void refreshConnection() {
        Object current = connectionSupplier == null ? null : connectionSupplier.get();
        if (current != connection) {
            connection = current;
            receivedSettings = null;
        }
    }

    private static ShadowRestrictionSettings fallback(Supplier<ShadowRestrictionSettings> supplier) {
        ShadowRestrictionSettings settings = supplier.get();
        return settings == null ? ShadowRestrictionSettings.defaults() : settings;
    }
}
