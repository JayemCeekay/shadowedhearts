package com.jayemceekay.shadowedhearts.signals;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mission Signal item. Holds a minimal payload in CustomData: Theme, Tier, Affixes, Seed.
 */
public class MissionSignalItem extends Item {
    public static final String TAG_THEME = "Theme";
    public static final String TAG_TIER = "Tier";
    public static final String TAG_AFFIXES = "Affixes";
    public static final String TAG_SEED = "Seed";

    public MissionSignalItem(Properties properties) {
        super(properties);
    }

    /**
     * Creates a Mission Signal stack with the specified payload.
     */
    public static ItemStack create(Item item, ResourceLocation theme, int tier, List<ResourceLocation> affixes, long seed) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_THEME, theme.toString());
        tag.putInt(TAG_TIER, tier);
        ListTag list = new ListTag();
        if (affixes != null) {
            for (ResourceLocation rl : affixes) {
                list.add(StringTag.valueOf(rl.toString()));
            }
        }
        tag.put(TAG_AFFIXES, list);
        tag.putLong(TAG_SEED, seed);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static @Nullable CompoundTag readTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    public static @Nullable ResourceLocation getTheme(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        if (tag == null || !tag.contains(TAG_THEME)) return null;
        try {
            return ResourceLocation.parse(tag.getString(TAG_THEME));
        } catch (Exception e) {
            return null;
        }
    }

    public static int getTier(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        return tag != null ? tag.getInt(TAG_TIER) : 0;
    }

    public static List<ResourceLocation> getAffixes(ItemStack stack) {
        List<ResourceLocation> out = new ArrayList<>();
        CompoundTag tag = readTag(stack);
        if (tag == null || !tag.contains(TAG_AFFIXES)) return out;
        ListTag list = tag.getList(TAG_AFFIXES, StringTag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(ResourceLocation.parse(list.getString(i)));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static long getSeed(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        return tag != null ? tag.getLong(TAG_SEED) : 0L;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
        ResourceLocation theme = getTheme(stack);
        int tier = getTier(stack);
        List<ResourceLocation> aff = getAffixes(stack);
        long seed = getSeed(stack);
        if (theme != null) tooltip.add(Component.literal("Theme: " + theme).withStyle(ChatFormatting.GRAY));
        if (tier > 0) tooltip.add(Component.literal("Tier: " + tier).withStyle(ChatFormatting.GRAY));
        if (!aff.isEmpty()) tooltip.add(Component.literal("Affixes: " + aff).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("Seed: " + seed).withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flags);
    }
}
