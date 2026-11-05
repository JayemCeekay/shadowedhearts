package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** World-owned records outlive mined cores, expired signals, and chunk unloads. */
public final class UmbrafallSiteData extends SavedData {
    public enum Placement { PLACING, READY, PAUSED, CANCELLED }

    public static final class Site {
        public final UUID id;
        public final BlockPos center;
        public final BlockPos core;
        public final UmbrafallTier tier;
        public final long seed;
        public final UmbrafallGeometry geometry;
        private final int[] terrainOffsets;
        // Preserve unsupported data so another save cannot turn it into a resumable flat site.
        private final CompoundTag rejectedTerrainProfile;
        private UmbrafallShapeBuilder cachedShape;
        public UUID owner;
        public long signalExpires;
        /** A committed site's owner has not yet received its completion handoff in this dimension. */
        public boolean pendingSignalAcknowledgement;
        public UmbrafallCoreState state = UmbrafallCoreState.DORMANT;
        public Placement placement = Placement.READY;
        public int cursor;
        public boolean rewardClaimed;

        public Site(UUID id, BlockPos center, BlockPos core, UmbrafallTier tier, long seed) {
            this(id, center, core, tier, seed, UmbrafallGeometry.CURRENT, null);
        }

        public Site(UUID id, BlockPos center, BlockPos core, UmbrafallTier tier, long seed, int[] terrainOffsets) {
            this(id, center, core, tier, seed, UmbrafallGeometry.CURRENT, terrainOffsets);
        }

        public Site(UUID id, BlockPos center, BlockPos core, UmbrafallTier tier, long seed, UmbrafallGeometry geometry) {
            this(id, center, core, tier, seed, geometry, null);
        }

        public Site(UUID id, BlockPos center, BlockPos core, UmbrafallTier tier, long seed,
                    UmbrafallGeometry geometry, int[] terrainOffsets) {
            this(id, center, core, tier, seed, geometry, terrainOffsets, null);
        }

        private Site(UUID id, BlockPos center, BlockPos core, UmbrafallTier tier, long seed,
                     UmbrafallGeometry geometry, int[] terrainOffsets, CompoundTag rejectedTerrainProfile) {
            this.id = id; this.center = center.immutable(); this.core = core.immutable(); this.tier = tier; this.seed = seed;
            this.geometry = Objects.requireNonNull(geometry);
            if (terrainOffsets != null) {
                if (terrainOffsets.length != geometry.diameter(tier) * geometry.diameter(tier)) {
                    throw new IllegalArgumentException("Umbrafall terrain profile must cover the complete footprint");
                }
                int limit = Math.max(16, geometry.radius(tier));
                for (int offset : terrainOffsets) {
                    if (offset < -limit || offset > limit) {
                        throw new IllegalArgumentException("Umbrafall terrain profile exceeds the supported height range");
                    }
                }
            }
            this.terrainOffsets = terrainOffsets == null ? null : terrainOffsets.clone();
            this.rejectedTerrainProfile = rejectedTerrainProfile == null ? null : rejectedTerrainProfile.copy();
        }

        public boolean hasValidTerrainProfile() { return rejectedTerrainProfile == null; }

        public boolean hasPendingPlacement() { return placement == Placement.PLACING || placement == Placement.PAUSED; }

        /** Retain site identity and its original plan; cancellation never refunds or rewinds terrain. */
        public boolean cancelPlacement() {
            if (!hasPendingPlacement()) return false;
            placement = Placement.CANCELLED;
            normalizeCancelledState();
            return true;
        }

        private void normalizeCancelledState() {
            state = UmbrafallCoreState.DEPLETED;
            rewardClaimed = true;
            signalExpires = 0;
            pendingSignalAcknowledgement = false;
        }

        /** Invalid records keep a conservative reservation without constructing an unsupported shape. */
        public int footprintRadius() {
            return hasValidTerrainProfile() ? geometry.radius(tier)
                    : Math.max(UmbrafallGeometry.LEGACY_V1.radius(tier), UmbrafallGeometry.CURRENT.radius(tier));
        }

        /** Null identifies a flat plan within its saved geometry version. Returned profiles are detached. */
        public int[] terrainOffsets() {
            requireValidTerrainProfile();
            return terrainOffsets == null ? null : terrainOffsets.clone();
        }

        /** Placement resumes from the saved surface, never from terrain already changed by the impact. */
        public UmbrafallShapeBuilder shape() {
            requireValidTerrainProfile();
            if (cachedShape == null) {
                cachedShape = terrainOffsets == null ? new UmbrafallShapeBuilder(tier, seed, geometry)
                        : new UmbrafallShapeBuilder(tier, seed, geometry, terrainOffsets);
            }
            return cachedShape;
        }

        private void requireValidTerrainProfile() {
            if (!hasValidTerrainProfile()) {
                throw new IllegalStateException("Unsupported or invalid geometry/terrain profile for Umbrafall site " + id);
            }
        }

        public boolean canLocate() {
            return hasValidTerrainProfile() && placement == Placement.READY && owner == null && state.active() && !rewardClaimed;
        }

        public boolean hasGuidanceFor(UUID playerId, long nowTick) {
            return playerId.equals(owner) && hasValidTerrainProfile() && placement == Placement.READY && signalExpires >= nowTick;
        }
    }

    public final Map<UUID, Site> sites = new LinkedHashMap<>();
    private static final Factory<UmbrafallSiteData> FACTORY = new Factory<>(UmbrafallSiteData::new, UmbrafallSiteData::load, null);

    public static UmbrafallSiteData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "shadowedhearts_umbrafall_sites");
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Site site : sites.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", site.id);
            entry.putLong("Center", site.center.asLong());
            entry.putLong("Core", site.core.asLong());
            entry.putString("Tier", site.tier.name());
            entry.putLong("Seed", site.seed);
            if (site.rejectedTerrainProfile != null) {
                for (String key : site.rejectedTerrainProfile.getAllKeys()) {
                    entry.put(key, site.rejectedTerrainProfile.get(key).copy());
                }
            } else {
                entry.putInt("ShapeVersion", site.geometry.version());
                if (site.terrainOffsets != null) {
                    entry.putInt("TerrainProfileVersion", 1);
                    entry.putIntArray("TerrainOffsets", site.terrainOffsets.clone());
                }
            }
            if (site.owner != null) entry.putUUID("Owner", site.owner);
            entry.putLong("SignalExpires", site.signalExpires);
            entry.putBoolean("PendingSignalAcknowledgement", site.pendingSignalAcknowledgement);
            entry.putString("State", site.state.name());
            entry.putString("Placement", (site.hasValidTerrainProfile() || site.placement == Placement.CANCELLED
                    ? site.placement : Placement.PAUSED).name());
            entry.putInt("Cursor", site.cursor);
            entry.putBoolean("RewardClaimed", site.rewardClaimed);
            entries.add(entry);
        }
        tag.put("Sites", entries);
        tag.putInt("Version", 2);
        return tag;
    }

    public static UmbrafallSiteData load(CompoundTag tag, HolderLookup.Provider registries) {
        UmbrafallSiteData data = new UmbrafallSiteData();
        for (Tag raw : tag.getList("Sites", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id")) continue;
            UmbrafallTier tier;
            try { tier = UmbrafallTier.valueOf(entry.getString("Tier")); }
            catch (IllegalArgumentException ignored) { continue; }
            Site site = loadSiteProfile(entry, tier);
            if (entry.hasUUID("Owner")) site.owner = entry.getUUID("Owner");
            site.signalExpires = entry.getLong("SignalExpires");
            site.pendingSignalAcknowledgement = entry.getBoolean("PendingSignalAcknowledgement");
            site.state = UmbrafallCoreState.load(entry.getString("State"));
            try { site.placement = Placement.valueOf(entry.getString("Placement")); }
            catch (IllegalArgumentException ignored) { site.placement = Placement.PAUSED; }
            if (!site.hasValidTerrainProfile() && site.placement != Placement.CANCELLED) site.placement = Placement.PAUSED;
            site.cursor = Math.max(0, entry.getInt("Cursor"));
            site.rewardClaimed = entry.getBoolean("RewardClaimed");
            if (site.placement == Placement.CANCELLED) site.normalizeCancelledState();
            data.sites.put(site.id, site);
        }
        return data;
    }

    private static Site loadSiteProfile(CompoundTag entry, UmbrafallTier tier) {
        UUID id = entry.getUUID("Id");
        BlockPos center = BlockPos.of(entry.getLong("Center"));
        BlockPos core = BlockPos.of(entry.getLong("Core"));
        long seed = entry.getLong("Seed");
        UmbrafallGeometry geometry = UmbrafallGeometry.LEGACY_V1;
        if (entry.contains("ShapeVersion")) {
            if (!entry.contains("ShapeVersion", Tag.TAG_INT)) {
                return rejectedProfile(entry, id, center, core, tier, seed, geometry);
            }
            try {
                geometry = UmbrafallGeometry.fromVersion(entry.getInt("ShapeVersion"));
            } catch (IllegalArgumentException ignored) {
                return rejectedProfile(entry, id, center, core, tier, seed, geometry);
            }
        }
        if (!entry.contains("TerrainProfileVersion") && !entry.contains("TerrainOffsets")) {
            return new Site(id, center, core, tier, seed, geometry);
        }
        if (entry.contains("TerrainProfileVersion", Tag.TAG_INT)
                && entry.getInt("TerrainProfileVersion") == 1 && entry.contains("TerrainOffsets", Tag.TAG_INT_ARRAY)) {
            try {
                return new Site(id, center, core, tier, seed, geometry, entry.getIntArray("TerrainOffsets"));
            } catch (IllegalArgumentException ignored) {
                // A corrupt or unsupported profile must not silently become legacy geometry.
            }
        }
        return rejectedProfile(entry, id, center, core, tier, seed, geometry);
    }

    private static Site rejectedProfile(CompoundTag entry, UUID id, BlockPos center, BlockPos core,
                                        UmbrafallTier tier, long seed, UmbrafallGeometry geometry) {
        CompoundTag profile = new CompoundTag();
        for (String key : new String[]{"ShapeVersion", "TerrainProfileVersion", "TerrainOffsets"}) {
            if (entry.contains(key)) profile.put(key, entry.get(key).copy());
        }
        return new Site(id, center, core, tier, seed, geometry, null, profile);
    }
}
