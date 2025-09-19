package com.jayemceekay.shadowedhearts.entrances;

import com.jayemceekay.shadowedhearts.core.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Placeholder Mission Gateway block. Future: consume a Mission Signal and open/run instance.
 * For now, right-click with a Mission Signal consumes one and notifies the player.
 */
public class MissionGatewayBlock extends Block {
    public MissionGatewayBlock(Properties props) {
        super(props);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, net.minecraft.core.BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!stack.isEmpty() && stack.getItem() == ModItems.MISSION_SIGNAL.get()) {
            stack.shrink(1);
            player.displayClientMessage(Component.literal("Gateway: Mission Signal consumed. Would start run (stub)."), true);
            return ItemInteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.literal("Use a Mission Signal on the Gateway."), true);
        return ItemInteractionResult.SUCCESS;
    }
}
