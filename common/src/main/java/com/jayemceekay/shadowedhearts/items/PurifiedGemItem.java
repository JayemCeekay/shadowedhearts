package com.jayemceekay.shadowedhearts.items;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PurifiedGemItem extends Item {
    public PurifiedGemItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(target instanceof PokemonEntity pe)) return InteractionResult.PASS;
        Pokemon pokemon = pe.getPokemon();
        if (pokemon == null) return InteractionResult.PASS;

        // Check if owned
        if (pokemon.getOwnerUUID() == null) {
             player.displayClientMessage(Component.translatable("message.shadowedhearts.purified_gem.not_owned").withStyle(ChatFormatting.RED), true);
             return InteractionResult.FAIL;
        }

        // Check if already shadow
        if (PokemonAspectUtil.hasShadowAspect(pokemon)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.purified_gem.already_shadow").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        // Check if already immunized
        if (PokemonAspectUtil.isImmunized(pokemon)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.purified_gem.already_immunized").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.PASS;
        }

        // Immunize
        PokemonAspectUtil.setImmunizedProperty(pokemon, true);
        player.displayClientMessage(Component.translatable("message.shadowedhearts.purified_gem.immunized", pokemon.getDisplayName(false).getString()).withStyle(ChatFormatting.GREEN), true);

        // Consume item
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }
}
