package com.jayemceekay.shadowedhearts.common.shadow.battle;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/** A queued packet from an old connection can never annotate a new server's rules. */
public final class ShadowBattleClientState {
    public static final ShadowBattleClientState INSTANCE = new ShadowBattleClientState();
    private Supplier<?> connectionSupplier;
    private Object connection;
    private ShadowBattleSettings settings;

    public synchronized void initialize(Supplier<?> supplier) {
        connectionSupplier = Objects.requireNonNull(supplier);
        connection = null;
        settings = null;
    }

    public synchronized void refreshConnection() {
        Object current = connectionSupplier == null ? null : connectionSupplier.get();
        if (current != connection) {
            connection = current;
            settings = null;
        }
    }

    public synchronized boolean receive(Object packetConnection, ShadowBattleSettings snapshot) {
        refreshConnection();
        if (connection == null || connection != packetConnection) return false;
        settings = Objects.requireNonNull(snapshot);
        return true;
    }

    @Nullable
    public synchronized ShadowBattleSettings current() {
        refreshConnection();
        return settings;
    }
}
