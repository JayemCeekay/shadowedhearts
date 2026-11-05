package com.jayemceekay.shadowedhearts.client.render.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Visual exposure is current neighbor geometry, not the site's persistent EXPOSED milestone. */
public final class UmbrafallCoreExposure {
    private static final VoxelShape OPENING_X = Shapes.box(0, .25, .25, 1, .75, .75);
    private static final VoxelShape OPENING_Y = Shapes.box(.25, 0, .25, .75, 1, .75);
    private static final VoxelShape OPENING_Z = Shapes.box(.25, .25, 0, .75, .75, 1);
    private UmbrafallCoreExposure() { }

    public static boolean isExposed(BlockGetter level, BlockPos core, Direction face) {
        BlockPos adjacent = core.relative(face);
        BlockState neighbor = level.getBlockState(adjacent);
        return !neighbor.canOcclude() || openingVisible(face,
                neighbor.getFaceOcclusionShape(level, adjacent, face.getOpposite()));
    }

    static boolean openingVisible(Direction face, VoxelShape occlusion) {
        // Conservative bounds of the six-pixel aperture, projected along the face normal.
        VoxelShape opening = switch (face.getAxis()) {
            case X -> OPENING_X;
            case Y -> OPENING_Y;
            case Z -> OPENING_Z;
        };
        return Shapes.joinIsNotEmpty(opening, occlusion, BooleanOp.ONLY_FIRST);
    }
}
