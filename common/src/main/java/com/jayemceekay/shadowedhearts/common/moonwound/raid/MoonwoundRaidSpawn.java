package com.jayemceekay.shadowedhearts.common.moonwound.raid;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallSiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/** Loaded-terrain placement for the full scaled raid boss, on or beside the meteorite. */
public final class MoonwoundRaidSpawn {
    private static final int FALLBACK_SEARCH_EXTENSION = 16;

    private MoonwoundRaidSpawn() { }

    public static @Nullable Vec3 findGround(ServerLevel level, UmbrafallSiteData.Site site, PokemonEntity entity) {
        return findGround(level, site, entity, bounds -> true);
    }

    /** Production callers authorize every occupied cell and its supporting floor before selection. */
    public static @Nullable Vec3 findGround(ServerLevel level, UmbrafallSiteData.Site site, PokemonEntity entity,
                                           Predicate<AABB> allowed) {
        entity.refreshDimensions();
        var dimensions = entity.getDimensions(entity.getPose());
        // Bound datapack-provided dimensions before inspecting terrain across the footprint.
        if (!Float.isFinite(dimensions.width()) || !Float.isFinite(dimensions.height())
                || dimensions.width() <= 0 || dimensions.height() <= 0
                || dimensions.width() > 32 || dimensions.height() > 32) return null;
        int radius = site.geometry.bodyRadius(site.tier) + Mth.ceil(dimensions.width() / 2) + 2;
        int minY = Math.max(level.getMinBuildHeight() + 1, site.center.getY() - 40);
        int maxY = Math.min(level.getMaxBuildHeight() - Mth.ceil(dimensions.height()),
                site.center.getY() + Math.max(16, site.shape().bounds().maxY() + 1));
        // Use the meteorite's remaining solid surfaces when the complete boss fits there.
        // Actual block support and collision, rather than the saved body projection, decide eligibility.
        Vec3 onSite = searchArea(level, site.center, entity, dimensions, 0, radius, minY, maxY, allowed);
        if (onSite != null) return onSite;
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int ring = 0; ring < 3; ring++) for (int index = 0; index < 16; index++) {
            double angle = (index + Math.floorMod(site.seed, 16)) * Math.PI * 2 / 16;
            int x = Mth.floor(site.center.getX() + .5 + Math.cos(angle) * (radius + ring * 4));
            int z = Mth.floor(site.center.getZ() + .5 + Math.sin(angle) * (radius + ring * 4));
            Vec3 position = groundAt(level, entity, dimensions, x, z, minY, maxY, allowed);
            if (position != null) {
                double score = Math.abs(position.y - site.center.getY()) * 4 + ring * 4;
                if (score < bestScore) { best = position; bestScore = score; }
            }
        }
        if (best != null) return best;

        // Wide species (including addon hitboxes) may have no full-footprint patch on the
        // sparse rings. Search every nearby column, including ground just beyond those rings.
        return searchArea(level, site.center, entity, dimensions,
                radius, radius + FALLBACK_SEARCH_EXTENSION, minY, maxY, allowed);
    }

    private static @Nullable Vec3 searchArea(ServerLevel level, BlockPos center, PokemonEntity entity,
                                            EntityDimensions dimensions, int innerRadius, int outerRadius,
                                            int minY, int maxY, Predicate<AABB> allowed) {
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int dx = -outerRadius; dx <= outerRadius; dx++) {
            for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared < innerRadius * innerRadius || distanceSquared > outerRadius * outerRadius) continue;
                Vec3 position = groundAt(level, entity, dimensions,
                        center.getX() + dx, center.getZ() + dz, minY, maxY, allowed);
                if (position == null) continue;
                double score = Math.abs(position.y - center.getY()) * 4 + Math.sqrt(distanceSquared);
                if (score < bestScore) { best = position; bestScore = score; }
            }
        }
        return best;
    }

    private static @Nullable Vec3 groundAt(ServerLevel level, PokemonEntity entity, EntityDimensions dimensions,
                                          int x, int z, int minY, int maxY, Predicate<AABB> allowed) {
        AABB column = dimensions.makeBoundingBox(new Vec3(x + .5, minY, z + .5)).deflate(1.0E-5);
        if (!loaded(level, column)) return null;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // Don't search under a high cliff or through an arbitrarily deep canopy into a cave.
        if (top > maxY || top < minY) return null;
        for (int y = top; y >= Math.max(minY, top - 8); y--) {
            Vec3 position = new Vec3(x + .5, y, z + .5);
            AABB actualBounds = dimensions.makeBoundingBox(position);
            AABB bounds = actualBounds.deflate(1.0E-5);
            if (!supported(level, bounds, y - 1) || !level.getWorldBorder().isWithinBounds(bounds)
                    || !allowed.test(actualBounds.expandTowards(0, -1, 0))
                    || !level.noCollision(entity, bounds) || level.containsAnyLiquid(bounds)) continue;
            boolean hazard = false;
            for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(bounds.minX), y, Mth.floor(bounds.minZ),
                    Mth.floor(bounds.maxX), Mth.floor(bounds.maxY), Mth.floor(bounds.maxZ))) {
                if (hazardous(level.getBlockState(pos))) { hazard = true; break; }
            }
            if (!hazard) return position;
        }
        return null;
    }

    private static boolean loaded(ServerLevel level, AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private static boolean supported(ServerLevel level, AABB bounds, int floorY) {
        for (BlockPos floor : BlockPos.betweenClosed(Mth.floor(bounds.minX), floorY, Mth.floor(bounds.minZ),
                Mth.floor(bounds.maxX), floorY, Mth.floor(bounds.maxZ))) {
            BlockState state = level.getBlockState(floor);
            if (hazardous(state) || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()
                    || !state.isFaceSturdy(level, floor, Direction.UP)
                    || state.getCollisionShape(level, floor).max(Direction.Axis.Y) < 1) return false;
        }
        return true;
    }

    private static boolean hazardous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE);
    }
}
