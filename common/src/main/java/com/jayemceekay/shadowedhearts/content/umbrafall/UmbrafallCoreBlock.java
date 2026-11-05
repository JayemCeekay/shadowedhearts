package com.jayemceekay.shadowedhearts.content.umbrafall;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The only active Umbrafall material block. Ordinary shell and vein blocks have no ticker. */
public final class UmbrafallCoreBlock extends Block implements EntityBlock {
    public UmbrafallCoreBlock(Properties properties) { super(properties); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UmbrafallCoreBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (world, pos, blockState, entity) -> {
            if (entity instanceof UmbrafallCoreBlockEntity core) core.tick();
        };
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof UmbrafallCoreBlockEntity core) core.inspect(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof UmbrafallCoreBlockEntity core) core.rupture(player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof UmbrafallCoreBlockEntity core) core.removed();
        super.onRemove(state, level, pos, replacement, moving);
    }
}
