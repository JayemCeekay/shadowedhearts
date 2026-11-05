package com.jayemceekay.shadowedhearts.common.moonwound;

/** Shared portal coordinates: padding extends the drawing surface without scaling the aperture. */
public final class MoonwoundPortalGeometry {
    public static final float QUAD_HALF_WIDTH = 1.4f;
    public static final float QUAD_TOP = 1.3f;

    private MoonwoundPortalGeometry() { }

    /** Original aperture coordinates remain within x = -1..1. */
    public static double halfWidth(float size) {
        requireSize(size);
        return size * 0.5;
    }

    /** Original aperture coordinates remain within y = -1..1. */
    public static double halfHeight(float size) {
        requireSize(size);
        return size * 0.7;
    }

    /** Integer center offset from the original meteorite's top face, including one block of clearance. */
    public static int centerOffsetAboveBodyTop(float size) {
        double offset = 1 + Math.ceil(halfHeight(size));
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Moonwound portal offset exceeds integer range");
        return (int) offset;
    }

    /** Original meteorite top face in the shader's centered, normalized aperture coordinates. */
    public static double fogFloor(float size) {
        return -centerOffsetAboveBodyTop(size) / halfHeight(size);
    }

    /** Leave drawing space below the contact plane for the fog's soft falloff. */
    public static double quadBottom(float size) {
        return Math.min(-1.6, fogFloor(size) - 0.35);
    }

    private static void requireSize(float size) {
        if (!Float.isFinite(size) || size <= 0) throw new IllegalArgumentException("Moonwound portal size must be positive and finite");
    }
}
