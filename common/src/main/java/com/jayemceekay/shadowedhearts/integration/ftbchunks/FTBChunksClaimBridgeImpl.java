package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import dev.architectury.platform.Platform;
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;

public class FTBChunksClaimBridgeImpl implements FTBChunksClaimBridge {
    @Override
    public ChunkClaim lookupClaim(ServerLevel level, ChunkPos chunkPos) {
        ClaimedChunkManager manager = loadedManager();
        if (manager == null) {
            return ChunkClaim.unavailable();
        }

        var chunk = manager.getChunk(new ChunkDimPos(level.dimension(), chunkPos));
        if (chunk == null) return ChunkClaim.unclaimed();
        var teamData = chunk.getTeamData();
        if (teamData == null) return ChunkClaim.unavailable();
        var team = teamData.getTeam();
        return team == null ? ChunkClaim.unavailable() : ChunkClaim.claimed(team.getId());
    }

    @Override
    public boolean canInteractBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        return canAct(player, pos, hand, false);
    }

    @Override
    public boolean canEditBlock(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        return canAct(player, pos, hand, true);
    }

    @Override
    public boolean canExplosionDestroyBlock(ServerLevel level, BlockPos pos, Explosion explosion) {
        if (level == null || pos == null || explosion == null) return false;
        ClaimedChunkManager manager = loadedManager();
        if (manager == null) return false;
        // Match FTBChunks.explosionDetonate, including explosions with no known source.
        // Do not substitute a player's edit permission for a team's explosion policy.
        if (FTBChunksWorldConfig.DISABLE_PROTECTION.get() || explosion.getToBlow().isEmpty()
                || (explosion.getDirectSourceEntity() == null
                && !FTBChunksWorldConfig.PROTECT_UNKNOWN_EXPLOSIONS.get())) return true;
        var chunk = manager.getChunk(new ChunkDimPos(level.dimension(), new ChunkPos(pos)));
        if (chunk == null) return true;
        var teamData = chunk.getTeamData();
        return teamData != null && teamData.canExplosionsDamageTerrain();
    }

    private boolean canAct(ServerPlayer player, BlockPos pos, InteractionHand hand, boolean edit) {
        if (player == null || pos == null || hand == null) return false;
        ClaimedChunkManager manager = loadedManager();
        if (manager == null) return false;
        // Match FTB's loader-specific block permissions. Its manager also owns public/ally
        // rules, admin bypass, disabled protection and no-wilderness restrictions.
        Protection protection = Platform.isFabric() ? Protection.EDIT_AND_INTERACT_BLOCK
                : edit ? Protection.EDIT_BLOCK : Protection.INTERACT_BLOCK;
        return !manager.shouldPreventInteraction(player, hand, pos, protection, null);
    }

    private static ClaimedChunkManager loadedManager() {
        FTBChunksAPI.API api = FTBChunksAPI.api();
        return api != null && api.isManagerLoaded() ? api.getManager() : null;
    }
}
