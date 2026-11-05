package com.jayemceekay.shadowedhearts.client.gui;

/** Uniform viewport fit around an existing, integer-centered panel; coordinates remain in GUI pixels. */
public record GuiViewportFit(double scale, double offsetX, double offsetY) {
    private static final double FRAME_MARGIN = 4.0;

    public static GuiViewportFit fit(int viewportWidth, int viewportHeight, int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) throw new IllegalArgumentException("Panel dimensions must be positive");
        int width = Math.max(1, viewportWidth);
        int height = Math.max(1, viewportHeight);
        double margin = Math.min(FRAME_MARGIN, Math.min(width, height) / 4.0);
        double scale = Math.min(1.0, Math.min((width - 2 * margin) / panelWidth,
                (height - 2 * margin) / panelHeight));
        // Keep the existing pixel alignment exactly when the authored layout already fits.
        if (scale == 1.0) return new GuiViewportFit(1.0, 0.0, 0.0);
        int left = (viewportWidth - panelWidth) / 2;
        int top = (viewportHeight - panelHeight) / 2;
        return new GuiViewportFit(scale, (width - panelWidth * scale) / 2 - left * scale,
                (height - panelHeight * scale) / 2 - top * scale);
    }

    public double toContentX(double screenX) { return (screenX - offsetX) / scale; }
    public double toContentY(double screenY) { return (screenY - offsetY) / scale; }
    public double toScreenX(double contentX) { return contentX * scale + offsetX; }
    public double toScreenY(double contentY) { return contentY * scale + offsetY; }
    public int mouseX(int screenX) { return (int) Math.floor(toContentX(screenX)); }
    public int mouseY(int screenY) { return (int) Math.floor(toContentY(screenY)); }
    public double dragDelta(double screenDelta) { return screenDelta / scale; }
}
