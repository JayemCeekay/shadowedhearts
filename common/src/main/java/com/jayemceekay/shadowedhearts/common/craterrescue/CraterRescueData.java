package com.jayemceekay.shadowedhearts.common.craterrescue;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** A site rolls once. Completed and skipped sites require only a UUID. */
final class CraterRescueData extends SavedData {
    private static final Factory<CraterRescueData> FACTORY = new Factory<>(CraterRescueData::new, CraterRescueData::load, null);
    final Map<UUID, CraterRescueEvent> active = new LinkedHashMap<>();
    final Set<UUID> closed = new HashSet<>();
    final Set<UUID> taught = new HashSet<>();
    private final ListTag rejected = new ListTag();

    static CraterRescueData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "shadowedhearts_crater_rescues");
    }

    void close(UUID site) {
        active.remove(site);
        closed.add(site);
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag events = rejected.copy();
        active.values().forEach(event -> events.add(event.save()));
        tag.put("Events", events);
        tag.put("Closed", uuids(closed));
        tag.put("Tutorial", uuids(taught));
        return tag;
    }

    private static ListTag uuids(Set<UUID> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(NbtUtils.createUUID(id)));
        return list;
    }

    static CraterRescueData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new CraterRescueData();
        for (Tag id : tag.getList("Closed", Tag.TAG_INT_ARRAY)) data.closed.add(NbtUtils.loadUUID(id));
        for (Tag id : tag.getList("Tutorial", Tag.TAG_INT_ARRAY)) data.taught.add(NbtUtils.loadUUID(id));
        for (Tag entry : tag.getList("Events", Tag.TAG_COMPOUND)) {
            CompoundTag raw = (CompoundTag) entry;
            try {
                var event = CraterRescueEvent.load(raw);
                if (!data.closed.contains(event.site)) data.active.put(event.site, event);
            } catch (IllegalArgumentException invalid) {
                if (raw.hasUUID("Site")) data.close(raw.getUUID("Site"));
                data.rejected.add(raw.copy());
            }
        }
        return data;
    }
}
