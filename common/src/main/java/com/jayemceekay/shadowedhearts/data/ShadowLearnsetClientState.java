package com.jayemceekay.shadowedhearts.data;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Publishes complete snapshots only, scoped to the network connection that supplied them. */
public final class ShadowLearnsetClientState {
    public static final ShadowLearnsetClientState INSTANCE = new ShadowLearnsetClientState();
    private Supplier<?> connectionSupplier;
    private Object connection;
    private Map<ResourceLocation, ShadowLearnset> incoming;
    private Map<ResourceLocation, ShadowLearnset> current = Map.of();

    public synchronized void initialize(Supplier<?> supplier) {
        connectionSupplier = supplier;
        connection = null;
        incoming = null;
        current = Map.of();
    }

    public synchronized void refreshConnection() {
        Object next = connectionSupplier == null ? null : connectionSupplier.get();
        if (next != connection) {
            connection = next;
            incoming = null;
            current = Map.of();
        }
    }

    public synchronized void receive(Object source, boolean first, boolean last, Map<ResourceLocation, ShadowLearnset> batch) {
        refreshConnection();
        if (source == null || source != connection) return;
        if (first) incoming = new HashMap<>();
        if (incoming == null) return;
        incoming.putAll(batch);
        if (last) {
            current = Map.copyOf(incoming);
            incoming = null;
        }
    }

    @Nullable
    public synchronized ShadowLearnset get(ResourceLocation species) {
        refreshConnection();
        return current.get(species);
    }
}
