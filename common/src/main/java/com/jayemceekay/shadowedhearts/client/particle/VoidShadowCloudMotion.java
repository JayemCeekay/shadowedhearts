package com.jayemceekay.shadowedhearts.client.particle;

/** Bounded presentation and particle-local curl for the shapeshift smoke. */
final class VoidShadowCloudMotion {
    static final float MIN_DIAMETER = 0.12f;
    static final float MAX_DIAMETER = 4.0f;

    private VoidShadowCloudMotion() {}

    static float clampDiameter(float diameter) {
        return Float.isFinite(diameter) ? Math.max(MIN_DIAMETER, Math.min(MAX_DIAMETER, diameter)) : 0.75f;
    }

    static float alpha(float age, int lifetime) {
        float t = progress(age, lifetime);
        // Conceal the change on the first frame, fill quickly, then leave a soft tail.
        return (0.68f + 0.20f * smoothstep(0, 0.10f, t)) * (1 - smoothstep(0.32f, 1, t));
    }

    static float diameter(float baseDiameter, float age, int lifetime) {
        // Size grows while opacity tapers; the final footprint never exceeds the requested cap.
        return clampDiameter(baseDiameter) * (0.72f + 0.28f * smoothstep(0, 0.40f, progress(age, lifetime)));
    }

    static Drift driftDelta(float age, float phaseSeed, float curlDirection, float diameter) {
        Drift now = driftOffset(age, phaseSeed, curlDirection, diameter);
        Drift previous = driftOffset(age - 1, phaseSeed, curlDirection, diameter);
        return new Drift(now.x - previous.x, now.y - previous.y, now.z - previous.z);
    }

    static Drift driftOffset(float age, float phaseSeed, float curlDirection, float diameter) {
        double ticks = Float.isFinite(age) ? Math.max(0, age) : 0;
        double seed = Float.isFinite(phaseSeed) ? Math.max(0, Math.min(1, phaseSeed)) : 0.5;
        double phase = seed * Math.PI * 2;
        double direction = curlDirection < 0 ? -1 : 1;
        double amplitude = Math.min(0.18, clampDiameter(diameter) * 0.10);
        double angle = phase + ticks * (0.18 + seed * 0.08) * direction;
        return new Drift(amplitude * (Math.cos(angle) - Math.cos(phase)),
                amplitude * 0.30 * (Math.sin(phase + ticks * 0.13) - Math.sin(phase)),
                amplitude * (Math.sin(angle) - Math.sin(phase)));
    }

    static double velocity(double component) {
        return Double.isFinite(component) ? Math.max(-0.09, Math.min(0.09, component)) : 0;
    }

    private static float progress(float age, int lifetime) {
        return Float.isFinite(age) ? Math.max(0, Math.min(1, age / Math.max(1, lifetime))) : 0;
    }

    private static float smoothstep(float lower, float upper, float value) {
        float t = Math.max(0, Math.min(1, (value - lower) / (upper - lower)));
        return t * t * (3 - 2 * t);
    }

    record Drift(double x, double y, double z) {}
}
