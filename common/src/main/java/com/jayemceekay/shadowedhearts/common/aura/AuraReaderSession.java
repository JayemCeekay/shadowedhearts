package com.jayemceekay.shadowedhearts.common.aura;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuraReaderSession {
    private final UUID playerId;
    private AuraReaderMode mode = AuraReaderMode.PASSIVE_AURA;
    private String selectedReadingId = "";
    private boolean active;
    private boolean explicitTracking;
    private long lastPulseTick;
    private long pulseCooldownEndTick;
    private long lastSnapshotSentTick;
    private boolean dirty = true;
    private final List<AuraReading> cachedReadings = new ArrayList<>();
    private final AuraReaderContacts contacts = new AuraReaderContacts();

    public AuraReaderSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public AuraReaderMode getMode() {
        return mode;
    }

    public void setMode(AuraReaderMode mode) {
        this.mode = AuraReaderMode.PASSIVE_AURA;
        this.dirty = true;
    }

    public String getSelectedReadingId() {
        return selectedReadingId;
    }

    public void setSelectedReadingId(String selectedReadingId) {
        this.selectedReadingId = selectedReadingId == null ? "" : selectedReadingId;
        this.dirty = true;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.dirty = true;
    }

    public long getLastPulseTick() {
        return lastPulseTick;
    }

    public void setLastPulseTick(long lastPulseTick) {
        this.lastPulseTick = lastPulseTick;
    }

    public long getPulseCooldownEndTick() {
        return pulseCooldownEndTick;
    }

    public void setPulseCooldownEndTick(long pulseCooldownEndTick) {
        this.pulseCooldownEndTick = pulseCooldownEndTick;
        this.dirty = true;
    }

    public long getLastSnapshotSentTick() {
        return lastSnapshotSentTick;
    }

    public void setLastSnapshotSentTick(long lastSnapshotSentTick) {
        this.lastSnapshotSentTick = lastSnapshotSentTick;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        this.dirty = false;
    }

    public List<AuraReading> getCachedReadings() {
        return List.copyOf(cachedReadings);
    }

    public AuraReaderContacts contacts() {
        return contacts;
    }

    public boolean isExplicitTracking() {
        return explicitTracking;
    }

    public boolean isPokemonSelection(String id) {
        if (id == null || id.isBlank()) return false;
        if (cachedReadings.stream().anyMatch(reading -> reading.id().equals(id)
                && reading.type() == AuraReadingType.SHADOW_POKEMON)) return true;
        try {
            return contacts.knows(UUID.fromString(id));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public void setExplicitTracking(boolean explicitTracking) {
        this.explicitTracking = explicitTracking;
    }

    public void replaceCachedReadings(List<AuraReading> readings) {
        this.cachedReadings.clear();
        if (readings != null) {
            this.cachedReadings.addAll(readings);
        }
        this.dirty = true;
    }
}
