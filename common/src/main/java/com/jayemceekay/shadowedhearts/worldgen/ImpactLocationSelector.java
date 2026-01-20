package com.jayemceekay.shadowedhearts.worldgen;

import com.jayemceekay.shadowedhearts.config.IWorldAlterationConfig;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.Random;

public class ImpactLocationSelector {
    private static final Random RANDOM = new Random();

    public static Optional<BlockPos> selectLocation(ServerLevel level) {
        if (level.players().isEmpty()) return Optional.empty();

        IWorldAlterationConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration();
        ServerPlayer targetPlayer = level.players().get(RANDOM.nextInt(level.players().size()));

        for (int i = 0; i < 10; i++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double distance = config.minImpactDistanceToPlayer() + RANDOM.nextDouble() * (config.maxImpactDistanceToPlayer() - config.minImpactDistanceToPlayer());
            int x = (int) (targetPlayer.getX() + Math.cos(angle) * distance);
            int z = (int) (targetPlayer.getZ() + Math.sin(angle) * distance);

            BlockPos pos = new BlockPos(x, 0, z);

            Optional<BlockPos> impactPos = findImpactPos(level, pos);
            if (impactPos.isPresent()) {
                return impactPos;
            }
        }

        return Optional.empty();
    }

    public static Optional<BlockPos> findImpactPos(LevelAccessor level, BlockPos pos) {
        if (level instanceof WorldGenLevel worldGenLevel && !worldGenLevel.ensureCanWrite(pos)) {
            return Optional.empty();
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        int x = pos.getX();
        int z = pos.getZ();

        if (isSafeLocation(level, chunkPos, pos)) {
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos surfacePos = new BlockPos(x, surfaceY, z);
            boolean isWater = level.getFluidState(surfacePos.below()).is(FluidTags.WATER);

            int y = isWater ? level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) : surfaceY;
            BlockPos impactPos = new BlockPos(x, y, z);

            if (canSeeSkyOrWater(level, impactPos)) {
                return Optional.of(impactPos);
            }
        }
        return Optional.empty();
    }

    private static boolean canSeeSkyOrWater(LevelAccessor level, BlockPos pos) {
        int highestBlock = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (highestBlock <= pos.getY()) return true;

        for (int y = pos.getY() + 1; y < highestBlock; y++) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeLocation(LevelAccessor level, ChunkPos chunkPos, BlockPos pos) {
        // Hard exclusions
        if (level instanceof ServerLevel serverLevel) {
            if (serverLevel.getChunkSource().getGeneratorState() != null && serverLevel.getChunkSource().getGenerator().getSpawnHeight(serverLevel) > 0) {
                BlockPos spawnPos = serverLevel.getSharedSpawnPos();
                if (pos.closerThan(spawnPos, ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().minImpactDistanceToSpawn()))
                    return false; // Simple spawn protection
            }

            // Heatmap
            if (PlayerActivityHeatmap.isCivilized(serverLevel, chunkPos)) return false;

            // Proximity to other players
            for (ServerPlayer player : serverLevel.players()) {
                if (pos.closerThan(player.blockPosition(), ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().minImpactDistanceToPlayer())) {
                    return false;
                }
            }

            // Proximity to structures
            int structureDistance = ShadowedHeartsConfigs.getInstance().getShadowConfig().worldAlteration().minImpactDistanceToStructures();
            if (structureDistance > 0) {
                for (int dx = -structureDistance; dx <= structureDistance; dx += 16) {
                    for (int dz = -structureDistance; dz <= structureDistance; dz += 16) {
                        BlockPos checkPos = pos.offset(dx, 0, dz);
                        if (serverLevel.structureManager().hasAnyStructureAt(checkPos)) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }
}
