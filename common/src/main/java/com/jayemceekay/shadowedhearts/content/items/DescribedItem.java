package com.jayemceekay.shadowedhearts.content.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import com.jayemceekay.shadowedhearts.common.items.ItemRuleFeedback;

/** An inventory material or held item with concise, translated usage information. */
public final class DescribedItem extends Item {
    private final int tooltipLines;

    public DescribedItem(Properties properties, int tooltipLines) {
        super(properties);
        this.tooltipLines = tooltipLines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (ItemRuleFeedback.appendDisabled(stack, tooltip)) return;
        for (int line = 1; line <= tooltipLines; line++) {
            tooltip.add(Component.translatable(getDescriptionId() + ".tooltip_" + line)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
