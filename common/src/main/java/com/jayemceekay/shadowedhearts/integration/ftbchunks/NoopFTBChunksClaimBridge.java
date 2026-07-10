package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public class NoopFTBChunksClaimBridge implements FTBChunksClaimBridge {
    @Override
    public boolean isChunkClaimed(ServerLevel level, ChunkPos chunkPos) {
        return false;
    }
}
