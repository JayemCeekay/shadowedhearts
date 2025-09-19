package com.jayemceekay.shadowedhearts.poketoss;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Very simple placeholder for the Command Post block described in design.
 * Right-click sends a hint message; future iterations will add UI, storage, and roles.
 */
public class CommandPostBlock extends Block {
    public CommandPostBlock(Properties properties) {
        super(properties);
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("Pasture: Command hub (stub) — future UI here."), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
