package com.jayemceekay.shadowedhearts.signals;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Simple item representing a Signal Fragment with a kind, value, and rarity.
 * Carries lightweight metadata that the synthesis UI/system can later consume.
 */
public class SignalFragmentItem extends Item {
    private final FragmentKind kind;
    private final String value;
    private final FragmentRarity rarity;
    private final @Nullable String lore;

    public SignalFragmentItem(Properties props, FragmentKind kind, String value, FragmentRarity rarity, @Nullable String lore) {
        super(props);
        this.kind = kind;
        this.value = value;
        this.rarity = rarity;
        this.lore = lore;
    }

    public FragmentKind getKind() { return kind; }
    public String getValue() { return value; }
    public FragmentRarity getRarity() { return rarity; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        tooltip.add(Component.literal(kind + ": " + value).withStyle(ChatFormatting.GRAY));
        ChatFormatting color = switch (rarity) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
        };
        tooltip.add(Component.literal("Rarity: " + rarity).withStyle(color));
        if (lore != null && !lore.isEmpty()) {
            tooltip.add(Component.literal(lore).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flags);
    }
}
