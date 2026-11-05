package com.jayemceekay.shadowedhearts.common.shadow;

import java.util.function.Supplier;

/** Live event-window state scoped to the connection which supplied it. */
public final class MoveLifecycleClientState {
    public static final MoveLifecycleClientState INSTANCE = new MoveLifecycleClientState();
    private Supplier<?> supplier;
    private Object connection;
    private boolean allowed;
    private MoveLifecycleClientState() { }
    public synchronized void initialize(Supplier<?> supplier) { this.supplier = supplier; connection = null; allowed = false; }
    public synchronized void refreshConnection() {
        Object current = supplier == null ? null : supplier.get();
        if (current != connection) { connection = current; allowed = false; }
    }
    public synchronized void receive(Object source, boolean value) {
        refreshConnection();
        if (source != null && source == connection) allowed = value;
    }
    public synchronized boolean allowed() { refreshConnection(); return allowed; }
}
