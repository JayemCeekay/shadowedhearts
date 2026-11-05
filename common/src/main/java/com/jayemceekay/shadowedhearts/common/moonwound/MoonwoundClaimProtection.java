package com.jayemceekay.shadowedhearts.common.moonwound;

import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.ChunkClaim;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallShapeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Fresh claim checks for event actions, including terrain which the party has already excavated. */
public final class MoonwoundClaimProtection {
    private static final int MAX_BOUNDS_SIDE = 64;
    private static final int MAX_SHAPE_VOLUME = 1_000_000;
    // Site geometry is immutable. Never retain claim or permission answers between operations.
    private static final Map<UmbrafallSiteData.Site, List<BlockPos>> FOOTPRINTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MoonwoundClaimProtection() { }

    /** Authorization is independent of phase, so a finishing event can authorize its last writes. */
    public static boolean canRun(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        if (!matches(level, site, event)) return false;
        for (UUID member : event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (canInteract(player, level, site) && canEditSite(player, level, site)) return true;
        }
        return false;
    }

    public static boolean canSpawn(ServerLevel level, UUID eventId, AABB bounds) {
        Binding binding = activeEvent(level, eventId);
        if (binding == null || !validBounds(level, bounds)) return false;
        for (UUID member : binding.event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (canInteract(player, level, binding.site) && canEditSite(player, level, binding.site)
                    && canEditBounds(player, level, bounds)) return true;
        }
        return false;
    }

    /**
     * Resolve site authorizers once during a synchronous terrain search. The caller must still use
     * canSpawn immediately before spawning; this predicate must not be retained for another tick.
     */
    public static Predicate<AABB> spawnCheck(ServerLevel level, UUID eventId) {
        Binding binding = activeEvent(level, eventId);
        if (binding == null) return bounds -> false;
        List<ServerPlayer> authorizers = new ArrayList<>();
        for (UUID member : binding.event.activeMembers()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (canInteract(player, level, binding.site) && canEditSite(player, level, binding.site)) {
                authorizers.add(player);
            }
        }
        return bounds -> {
            for (ServerPlayer player : authorizers) {
                if (canEditBounds(player, level, bounds)) return true;
            }
            return false;
        };
    }

    public static boolean canParticipate(ServerPlayer player, UUID eventId) {
        if (player == null) return false;
        ServerLevel level = player.serverLevel();
        Binding binding = activeEvent(level, eventId);
        return binding != null && binding.event.isActiveMember(player.getUUID())
                && canInteract(player, level, binding.site) && canRun(level, binding.site, binding.event);
    }

    public static boolean canInteractEncounter(ServerPlayer player, UUID eventId, AABB bounds) {
        if (!canParticipate(player, eventId)) return false;
        ServerLevel level = player.serverLevel();
        if (!validBounds(level, bounds)) return false;
        for (BlockPos pos : positions(bounds)) {
            if (!canInteractAt(player, level, pos)) return false;
        }
        return true;
    }

    public static boolean canInteract(ServerPlayer player, ServerLevel level, UmbrafallSiteData.Site site) {
        return site != null && validPlayer(player, level) && canInteractAt(player, level, site.core);
    }

    public static boolean canEditSite(ServerPlayer player, ServerLevel level, UmbrafallSiteData.Site site) {
        if (!validPlayer(player, level) || !player.mayBuild()) return false;
        List<BlockPos> footprint = footprint(site);
        if (footprint.isEmpty()) return false;
        for (BlockPos pos : footprint) {
            if (!canEditAt(player, level, pos)) return false;
        }
        return true;
    }

    public static boolean canEditBounds(ServerPlayer player, ServerLevel level, AABB bounds) {
        if (!validPlayer(player, level) || !player.mayBuild() || !validBounds(level, bounds)) return false;
        for (BlockPos pos : positions(bounds)) {
            if (!canEditAt(player, level, pos)) return false;
        }
        return true;
    }

    public static boolean canRetireCore(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        return matches(level, site, event) && canRetireBlock(level, site.core, event);
    }

    /** Original members may finish cleanup after leaving; absence of FTB preserves actorless cleanup. */
    public static boolean canRetireBlock(ServerLevel level, BlockPos pos, MoonwoundEvent event) {
        if (event == null || !validPosition(level, pos)) return false;
        if (FTBChunksClaimBridgeHolder.INSTANCE.lookupClaim(level, new ChunkPos(pos)).isAbsent()) return true;
        for (UUID member : event.members()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (validPlayer(player, level) && player.mayBuild() && canEditAt(player, level, pos)) return true;
        }
        return false;
    }

    /** A trusted occupant of any affected claim can release an event which no member can authorize. */
    public static boolean canRecover(ServerPlayer player, ServerLevel level, UmbrafallSiteData.Site site,
                                     MoonwoundEvent event) {
        if (!matches(level, site, event) || event.phase().isTerminal() || !validPlayer(player, level)
                || !player.mayBuild() || canRun(level, site, event)) return false;
        Map<ChunkPos, ChunkClaim> claims = new HashMap<>();
        for (BlockPos pos : footprint(site)) {
            if (!validPosition(level, pos)) continue;
            ChunkPos chunk = new ChunkPos(pos);
            ChunkClaim claim = claims.computeIfAbsent(chunk,
                    key -> FTBChunksClaimBridgeHolder.INSTANCE.lookupClaim(level, key));
            if (claim.isClaimed() && canEditAt(player, level, pos)) return true;
        }
        return false;
    }

    private static boolean canInteractAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return validPosition(level, pos) && level.mayInteract(player, pos)
                && FTBChunksClaimBridgeHolder.INSTANCE.canInteractBlock(player, pos, InteractionHand.MAIN_HAND);
    }

    private static boolean canEditAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return validPosition(level, pos) && level.mayInteract(player, pos)
                && FTBChunksClaimBridgeHolder.INSTANCE.canEditBlock(player, pos, InteractionHand.MAIN_HAND);
    }

    private static boolean validPlayer(ServerPlayer player, ServerLevel level) {
        return level != null && level.getServer().isSameThread() && player != null && player.serverLevel() == level
                && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static boolean validPosition(ServerLevel level, BlockPos pos) {
        return level != null && level.getServer().isSameThread() && pos != null
                && !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static boolean validBounds(ServerLevel level, AABB bounds) {
        if (level == null || !level.getServer().isSameThread() || bounds == null
                || !Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
                || bounds.getXsize() <= 0 || bounds.getXsize() > MAX_BOUNDS_SIDE
                || bounds.getYsize() <= 0 || bounds.getYsize() > MAX_BOUNDS_SIDE
                || bounds.getZsize() <= 0 || bounds.getZsize() > MAX_BOUNDS_SIDE
                || bounds.minY < level.getMinBuildHeight() || bounds.maxY > level.getMaxBuildHeight()) return false;
        var border = level.getWorldBorder();
        if (bounds.minX < border.getMinX() || bounds.maxX > border.getMaxX()
                || bounds.minZ < border.getMinZ() || bounds.maxZ > border.getMaxZ()) return false;
        for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(Math.nextDown(bounds.maxX)) >> 4; x++) {
            for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(Math.nextDown(bounds.maxZ)) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private static Iterable<BlockPos> positions(AABB bounds) {
        return BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY), Math.nextDown(bounds.maxZ)));
    }

    private static boolean matches(ServerLevel level, UmbrafallSiteData.Site site, MoonwoundEvent event) {
        return level != null && level.getServer().isSameThread() && site != null && event != null
                && event.siteId().equals(site.id);
    }

    private static Binding activeEvent(ServerLevel level, UUID eventId) {
        if (level == null || !level.getServer().isSameThread() || eventId == null) return null;
        for (MoonwoundEvent event : MoonwoundSavedData.get(level).events()) {
            if (!event.eventId().equals(eventId)) continue;
            if (event.phase() == MoonwoundEvent.Phase.LOBBY || event.phase().isTerminal()) return null;
            UmbrafallSiteData.Site site = UmbrafallSiteData.get(level).sites.get(event.siteId());
            return site == null ? null : new Binding(site, event);
        }
        return null;
    }

    private static List<BlockPos> footprint(UmbrafallSiteData.Site site) {
        if (site == null || !site.hasValidTerrainProfile() || !site.geometry.usesStratifiedLayers()) return List.of();
        return FOOTPRINTS.computeIfAbsent(site, value -> {
            UmbrafallShapeBuilder shape = value.shape();
            var cursor = shape.cursor();
            if (cursor.total() > MAX_SHAPE_VOLUME) return List.of();
            LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
            positions.add(value.core.immutable());
            cursor.scan(cursor.total(), (x, y, z, material) -> {
                if (shape.structuralLayerAt(x, y, z) != UmbrafallShapeBuilder.StructuralLayer.NONE) {
                    positions.add(value.center.offset(x, y, z));
                }
            });
            return List.copyOf(positions);
        });
    }

    private record Binding(UmbrafallSiteData.Site site, MoonwoundEvent event) { }
}
