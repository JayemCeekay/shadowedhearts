package com.jayemceekay.shadowedhearts.config;

/** Client presentation choice for the finished Shadow aura effects. */
public enum AuraPixelationStyle {
    SMOOTH(1), PIXELATED(4), CHUNKY(8);

    private final int pixelSize;

    AuraPixelationStyle(int pixelSize) {
        this.pixelSize = pixelSize;
    }

    public int pixelSize() { return pixelSize; }
}
