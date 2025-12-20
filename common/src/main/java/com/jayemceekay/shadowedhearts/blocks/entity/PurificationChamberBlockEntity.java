package com.jayemceekay.shadowedhearts.blocks.entity;

import com.jayemceekay.shadowedhearts.blocks.PurificationChamberBlock;
import com.jayemceekay.shadowedhearts.core.ModBlockEntities;
import com.jayemceekay.shadowedhearts.storage.purification.link.ProximityPurificationLink;
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLink;
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * BlockEntity backing the Purification Chamber UI.
 * MVP: holds 5 slots (index 0 = center, 1-4 = orbit) and provides a menu.
 */
public class PurificationChamberBlockEntity extends BlockEntity  {

    /**
     * BlockEntity ticker mirroring Cobblemon's PCBlockEntity.TICKER behavior.
     * Server-side: toggles the chamber's ON state when at least one viewer is in range.
     * Placeholder viewer detection uses proximity until a proper link is added.
     */
    public static final BlockEntityTicker<PurificationChamberBlockEntity> TICKER = (world, pos, state, be) -> {
        if (world.isClientSide) return;
        be.togglePCOn(be.getInRangeViewerCount(world, pos, 5.0) > 0);
    };

    public PurificationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PURIFICATION_CHAMBER_BE.get(), pos, state);
    }

    /**
     * Mirrors PCBlockEntity.togglePCOn: sets the ON property on both halves of the
     * Purification Chamber, if it differs from the desired value. Server-side only.
     * This method is safe to call on either the top or bottom half's BE position,
     * but the BE only exists on the bottom in normal operation.
     */
    public void toggleChamberOn(boolean on) {
        Level world = this.level;
        if (world == null || world.isClientSide) return;

        BlockState selfState = this.getBlockState();
        if (!(selfState.getBlock() instanceof PurificationChamberBlock chamberBlock)) return;

        // Determine base (bottom) position/state regardless of which half we are at
        BlockPos posBottom = selfState.getValue(PurificationChamberBlock.PART) == PurificationChamberBlock.Part.BOTTOM
                ? this.worldPosition
                : this.worldPosition.below();
        BlockState stateBottom = world.getBlockState(posBottom);

        // Validate it's our block
        if (!(stateBottom.getBlock() instanceof PurificationChamberBlock)) return;

        BlockPos posTop = posBottom.above();
        BlockState stateTop = world.getBlockState(posTop);

        try {
            Boolean current = stateBottom.getValue(PurificationChamberBlock.ON);
            if (current != on) {
                // Update both halves; order mirrors PC behavior (top then bottom)
                if (stateTop.getBlock() instanceof PurificationChamberBlock) {
                    world.setBlockAndUpdate(posTop, stateTop.setValue(PurificationChamberBlock.ON, on));
                }
                world.setBlockAndUpdate(posBottom, stateBottom.setValue(PurificationChamberBlock.ON, on));
            }
        } catch (IllegalArgumentException ex) {
            // If states are missing properties (legacy/invalid), clean up both halves gracefully
            if (world.getBlockState(this.worldPosition.above()).getBlock() instanceof PurificationChamberBlock) {
                world.setBlockAndUpdate(this.worldPosition.above(), Blocks.AIR.defaultBlockState());
            } else {
                world.setBlockAndUpdate(this.worldPosition.below(), Blocks.AIR.defaultBlockState());
            }
            world.setBlockAndUpdate(this.worldPosition, Blocks.AIR.defaultBlockState());
        }
    }

    // ------- PC parity helper predicates (server-authoritative via link manager) -------
    private boolean isPlayerViewing(Player player) {
        if (player == null) return false;
        if (this.level == null || this.level.isClientSide) return false;

        PurificationLink link = PurificationLinkManager.INSTANCE.getLink(player.getUUID());
        if (!(link instanceof ProximityPurificationLink prox)) return false;

        // Match this chamber by base position and dimension
        return this.worldPosition.equals(prox.getPos()) && prox.getWorld().dimension().equals(player.level().dimension());
    }

    private int getInRangeViewerCount(Level world, BlockPos pos, double range) {
        AABB box = new AABB(
                pos.getX() - range,
                pos.getY() - range,
                pos.getZ() - range,
                pos.getX() + 1 + range,
                pos.getY() + 1 + range,
                pos.getZ() + 1 + range
        );
        return world.getEntities(EntityTypeTest.forClass(Player.class), box, this::isPlayerViewing).size();
    }

    /**
     * Parity helper with PCBlockEntity: keep the same method name used by PC so
     * external callers can interact uniformly. Delegates to toggleChamberOn.
     */
    public void togglePCOn(boolean on) {
        toggleChamberOn(on);
    }
}
