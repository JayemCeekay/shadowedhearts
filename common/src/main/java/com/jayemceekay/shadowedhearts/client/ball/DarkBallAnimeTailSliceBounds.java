package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

/** Conservative, continuous cone slopes for the packed stream's forward join. */
final class DarkBallAnimeTailSliceBounds {
    private static final int INTERVALS = 16;

    private DarkBallAnimeTailSliceBounds() { }

    /** Positive infinity keeps the caller's previous safe bound on unsupported input. */
    static double requiredSlope(Vec3 tip, Vec3 axis, double join, double length, float[] packed, int count) {
        return requiredSlope(tip, axis, join, length, packed, count, 0, 0);
    }

    /**
     * Certifies radius <= baseRadius + slope * (z - crossing) throughout the join.
     * The credited radius and crossing must match the values used by the caller's
     * flare. Positive infinity retains its previous safe bound on invalid input.
     */
    static double requiredSlope(Vec3 tip, Vec3 axis, double join, double length, float[] packed, int count,
                                double crossing, double baseRadius) {
        if (!finite(tip) || !finite(axis) || !(axis.lengthSqr() > 0) || !Double.isFinite(axis.lengthSqr())
                || !Double.isFinite(join) || !Double.isFinite(length) || !(join > 0) || length < join
                || !Double.isFinite(crossing) || crossing < 0 || crossing >= join
                || !Double.isFinite(baseRadius) || baseRadius < 0
                || count < 0 || count > 24 || packed == null || packed.length < count * 16)
            return Double.POSITIVE_INFINITY;
        Vec3 direction = axis.scale(1 / axis.length());
        double required = 0;
        for (int i = 0; i < count; i++) {
            int at = i * 16;
            Vec3 center = vector(packed, at), r0 = vector(packed, at + 4);
            Vec3 r1 = vector(packed, at + 8), r2 = vector(packed, at + 12);
            if (!finite(center) || !finite(r0) || !finite(r1) || !finite(r2)) return Double.POSITIVE_INFINITY;
            double determinant = r0.dot(r1.cross(r2));
            if (!Double.isFinite(determinant) || determinant == 0) return Double.POSITIVE_INFINITY;
            Vec3 a = r1.cross(r2).scale(1 / determinant);
            Vec3 b = r2.cross(r0).scale(1 / determinant);
            Vec3 c = r0.cross(r1).scale(1 / determinant);
            if (!finite(a) || !finite(b) || !finite(c)) return Double.POSITIVE_INFINITY;
            Vec3 offset = center.subtract(tip);
            double centerZ = offset.dot(direction);
            Vec3 h = new Vec3(a.dot(direction), b.dot(direction), c.dot(direction));
            double height = h.length();
            if (!(height > 0) || !Double.isFinite(height)) return Double.POSITIVE_INFINITY;
            Vec3 normal = h.scale(1 / height);
            Vec3 hint = Math.abs(normal.x) <= Math.abs(normal.y) && Math.abs(normal.x) <= Math.abs(normal.z)
                    ? new Vec3(1, 0, 0) : Math.abs(normal.y) <= Math.abs(normal.z)
                    ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
            Vec3 e = normal.cross(hint);
            e = e.scale(1 / e.length());
            Vec3 f = normal.cross(e);
            // In unit-sphere coordinates a plane slice has center normal*t
            // and a disk of radius sqrt(1-t²) in the e/f basis. Mapping that
            // disk directly avoids subtracting nearly equal covariance terms.
            Vec3 u = radial(combine(a, b, c, e), direction);
            Vec3 v = radial(combine(a, b, c, f), direction);
            double uu = u.lengthSqr(), vv = v.lengthSqr(), uv = u.dot(v);
            double diskRadius = Math.sqrt(.5 * (uu + vv + Math.hypot(uu - vv, 2 * uv)));
            Vec3 radialCenter = radial(offset, direction);
            Vec3 radialSlope = radial(combine(a, b, c, normal), direction);
            double arithmeticScale = Math.max(Math.max(maxAbs(tip), maxAbs(center)),
                    Math.max(a.length(), Math.max(b.length(), c.length())));
            arithmeticScale = Math.max(arithmeticScale, Math.max(length, Math.max(crossing, baseRadius)));
            double guard = Math.ulp(arithmeticScale) * 128;
            if (!Double.isFinite(diskRadius) || !Double.isFinite(guard) || join - crossing <= guard)
                return Double.POSITIVE_INFINITY;
            double near = centerZ - height, far = centerZ + height;
            for (int part = 0; part < INTERVALS; part++) {
                double low = join + (length - join) * part / INTERVALS;
                double high = join + (length - join) * (part + 1) / INTERVALS;
                double parameterGuard = guard / height + 1e-13;
                double t0 = clamp((low - centerZ) / height - parameterGuard, -1, 1);
                double t1 = clamp((high - centerZ) / height + parameterGuard, -1, 1);
                // Retain a zero-radius pole outside the real axial support.
                // Its denominator follows that pole (at least join), so the
                // certificate changes continuously when a section enters or
                // leaves the slab, without a distant whole-primitive penalty.
                double denominator = Math.max(join, clamp(low, near, far)) - crossing - guard;
                double centerRadius = Math.max(radialCenter.add(radialSlope.scale(t0)).length(),
                        radialCenter.add(radialSlope.scale(t1)).length());
                double closest = t0 <= 0 && t1 >= 0 ? 0 : Math.min(Math.abs(t0), Math.abs(t1));
                double sliceRadius = diskRadius * Math.sqrt(Math.max(0, 1 - closest * closest));
                required = Math.max(required, Math.max(0, centerRadius + sliceRadius + guard - baseRadius) / denominator);
            }
        }
        return Double.isFinite(required) ? Math.nextUp(required * 1.000001 + 1e-12) : Double.POSITIVE_INFINITY;
    }

    private static Vec3 combine(Vec3 a, Vec3 b, Vec3 c, Vec3 weights) {
        return a.scale(weights.x).add(b.scale(weights.y)).add(c.scale(weights.z));
    }
    private static Vec3 radial(Vec3 value, Vec3 axis) { return value.subtract(axis.scale(value.dot(axis))); }
    private static Vec3 vector(float[] values, int at) { return new Vec3(values[at], values[at + 1], values[at + 2]); }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private static double maxAbs(Vec3 value) { return Math.max(Math.abs(value.x), Math.max(Math.abs(value.y), Math.abs(value.z))); }
    private static boolean finite(Vec3 value) { return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
}
