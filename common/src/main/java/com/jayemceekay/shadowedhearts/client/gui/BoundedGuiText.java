package com.jayemceekay.shadowedhearts.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Fits a single display row without losing styled spans or splitting a Unicode code point. */
public final class BoundedGuiText {
    private BoundedGuiText() {}

    /** Wrap complete text while retaining component styles and hover metadata on every span. */
    public static List<Component> wrap(Component text, int width, Font font) {
        return wrap(text, width, font.getSplitter());
    }

    public static List<Component> wrap(Component text, int width, StringSplitter splitter) {
        List<Component> lines = new ArrayList<>();
        for (FormattedText part : splitter.splitLines(text, Math.max(1, width), Style.EMPTY)) {
            var line = Component.empty();
            part.visit((style, value) -> {
                line.append(Component.literal(value).withStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            lines.add(line);
        }
        return lines;
    }

    public static MutableComponent fit(Component text, int width, Font font) {
        return fit(text, width, (ToIntFunction<Component>) font::width);
    }

    public static MutableComponent fit(Component text, int width, ToIntFunction<Component> measure) {
        if (width <= 0) return Component.empty();
        if (measure.applyAsInt(text) <= width) return text.copy();
        Component suffix = Component.literal("...").withStyle(text.getStyle());
        // A very narrow field should not overflow just to display its ellipsis.
        if (measure.applyAsInt(suffix) > width) suffix = Component.empty();
        int low = 0, high = text.getString().codePointCount(0, text.getString().length());
        while (low < high) {
            int count = (low + high + 1) >>> 1;
            if (measure.applyAsInt(prefix(text, count).append(suffix.copy())) <= width) low = count;
            else high = count - 1;
        }
        return prefix(text, low).append(suffix.copy());
    }

    private static MutableComponent prefix(Component text, int codePoints) {
        MutableComponent result = Component.empty().withStyle(text.getStyle());
        int[] remaining = {codePoints};
        text.visit((style, value) -> {
            int count = Math.min(remaining[0], value.codePointCount(0, value.length()));
            if (count > 0) result.append(Component.literal(value.substring(0, value.offsetByCodePoints(0, count))).withStyle(style));
            remaining[0] -= count;
            return remaining[0] == 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
