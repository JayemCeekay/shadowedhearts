package com.jayemceekay.shadowedhearts.config;

/** Pixelation strength at the reference size; on-screen texels shrink with distance. */
public enum MoonwoundPortalStyle {
    SMOOTH(1), PIXELATED(4), CHUNKY(8);

    private final int pixelSize;

    MoonwoundPortalStyle(int pixelSize) {
        this.pixelSize = pixelSize;
    }

    public int pixelSize() { return pixelSize; }
}
