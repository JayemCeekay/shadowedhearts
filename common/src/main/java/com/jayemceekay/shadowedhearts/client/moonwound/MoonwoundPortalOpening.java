package com.jayemceekay.shadowedhearts.client.moonwound;

/** Opening timing in seconds; the aperture and descending front use the same times in portal.fsh. */
public final class MoonwoundPortalOpening {
    public static final float SETTLED_SECONDS = 60;
    public static final double IMPACT_SECONDS = 2.65;
    private static final double SURFACE_FADE_SECONDS = 0.40;
    private static final double SPREAD_SPEED = 4.0;
    private static final double FRONT_SOFTNESS = 0.65;

    private MoonwoundPortalOpening() { }

    /** Reveal the established flow outward along each rock-following route after the first impact. */
    public static double surfaceReveal(double openingAge, double distanceAlongPath) {
        if (!Double.isFinite(openingAge) || !Double.isFinite(distanceAlongPath) || distanceAlongPath < 0)
            throw new IllegalArgumentException("Invalid Moonwound opening age or surface distance");
        if (openingAge <= IMPACT_SECONDS) return 0;
        double sinceImpact = openingAge - IMPACT_SECONDS;
        double reach = sinceImpact * SPREAD_SPEED;
        return smooth(0, SURFACE_FADE_SECONDS, sinceImpact)
                * smooth(distanceAlongPath - FRONT_SOFTNESS, distanceAlongPath + FRONT_SOFTNESS, reach);
    }

    private static double smooth(double low, double high, double value) {
        double t = Math.max(0, Math.min(1, (value - low) / (high - low)));
        return t * t * (3 - 2 * t);
    }
}
