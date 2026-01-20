package com.jayemceekay.shadowedhearts.blocks;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.jayemceekay.shadowedhearts.blocks.entity.PurificationChamberBlockEntity;
import com.jayemceekay.shadowedhearts.client.net.storage.purification.OpenPurificationChamberPacket;
import com.jayemceekay.shadowedhearts.core.ModBlockEntities;
import com.jayemceekay.shadowedhearts.storage.purification.PurificationChamberStore;
import com.jayemceekay.shadowedhearts.storage.purification.link.ProximityPurificationLink;
import com.jayemceekay.shadowedhearts.storage.purification.link.PurificationLinkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Purification Chamber block: opens the Purification UI on use.
 */
public class PurificationChamberBlock extends Block implements EntityBlock {

    public enum Part implements StringRepresentable {
        TOP("top"),
        BOTTOM("bottom");

        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
        @Override public String toString() { return name; }
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty ON = BooleanProperty.create("on");

    public PurificationChamberBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.BOTTOM)
                .setValue(ON, Boolean.FALSE));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PurificationChamberBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {

        return type == ModBlockEntities.PURIFICATION_CHAMBER_BE.get()
                ? (lvl, pos, st, be) -> {
                    if (be instanceof PurificationChamberBlockEntity chamber) {
                        PurificationChamberBlockEntity.TICKER.tick(lvl, pos, st, chamber);
                    }
                }
                : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, ON);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        Direction facing = context.getHorizontalDirection();

        BlockPos above = pos.above();
        if (!level.getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART, Part.BOTTOM)
                .setValue(ON, Boolean.FALSE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos above = pos.above();
        BlockState topState = state.setValue(PART, Part.TOP);
        level.setBlock(above, topState, Block.UPDATE_ALL);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Part part = state.getValue(PART);
        if (part == Part.TOP) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(PART) == Part.BOTTOM && below.getValue(FACING) == state.getValue(FACING);
        } else {
            // bottom: ensure space above
            BlockState above = level.getBlockState(pos.above());
            return above.canBeReplaced();
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Part part = state.getValue(PART);
        if (part == Part.BOTTOM && dir == Direction.UP) {
            if (!(neighborState.is(this) && neighborState.getValue(PART) == Part.TOP && neighborState.getValue(FACING) == state.getValue(FACING))) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        if (part == Part.TOP && dir == Direction.DOWN) {
            if (!(neighborState.is(this) && neighborState.getValue(PART) == Part.BOTTOM && neighborState.getValue(FACING) == state.getValue(FACING))) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }
        Part part = state.getValue(PART);
        BlockPos otherPos = (part == Part.BOTTOM) ? pos.above() : pos.below();
        BlockState other = level.getBlockState(otherPos);
        if (other.is(this) && other.getValue(PART) != part) {
            level.removeBlock(otherPos, false);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.TOP) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this)) {
                return this.useWithoutItem(belowState, level, below, player, hit);
            }
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (PlayerExtensionsKt.isInBattle(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.translatable("shadowedhearts.purification_chamber.in_battle").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }

            // Ensure the player's store exists/loads (persistence handled by Cobblemon)
            PurificationChamberStore store = Cobblemon.INSTANCE.getStorage().getCustomStore(PurificationChamberStore.class, player.getUUID(), player.registryAccess());
            // Mark store dirty on first open so persistence creates/updates the file even when empty
            if (store != null) {
                store.touch();
                // Register a proximity-based link so server can authorize edits and drive ON state, like PC
                try {
                    PurificationLinkManager.INSTANCE.addLink(new ProximityPurificationLink(store, player.getUUID(), pos, serverPlayer.serverLevel()));
                } catch (Throwable ignored) {
                    // Fallback if anything goes wrong; UI can still open
                }
            }
            OpenPurificationChamberPacket packet = new OpenPurificationChamberPacket(store);
            packet.sendToPlayer(serverPlayer);
            // Send a full snapshot of the store contents so the client UI populates with saved Pokémon
            if (store != null) {
                store.sendTo(serverPlayer);
            }
            level.gameEvent(serverPlayer, GameEvent.BLOCK_OPEN, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.SUCCESS;
    }
}
