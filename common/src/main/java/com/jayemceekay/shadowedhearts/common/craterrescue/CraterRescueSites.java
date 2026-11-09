package com.jayemceekay.shadowedhearts.common.craterrescue;

import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallCoreBlockEntity;
import com.jayemceekay.shadowedhearts.world.gen.MeteoroidStructure;
import com.jayemceekay.shadowedhearts.world.gen.MeteoroidStructurePiece;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallSiteGenerator;
import com.jayemceekay.shadowedhearts.world.umbrafall.UmbrafallTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

final class CraterRescueSites {
    private CraterRescueSites() {}

    static boolean eligible(UmbrafallSiteData.Site site) {
        return (site.tier == UmbrafallTier.WEAK || site.tier == UmbrafallTier.MODERATE)
                && site.placement == UmbrafallSiteData.Placement.READY && site.hasValidTerrainProfile()
                && site.state.active() && !site.rewardClaimed;
    }

    static boolean matchesGeneratedCore(UmbrafallSiteData.Site site) {
        // Scattered cores placed as blocks also acquire site records. Only Signal Data placement
        // advances a cursor, and structure generation derives its ID from the impact center/seed.
        return (site.cursor > 0 || site.id.equals(UmbrafallCoreBlockEntity.naturalSiteId(site.center, site.seed)))
                && site.core.equals(UmbrafallSiteGenerator.coreAt(site.center, site.shape()));
    }

    /** Unloaded candidate positions must not consume a site's one encounter roll. */
    static boolean rimLoaded(ServerLevel level, UmbrafallSiteData.Site site) {
        int reach = Math.min(site.footprintRadius(), 12) + 8;
        return level.hasChunksAt(site.center.offset(-reach, 0, -reach), site.center.offset(reach, 0, reach));
    }

    /** The terrain can arrive before saved Pokémon become visible to the server's UUID lookup. */
    static boolean actorsLoaded(ServerLevel level, BlockPos home) {
        if (!level.isPositionEntityTicking(home)) return false;
        for (int x = (home.getX() - 8) >> 4; x <= (home.getX() + 8) >> 4; x++) {
            for (int z = (home.getZ() - 8) >> 4; z <= (home.getZ() + 8) >> 4; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
            }
        }
        return true;
    }

    /** Work outwards from the impact, placing the group on the exposed meteor and basin floor. */
    static List<BlockPos> homes(ServerLevel level, UmbrafallSiteData.Site site, int count) {
        int reach = Math.min(site.footprintRadius(), 12);
        List<BlockPos> homes = new ArrayList<>(count);
        for (BlockPos column : BlockPos.withinManhattan(site.center, reach, 0, reach)) {
            if (homes.stream().anyMatch(other -> horizontalDistanceSquared(other, column) < 6)) continue;
            BlockPos feet = safeSurface(level, column);
            if (feet != null && insidePlan(level, site, feet)) {
                homes.add(feet);
                if (homes.size() == count) return homes;
            }
        }
        return List.of();
    }

    private static int horizontalDistanceSquared(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** The actual landing position may differ from the requested home during Cobblemon send-out. */
    static boolean insideEncounter(ServerLevel level, UmbrafallSiteData.Site site, BlockPos feet) {
        return site != null && site.hasValidTerrainProfile() && feet.equals(safeSurface(level, feet))
                && insidePlan(level, site, feet);
    }

    private static boolean insidePlan(ServerLevel level, UmbrafallSiteData.Site site, BlockPos feet) {
        var shape = site.shape();
        int x = feet.getX() - site.center.getX(), z = feet.getZ() - site.center.getZ();
        BlockPos ground = feet.below();
        int y = ground.getY() - site.center.getY();
        var floor = level.getBlockState(ground);
        var standing = level.getBlockState(feet);
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || standing.is(BlockTags.FIRE)
                || standing.is(Blocks.WITHER_ROSE) || standing.is(Blocks.SWEET_BERRY_BUSH)) return false;
        if (shape.isBodyBlock(x, y, z)) {
            var original = UmbrafallSiteGenerator.stateFor(shape.materialAt(x, y, z), level, ground);
            if (floor.is(original.getBlock())) return true;
        }
        // Signal Data can fit each column to a different original surface. Exclude surrounding
        // ejecta, raised structures above the crater and unrelated caves below an excavated floor.
        int originalSurface = shape.columnShift(x, z);
        return shape.isInsideCrater(x, z) && y < originalSurface && y >= originalSurface - shape.craterDepth() - 1;
    }

    static BlockPos safeSurface(ServerLevel level, BlockPos column) {
        if (!level.hasChunkAt(column)) return null;
        BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        if (!level.getWorldBorder().isWithinBounds(feet) || !level.getBlockState(feet.below())
                .isFaceSturdy(level, feet.below(), Direction.UP) || !level.getFluidState(feet.below()).isEmpty()
                || !clear(level, feet) || !clear(level, feet.above())) return null;
        return feet;
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    static boolean atFaintSite(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        // Natural Faint sites have no core block entity and therefore no core-owned site record.
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        for (var entry : chunk.getAllReferences().entrySet()) {
            if (!(entry.getKey() instanceof MeteoroidStructure)) continue;
            for (long ref : entry.getValue()) {
                ChunkPos startPos = new ChunkPos(ref);
                var startChunk = level.getChunkSource().getChunkNow(startPos.x, startPos.z);
                if (startChunk == null) continue;
                var start = startChunk.getStartForStructure(entry.getKey());
                if (start != null && start.getPieces().stream().anyMatch(piece ->
                        piece instanceof MeteoroidStructurePiece meteor && meteor.isFaintSiteNear(pos))) return true;
            }
        }
        // Signal Data records its Faint deposits directly, including sites with no structure start.
        return UmbrafallSiteData.get(level).sites.values().stream().anyMatch(site ->
                site.tier == UmbrafallTier.FAINT && site.placement == UmbrafallSiteData.Placement.READY
                        && site.hasValidTerrainProfile() && site.center.closerThan(pos, 16));
    }
}
