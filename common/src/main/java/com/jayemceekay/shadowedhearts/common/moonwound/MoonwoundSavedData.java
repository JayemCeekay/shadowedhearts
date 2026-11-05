package com.jayemceekay.shadowedhearts.common.moonwound;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One store per dimension, retaining completed and rejected site records to prevent event/reward resets. */
public final class MoonwoundSavedData extends SavedData {
    private static final Factory<MoonwoundSavedData> FACTORY =
            new Factory<>(MoonwoundSavedData::new, MoonwoundSavedData::load, null);
    private final Map<UUID, MoonwoundEvent> events = new LinkedHashMap<>();
    private final Set<UUID> quarantinedSites = new LinkedHashSet<>();
    private final ListTag rejected = new ListTag();
    private CompoundTag unsupportedRoot;
    private boolean unidentifiedRecord;

    public static MoonwoundSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "shadowedhearts_moonwounds");
    }

    public MoonwoundEvent find(UUID siteId) { return events.get(siteId); }
    public Collection<MoonwoundEvent> events() { return Collections.unmodifiableCollection(events.values()); }
    public Set<UUID> quarantinedSites() { return Collections.unmodifiableSet(quarantinedSites); }
    public boolean isReserved(UUID siteId) {
        return unsupportedRoot != null || unidentifiedRecord || events.containsKey(siteId) || quarantinedSites.contains(siteId);
    }

    public boolean add(MoonwoundEvent event) {
        if (isReserved(event.siteId()) || events.values().stream().anyMatch(existing -> existing.eventId().equals(event.eventId()))) return false;
        events.put(event.siteId(), event);
        event.onChanged(this::setDirty);
        setDirty();
        return true;
    }

    /** Cancelling preparation is reversible; activated/completed/quarantined sites cannot be reset. */
    public boolean removeLobby(UUID siteId) {
        MoonwoundEvent event = events.get(siteId);
        if (unsupportedRoot != null || unidentifiedRecord || event == null || event.phase() != MoonwoundEvent.Phase.LOBBY) return false;
        events.remove(siteId);
        event.onChanged(() -> {});
        setDirty();
        return true;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unsupportedRoot != null) return unsupportedRoot.copy();
        ListTag entries = new ListTag();
        for (MoonwoundEvent event : events.values()) entries.add(event.save());
        for (Tag entry : rejected) entries.add(entry.copy());
        tag.putInt("Version", 1);
        tag.put("Events", entries);
        return tag;
    }

    public static MoonwoundSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MoonwoundSavedData data = new MoonwoundSavedData();
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.contains("Events", Tag.TAG_LIST)) {
            data.unsupportedRoot = tag.copy();
            return data;
        }
        ListTag entries = (ListTag) tag.get("Events");
        if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            data.unsupportedRoot = tag.copy();
            return data;
        }
        Map<UUID, UUID> sitesByEvent = new LinkedHashMap<>();
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Site")) {
                data.unidentifiedRecord = true;
                data.rejected.add(entry.copy());
                continue;
            }
            UUID siteId = entry.getUUID("Site");
            MoonwoundEvent duplicate = data.events.remove(siteId);
            if (duplicate != null) {
                data.quarantine(duplicate.save(), siteId);
                data.quarantine(entry, siteId);
                continue;
            }
            if (data.quarantinedSites.contains(siteId)) {
                data.quarantine(entry, siteId);
                continue;
            }
            try {
                MoonwoundEvent event = MoonwoundEvent.load(entry);
                UUID priorSite = sitesByEvent.putIfAbsent(event.eventId(), siteId);
                if (priorSite != null) {
                    MoonwoundEvent prior = data.events.remove(priorSite);
                    if (prior != null) data.quarantine(prior.save(), priorSite);
                    data.quarantine(entry, siteId);
                    continue;
                }
                data.events.put(siteId, event);
                event.onChanged(data::setDirty);
            } catch (IllegalArgumentException exception) {
                data.quarantine(entry, siteId);
            }
        }
        return data;
    }

    private void quarantine(CompoundTag entry, UUID siteId) {
        quarantinedSites.add(siteId);
        rejected.add(entry.copy());
    }
}
