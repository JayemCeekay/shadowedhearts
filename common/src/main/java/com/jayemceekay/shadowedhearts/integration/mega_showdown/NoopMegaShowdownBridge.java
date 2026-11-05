package com.jayemceekay.shadowedhearts.integration.mega_showdown;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class NoopMegaShowdownBridge implements MegaShowdownBridge {
    @Override
    public Item createShadowiumZ(Item.Properties properties) {
        return new Item(properties) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                tooltip.add(Component.translatable("tooltip.shadowedhearts.shadowium_z.requires_mega_showdown")
                        .withStyle(ChatFormatting.GOLD));
            }
        };
    }

    @Override
    public void revertMegaShowdownAspects(Pokemon pokemon) {
    }
}
