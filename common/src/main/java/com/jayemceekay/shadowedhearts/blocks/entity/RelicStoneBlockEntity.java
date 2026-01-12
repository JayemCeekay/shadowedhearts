package com.jayemceekay.shadowedhearts.blocks.entity;
 
import com.jayemceekay.shadowedhearts.core.ModBlockEntities;
import com.jayemceekay.shadowedhearts.network.RelicStoneMotePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RelicStoneBlockEntity extends BlockEntity {

    public RelicStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELIC_STONE_BE.get(), pos, state);
    }

    public static void tick(
            Level level,
            BlockPos pos,
            BlockState state,
            RelicStoneBlockEntity blockEntity
    ) {
        if (!level.isClientSide && level.getGameTime() % 2 == 0) {
            Player closestPlayer = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5.0, false);
            if (closestPlayer != null) {
                // Send packet to nearby players to spawn particles
                RelicStoneMotePacket packet = new RelicStoneMotePacket(pos);
                ((ServerLevel) level).players().forEach(player -> {
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 32 * 32) {
                        packet.sendToPlayer(player);
                    }
                });
            }
        }
    }
}

