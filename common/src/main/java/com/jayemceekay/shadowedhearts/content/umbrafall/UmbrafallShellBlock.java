package com.jayemceekay.shadowedhearts.content.umbrafall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.Vec3;

/** A full cube with all 24 persisted orientations; the block has no ticker or block entity. */
public class UmbrafallShellBlock extends Block {
    /** Direction of the authored model's top (+Y), rather than its front (-Z). */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 3);
    private static final Direction[] TOP_DIRECTIONS = Direction.values();
    private static final double PLACEMENT_CENTER_RADIUS = 0.125;

    public UmbrafallShellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction top = context.getClickedFace();
        // The supporting block and destination differ only along top, which is projected away.
        Vec3 offset = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        Direction front = placementFront(top, offset, context.getNearestLookingDirections());
        return orientedState(defaultBlockState(), top, front);
    }

    /** The nearest clicked edge selects the front; a central click faces it back toward the player. */
    static Direction placementFront(Direction top, Vec3 offset, Direction[] nearestLookingDirections) {
        Direction front = null;
        double strongest = PLACEMENT_CENTER_RADIUS;
        for (Direction candidate : TOP_DIRECTIONS) {
            if (candidate.getAxis() == top.getAxis()) continue;
            double distance = offset.x * candidate.getStepX() + offset.y * candidate.getStepY()
                    + offset.z * candidate.getStepZ();
            if (distance > strongest) {
                strongest = distance;
                front = candidate;
            }
        }
        if (front != null) return front;
        for (Direction looking : nearestLookingDirections) {
            if (looking.getAxis() != top.getAxis()) return looking.getOpposite();
        }
        return frontFor(top, 0);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty() || !player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) level.setBlock(pos, state.cycle(ROTATION), Block.UPDATE_ALL);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction top = state.getValue(FACING);
        Direction front = frontFor(top, state.getValue(ROTATION));
        return orientedState(state, rotation.rotate(top), rotation.rotate(front));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction top = state.getValue(FACING);
        Direction front = frontFor(top, state.getValue(ROTATION));
        // As with directional vanilla blocks, mirror the orientation axes, retaining authored texture UVs.
        return orientedState(state, mirror.mirror(top), mirror.mirror(front));
    }

    private static BlockState orientedState(BlockState state, Direction top, Direction front) {
        return state.setValue(FACING, top).setValue(ROTATION, rotationFor(top, front));
    }

    /**
     * Rotation zero uses NORTH as front for vertical tops and UP for horizontal tops.
     * Each next quarter-turn is cross(front, top). Resource variants follow this exact convention.
     */
    public static Direction frontFor(Direction top, int rotation) {
        Direction front = top.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        for (int turn = 0; turn < Math.floorMod(rotation, 4); turn++) {
            front = Direction.fromDelta(
                    front.getStepY() * top.getStepZ() - front.getStepZ() * top.getStepY(),
                    front.getStepZ() * top.getStepX() - front.getStepX() * top.getStepZ(),
                    front.getStepX() * top.getStepY() - front.getStepY() * top.getStepX());
        }
        return front;
    }

    public static int rotationFor(Direction top, Direction front) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (frontFor(top, rotation) == front) return rotation;
        }
        throw new IllegalArgumentException("Shell top and front must have different axes: " + top + ", " + front);
    }

    /** Position hashing keeps random-looking worldgen stable across chunk slices and resumed site writes. */
    public static BlockState generatedStateAt(BlockState state, BlockPos pos) {
        long mixed = pos.asLong() + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        int orientation = (int) Math.floorMod(mixed, 24L);
        return state.setValue(FACING, TOP_DIRECTIONS[orientation / 4]).setValue(ROTATION, orientation % 4);
    }
}
