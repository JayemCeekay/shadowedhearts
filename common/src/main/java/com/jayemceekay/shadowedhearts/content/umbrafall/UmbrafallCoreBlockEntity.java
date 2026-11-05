package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallGeometry;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class UmbrafallCoreBlockEntity extends BlockEntity {
    private UmbrafallTier tier = UmbrafallTier.WEAK;
    private UmbrafallGeometry geometry = UmbrafallGeometry.CURRENT;
    private Tag rejectedShapeVersion;
    private UmbrafallCoreState state = UmbrafallCoreState.DORMANT;
    private UUID siteId;
    private UUID owner;
    private BlockPos center;
    private long seed;
    private int disturbance;
    // Preserve old saves' progress while stabilization is deferred.
    private int stabilization;
    private boolean resolved;

    public UmbrafallCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UMBRAFALL_CORE.get(), pos, state);
        center = pos;
    }

    public boolean belongsToSite(UUID id) { return id != null && id.equals(siteId); }

    /** A cancelled reservation may outlive a core write which never reached configure(). */
    public boolean retireCancelled(ServerLevel world) {
        if (level != world || world.getBlockEntity(worldPosition) != this) return false;
        var cancelled = cancelledRecord(world);
        if (cancelled == null) return false;
        bindCancelled(cancelled);
        return world.setBlockAndUpdate(worldPosition, ModBlocks.UMBRAFALL_SHELL.get().defaultBlockState())
                || world.getBlockState(worldPosition).is(ModBlocks.UMBRAFALL_SHELL.get());
    }

    private UmbrafallSiteData.Site cancelledRecord(ServerLevel world) {
        var sites = UmbrafallSiteData.get(world).sites;
        if (siteId != null) {
            var known = sites.get(siteId);
            return known != null && known.placement == UmbrafallSiteData.Placement.CANCELLED ? known : null;
        }
        var reservation = reservationAtCore(world);
        return reservation != null && reservation.placement == UmbrafallSiteData.Placement.CANCELLED ? reservation : null;
    }

    private UmbrafallSiteData.Site reservationAtCore(ServerLevel world) {
        return UmbrafallSiteData.get(world).sites.values().stream()
                .filter(site -> site.core.equals(worldPosition)
                        && (site.hasPendingPlacement() || site.placement == UmbrafallSiteData.Placement.CANCELLED))
                .findFirst().orElse(null);
    }

    private void bindCancelled(UmbrafallSiteData.Site site) {
        siteId = site.id;
        state = UmbrafallCoreState.DEPLETED;
        resolved = true;
        setChanged();
    }

    /** Safe during worldgen: writes only this block entity, never world SavedData. */
    public void configure(UmbrafallTier tier, UUID siteId, UUID owner, BlockPos center, long seed) {
        configure(tier, siteId, owner, center, seed, UmbrafallGeometry.CURRENT);
    }

    /** Geometry is part of the saved site's identity, including when an older core creates its record. */
    public void configure(UmbrafallTier tier, UUID siteId, UUID owner, BlockPos center, long seed,
                          UmbrafallGeometry geometry) {
        this.tier = tier;
        this.geometry = java.util.Objects.requireNonNull(geometry);
        this.rejectedShapeVersion = null;
        this.siteId = siteId == null ? UUID.nameUUIDFromBytes((center.asLong() + ":" + seed).getBytes(StandardCharsets.UTF_8)) : siteId;
        this.owner = owner;
        this.center = center.immutable();
        this.seed = seed;
        setChanged();
    }

    private UmbrafallSiteData.Site record(ServerLevel world) {
        if (siteId == null) {
            // Rupture/removal can precede the first tick after a partial core write or reload.
            var reservation = reservationAtCore(world);
            if (reservation != null) {
                if (reservation.placement == UmbrafallSiteData.Placement.CANCELLED) {
                    bindCancelled(reservation);
                    return reservation;
                }
                if (reservation.hasValidTerrainProfile()) {
                    configure(reservation.tier, reservation.id, reservation.owner, reservation.center,
                            reservation.seed, reservation.geometry);
                } else {
                    // Keep unsupported plans reserved without interpreting their geometry.
                    siteId = reservation.id;
                    setChanged();
                }
            } else {
                configure(tier, UUID.randomUUID(), owner, center, seed, geometry);
            }
        }
        UmbrafallSiteData data = UmbrafallSiteData.get(world);
        var site = data.sites.get(siteId);
        if (site == null) {
            site = new UmbrafallSiteData.Site(siteId, center, worldPosition, tier, seed, geometry);
            site.owner = owner;
            site.state = state;
            data.sites.put(siteId, site);
            data.setDirty();
        } else if (!site.state.active() || site.rewardClaimed) {
            state = site.state.active() ? UmbrafallCoreState.DEPLETED : site.state;
            resolved = true;
        }
        return site;
    }

    public void tick() {
        if (!(level instanceof ServerLevel world) || world.getGameTime() % 20 != 0) return;
        // The cancellation receipt outlives loaded chunks and unsupported terrain profiles.
        if (retireCancelled(world)) return;
        if (geometry == null) return;
        var site = record(world);
        if (!site.hasValidTerrainProfile() || site.placement != UmbrafallSiteData.Placement.READY) return;
        if (com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService.controlsCore(world, site.id)) return;
        if (resolved || !state.active()) {
            world.setBlockAndUpdate(worldPosition, ModBlocks.UMBRAFALL_SHELL.get().defaultBlockState());
            return;
        }
        if (state.ordinal() < UmbrafallCoreState.EXPOSED.ordinal()) {
            for (Direction direction : Direction.values()) {
                if (world.getBlockState(worldPosition.relative(direction)).isAir()) {
                    change(UmbrafallCoreState.EXPOSED);
                    break;
                }
            }
        }
        // Surging records excavation disturbance. Moonwound owns Pokemon encounters.
    }

    public void disturbed(Player player) {
        if (geometry == null || !state.active() || resolved) return;
        if (level instanceof ServerLevel world && siteId != null
                && com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService.controlsCore(world, siteId)) return;
        disturbance++;
        change(disturbance >= 3 ? UmbrafallCoreState.SURGING
                : state == UmbrafallCoreState.DORMANT ? UmbrafallCoreState.DISTURBED : state);
        if (disturbance == 3) player.displayClientMessage(Component.translatable("message.shadowedhearts.umbrafall_core.surging"), false);
    }

    public void inspect(Player player) {
        if (geometry == null) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.umbrafall.core_unsupported_geometry"), true);
            return;
        }
        if (state == UmbrafallCoreState.DORMANT || state == UmbrafallCoreState.DISTURBED) change(UmbrafallCoreState.EXPOSED);
        player.displayClientMessage(Component.translatable("message.shadowedhearts.umbrafall.core_status", Component.translatable("umbrafall.tier." + tier.name().toLowerCase(java.util.Locale.ROOT)), Component.translatable("umbrafall.core_state." + state.name().toLowerCase(java.util.Locale.ROOT))), true);
    }

    public void rupture(Player player) {
        if (geometry == null || !(level instanceof ServerLevel world) || resolved) return;
        if (siteId != null && com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService.controlsCore(world, siteId)) return;
        // Breaking a core is the deliberate destructive resolution; it never explodes neighbouring builds.
        finish(world, UmbrafallCoreState.RUPTURED, player, player.hasCorrectToolForDrops(getBlockState()));
    }

    public void removed() {
        if (level instanceof ServerLevel world && siteId != null
                && com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundService.controlsCore(world, siteId)) return;
        if (geometry != null && level instanceof ServerLevel world && !resolved) finish(world, UmbrafallCoreState.RUPTURED, null, false);
    }

    private void finish(ServerLevel world, UmbrafallCoreState outcome, Player player, boolean reward) {
        var site = record(world);
        if (!site.hasValidTerrainProfile() || site.placement != UmbrafallSiteData.Placement.READY) return;
        if (site.rewardClaimed || !site.state.active()) { resolved = true; return; }
        resolved = true;
        state = outcome;
        site.state = outcome;
        // Commit completion before external item grants; all future interactions fail closed.
        site.rewardClaimed = true;
        UmbrafallSiteData.get(world).setDirty();
        setChanged();
        if (player != null && reward && !player.isCreative()) {
            ItemStack drop = new ItemStack(ModItems.CORE_SAMPLE.get(), 1 + tier.ordinal());
            if (!player.getInventory().add(drop)) player.drop(drop, false);
        }
        if (player != null) player.displayClientMessage(Component.translatable("message.shadowedhearts.umbrafall.core_ruptured"), false);
    }

    private void change(UmbrafallCoreState next) {
        if (geometry == null || !state.active() || resolved) return;
        if (level instanceof ServerLevel world) {
            var site = record(world);
            if (resolved || !site.hasValidTerrainProfile() || !site.state.active() || site.placement != UmbrafallSiteData.Placement.READY) return;
            site.state = next;
            UmbrafallSiteData.get(world).setDirty();
        }
        state = next;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Tier", tier.name()); tag.putString("CoreState", state.name());
        if (rejectedShapeVersion != null) tag.put("ShapeVersion", rejectedShapeVersion.copy());
        else tag.putInt("ShapeVersion", geometry.version());
        if (siteId != null) tag.putUUID("SiteId", siteId);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putLong("Center", center.asLong()); tag.putLong("Seed", seed);
        tag.putInt("Disturbance", disturbance); tag.putInt("Stabilization", stabilization);
        tag.putBoolean("Resolved", resolved);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try { tier = UmbrafallTier.valueOf(tag.getString("Tier")); } catch (IllegalArgumentException ignored) { tier = UmbrafallTier.WEAK; }
        geometry = UmbrafallGeometry.LEGACY_V1;
        rejectedShapeVersion = null;
        if (tag.contains("ShapeVersion")) {
            try {
                if (!tag.contains("ShapeVersion", Tag.TAG_INT)) throw new IllegalArgumentException("Malformed shape version");
                geometry = UmbrafallGeometry.fromVersion(tag.getInt("ShapeVersion"));
            } catch (IllegalArgumentException exception) {
                geometry = null;
                rejectedShapeVersion = tag.get("ShapeVersion").copy();
            }
        }
        state = tag.contains("CoreState") ? UmbrafallCoreState.load(tag.getString("CoreState")) : UmbrafallCoreState.DORMANT;
        siteId = tag.hasUUID("SiteId") ? tag.getUUID("SiteId") : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        center = tag.contains("Center") ? BlockPos.of(tag.getLong("Center")) : worldPosition;
        seed = tag.getLong("Seed"); disturbance = Math.max(0, tag.getInt("Disturbance"));
        stabilization = Math.max(0, Math.min(3, tag.getInt("Stabilization")));
        resolved = tag.getBoolean("Resolved");
    }
}
