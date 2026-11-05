package com.jayemceekay.shadowedhearts.common.voidshadow;

import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.SpawningZone;
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator;
import com.cobblemon.mod.common.api.spawning.spawner.Spawner;
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native spawn contexts for a local disguise query, without a spawn pass or chunk loading. */
public final class VoidShadowSpawnArea {
    public static final int HORIZONTAL_RADIUS = 16;
    public static final int VERTICAL_RADIUS = 8;

    private VoidShadowSpawnArea() { }

    public static List<AreaSpawnablePosition> resolve(ServerLevel world, BlockPos center,
                                                     Spawner spawner, SpawnCause cause) {
        int baseX = center.getX() - HORIZONTAL_RADIUS;
        int baseZ = center.getZ() - HORIZONTAL_RADIUS;
        int baseY = Math.max(world.getMinBuildHeight(), center.getY() - VERTICAL_RADIUS);
        int topY = Math.min(world.getMaxBuildHeight(), center.getY() + VERTICAL_RADIUS + 1);
        int height = topY - baseY;
        if (height <= 0) return List.of();
        int diameter = HORIZONTAL_RADIUS * 2 + 1;

        // Match Cobblemon's slice representation. Unknown cells stay solid and dark;
        // they cannot become floor/air or fluid contexts in the native resolver.
        var unknown = new SpawningZone.BlockData(Blocks.STONE.defaultBlockState(), 0, 0);
        var blocks = new SpawningZone.BlockData[diameter][height][diameter];
        var skyLevel = new Integer[diameter][diameter];
        for (int x = 0; x < diameter; x++) {
            Arrays.fill(skyLevel[x], world.getMaxBuildHeight());
            for (int y = 0; y < height; y++) Arrays.fill(blocks[x][y], unknown);
        }

        Map<Long, LevelChunk> chunks = new HashMap<>();
        var position = new BlockPos.MutableBlockPos();
        var skyLight = world.getLightEngine().getLayerListener(LightLayer.SKY);
        for (int x = baseX; x < baseX + diameter; x++) {
            for (int z = baseZ; z < baseZ + diameter; z++) {
                int chunkX = SectionPos.blockToSectionCoord(x);
                int chunkZ = SectionPos.blockToSectionCoord(z);
                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (!chunks.containsKey(key)) {
                    chunks.put(key, world.getChunkSource().getChunkNow(chunkX, chunkZ));
                }
                LevelChunk chunk = chunks.get(key);
                if (chunk == null) continue;

                boolean canSeeSky = world.canSeeSkyFromBelowWater(position.set(x, topY - 1, z));
                for (int y = topY - 1; y >= baseY; y--) {
                    position.set(x, y, z);
                    var state = chunk.getBlockState(position);
                    blocks[x - baseX][y - baseY][z - baseZ] = new SpawningZone.BlockData(
                            state, world.getMaxLocalRawBrightness(position), skyLight.getLightValue(position));
                    if (canSeeSky) skyLevel[x - baseX][z - baseZ] = y;
                    if (state.getFluidState().isEmpty() && !state.is(CobblemonBlockTags.SEES_SKY)) {
                        canSeeSky = false;
                    }
                }
            }
        }

        // Input detectors include habitat/attraction searches far outside this area.
        // The available ecology is independent of current mob spacing and spawn caps.
        var zone = new SpawningZone(cause, world, baseX, baseY, baseZ,
                blocks, skyLevel, List.of(), List.of());
        return spawner.getResolver().resolve(spawner,
                        SpawnablePositionCalculator.Companion.getPrioritizedAreaCalculators(), zone).stream()
                .filter(candidate -> candidate.getPosition().getY() >= baseY && candidate.getPosition().getY() < topY)
                .filter(candidate -> chunks.get(ChunkPos.asLong(
                        SectionPos.blockToSectionCoord(candidate.getPosition().getX()),
                        SectionPos.blockToSectionCoord(candidate.getPosition().getZ()))) != null)
                .filter(candidate -> {
                    long dx = candidate.getPosition().getX() - (long) center.getX();
                    long dz = candidate.getPosition().getZ() - (long) center.getZ();
                    return dx * dx + dz * dz <= HORIZONTAL_RADIUS * HORIZONTAL_RADIUS;
                }).toList();
    }
}
