package com.jayemceekay.shadowedhearts.client;

import com.jayemceekay.shadowedhearts.content.items.AuraReaderItem;
import com.jayemceekay.shadowedhearts.client.gui.BoundedGuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/** Wrap Reader instructions when the client builds a tooltip, using its current font and GUI width. */
public final class AuraReaderTooltips {
    private AuraReaderTooltips() {}

    public static void wrap(ItemStack stack, List<Component> lines, Item.TooltipContext context, TooltipFlag flag) {
        if (!(stack.getItem() instanceof AuraReaderItem)) return;
        Minecraft client = Minecraft.getInstance();
        wrapDescriptions(lines, client.font.getSplitter(), client.getWindow().getGuiScaledWidth());
    }

    public static int wrapWidth(int viewportWidth) {
        // Reserve room for the vanilla tooltip's border and cursor offset on either side.
        return Math.max(1, Math.min(240, viewportWidth - 24));
    }

    public static void wrapDescriptions(List<Component> lines, StringSplitter splitter, int viewportWidth) {
        var wrapped = new ArrayList<Component>();
        for (Component line : lines) {
            if (!(line.getContents() instanceof TranslatableContents text)
                    || !(text.getKey().equals("item.shadowedhearts.aura_reader.tooltip")
                    || text.getKey().startsWith("item.shadowedhearts.aura_reader.tooltip."))) {
                wrapped.add(line);
                continue;
            }
            wrapped.addAll(BoundedGuiText.wrap(line, wrapWidth(viewportWidth), splitter));
        }
        lines.clear();
        lines.addAll(wrapped);
    }
}
