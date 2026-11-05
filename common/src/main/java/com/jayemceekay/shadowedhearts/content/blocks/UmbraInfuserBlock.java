package com.jayemceekay.shadowedhearts.content.blocks;

import com.jayemceekay.shadowedhearts.content.blocks.entity.UmbraInfuserBlockEntity;
import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Two world blocks share the machine inventory in the lower half. */
public final class UmbraInfuserBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final double ROOT_TWO = Math.sqrt(2.0);
    // Conservative axis-aligned bounds of the authored 45-degree console.
    private static final VoxelShape CONSOLE_NORTH = Block.box(1, 8 - 1 / ROOT_TWO, -4,
            15, 8 + 6 / ROOT_TWO, -4 + 7 / ROOT_TWO);
    private static final VoxelShape LOWER_NORTH = Shapes.or(
            Block.box(1, 0, 1, 15, 11, 15),
            Block.box(2, 11, 2, 14, 16, 14),
            CONSOLE_NORTH,
            Block.box(3, 9, -2, 5, 10, 1),
            Block.box(11, 9, -2, 13, 10, 1));
    private static final VoxelShape UPPER_NORTH = Shapes.or(
            Block.box(2, 0, 2, 14, 7, 14),
            Block.box(3, 7, 3, 13, 8, 13),
            Block.box(5, 8, 5, 11, 9, 11));
    private static final VoxelShape[] LOWER_SHAPES = rotatedShapes(LOWER_NORTH);
    private static final VoxelShape[] UPPER_SHAPES = rotatedShapes(UPPER_NORTH);

    public UmbraInfuserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    public static boolean isLower(BlockState state) {
        return state.is(ModBlocks.UMBRA_INFUSER.get()) && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos above = context.getClickedPos().above();
        Player player = context.getPlayer();
        if (above.getY() >= level.getMaxBuildHeight() || !level.getBlockState(above).canBeReplaced(context)
                || (player != null && (!level.mayInteract(player, above)
                || !player.mayUseItemAt(above, context.getClickedFace(), context.getItemInHand())))) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState lower = level.getBlockState(pos.below());
        return isLower(lower) && lower.getValue(FACING) == state.getValue(FACING);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction partner = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == partner && (!neighbor.is(this) || neighbor.getValue(HALF) == half
                || neighbor.getValue(FACING) != state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER
                && isLower(level.getBlockState(pos.below()))) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof UmbraInfuserBlockEntity infuser) infuser.dropContents();
            BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState other = level.getBlockState(otherPos);
            if (other.is(this) && other.getValue(HALF) != state.getValue(HALF)) {
                if (state.getValue(HALF) == DoubleBlockHalf.UPPER) level.destroyBlock(otherPos, true);
                else level.removeBlock(otherPos, false);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new UmbraInfuserBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && type == ModBlockEntities.UMBRA_INFUSER_BE.get()
                ? (world, pos, blockState, entity) -> UmbraInfuserBlockEntity.tick(world, (UmbraInfuserBlockEntity) entity)
                : null;
    }

    @Nullable
    public static UmbraInfuserBlockEntity machine(Level level, BlockPos pos, BlockState state) {
        BlockPos bottom = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return level.getBlockEntity(bottom) instanceof UmbraInfuserBlockEntity infuser ? infuser : null;
    }

    private static void openMenu(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            UmbraInfuserBlockEntity infuser = machine(level, pos, state);
            if (infuser != null && infuser.canAccess(player)) serverPlayer.openMenu(infuser);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        openMenu(level, pos, state, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        openMenu(level, pos, state, player);
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES)[state.getValue(FACING).get2DDataValue()];
    }

    private static VoxelShape[] rotatedShapes(VoxelShape north) {
        VoxelShape[] shapes = new VoxelShape[4];
        VoxelShape shape = north;
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            shapes[facing.get2DDataValue()] = shape;
            VoxelShape[] rotated = {Shapes.empty()};
            shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotated[0] = Shapes.or(rotated[0],
                    Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX)));
            shape = rotated[0];
        }
        return shapes;
    }
}
