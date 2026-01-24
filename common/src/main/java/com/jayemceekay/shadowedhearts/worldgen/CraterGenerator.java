package com.jayemceekay.shadowedhearts.worldgen;

import com.jayemceekay.shadowedhearts.config.IWorldAlterationConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import java.util.Random;

public class CraterGenerator {
    private static final Random RANDOM = new Random();

    public static void generateCrater(LevelAccessor level, BlockPos center) {
        generateSlicedCrater(level, null, center, RandomSource.create(new Random().nextLong()));
    }

    public static void generateSlicedCrater(LevelAccessor level, ChunkPos chunkPos, BlockPos center, RandomSource randomSource) {
        IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();

        // Deterministic random for this crater center
        long seed = (long) center.getX() * 3121L + (long) center.getZ() * 4961L + (long) center.getY() * 123L;
        RandomSource deterministicRandom = RandomSource.create(seed);

        int radius = config.minCraterRadius() + deterministicRandom.nextInt(config.maxCraterRadius() - config.minCraterRadius() + 1);
        int depth = radius / 2;

        double angleX = (deterministicRandom.nextDouble() - 0.5) * 0.5;
        double angleZ = (deterministicRandom.nextDouble() - 0.5) * 0.5;

        int checkRadius = radius + 4; // Increased padding for slant

        int minX = chunkPos != null ? chunkPos.getMinBlockX() : center.getX() - checkRadius;
        int maxX = chunkPos != null ? chunkPos.getMaxBlockX() : center.getX() + checkRadius;
        int minZ = chunkPos != null ? chunkPos.getMinBlockZ() : center.getZ() - checkRadius;
        int maxZ = chunkPos != null ? chunkPos.getMaxBlockZ() : center.getZ() + checkRadius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Determine if this column is within the crater's horizontal reach
                double relX = x - center.getX();
                double relZ = z - center.getZ();

                // Check columns within a wider range because slant moves the crater at depth
                if (Math.abs(relX) > checkRadius || Math.abs(relZ) > checkRadius) continue;

                for (int yOffset = -depth; yOffset <= radius / 4; yOffset++) {
                    double shiftedX = relX - (yOffset * angleX);
                    double shiftedZ = relZ - (yOffset * angleZ);

                    // Deterministic noise per column with multiple octaves for more detail
                    double columnNoise = 0.8;
                    RandomSource noiseRand = RandomSource.create((long) x * 31 + (long) z * 17 + seed);
                    columnNoise += noiseRand.nextDouble() * 0.2; // Primary noise
                    columnNoise += noiseRand.nextDouble() * 0.15; // Secondary detail noise
                    columnNoise *= (0.9 + noiseRand.nextDouble() * 0.2); // Overall scale variation

                    double distSq = (shiftedX * shiftedX) / (radius * radius * columnNoise)
                            + (double) (yOffset * yOffset) / (depth * depth)
                            + (shiftedZ * shiftedZ) / (radius * radius * columnNoise);

                    if (distSq <= 1.0) {
                        BlockPos pos = center.offset((int) relX, yOffset, (int) relZ);
                        if (yOffset <= 0) {
                            BlockState currentState = safeGetBlockState(level, pos);
                            if (currentState.getFluidState().isEmpty()) {
                                if (pos.getY() < level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ())) {
                                    safeSetBlock(level, pos, Blocks.WATER.defaultBlockState(), 3);
                                } else {
                                    safeSetBlock(level, pos, Blocks.AIR.defaultBlockState(), 3);
                                }
                            } else {
                                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
                            }

                            // Clear terrain above following the impact angle
                            // We use a wider clearing cone as we go up to ensure no floating bits
                            for (int moveUp = 1; center.getY() + yOffset + moveUp <= level.getMaxBuildHeight(); moveUp++) {
                                // Calculate the slant at this specific height above the impact point
                                double slantX = (yOffset + moveUp) * angleX;
                                double slantZ = (yOffset + moveUp) * angleZ;

                                // To prevent floating terrain, we also check a small radius around the slanted path
                                // especially as we move further up.
                                int clearRange = 1 + (moveUp / 4); // Wider clearing as it goes up
                                for (int dx = -clearRange; dx <= clearRange; dx++) {
                                    for (int dz = -clearRange; dz <= clearRange; dz++) {
                                        BlockPos abovePos = center.offset((int) (relX + slantX + dx), yOffset + moveUp, (int) (relZ + slantZ + dz));

                                        if (chunkPos != null && !isWithinChunk(abovePos, chunkPos)) continue;

                                        BlockState stateAbove = safeGetBlockState(level, abovePos);
                                        // If we hit air or water significantly above the impact, we might be done with this column
                                        // But we should continue clearing if we are still within the "impact tunnel"
                                        if (dx == 0 && dz == 0 && stateAbove.isAir() && (yOffset + moveUp) > 5) {
                                            // Optional: stop if we've cleared enough air, but for safety with overhangs we keep going a bit
                                        }

                                        if (!stateAbove.isAir() && stateAbove.getFluidState().isEmpty()) {
                                            if (abovePos.getY() < level.getHeight(Heightmap.Types.OCEAN_FLOOR, abovePos.getX(), abovePos.getZ())) {
                                                safeSetBlock(level, abovePos, Blocks.WATER.defaultBlockState(), 3);
                                            } else {
                                                safeSetBlock(level, abovePos, Blocks.AIR.defaultBlockState(), 3);
                                            }
                                        } else if (!stateAbove.getFluidState().isEmpty()) {
                                            level.scheduleTick(abovePos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
                                        }
                                    }
                                }
                                
                                // Optimization: if we've reached a very high point and everything is air, we can stop
                                if (moveUp > 20 && moveUp % 5 == 0) {
                                     BlockPos checkPos = center.offset((int) (relX + (yOffset + moveUp) * angleX), yOffset + moveUp, (int) (relZ + (yOffset + moveUp) * angleZ));
                                     if (chunkPos == null || isWithinChunk(checkPos, chunkPos)) {
                                         if (safeGetBlockState(level, checkPos).isAir()) {
                                             // Check a few blocks higher to be sure
                                             if (safeGetBlockState(level, checkPos.above(10)).isAir()) break;
                                         }
                                     }
                                }
                            }
                        } else {
                            if (distSq > 0.7 && RandomSource.create((long) x * 13 + (long) z * 19 + seed).nextDouble() > 0.3) {
                                BlockPos below = pos.below();
                                if (chunkPos == null || isWithinChunk(below, chunkPos)) {
                                    BlockState currentState = safeGetBlockState(level, pos);
                                    if (currentState.getFluidState().isEmpty() && safeGetBlockState(level, below).isSolid()) {
                                        safeSetBlock(level, pos, RandomSource.create((long) x * 7 + (long) z * 23).nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), 3);
                                    } else if (!currentState.getFluidState().isEmpty()) {
                                        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        int coreXOffset = (int) ((-depth) * angleX);
        int coreZOffset = (int) ((-depth) * angleZ);
        generateCore(level, chunkPos, center.offset(coreXOffset, -depth + 1, coreZOffset), radius, seed);
    }

    private static void generateCore(LevelAccessor level, ChunkPos chunkPos, BlockPos center, int radius, long seed) {
        BlockState coreBlock = ModBlocks.SHADOWFALL_METEOROID.get().defaultBlockState();
        RandomSource random = RandomSource.create(seed ^ 0x0001L);
        // blobSize is diameter. blobRadius is diameter / 2.
        int blobSize = 5 + random.nextInt(4); // 5, 6, 7, or 8
        // Scale with radius slightly, but keep it within a reasonable range if radius is large
        if (radius > 15) {
            blobSize += random.nextInt(radius / 10);
        }
        int blobRadius = blobSize / 2;
        if (blobRadius < 1) blobRadius = 1; // Minimum radius of 1 ensures at least a 3x3x3 area is checked 

        for (int x = -blobRadius - 1; x <= blobRadius + 1; x++) {
            for (int y = -blobRadius - 1; y <= blobRadius + 1; y++) {
                for (int z = -blobRadius - 1; z <= blobRadius + 1; z++) {
                    BlockPos pos = center.offset(x, y - 1, z);
                    if (chunkPos != null && !isWithinChunk(pos, chunkPos)) continue;

                    double dist = Math.sqrt(x * x + y * y + z * z);
                    
                    // Multi-octave noise for a more irregular "rocky" shape
                    RandomSource noiseRand = RandomSource.create((long) x * 3121L + (long) y * 4961L + (long) z * 123L + seed);
                    double noise = 0.0;
                    noise += noiseRand.nextDouble() * 0.5; // Large scale irregularities
                    noise += noiseRand.nextDouble() * 0.3; // Medium scale
                    noise += noiseRand.nextDouble() * 0.2; // Small details
                    
                    // Base threshold on the desired radius (diameter/2)
                    double targetRadius = blobSize / 2.0;
                    
                    // The noise modulates the radius at this specific angle/direction
                    // We map the 0..1 noise to a factor like 0.6..1.2
                    double noiseFactor = 0.6 + (noise * 0.6);
                    double threshold = targetRadius * noiseFactor;

                    if (dist <= threshold) {
                        BlockState currentState = safeGetBlockState(level, pos);
                        if (currentState.getFluidState().isEmpty()) {
                            safeSetBlock(level, pos, coreBlock, 3);
                        } else {
                            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
                        }
                    }
                }
            }
        }
    }

    private static boolean isWithinChunk(BlockPos pos, ChunkPos chunkPos) {
        return pos.getX() >= chunkPos.getMinBlockX() && pos.getX() <= chunkPos.getMaxBlockX() &&
                pos.getZ() >= chunkPos.getMinBlockZ() && pos.getZ() <= chunkPos.getMaxBlockZ();
    }

    private static BlockState safeGetBlockState(LevelAccessor level, BlockPos pos) {
        if (level instanceof WorldGenLevel wgl) {
            if (wgl.ensureCanWrite(pos)) {
                return level.getBlockState(pos);
            }
        } else {
            return level.getBlockState(pos);
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static void safeSetBlock(LevelAccessor level, BlockPos pos, BlockState state, int flags) {
        if (level instanceof WorldGenLevel wgl) {
            if (wgl.ensureCanWrite(pos)) {
                level.setBlock(pos, state, flags);
            }
        } else {
            level.setBlock(pos, state, flags);
        }
    }
}
