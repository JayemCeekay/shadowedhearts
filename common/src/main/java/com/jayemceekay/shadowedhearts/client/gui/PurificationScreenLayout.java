package com.jayemceekay.shadowedhearts.client.gui;

/** The complete authored chamber bounds include its lower tutorial button and left trim. */
public final class PurificationScreenLayout {
    public static final int BASE_WIDTH = 349;
    public static final int BASE_HEIGHT = 205;
    public static final int CONTENT_LEFT = -2;
    public static final int CONTENT_WIDTH = BASE_WIDTH - CONTENT_LEFT;
    public static final int CONTENT_HEIGHT = 217;

    private PurificationScreenLayout() {}

    public static GuiViewportFit fit(int viewportWidth, int viewportHeight) {
        int width = Math.max(1, viewportWidth), height = Math.max(1, viewportHeight);
        double margin = Math.min(4.0, Math.min(width, height) / 4.0);
        double scale = Math.min(1, Math.min((width - margin * 2) / CONTENT_WIDTH,
                (height - margin * 2) / CONTENT_HEIGHT));
        int left = (viewportWidth - BASE_WIDTH) / 2 + CONTENT_LEFT;
        int top = (viewportHeight - BASE_HEIGHT) / 2;
        if (scale == 1 && left >= margin && top >= margin
                && left + CONTENT_WIDTH <= width - margin && top + CONTENT_HEIGHT <= height - margin) {
            return new GuiViewportFit(1, 0, 0);
        }
        return new GuiViewportFit(scale, (width - CONTENT_WIDTH * scale) / 2 - left * scale,
                (height - CONTENT_HEIGHT * scale) / 2 - top * scale);
    }

    public static int tutorialWidth(int viewportWidth) {
        return Math.min(300, Math.max(160, viewportWidth / 4));
    }
}
