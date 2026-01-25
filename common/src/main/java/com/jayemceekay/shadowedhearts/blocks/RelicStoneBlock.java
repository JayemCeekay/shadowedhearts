package com.jayemceekay.shadowedhearts.blocks;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.blocks.entity.RelicStoneBlockEntity;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.core.ModBlockEntities;
import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.util.PlayerPersistentData;
import com.jayemceekay.shadowedhearts.util.ShadowedHeartsPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RelicStoneBlock extends Block implements EntityBlock {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public static final IntegerProperty LAYER = IntegerProperty.create("layer", 0, 2);
    public static final BooleanProperty HAS_BE = BooleanProperty.create("has_be");

    private static final int CENTER_PART = 4;

    // Part index layout (row-major):
    // 0 1 2
    // 3 4 5
    // 6 7 8
    private static final int[][] OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1,  0}, {0,  0}, {1,  0},
            {-1,  1}, {0,  1}, {1,  1},
    };

    // Total visual height in "pixels" (1/16 block units).
    private static final double TOTAL_HEIGHT = 42.0;
    // Top layer height (layer 2 covers y in [32..42]).
    private static final double TOP_LAYER_HEIGHT = 10.0;

    // Visual outline for the center block (outline only; can extend beyond 1 block).
    private static final VoxelShape OUTLINE_CENTER = Block.box(-8.0, 0.0, -8.0, 24.0, TOTAL_HEIGHT, 24.0);

    public RelicStoneBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, CENTER_PART)
                .setValue(LAYER, 0)
                .setValue(HAS_BE, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, LAYER, HAS_BE);
    }

    private static boolean isCenter(BlockState state) {
        return state.getValue(PART) == CENTER_PART && state.getValue(LAYER) == 0;
    }

    private static BlockPos getCenterPos(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        int layer = state.getValue(LAYER);
        int dx = OFFSETS[part][0];
        int dz = OFFSETS[part][1];
        return pos.offset(-dx, -layer, -dz);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }

    private static double layerHeight(BlockState state) {
        return state.getValue(LAYER) == 2 ? TOP_LAYER_HEIGHT : 16.0;
    }

    // Half/quarter collision cells (coordinates are 0..16 per block cell, height depends on layer).
    private static VoxelShape westHalf(double h)  { return Block.box(0, 0, 0, 8,  h, 16); }
    private static VoxelShape eastHalf(double h)  { return Block.box(8, 0, 0, 16, h, 16); }
    private static VoxelShape northHalf(double h) { return Block.box(0, 0, 0, 16, h, 8); }
    private static VoxelShape southHalf(double h) { return Block.box(0, 0, 8, 16, h, 16); }

    private static VoxelShape nwQuarter(double h) { return Block.box(0, 0, 0, 8,  h, 8); }
    private static VoxelShape neQuarter(double h) { return Block.box(8, 0, 0, 16, h, 8); }
    private static VoxelShape swQuarter(double h) { return Block.box(0, 0, 8, 8,  h, 16); }
    private static VoxelShape seQuarter(double h) { return Block.box(8, 0, 8, 16, h, 16); }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos center = ctx.getClickedPos();

        // Reserve a 3x3 footprint for all 3 vertical layers (0..2) so collision works for the full 42px height.
        for (int layer = 0; layer <= 2; layer++) {
            for (int part = 0; part < 9; part++) {
                BlockPos p = center.offset(OFFSETS[part][0], layer, OFFSETS[part][1]);
                if (!level.getBlockState(p).canBeReplaced(ctx)) return null;
            }
        }

        return this.defaultBlockState().setValue(PART, CENTER_PART).setValue(LAYER, 0).setValue(HAS_BE, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos center, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!isCenter(state)) return;

        // Place all dummy parts for layers 0..2, except the visible center itself.
        for (int layer = 0; layer <= 2; layer++) {
            for (int part = 0; part < 9; part++) {
                if (layer == 0 && part == CENTER_PART) continue;
                BlockPos p = center.offset(OFFSETS[part][0], layer, OFFSETS[part][1]);
                level.setBlock(p, this.defaultBlockState().setValue(PART, part).setValue(LAYER, layer).setValue(HAS_BE, false), 3);
            }
        }
    }

    private void removeAllParts(Level level, BlockPos center) {
        for (int layer = 0; layer <= 2; layer++) {
            for (int part = 0; part < 9; part++) {
                BlockPos p = center.offset(OFFSETS[part][0], layer, OFFSETS[part][1]);
                if (level.getBlockState(p).getBlock() == this) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos center = getCenterPos(pos, state);
            if (!pos.equals(center)) {
                // Breaking any dummy part breaks the center (drops once, cleans up via onRemove/playerWillDestroy).
                level.destroyBlock(center, true, player);
            } else {
                // Breaking the center directly: ensure the whole structure is removed.
                removeAllParts(level, center);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
        return state;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide) {
            if (!state.is(newState.getBlock())) {
                BlockPos center = getCenterPos(pos, state);
                removeAllParts(level, center);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
        // Only the center (layer 0) renders.
        return isCenter(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Only show the big outline when targeting the center.
        return isCenter(state) ? OUTLINE_CENTER : Shapes.empty();
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double h = layerHeight(state);
        int part = state.getValue(PART);

        // Center cell: keep conservative/solid so you can't "fall into" seams on the top surface.
        if (part == CENTER_PART) {
            return Block.box(0, 0, 0, 16, h, 16);
        }

        return switch (part) {
            // edges (touching-half)
            case 3 -> eastHalf(h);   // west cell: solid east half
            case 5 -> westHalf(h);   // east cell: solid west half
            case 1 -> southHalf(h);  // north cell: solid south half
            case 7 -> northHalf(h);  // south cell: solid north half

            // corners (touching-quarter)
            case 0 -> seQuarter(h);  // NW cell: solid SE quarter
            case 2 -> swQuarter(h);  // NE cell: solid SW quarter
            case 6 -> neQuarter(h);  // SW cell: solid NE quarter
            case 8 -> nwQuarter(h);  // SE cell: solid NW quarter

            default -> Shapes.empty();
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelicStoneBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.RELIC_STONE_BE.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<?>) (BlockEntityTicker<RelicStoneBlockEntity>) RelicStoneBlockEntity::tick : null;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return isCenter(state) ? super.getDrops(state, params) : List.of();
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.SHADOW_SHARD.get()) && stack.getCount() >= 8) {
            if (!level.isClientSide) {
                stack.shrink(8);
                ItemStack gem = new ItemStack(ModItems.PURIFIED_GEM.get());
                if (!player.getInventory().add(gem)) {
                    player.drop(gem, false);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModCriteriaTriggers.triggerRelicStoneInteract(serverPlayer);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if(player instanceof ServerPlayer serverPlayer) {
            long lastPurify = 0; // Fallback
            long now = level.getGameTime();
            long cooldownTicks = (long) ShadowedHeartsConfigs.getInstance().getShadowConfig().relicStoneCooldownMinutes() * 60 * 20;

            // Try to get persistent data
            ShadowedHeartsPlayerData data = PlayerPersistentData.get(serverPlayer);
            lastPurify = data.getLastRelicStonePurify();

            if (now - lastPurify < cooldownTicks) {
                long remainingTicks = cooldownTicks - (now - lastPurify);
                long remainingSeconds = remainingTicks / 20;
                long minutes = remainingSeconds / 60;
                long seconds = remainingSeconds % 60;
                serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.cooldown", minutes, seconds).withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }

            // Search for nearby PokemonEntities owned by the serverPlayer
            AABB area = new AABB(pos).inflate(5.0);
            List<PokemonEntity> nearbyPokemon = level.getEntitiesOfClass(PokemonEntity.class, area, pe -> {
                Pokemon p = pe.getPokemon();
                return p != null && serverPlayer.getUUID().equals(p.getOwnerUUID()) && PokemonAspectUtil.hasShadowAspect(p);
            });

            boolean purifiedAny = false;
            for (PokemonEntity pe : nearbyPokemon) {
                Pokemon p = pe.getPokemon();
                if (PokemonAspectUtil.getHeartGaugePercent(p) == 0) {
                    ShadowService.fullyPurify(p, pe);
                    purifiedAny = true;
                    serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.purified", p.getDisplayName(false)).withStyle(ChatFormatting.GREEN), false);
                }
            }

            if (purifiedAny) {
                PlayerPersistentData.get(serverPlayer).setLastRelicStonePurify(now);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                if (nearbyPokemon.isEmpty()) {
                    serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.no_pokemon"), true);
                } else {
                    serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.not_ready"), true);
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

}
