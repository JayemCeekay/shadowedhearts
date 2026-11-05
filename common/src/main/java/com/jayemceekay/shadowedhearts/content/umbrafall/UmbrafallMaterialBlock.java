package com.jayemceekay.shadowedhearts.content.umbrafall;

import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Static site material. Only player mining notifies the nearby site; no block entities or tick scans. */
public final class UmbrafallMaterialBlock extends Block {
    private final VoxelShape shape;

    public UmbrafallMaterialBlock(Properties properties) {
        this(properties, 16.0);
    }

    public UmbrafallMaterialBlock(Properties properties, double height) {
        super(properties);
        shape = Block.box(0.0, 0.0, 0.0, 16.0, height, 16.0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (!level.isClientSide && (state.is(ModBlocks.UMBRAFALL_VEIN.get()) || state.is(ModBlocks.UMBRAFALL_CRYSTAL.get()))) {
            UmbrafallSites.onMaterialMined(level, pos, player);
        }
    }
}
