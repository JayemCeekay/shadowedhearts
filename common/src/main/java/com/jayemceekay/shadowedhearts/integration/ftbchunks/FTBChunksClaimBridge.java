package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;

public interface FTBChunksClaimBridge {
    ChunkClaim lookupClaim(ServerLevel level, ChunkPos chunkPos);

    /** Player permissions are independent of claim ownership; unknown adapters deny by default. */
    default boolean canInteractBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        return false;
    }

    default boolean canEditBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        return false;
    }

    /** Explosion policy is independent of player edit permissions; unknown adapters deny. */
    default boolean canExplosionDestroyBlock(ServerLevel level, BlockPos pos, Explosion explosion) {
        return false;
    }

    /** Conservative compatibility helper: unavailable protection is treated as protected. */
    default boolean isChunkClaimed(ServerLevel level, ChunkPos chunkPos) {
        ChunkClaim claim = lookupClaim(level, chunkPos);
        return claim.isClaimed() || claim.isUnavailable();
    }

    default boolean isBlockInClaimedChunk(ServerLevel level, BlockPos pos) {
        return isChunkClaimed(level, new ChunkPos(pos));
    }
}
