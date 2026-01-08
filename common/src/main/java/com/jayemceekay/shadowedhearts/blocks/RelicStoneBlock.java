package com.jayemceekay.shadowedhearts.blocks;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class RelicStoneBlock extends Block {
    public RelicStoneBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Search for nearby PokemonEntities owned by the player
        AABB area = new AABB(pos).inflate(5.0);
        List<PokemonEntity> nearbyPokemon = level.getEntitiesOfClass(PokemonEntity.class, area, pe -> {
            Pokemon p = pe.getPokemon();
            return p != null && player.getUUID().equals(p.getOwnerUUID()) && PokemonAspectUtil.hasShadowAspect(p);
        });

        boolean purifiedAny = false;
        for (PokemonEntity pe : nearbyPokemon) {
            Pokemon p = pe.getPokemon();
            if (PokemonAspectUtil.getHeartGaugePercent(p) == 0) {
                ShadowService.fullyPurify(p, pe);
                purifiedAny = true;
                player.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.purified", p.getDisplayName(false)).withStyle(ChatFormatting.GREEN), false);
            }
        }

        if (!purifiedAny) {
            if (nearbyPokemon.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.no_pokemon"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.shadowedhearts.relic_stone.not_ready"), true);
            }
        }

        return InteractionResult.SUCCESS;
    }
}
