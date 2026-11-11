package com.jayemceekay.shadowedhearts.content.blocks;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.mark.Marks;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.trade.TradeManager;
import com.jayemceekay.shadowedhearts.advancements.ModCriteriaTriggers;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowService;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.config.GameplayTuningConfig;
import com.jayemceekay.shadowedhearts.content.blocks.entity.RelicStoneBlockEntity;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import com.jayemceekay.shadowedhearts.network.purification.RelicStoneMotePacket;
import com.jayemceekay.shadowedhearts.registry.ModBlockEntities;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import com.jayemceekay.shadowedhearts.storage.player.PlayerPersistentData;
import com.jayemceekay.shadowedhearts.storage.player.ShadowedHeartsPlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
            {-1, 0}, {0, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1},
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

    public static boolean isCenter(BlockState state) {
        return state.getValue(PART) == CENTER_PART && state.getValue(LAYER) == 0;
    }

    public static BlockPos getCenterPos(BlockPos pos, BlockState state) {
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
    private static VoxelShape westHalf(double h) {
        return Block.box(0, 0, 0, 8, h, 16);
    }

    private static VoxelShape eastHalf(double h) {
        return Block.box(8, 0, 0, 16, h, 16);
    }

    private static VoxelShape northHalf(double h) {
        return Block.box(0, 0, 0, 16, h, 8);
    }

    private static VoxelShape southHalf(double h) {
        return Block.box(0, 0, 8, 16, h, 16);
    }

    private static VoxelShape nwQuarter(double h) {
        return Block.box(0, 0, 0, 8, h, 8);
    }

    private static VoxelShape neQuarter(double h) {
        return Block.box(8, 0, 0, 16, h, 8);
    }

    private static VoxelShape swQuarter(double h) {
        return Block.box(0, 0, 8, 8, h, 16);
    }

    private static VoxelShape seQuarter(double h) {
        return Block.box(8, 0, 8, 16, h, 16);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos center = ctx.getClickedPos();
        Player player = ctx.getPlayer();

        // Reserve a 3x3 footprint for all 3 vertical layers (0..2) so collision works for the full 42px height.
        for (int layer = 0; layer <= 2; layer++) {
            for (int part = 0; part < 9; part++) {
                BlockPos p = center.offset(OFFSETS[part][0], layer, OFFSETS[part][1]);
                BlockState partState = this.defaultBlockState().setValue(PART, part).setValue(LAYER, layer);
                if (level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p)
                        || (level instanceof ServerLevel && !level.hasChunkAt(p))
                        || !level.getBlockState(p).canBeReplaced(BlockPlaceContext.at(ctx, p, ctx.getClickedFace()))
                        || !level.isUnobstructed(partState, p, CollisionContext.empty())
                        || (player != null && (!level.mayInteract(player, p)
                        || !player.mayUseItemAt(p, ctx.getClickedFace(), ctx.getItemInHand())))) return null;
                // Authorize the full footprint, including cells outside the targeted center,
                // before BlockItem writes anything or consumes the portable Relic Stone.
                if (level instanceof ServerLevel serverLevel) {
                    var claims = FTBChunksClaimBridgeHolder.INSTANCE;
                    if (player instanceof ServerPlayer serverPlayer) {
                        if (serverPlayer.serverLevel() != serverLevel
                                || !claims.canEditBlock(serverPlayer, p, ctx.getHand())) return null;
                    } else if (claims.isBlockInClaimedChunk(serverLevel, p)) {
                        // Actorless placement has no player permission to inherit in protected land.
                        return null;
                    }
                }
            }
        }

        return this.defaultBlockState().setValue(PART, CENTER_PART).setValue(LAYER, 0).setValue(HAS_BE, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos center, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!isCenter(state)) return;

        // Place all dummy parts for layers 0..2, except the visible center itself.
        for (int layer = 0; layer <= 2; layer++) {
            for (int part = 0; part < 9; part++) {
                if (layer == 0 && part == CENTER_PART) continue;
                BlockPos p = center.offset(OFFSETS[part][0], layer, OFFSETS[part][1]);
                level.setBlock(p, this.defaultBlockState().setValue(PART, part).setValue(LAYER, layer).setValue(HAS_BE, false), Block.UPDATE_ALL);
            }
        }
    }

    private void removeDummyParts(Level level, BlockPos center) {
        for (int layer = 0; layer <= 2; layer++) {
            for (int part = 0; part < 9; part++) {
                if (layer == 0 && part == CENTER_PART) continue;
                BlockPos p = center.offset(OFFSETS[part][0], layer, OFFSETS[part][1]);
                BlockState partState = level.getBlockState(p);
                if (partState.is(this) && getCenterPos(p, partState).equals(center)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        // Vanilla calls this only after a successful survival harvest with the correct tool.
        // Any selected part represents the same stone; cleanup itself never generates loot.
        BlockState centerState = state.setValue(PART, CENTER_PART).setValue(LAYER, 0).setValue(HAS_BE, true);
        super.playerDestroy(level, player, pos, centerState, blockEntity, tool);
    }

    private void checkAndFixBroken(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide && isCenter(state)) {
            if (level.getBlockEntity(pos) == null) {
                level.setBlockEntity(new RelicStoneBlockEntity(pos, state.setValue(HAS_BE, true)));
            }
            if (!state.getValue(HAS_BE)) {
                level.setBlock(pos, state.setValue(HAS_BE, true), 3);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        checkAndFixBroken(state, level, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        checkAndFixBroken(state, level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockPos center = getCenterPos(pos, state);
            if (isCenter(state)) {
                // The center is already replaced before this callback. Each helper can now
                // disappear without starting another cleanup or dropping a second item.
                removeDummyParts(level, center);

                // Send stop sound packet
                RelicStoneMotePacket stopPacket = new RelicStoneMotePacket(center, true);
                ((ServerLevel) level).players().forEach(player -> {
                    if (player.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) < 32 * 32) {
                        stopPacket.sendToPlayer(player);
                    }
                });
            } else {
                BlockState centerState = level.getBlockState(center);
                if (centerState.is(this) && isCenter(centerState)) {
                    level.setBlock(center, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
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
        // Outer and upper cells must be selectable so the stone can be mined from any side.
        return isCenter(state) ? OUTLINE_CENTER : getCollisionShape(state, level, pos, context);
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
        return isCenter(state) ? new RelicStoneBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isCenter(state) && type == ModBlockEntities.RELIC_STONE_BE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<RelicStoneBlockEntity>) RelicStoneBlockEntity::tick : null;
    }

    @Override
    public @NotNull List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (isCenter(state)) return super.getDrops(state, params);

        // Environmental destruction requests loot before removing the selected part.
        // Only a live helper belonging to a live center may represent the stone here;
        // cleanup removes all remaining cells without requesting further loot.
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return List.of();
        BlockPos pos = BlockPos.containing(origin);
        ServerLevel level = params.getLevel();
        if (!level.getBlockState(pos).equals(state)) return List.of();
        BlockState centerState = level.getBlockState(getCenterPos(pos, state));
        return centerState.is(this) && isCenter(centerState) ? super.getDrops(centerState, params) : List.of();
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos center = getCenterPos(pos, state);
        if (!canUseCenter(level, pos, center, player, hand)) return ItemInteractionResult.FAIL;
        BlockState centerState = level.getBlockState(center);
        if (!centerState.is(this) || !isCenter(centerState)) return ItemInteractionResult.FAIL;
        checkAndFixBroken(centerState, level, center);

        if (stack.is(ModItems.SHADOW_SHARD.get()) && stack.getCount() >= 8) {
            if (!level.isClientSide) {
                stack.shrink(8);
                ItemStack gem = new ItemStack(ModItems.PURIFIED_GEM.get());
                if (!player.getInventory().add(gem)) {
                    player.drop(gem, false);
                }
                if (player instanceof ServerPlayer serverPlayer) {
                    ModCriteriaTriggers.triggerRelicStoneInteract(serverPlayer);
                }
                level.playSound(null, center, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos center = getCenterPos(pos, state);
        if (!canUseCenter(level, pos, center, player, InteractionHand.MAIN_HAND)) return InteractionResult.FAIL;
        BlockState centerState = level.getBlockState(center);
        if (!centerState.is(this) || !isCenter(centerState)) return InteractionResult.FAIL;
        checkAndFixBroken(centerState, level, center);

        if (player instanceof ServerPlayer serverPlayer) {
            ModCriteriaTriggers.triggerRelicStoneInteract(serverPlayer);
        }
        if (level.isClientSide) {
            return InteractionResult.PASS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (!centerState.is(this) || !isCenter(centerState) || !isAvailablePlayer(serverPlayer, level)) {
                return InteractionResult.FAIL;
            }
            String refusal = purificationRefusal(serverPlayer);
            if (!GameplayTuningConfig.INSTANCE.relicStonePurificationEnabled()) {
                serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.disabled")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            if (refusal != null) {
                serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone." + refusal)
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            long lastPurify; // Fallback
            long now = level.getGameTime();
            long cooldownTicks = (long) ShadowedHeartsConfigs.getInstance().getShadowConfig().relicStoneCooldownMinutes() * 60 * 20;

            // Try to get persistent data
            ShadowedHeartsPlayerData data = PlayerPersistentData.get(serverPlayer);
            lastPurify = data.getLastRelicStonePurify();

            if (now - lastPurify < cooldownTicks && lastPurify != 0) {
                long remainingTicks = cooldownTicks - (now - lastPurify);
                long remainingSeconds = Math.ceilDiv(remainingTicks, 20);
                long minutes = remainingSeconds / 60;
                long seconds = remainingSeconds % 60;
                serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.cooldown", minutes, seconds).withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }

            // Snapshot both identities. A prior target's callbacks can change a later
            // entity or its store, so every target is authorized again before mutation.
            AABB area = new AABB(center).inflate(5.0);
            List<PurificationTarget> nearbyPokemon = level.getEntitiesOfClass(PokemonEntity.class, area, pe -> {
                Pokemon p = pe.getPokemon();
                return serverPlayer.getUUID().equals(p.getOwnerUUID()) && ShadowAspectUtil.hasShadowAspect(p);
            }).stream().map(pe -> new PurificationTarget(pe, pe.getPokemon())).toList();

            boolean purifiedAny = false;
            boolean currentShadowFound = false;
            boolean battleBlocked = false;
            for (PurificationTarget target : nearbyPokemon) {
                if (!canUseCenter(level, pos, center, serverPlayer, InteractionHand.MAIN_HAND)
                        || purificationRefusal(serverPlayer) != null) break;
                if (!area.intersects(target.entity().getBoundingBox()) || !isCurrentOwnedTarget(serverPlayer, target)) continue;
                PokemonEntity pe = target.entity();
                Pokemon p = target.pokemon();
                if (!ShadowAspectUtil.hasShadowAspect(p)) continue;
                currentShadowFound = true;
                if (p.isBattleClone() || pe.isBattling()) {
                    battleBlocked = true;
                    continue;
                }
                if (ShadowHeartGauge.isReadyForPurification(p)) {
                    ShadowService.fullyPurify(p, pe, serverPlayer);
                    Mark nationalRibbon = Marks.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", "ribbon_event_national"));
                    if (nationalRibbon != null) p.exchangeMark(nationalRibbon, true);
                    ModCriteriaTriggers.triggerShadowPurified(serverPlayer);
                    purifiedAny = true;
                    serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.purified", p.getDisplayName(false)).withStyle(ChatFormatting.GREEN), false);
                }
            }

            if (purifiedAny) {
                PlayerPersistentData.get(serverPlayer).setLastRelicStonePurify(now);
                level.playSound(null, center, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                if (battleBlocked) {
                    serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.in_battle")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResult.SUCCESS;
                } else if (!currentShadowFound) {
                    serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.no_pokemon"), true);
                    return InteractionResult.SUCCESS;
                } else {
                    serverPlayer.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.not_ready"), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    private static boolean canUseCenter(Level level, BlockPos selected, BlockPos center, Player player, InteractionHand hand) {
        if (level.isClientSide) return true;
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !isAvailablePlayer(serverPlayer, level)
                || !level.hasChunkAt(selected) || !level.hasChunkAt(center)
                || !level.mayInteract(player, selected) || !level.mayInteract(player, center)) return false;
        // Clicking an accessible helper also acts on its center, including repairing
        // its block entity. Authorize both before any repair, reward or purification.
        var claims = FTBChunksClaimBridgeHolder.INSTANCE;
        return claims.canInteractBlock(serverPlayer, selected, hand)
                && (selected.equals(center) || claims.canInteractBlock(serverPlayer, center, hand));
    }

    private static boolean isAvailablePlayer(ServerPlayer player, Level level) {
        return player.level() == level && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static @Nullable String purificationRefusal(ServerPlayer player) {
        if (BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null) return "in_battle";
        if (TradeManager.INSTANCE.getActiveTrade(player.getUUID()) != null) return "in_trade";
        return null;
    }

    private static boolean isCurrentOwnedTarget(ServerPlayer player, PurificationTarget target) {
        PokemonEntity entity = target.entity();
        Pokemon pokemon = target.pokemon();
        if (entity.isRemoved() || !entity.isAlive() || entity.level() != player.level()
                || entity.getPokemon() != pokemon || pokemon.getEntity() != entity
                || !player.getUUID().equals(pokemon.getOwnerUUID())) return false;

        // UUID/owner metadata can survive a trade, replacement or clone. Require the
        // actual stored instance, including legitimate PC-owned pastured Pokemon.
        var storage = Cobblemon.INSTANCE.getStorage();
        return storage.getParty(player).get(pokemon.getUuid()) == pokemon
                || storage.getPC(player).get(pokemon.getUuid()) == pokemon;
    }

    private record PurificationTarget(PokemonEntity entity, Pokemon pokemon) { }
}
