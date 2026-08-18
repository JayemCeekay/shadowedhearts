package com.jayemceekay.shadowedhearts.client.aura;

import java.util.Objects;

/**
 * Integer HUD rectangle with an inclusive left/top and exclusive right/bottom.
 */
public record AuraReaderHudRect(int left, int top, int right, int bottom) {
    public AuraReaderHudRect {
        if (right < left) {
            throw new IllegalArgumentException("right must not precede left");
        }
        if (bottom < top) {
            throw new IllegalArgumentException("bottom must not precede top");
        }
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    /**
     * Returns whether this rectangle and {@code other} share positive area.
     * Touching exclusive edges do not count as an intersection.
     */
    public boolean intersects(AuraReaderHudRect other) {
        Objects.requireNonNull(other, "other");
        return width() > 0
                && height() > 0
                && other.width() > 0
                && other.height() > 0
                && left < other.right
                && right > other.left
                && top < other.bottom
                && bottom > other.top;
    }

    /**
     * Clamps this rectangle into a viewport beginning at {@code (0, 0)}.
     * Size is preserved when it fits and reduced only when the viewport is
     * smaller than the rectangle.
     */
    public AuraReaderHudRect clamp(int viewportWidth, int viewportHeight) {
        int width = Math.max(0, viewportWidth);
        int height = Math.max(0, viewportHeight);
        return clamp(new AuraReaderHudRect(0, 0, width, height));
    }

    /**
     * Clamps this rectangle into an arbitrary bounding rectangle.
     */
    public AuraReaderHudRect clamp(AuraReaderHudRect bounds) {
        Objects.requireNonNull(bounds, "bounds");

        int clampedWidth = Math.min(width(), bounds.width());
        int clampedHeight = Math.min(height(), bounds.height());
        int clampedLeft = clampValue(
                left, bounds.left, bounds.right - clampedWidth);
        int clampedTop = clampValue(
                top, bounds.top, bounds.bottom - clampedHeight);
        return new AuraReaderHudRect(
                clampedLeft,
                clampedTop,
                clampedLeft + clampedWidth,
                clampedTop + clampedHeight);
    }

    private static int clampValue(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
