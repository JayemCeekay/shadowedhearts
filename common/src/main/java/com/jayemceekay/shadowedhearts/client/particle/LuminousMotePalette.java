package com.jayemceekay.shadowedhearts.client.particle;

/** Violet and pink-lilac points, punctuated by cool white and warm amber/cream. */
final class LuminousMotePalette {
    private static final Color VIOLET = new Color(0.60f, 0.24f, 1.0f);
    private static final Color LAVENDER = new Color(0.82f, 0.55f, 1.0f);
    private static final Color PINK = new Color(1.0f, 0.24f, 0.68f);
    private static final Color WHITE = new Color(0.94f, 0.92f, 1.0f);
    private static final Color AMBER = new Color(1.0f, 0.56f, 0.12f);
    private static final Color CREAM = new Color(1.0f, 0.82f, 0.42f);

    private LuminousMotePalette() {}

    static Color colosseum(float roll) {
        if (!Float.isFinite(roll) || roll < 0.35f) return VIOLET;
        if (roll < 0.60f) return LAVENDER;
        if (roll < 0.78f) return PINK;
        if (roll < 0.88f) return WHITE;
        return roll < 0.96f ? AMBER : CREAM;
    }

    record Color(float red, float green, float blue) {}
}
