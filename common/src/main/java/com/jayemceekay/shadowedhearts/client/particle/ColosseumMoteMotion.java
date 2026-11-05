package com.jayemceekay.shadowedhearts.client.particle;

import net.minecraft.world.phys.Vec3;

/** Independent birth directions and bounded, particle-local curvature. */
final class ColosseumMoteMotion {
    private ColosseumMoteMotion() {}

    static Vec3 initialVelocity(double scale, double headingRoll, double speedRoll, double riseRoll) {
        double localScale = scale(scale);
        double angle = unit(headingRoll) * Math.PI * 2.0;
        double horizontalSpeed = (0.004 + 0.020 * unit(speedRoll)) * localScale;
        double rise = (0.024 + 0.050 * unit(riseRoll)) * localScale;
        return new Vec3(Math.cos(angle) * horizontalSpeed, rise, Math.sin(angle) * horizontalSpeed);
    }

    static Vec3 driftDelta(double ageTicks, float seed, double scale) {
        return driftOffset(ageTicks, seed, scale).subtract(driftOffset(ageTicks - 1.0, seed, scale));
    }

    static Vec3 driftOffset(double ageTicks, float seed, double scale) {
        double age = Double.isFinite(ageTicks) ? Math.max(0.0, ageTicks) : 0.0;
        double phase = unit(seed) * Math.PI * 2.0;
        double localScale = scale(scale);
        double xAmplitude = (0.07 + 0.09 * fraction(seed * 7.31)) * localScale;
        double zAmplitude = (0.06 + 0.10 * fraction(seed * 11.73)) * localScale;
        double yAmplitude = (0.018 + 0.025 * fraction(seed * 19.17)) * localScale;
        double xSpeed = 0.075 + 0.100 * fraction(seed * 13.47);
        double zSpeed = 0.060 + 0.090 * fraction(seed * 27.11);
        double ySpeed = 0.050 + 0.080 * fraction(seed * 31.71);
        return new Vec3(xAmplitude * (Math.sin(phase + age * xSpeed) - Math.sin(phase)),
                yAmplitude * (Math.sin(phase * 1.7 + age * ySpeed) - Math.sin(phase * 1.7)),
                zAmplitude * (Math.cos(phase * 1.3 + age * zSpeed) - Math.cos(phase * 1.3)));
    }

    static double riseAcceleration(float seed) {
        // Applied after vanilla's 0.98 velocity damping to sustain buoyancy.
        return 0.0007 + unit(seed) * 0.0004;
    }

    private static double scale(double value) { return Double.isFinite(value) ? Math.max(0.7, Math.min(1.6, value)) : 1.0; }
    private static double unit(double value) { return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.5; }
    private static double fraction(double value) { return Double.isFinite(value) ? value - Math.floor(value) : 0.5; }
}
