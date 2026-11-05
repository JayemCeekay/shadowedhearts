package com.jayemceekay.shadowedhearts.content.items;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.block.TypeGemClusterBlock;
import com.jayemceekay.shadowedhearts.integration.ftbchunks.FTBChunksClaimBridgeHolder;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** A plantable type gem whose single-use battle effect is supplied by Showdown. */
public final class ShadowGemItem extends Item {
    public ShadowGemItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        com.jayemceekay.shadowedhearts.common.items.ItemRuleFeedback.appendDisabled(stack, tooltip);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var facing = context.getClickedFace();
        var target = pos.relative(facing);
        var stack = context.getItemInHand();
        if (player == null || player.level() != level || !player.mayBuild() || stack.isEmpty()
                || level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)
                || (level instanceof ServerLevel && (!level.hasChunkAt(pos) || !level.hasChunkAt(target)))
                || !level.mayInteract(player, target) || !player.mayUseItemAt(target, facing, stack)
                || !level.getBlockState(pos).isFaceSturdy(level, pos, facing)
                || !level.getBlockState(target).isAir()) {
            return InteractionResult.FAIL;
        }

        // Item interactions are checked at the clicked support by FTB Chunks.
        // This custom placement must also authorize the adjacent destination.
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || serverPlayer.serverLevel() != serverLevel
                || !FTBChunksClaimBridgeHolder.INSTANCE.canEditBlock(serverPlayer, target, context.getHand())) {
            return InteractionResult.FAIL;
        }

        // Match Cobblemon's GemItem: planting starts at the medium bud stage.
        if (!level.setBlockAndUpdate(target, ModBlocks.SHADOW_GEM_CLUSTER.get().defaultBlockState()
                .setValue(TypeGemClusterBlock.Companion.getFACING(), facing)
                .setValue(TypeGemClusterBlock.Companion.getSTAGE(), 1))) return InteractionResult.FAIL;
        stack.consume(1, player);
        level.playSound(null, pos, CobblemonSounds.TYPE_GEM_CLUSTER_SOUNDS.getPlaceSound(), SoundSource.BLOCKS);
        return InteractionResult.SUCCESS;
    }
}
