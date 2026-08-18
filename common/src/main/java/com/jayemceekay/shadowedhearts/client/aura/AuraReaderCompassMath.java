package com.jayemceekay.shadowedhearts.client.aura;

/**
 * Pure compass helpers shared by Aura Reader presentation code.
 *
 * <p>Minecraft yaw is {@code 0} toward south ({@code +Z}), {@code -90}
 * toward east ({@code +X}), {@code 90} toward west ({@code -X}), and
 * {@code 180} toward north ({@code -Z}).</p>
 */
public final class AuraReaderCompassMath {
    private AuraReaderCompassMath() {
    }

    public static float worldYaw(double directionX, double directionZ) {
        if (!Double.isFinite(directionX) || !Double.isFinite(directionZ)) {
            return 0.0f;
        }
        return normalizeSignedDelta((float) Math.toDegrees(Math.atan2(-directionX, directionZ)));
    }

    public static float signedDelta(float fromYaw, float toYaw) {
        return normalizeSignedDelta(toYaw - fromYaw);
    }

    public static float normalizeSignedDelta(float degrees) {
        if (!Float.isFinite(degrees)) {
            return 0.0f;
        }

        float wrapped = degrees % 360.0f;
        if (wrapped <= -180.0f) {
            wrapped += 360.0f;
        } else if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        }
        return wrapped;
    }

    public static boolean isInsideVisibleArc(float relativeYaw, float visibleArcDegrees) {
        if (!Float.isFinite(relativeYaw) || !Float.isFinite(visibleArcDegrees) || visibleArcDegrees <= 0.0f) {
            return false;
        }
        float halfArc = Math.min(360.0f, visibleArcDegrees) * 0.5f;
        return Math.abs(normalizeSignedDelta(relativeYaw)) <= halfArc;
    }

    /**
     * Maps a relative yaw to the compass rail, where {@code -1} is the left
     * edge, {@code 0} is center, and {@code 1} is the right edge.
     */
    public static float normalizedXPosition(float relativeYaw, float visibleArcDegrees) {
        if (!Float.isFinite(relativeYaw) || !Float.isFinite(visibleArcDegrees) || visibleArcDegrees <= 0.0f) {
            return 0.0f;
        }
        float halfArc = Math.min(360.0f, visibleArcDegrees) * 0.5f;
        float position = normalizeSignedDelta(relativeYaw) / halfArc;
        return Math.max(-1.0f, Math.min(1.0f, position));
    }

    public static int edgeSide(float relativeYaw) {
        float normalized = normalizeSignedDelta(relativeYaw);
        if (normalized < 0.0f) {
            return -1;
        }
        if (normalized > 0.0f) {
            return 1;
        }
        return 0;
    }

    /**
     * Smoothly fades compass content as it approaches either normalized rail
     * edge. The result is one through the center section and zero at ±1.
     */
    public static float edgeFade(float normalizedX, float fadeStart) {
        if (!Float.isFinite(normalizedX) || !Float.isFinite(fadeStart)) {
            return 0.0f;
        }

        float distance = Math.min(1.0f, Math.abs(normalizedX));
        float start = Math.max(0.0f, Math.min(0.999f, fadeStart));
        if (distance <= start) {
            return 1.0f;
        }

        float progress = (distance - start) / (1.0f - start);
        float smoothstep = progress * progress * (3.0f - 2.0f * progress);
        return Math.max(0.0f, Math.min(1.0f, 1.0f - smoothstep));
    }

    /**
     * Returns a shallow upward arch in screen-space pixels. The center rises
     * by {@code maximumRise}; both ends return to the original rail height.
     */
    public static float upwardCurveOffset(float normalizedX, float maximumRise) {
        if (!Float.isFinite(normalizedX)
                || !Float.isFinite(maximumRise)
                || maximumRise <= 0.0f) {
            return 0.0f;
        }

        float position = Math.max(-1.0f, Math.min(1.0f, normalizedX));
        return -maximumRise * (1.0f - position * position);
    }

    /**
     * Produces a broad center section that tapers symmetrically toward both
     * rail ends. The quadratic profile keeps most of the center at close to
     * full thickness while allowing the final quarter to narrow decisively.
     */
    public static float taperedRailThickness(
            float normalizedX,
            float centerThickness,
            float endThickness) {
        if (!Float.isFinite(normalizedX)
                || !Float.isFinite(centerThickness)
                || !Float.isFinite(endThickness)) {
            return 0.0f;
        }

        float center = Math.max(0.0f, centerThickness);
        float end = Math.max(0.0f, Math.min(center, endThickness));
        float position = Math.max(-1.0f, Math.min(1.0f, normalizedX));
        float centerWeight = 1.0f - position * position;
        return end + (center - end) * centerWeight;
    }

    public static float lerpShortestAngle(float fromYaw, float toYaw, float progress) {
        float clampedProgress = Math.max(0.0f, Math.min(1.0f, progress));
        return normalizeSignedDelta(fromYaw + signedDelta(fromYaw, toYaw) * clampedProgress);
    }
}
