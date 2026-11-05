package com.jayemceekay.shadowedhearts.content.items;

import com.jayemceekay.shadowedhearts.common.shadow.NullCharmClientState;
import com.jayemceekay.shadowedhearts.common.shadow.NullCharmEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Reusable held item whose tooltip follows the server's active Shadow battle rules. */
public final class NullCharmItem extends Item {
    public NullCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        NullCharmEffect effect = NullCharmClientState.INSTANCE.currentEffect(NullCharmEffect::serverCurrent);
        String key = effect == null ? "item.shadowedhearts.null_charm.tooltip_pending" : effect.tooltipKey();
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
