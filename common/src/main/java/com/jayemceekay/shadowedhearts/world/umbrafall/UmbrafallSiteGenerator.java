package com.jayemceekay.shadowedhearts.world.umbrafall;

import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallCoreBlockEntity;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallDebrisBlock;
import com.jayemceekay.shadowedhearts.content.umbrafall.UmbrafallShellBlock;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Minecraft block adapter. Live-world searches, claims and placement budgets belong to the site service. */
public final class UmbrafallSiteGenerator {
    private UmbrafallSiteGenerator() { }

    public static BoundingBox boundsAt(BlockPos center, UmbrafallShapeBuilder shape) {
        UmbrafallShapeBuilder.Bounds b = shape.bounds();
        return new BoundingBox(center.getX() + b.minX(), center.getY() + b.minY(), center.getZ() + b.minZ(),
                center.getX() + b.maxX(), center.getY() + b.maxY(), center.getZ() + b.maxZ());
    }

    public static BlockPos coreAt(BlockPos center, UmbrafallShapeBuilder shape) {
        UmbrafallShapeBuilder.Point p = shape.coreOffset();
        return center.offset(p.x(), p.y(), p.z());
    }

    /** KEEP has no block representation and must be filtered by a cursor/caller. */
    public static BlockState stateFor(UmbrafallShapeBuilder.Material material, LevelAccessor level, BlockPos pos) {
        return switch (material) {
            case KEEP -> throw new IllegalArgumentException("KEEP is not a block write");
            case AIR -> Blocks.AIR.defaultBlockState();
            case RIM -> rimState(level, pos);
            case SHELL -> UmbrafallShellBlock.generatedStateAt(ModBlocks.UMBRAFALL_SHELL.get().defaultBlockState(), pos);
            case CRACKED_SHELL -> UmbrafallShellBlock.generatedStateAt(ModBlocks.CRACKED_UMBRAFALL_SHELL.get().defaultBlockState(), pos);
            case UMBRAL_LATTICE -> UmbrafallShellBlock.generatedStateAt(ModBlocks.UMBRAL_LATTICE.get().defaultBlockState(), pos);
            case CORE_CASING -> UmbrafallShellBlock.generatedStateAt(ModBlocks.UMBRAFALL_CORE_CASING.get().defaultBlockState(), pos);
            case VEIN -> ModBlocks.UMBRAFALL_VEIN.get().defaultBlockState();
            case CRYSTAL -> ModBlocks.UMBRAFALL_CRYSTAL.get().defaultBlockState();
            case CORE -> ModBlocks.UMBRAFALL_CORE.get().defaultBlockState();
            case RESIDUE -> ModBlocks.SHADOW_RESIDUE.get().defaultBlockState();
            case FRAGMENT -> UmbrafallDebrisBlock.generatedStateAt(ModBlocks.UMBRAFALL_DEBRIS.get().defaultBlockState(), pos);
        };
    }

    /**
     * Only used by structure worldgen, where Minecraft already schedules a chunk-sized slice.
     * It never touches adjacent chunks or mutable global SavedData from a generation worker.
     */
    public static void placeSlice(WorldGenLevel level, BlockPos center, UmbrafallTier tier, long seed,
                                  BoundingBox worldSlice) {
        placeSlice(level, center, tier, seed, UmbrafallGeometry.CURRENT, worldSlice);
    }

    public static void placeSlice(WorldGenLevel level, BlockPos center, UmbrafallTier tier, long seed,
                                  UmbrafallGeometry geometry, BoundingBox worldSlice) {
        UmbrafallShapeBuilder shape = new UmbrafallShapeBuilder(tier, seed, geometry);
        UmbrafallShapeBuilder.Bounds b = shape.bounds();
        int minX = Math.max(b.minX(), worldSlice.minX() - center.getX());
        int maxX = Math.min(b.maxX(), worldSlice.maxX() - center.getX());
        int minY = Math.max(b.minY(), Math.max(worldSlice.minY(), level.getMinBuildHeight()) - center.getY());
        int maxY = Math.min(b.maxY(), Math.min(worldSlice.maxY(), level.getMaxBuildHeight() - 1) - center.getY());
        int minZ = Math.max(b.minZ(), worldSlice.minZ() - center.getZ());
        int maxZ = Math.min(b.maxZ(), worldSlice.maxZ() - center.getZ());
        if (minX > maxX || minY > maxY || minZ > maxZ) return;

        UmbrafallShapeBuilder.Cursor cursor = shape.cursor(
                new UmbrafallShapeBuilder.Bounds(minX, minY, minZ, maxX, maxY, maxZ));
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        cursor.scan(cursor.total(), (x, y, z, material) -> {
            target.set(center.getX() + x, center.getY() + y, center.getZ() + z);
            if (!level.ensureCanWrite(target)) return;
            BlockState old = level.getBlockState(target);
            if (old.hasBlockEntity() || old.getDestroySpeed(level, target) < 0 || !old.getFluidState().isEmpty()) return;
            if (material == UmbrafallShapeBuilder.Material.AIR && old.isAir()) return;
            // Sparse ejecta must sit on actual terrain; skip slopes that would leave debris floating.
            if (shape.isSurfaceDebris(x, y, z, material)
                    && !level.getBlockState(target.below()).isSolidRender(level, target.below())) return;
            BlockState state = stateFor(material, level, target);
            if (!level.setBlock(target, state, 2)) return;
            if (material == UmbrafallShapeBuilder.Material.CORE
                    && level.getBlockEntity(target) instanceof UmbrafallCoreBlockEntity core) {
                core.configure(tier, null, null, center, seed, geometry);
            }
        });
    }

    private static BlockState rimState(LevelAccessor level, BlockPos pos) {
        var biome = level.getBiome(pos);
        // Stable biome palettes avoid dependence on which neighbouring chunk was generated first.
        if (biome.is(BiomeTags.IS_BADLANDS)) return Blocks.TERRACOTTA.defaultBlockState();
        if (biome.is(Biomes.DESERT) || biome.is(Biomes.BEACH)) return Blocks.SANDSTONE.defaultBlockState();
        if (biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES)
                || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.SNOWY_BEACH)) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_OCEAN)) {
            return Blocks.STONE.defaultBlockState();
        }
        return Blocks.COARSE_DIRT.defaultBlockState();
    }
}
