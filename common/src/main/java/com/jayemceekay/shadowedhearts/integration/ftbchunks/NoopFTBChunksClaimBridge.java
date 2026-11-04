package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;

public class NoopFTBChunksClaimBridge implements FTBChunksClaimBridge {
    @Override
    public ChunkClaim lookupClaim(ServerLevel level, ChunkPos chunkPos) {
        return ChunkClaim.absent();
    }

    @Override
    public boolean canInteractBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        return true;
    }

    @Override
    public boolean canEditBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        return true;
    }

    @Override
    public boolean canExplosionDestroyBlock(ServerLevel level, BlockPos pos, Explosion explosion) {
        return true;
    }
}
