package com.jayemceekay.shadowedhearts.common.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Connected clients use server snapshots; unknown settings never advertise an active effect. */
public final class ItemRuleFeedback {
    private ItemRuleFeedback() {}

    public static boolean permitsClient(String effect) {
        ItemRuleSettings rules = ItemRuleClientState.INSTANCE.currentSettings(ItemRuleSettings::serverCurrent);
        return rules != null && rules.effectEnabled(effect);
    }

    public static boolean appendDisabled(ItemStack stack, List<Component> tooltip) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace("_", "");
        String effect = ItemRuleSettings.EFFECT_IDS.stream().filter(candidate -> candidate.equalsIgnoreCase(id)).findFirst().orElse(null);
        if (effect == null) return false;
        ItemRuleSettings rules = ItemRuleClientState.INSTANCE.currentSettings(ItemRuleSettings::serverCurrent);
        if (rules != null && rules.effectEnabled(effect)) return false;
        tooltip.add(Component.translatable(rules == null ? "tooltip.shadowedhearts.item_rule.pending" : "tooltip.shadowedhearts.item_rule.disabled")
                .withStyle(ChatFormatting.GRAY));
        return true;
    }
}
