package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public interface FTBChunksClaimBridge {
    boolean isChunkClaimed(ServerLevel level, ChunkPos chunkPos);

    default boolean isBlockInClaimedChunk(ServerLevel level, BlockPos pos) {
        return isChunkClaimed(level, new ChunkPos(pos));
    }
}
