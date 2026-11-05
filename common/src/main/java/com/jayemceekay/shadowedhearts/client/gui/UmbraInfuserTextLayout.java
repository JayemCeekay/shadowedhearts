package com.jayemceekay.shadowedhearts.client.gui;

import java.util.function.IntFunction;

/** Text geometry is measured in GUI pixels, after the container's viewport transform is removed. */
public final class UmbraInfuserTextLayout {
    public static final int TOOLTIP_HORIZONTAL_PADDING = 8;
    public static final double STATUS_SCALE = 0.5;
    public static final int STATUS_LINES = 2;
    public static final int STATUS_LINE_SPACING = 6;

    private UmbraInfuserTextLayout() {}

    public record Size(int width, int height) {}

    public record Tooltip(int left, int top, int width, int height, int wrapWidth,
                          int contentWidth, int contentHeight, double scale) {}

    /** Widen unusually tall help before scaling its complete measured footprint into the viewport. */
    public static Tooltip tooltip(int viewportWidth, int viewportHeight, int mouseX, int mouseY,
                                  IntFunction<Size> measure) {
        int width = Math.max(1, viewportWidth), height = Math.max(1, viewportHeight);
        int margin = Math.min(3, (Math.min(width, height) - 1) / 2);
        int availableWidth = width - margin * 2, availableHeight = height - margin * 2;
        int maxWrapWidth = Math.max(1, availableWidth - TOOLTIP_HORIZONTAL_PADDING);
        int wrapWidth = Math.min(220, maxWrapWidth);
        Size size = measure.apply(wrapWidth);
        if (size.height > availableHeight && wrapWidth < maxWrapWidth) {
            int low = wrapWidth, high = maxWrapWidth;
            while (low < high) {
                int middle = (low + high) / 2;
                if (measure.apply(middle).height > availableHeight) low = middle + 1;
                else high = middle;
            }
            wrapWidth = low;
            size = measure.apply(wrapWidth);
        }
        int contentWidth = Math.max(1, size.width) + TOOLTIP_HORIZONTAL_PADDING;
        int contentHeight = Math.max(2, size.height);
        double scale = Math.min(1.0, Math.min(availableWidth / (double) contentWidth,
                availableHeight / (double) contentHeight));
        int renderedWidth = Math.min(availableWidth, (int) Math.ceil(contentWidth * scale));
        int renderedHeight = Math.min(availableHeight, (int) Math.ceil(contentHeight * scale));
        int left = clamp(mouseX - renderedWidth / 2, margin, width - margin - renderedWidth);
        int below = mouseY + 12;
        int preferredTop = below + renderedHeight <= height - margin ? below : mouseY - renderedHeight - 6;
        int top = clamp(preferredTop, margin, height - margin - renderedHeight);
        return new Tooltip(left, top, renderedWidth, renderedHeight, wrapWidth,
                contentWidth, contentHeight, scale);
    }

    public record Status(int wrapWidth, boolean ellipsis) {}

    /** Compact panels keep readable half-size text; their full content is available on hover. */
    public static Status status(int displayWidth, int lineCount, int ellipsisWidth) {
        int logicalWidth = Math.max(1, (int) (displayWidth / STATUS_SCALE));
        boolean clipped = lineCount > STATUS_LINES;
        return new Status(clipped ? Math.max(1, logicalWidth - ellipsisWidth) : logicalWidth, clipped);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
