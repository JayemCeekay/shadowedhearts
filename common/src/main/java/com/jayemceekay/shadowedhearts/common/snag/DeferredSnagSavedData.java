package com.jayemceekay.shadowedhearts.common.snag;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** World-owned delivery queue for won NPC snags. Unresolved battle captures must never enter this store. */
public final class DeferredSnagSavedData extends SavedData {
    public static final String DATA_ID = "shadowedhearts_deferred_snags";
    private static final Factory<DeferredSnagSavedData> FACTORY =
            new Factory<>(DeferredSnagSavedData::new, DeferredSnagSavedData::load, null);
    private final Map<UUID, Entry> pending = new LinkedHashMap<>();
    private final Set<UUID> rejectedIds = new LinkedHashSet<>();
    // Kept outside Entries so a conflicting record cannot become deliverable after the first is removed.
    private final ListTag rejected = new ListTag();
    private CompoundTag unsupportedRoot;

    public DeferredSnagSavedData() { }

    /** Encoded Pokemon data is copied on both ingress and access, including nested tags. */
    public record Entry(UUID player, UUID pokemon, CompoundTag data) {
        public Entry {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(pokemon, "pokemon");
            data = Objects.requireNonNull(data, "data").copy();
        }

        @Override public CompoundTag data() { return data.copy(); }
    }

    public static DeferredSnagSavedData get(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    /**
     * Returns true only for a new UUID. An existing or quarantined UUID is never replaced or reassigned.
     * The caller must serialize successfully before queueing and remove only after native storage accepts delivery.
     */
    public boolean queue(UUID player, UUID pokemon, CompoundTag pokemonData) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(pokemon, "pokemon");
        Objects.requireNonNull(pokemonData, "pokemonData");
        if (unsupportedRoot != null) throw new IllegalStateException("Unsupported deferred snag data must be repaired before queueing");
        if (pending.containsKey(pokemon) || rejectedIds.contains(pokemon)) return false;
        pending.put(pokemon, new Entry(player, pokemon, pokemonData));
        setDirty();
        return true;
    }

    /** Detached, immutable snapshot in queue order; later delivery/removal does not change this list. */
    public List<Entry> entries(UUID player) {
        Objects.requireNonNull(player, "player");
        return pending.values().stream().filter(entry -> entry.player().equals(player)).toList();
    }

    public boolean remove(UUID pokemon) {
        Objects.requireNonNull(pokemon, "pokemon");
        if (pending.remove(pokemon) == null) return false;
        setDirty();
        return true;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unsupportedRoot != null) return unsupportedRoot.copy();
        ListTag entries = new ListTag();
        for (Entry entry : pending.values()) {
            CompoundTag record = new CompoundTag();
            record.putUUID("Player", entry.player());
            record.putUUID("PokemonId", entry.pokemon());
            record.put("Pokemon", entry.data());
            entries.add(record);
        }
        tag.putInt("Version", 1);
        tag.put("Entries", entries);
        tag.put("Rejected", rejected.copy());
        return tag;
    }

    public static DeferredSnagSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DeferredSnagSavedData data = new DeferredSnagSavedData();
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !isCompoundList(tag, "Entries")
                || tag.contains("Rejected") && !isCompoundList(tag, "Rejected")) {
            data.unsupportedRoot = tag.copy();
            return data;
        }
        for (Tag raw : tag.getList("Rejected", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            data.rejected.add(record.copy());
            if (record.hasUUID("PokemonId")) data.rejectedIds.add(record.getUUID("PokemonId"));
        }
        Map<UUID, CompoundTag> originals = new LinkedHashMap<>();
        for (Tag raw : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            if (!record.hasUUID("Player") || !record.hasUUID("PokemonId")
                    || !record.contains("Pokemon", Tag.TAG_COMPOUND)) {
                data.reject(record, originals);
                continue;
            }
            UUID id = record.getUUID("PokemonId");
            Entry entry = new Entry(record.getUUID("Player"), id, record.getCompound("Pokemon"));
            if (data.rejectedIds.contains(id)) {
                data.reject(record, originals);
                continue;
            }
            Entry existing = data.pending.get(id);
            if (existing != null) {
                if (!existing.equals(entry)) data.reject(record, originals);
                continue;
            }
            data.pending.put(id, entry);
            originals.put(id, record.copy());
        }
        return data;
    }

    private void reject(CompoundTag record, Map<UUID, CompoundTag> originals) {
        if (record.hasUUID("PokemonId")) {
            UUID id = record.getUUID("PokemonId");
            rejectedIds.add(id);
            pending.remove(id);
            CompoundTag prior = originals.remove(id);
            if (prior != null) rejected.add(prior);
        }
        rejected.add(record.copy());
    }

    private static boolean isCompoundList(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) return false;
        ListTag list = (ListTag) tag.get(key);
        return list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND;
    }
}
