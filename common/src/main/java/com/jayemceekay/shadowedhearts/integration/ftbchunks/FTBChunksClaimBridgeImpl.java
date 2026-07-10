package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public class FTBChunksClaimBridgeImpl implements FTBChunksClaimBridge {
    @Override
    public boolean isChunkClaimed(ServerLevel level, ChunkPos chunkPos) {
        FTBChunksAPI.API api = FTBChunksAPI.api();
        if (api == null || !api.isManagerLoaded()) {
            return false;
        }

        ClaimedChunkManager manager = api.getManager();
        if (manager == null) {
            return false;
        }

        return manager.getChunk(new ChunkDimPos(level.dimension(), chunkPos)) != null;
    }
}
