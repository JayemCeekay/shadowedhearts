package com.jayemceekay.shadowedhearts.common.aura;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-owned discovery grants. Reading refreshes never extend a pulse grant. */
public final class AuraReaderContacts {
    public static final int CONTACT_LIFETIME_TICKS = 30 * 20;

    private final Map<UUID, Long> discoveries = new LinkedHashMap<>();
    private String dimension = "";
    private UUID pinned;

    public void observeDimension(String nextDimension) {
        String next = nextDimension == null ? "" : nextDimension;
        if (!dimension.equals(next)) {
            discoveries.clear();
            pinned = null;
            dimension = next;
        }
    }

    public void discover(UUID id, long nowTick) {
        discoveries.put(Objects.requireNonNull(id), nowTick + CONTACT_LIFETIME_TICKS);
    }

    public boolean isDiscovered(UUID id, long nowTick) {
        return id != null && discoveries.getOrDefault(id, 0L) > nowTick;
    }

    public boolean isAuthorized(UUID id, long nowTick) {
        return id != null && (id.equals(pinned) || isDiscovered(id, nowTick));
    }

    public long expiryTick(UUID id) {
        return discoveries.getOrDefault(id, 0L);
    }

    public List<UUID> contactIds(long nowTick) {
        discoveries.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
        var ids = new LinkedHashSet<>(discoveries.keySet());
        if (pinned != null) ids.add(pinned);
        return List.copyOf(ids);
    }

    /** Pinning a selected signal requires a fresh server discovery. */
    public boolean pinDiscovered(UUID id, long nowTick) {
        if (!isAuthorized(id, nowTick)) return false;
        pinned = id;
        return true;
    }

    /** Only the server's unobstructed reticle hit may use this path. */
    public void pinVisible(UUID id) {
        pinned = Objects.requireNonNull(id);
    }

    public UUID pinnedId() {
        return pinned;
    }

    public void clearPin() {
        pinned = null;
    }

    public void clear() {
        discoveries.clear();
        pinned = null;
        dimension = "";
    }

    /** Includes expired entries until pruning, so lifecycle cleanup can recognize a saved selection. */
    public boolean knows(UUID id) {
        return id != null && (id.equals(pinned) || discoveries.containsKey(id));
    }

    public void forget(UUID id) {
        discoveries.remove(id);
        if (Objects.equals(id, pinned)) pinned = null;
    }

    public static boolean usable(boolean alive, boolean shadow, double distanceSquared, double range) {
        return alive && shadow && Double.isFinite(distanceSquared) && distanceSquared >= 0.0
                && Double.isFinite(range) && range > 0.0 && distanceSquared <= range * range;
    }
}
