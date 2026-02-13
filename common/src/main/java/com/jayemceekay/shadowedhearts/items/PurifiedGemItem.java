package com.jayemceekay.shadowedhearts.items;

import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.mark.Marks;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.MarkAddUpdatePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.purified_gem.already_shadow").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        // Check if already immunized
        if (ShadowAspectUtil.isImmunized(pokemon)) {
            player.displayClientMessage(Component.translatable("message.shadowedhearts.purified_gem.already_immunized").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.PASS;
        }

        // Immunize
        ShadowAspectUtil.setImmunizedProperty(pokemon, true);
        player.displayClientMessage(Component.translatable("message.shadowedhearts.purified_gem.immunized", pokemon.getDisplayName(false).getString()).withStyle(ChatFormatting.GREEN), true);
        Mark purityRibbon = Marks.getByIdentifier(ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "ribbon_event_purity"));
        try {
            pokemon.exchangeMark(purityRibbon, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pokemon.onChange(new MarkAddUpdatePacket(() -> pokemon, purityRibbon));
        // Consume item
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }
}
