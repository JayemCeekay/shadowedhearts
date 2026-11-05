package com.jayemceekay.shadowedhearts.integration.ftbchunks;

import com.jayemceekay.shadowedhearts.content.blocks.RelicStoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/** Authorizes the cleanup cascade before its first block or loot is changed. */
public final class RelicStoneProtection {
    private RelicStoneProtection() { }

    public static boolean canPlayerRemove(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RelicStoneBlock)) return true;
        if (player.serverLevel() != level) return false;
        return canRemove(level, pos, state, part -> level.mayInteract(player, part)
                && FTBChunksClaimBridgeHolder.INSTANCE.canEditBlock(player, part, InteractionHand.MAIN_HAND));
    }

    public static boolean canExplosionRemove(ServerLevel level, BlockPos pos, BlockState state, Explosion explosion) {
        if (!(state.getBlock() instanceof RelicStoneBlock)) return true;
        return canRemove(level, pos, state,
                part -> FTBChunksClaimBridgeHolder.INSTANCE.canExplosionDestroyBlock(level, part, explosion));
    }

    private static boolean canRemove(ServerLevel level, BlockPos pos, BlockState state,
                                     Predicate<BlockPos> allowed) {
        if (!level.hasChunkAt(pos) || !allowed.test(pos)) return false;
        BlockPos center = RelicStoneBlock.getCenterPos(pos, state);
        if (!level.hasChunkAt(center)) return false;
        BlockState centerState = level.getBlockState(center);
        // An orphan helper has no cleanup cascade. Check only the selected block.
        if (!centerState.is(state.getBlock()) || !RelicStoneBlock.isCenter(centerState)) return true;

        for (int layer = 0; layer <= 2; layer++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos part = center.offset(x, layer, z);
                    // Never load another chunk just to decide whether a multiblock can be removed.
                    if (!level.hasChunkAt(part)) return false;
                    BlockState partState = level.getBlockState(part);
                    if (partState.is(state.getBlock()) && RelicStoneBlock.getCenterPos(part, partState).equals(center)
                            && !part.equals(pos) && !allowed.test(part)) return false;
                }
            }
        }
        return true;
    }
}
