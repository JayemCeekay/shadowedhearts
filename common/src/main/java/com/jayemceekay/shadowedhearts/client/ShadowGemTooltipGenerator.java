package com.jayemceekay.shadowedhearts.client;

import com.cobblemon.mod.common.client.tooltips.TooltipGenerator;
import com.jayemceekay.shadowedhearts.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Adds Shadow Gem's description through the same tooltip pipeline as Cobblemon's gems. */
public final class ShadowGemTooltipGenerator extends TooltipGenerator {
    @Override
    public List<Component> generateTooltip(ItemStack stack, List<Component> lines) {
        if (!stack.is(ModItems.SHADOW_GEM.get())) {
            return List.of();
        }
        return List.of(
                Component.translatable("item.shadowedhearts.shadow_gem.tooltip_1").withStyle(ChatFormatting.GRAY),
                Component.translatable("item.shadowedhearts.shadow_gem.tooltip_2").withStyle(ChatFormatting.GRAY)
        );
    }
}
