package com.jayemceekay.shadowedhearts.content.blocks;

import com.jayemceekay.shadowedhearts.content.blocks.entity.UmbraInfuserBlockEntity;
import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** External energy storage. Adjacent infusers include its charge and capacity. */
public final class UmbraInfuserTankBlock extends Block implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public UmbraInfuserTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    public static boolean isLower(BlockState state) {
        return state.is(ModBlocks.UMBRA_INFUSER_TANK.get()) && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HALF); }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos above = context.getClickedPos().above();
        Player player = context.getPlayer();
        if (above.getY() >= level.getMaxBuildHeight() || !level.getBlockState(above).canBeReplaced(context)
                || (player != null && (!level.mayInteract(player, above)
                || !player.mayUseItemAt(above, context.getClickedFace(), context.getItemInHand())))) return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER || isLower(level.getBlockState(pos.below()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction partner = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == partner && (!neighbor.is(this) || neighbor.getValue(HALF) == half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // The lower-half loot table is the sole source of the tank item.
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER
                && isLower(level.getBlockState(pos.below()))) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof UmbraInfuserBlockEntity tank) tank.dropContents();
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

    private static UmbraInfuserBlockEntity tank(Level level, BlockPos pos, BlockState state) {
        BlockPos bottom = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return level.getBlockEntity(bottom) instanceof UmbraInfuserBlockEntity tank ? tank : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && player.mayBuild()) {
            UmbraInfuserBlockEntity tank = tank(level, pos, state);
            if (tank != null) tank.addFuel(player, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player.mayBuild()) {
            UmbraInfuserBlockEntity tank = tank(level, pos, state);
            if (tank != null) {
                // Old tanks owned the workpiece. Keep that persisted inventory recoverable
                // after their role changes to external energy storage.
                if (!tank.getDisplayItem().isEmpty()) tank.extract(player);
                else tank.showStatus(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
