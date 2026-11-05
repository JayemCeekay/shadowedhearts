package com.jayemceekay.shadowedhearts.content.umbrafall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stackable debris with four pile sizes and four persisted horizontal orientations. */
public final class UmbrafallDebrisBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STONES = IntegerProperty.create("stones", 1, 4);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public UmbrafallDebrisBlock(Properties properties) {
        super(properties);
        // Existing saves without a count keep the original four-stone model.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STONES, 4));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STONES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) return existing.setValue(STONES, Math.min(4, existing.getValue(STONES) + 1));
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(STONES, 1);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(asItem()) && state.getValue(STONES) < 4
                || super.canBeReplaced(state, context);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty() || !player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) level.setBlock(pos, state.rotate(Rotation.CLOCKWISE_90), Block.UPDATE_ALL);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    /** Stable across chunk order and resumed site writes, like the surrounding shell blocks. */
    public static BlockState generatedStateAt(BlockState state, BlockPos pos) {
        long mixed = pos.asLong() + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return state.setValue(FACING, Direction.from2DDataValue((int) Math.floorMod(mixed, 4L)))
                .setValue(STONES, 1 + (int) ((mixed >>> 2) & 3L));
    }
}
