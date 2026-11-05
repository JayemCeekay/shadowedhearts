package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

/** A source-anchored point which opens into the existing curved stream. */
final class DarkBallAnimeTailCap {
    // Authored room around the source profile keeps the narrow rear connected;
    // this is independent of the numerical rounding margin applied below.
    private static final double SOURCE_OPENING = 1.10;

    private DarkBallAnimeTailCap() { }

    record Frame(Vec3 tip, Vec3 direction, float slope, float length, float flareSlope, float flareOffset) {
        Frame(Vec3 tip, Vec3 direction, float slope, float length) {
            this(tip, direction, slope, length, slope, 0);
        }
    }

    /** All inputs describe the actual packed geometry, not its pre-upload fit. */
    static Frame prepare(Vec3 tip, Vec3[] path, float[] primitives, int count, double radius) {
        return prepare(tip, path, primitives, null, 0, count, radius);
    }

    static Frame prepare(Vec3 tip, Vec3[] path, float[] primitives, Vec3 referencePoint,
                         double referenceRadius, int count, double radius) {
        if (count == 0 || path.length < 2) return null;
        if (count > 12) throw new IllegalArgumentException("A pointed stream supports at most twelve primitives");
        Vec3 direction = supportingDirection(tip, path, radius);
        if (direction == null) return null;
        double reach = 0;
        for (Vec3 point : path) reach = Math.max(reach, point.subtract(tip).dot(direction));
        double length = reach * .60;
        if (!(length > radius * 1e-9)) return null;
        double crossing = reach * .25;
        double slope = .05;
        if (referencePoint != null) {
            Vec3 relative = referencePoint.subtract(tip);
            double axial = relative.dot(direction);
            if (axial > radius * 1e-10)
                slope = Math.max(slope, (relative.subtract(direction.scale(axial)).length() + referenceRadius) / axial);
        } else {
            double referenceLength = reach * .42;
            slope = Math.max(slope, requiredSlope(tip, direction, referenceLength * .8, referenceLength,
                    primitives, count, 0, 0));
        }
        // The rear cone follows the physical source profile. Only its part
        // before the flare must contain the connecting centerline.
        slope = Math.max(slope, pathSlope(tip, direction, path, radius, 0, crossing, 0));
        slope *= SOURCE_OPENING;
        double baseRadius = slope * crossing;
        double flare = Math.max(slope, pathSlope(tip, direction, path, radius, crossing, length, baseRadius));
        // The flare encloses complete packed slices before the forward plane
        // opens. Crediting the existing rear radius keeps broad downstream
        // material from forcing the point itself into a wide circular cap.
        flare = Math.max(flare, requiredSlope(tip, direction, length * .8, length,
                primitives, count, crossing, baseRadius));
        float packedSlope = Math.nextUp((float) (slope * 1.0001));
        float packedFlare = Math.nextUp((float) (flare * 1.0001));
        float offset = Math.max(0, Math.nextDown((float) (crossing * (1 - (double) packedSlope / packedFlare))));
        return new Frame(floats(tip), floats(direction), packedSlope, (float) length, packedFlare, offset);
    }

    private static double pathSlope(Vec3 tip, Vec3 direction, Vec3[] path, double radius,
                                    double start, double end, double baseRadius) {
        double slope = 0;
        // Each positive cone is convex. Endpoints and the exact boundary-plane
        // crossings certify the complete straight segments on either side.
        for (int i = 0; i < path.length; i++) {
            Vec3 p = path[i].subtract(tip);
            double z = p.dot(direction);
            if (z > start + radius * 1e-10 && z <= end)
                slope = Math.max(slope, (p.subtract(direction.scale(z)).length() - baseRadius) / (z - start));
            if (i == 0) continue;
            Vec3 previous = path[i - 1].subtract(tip);
            double previousZ = previous.dot(direction);
            if ((previousZ < end && z > end) || (z < end && previousZ > end)) {
                Vec3 crossing = previous.add(p.subtract(previous).scale((end - previousZ) / (z - previousZ)));
                slope = Math.max(slope, (crossing.subtract(direction.scale(end)).length() - baseRadius) / (end - start));
            }
        }
        return slope;
    }

    private static double requiredSlope(Vec3 tip, Vec3 direction, double join, double length,
                                        float[] primitives, int count, double crossing, double baseRadius) {
        // Before the forward halfspace opens, the cone must already enclose
        // every packed ellipsoid crossing that join. A whole-primitive radial
        // bound over the final fifth of the cone avoids a shoulder at the join.
        double carrierSlope = 0;
        for (int i = 0; i < count; i++) {
            int at = i * 16;
            Vec3 r0 = vector(primitives, at + 4), r1 = vector(primitives, at + 8), r2 = vector(primitives, at + 12);
            double determinant = r0.dot(r1.cross(r2));
            Vec3 a = r1.cross(r2).scale(1 / determinant);
            Vec3 b = r2.cross(r0).scale(1 / determinant);
            Vec3 c = r0.cross(r1).scale(1 / determinant);
            Vec3 center = vector(primitives, at).subtract(tip);
            double z = center.dot(direction);
            double axial = Math.sqrt(square(a.dot(direction)) + square(b.dot(direction)) + square(c.dot(direction)));
            Vec3 radialA = a.subtract(direction.scale(a.dot(direction)));
            Vec3 radialB = b.subtract(direction.scale(b.dot(direction)));
            Vec3 radialC = c.subtract(direction.scale(c.dot(direction)));
            double radial = center.subtract(direction.scale(z)).length()
                    + Math.sqrt(radialA.lengthSqr() + radialB.lengthSqr() + radialC.lengthSqr());
            // Retain every contribution continuously as an ellipsoid crosses
            // the join. A binary slab-overlap test would make the cone slope
            // jump when a broad section first touches that slab.
            // Accumulate the broad fallback separately from centerline support.
            carrierSlope = Math.max(carrierSlope, Math.max(0, radial - baseRadius)
                    / (Math.max(join, z - axial) - crossing));
        }
        double sliceSlope = DarkBallAnimeTailSliceBounds.requiredSlope(tip, direction, join, length,
                primitives, count, crossing, baseRadius);
        return Math.min(carrierSlope, sliceSlope);
    }

    static double sample(Frame frame, Vec3 point) {
        Vec3 direction = unit(frame.direction);
        Vec3 offset = point.subtract(frame.tip);
        double z = offset.dot(direction);
        double radial = offset.subtract(direction.scale(z)).length();
        double cone = (radial - frame.slope * z) / Math.sqrt(1 + frame.slope * frame.slope);
        double flare = (radial - frame.flareSlope * (z - frame.flareOffset))
                / Math.sqrt(1 + frame.flareSlope * frame.flareSlope);
        return Math.max(-z, Math.min(Math.min(cone, flare), frame.length - z));
    }

    private static boolean forward(Vec3[] path, Vec3 tip, Vec3 direction, double radius) {
        for (Vec3 point : path) if (point.subtract(tip).dot(direction) < -radius * 1e-9) return false;
        return true;
    }

    private static Vec3 supportingDirection(Vec3 tip, Vec3[] path, double radius) {
        Vec3[] directions = new Vec3[path.length];
        Vec3 nearest = Vec3.ZERO;
        int count = 0;
        for (Vec3 point : path) {
            Vec3 offset = point.subtract(tip);
            if (offset.lengthSqr() < radius * radius * 1e-18) continue;
            directions[count++] = unit(offset);
            nearest = nearest.add(directions[count - 1]);
        }
        if (count == 0) return null;
        nearest = nearest.scale(1.0 / count);
        // The nearest point in the convex hull of the unit path directions
        // supplies a separating plane, even when neither endpoint direction
        // does. Each bounded Gilbert step decreases its squared norm.
        for (int pass = 0; pass < 64; pass++) {
            Vec3 support = directions[0];
            for (int i = 1; i < count; i++)
                if (directions[i].dot(nearest) < support.dot(nearest)) support = directions[i];
            Vec3 difference = support.subtract(nearest);
            double denominator = difference.lengthSqr();
            if (denominator < 1e-18) break;
            double step = Math.max(0, Math.min(1, -nearest.dot(difference) / denominator));
            if (step < 1e-10) break;
            nearest = nearest.add(difference.scale(step));
        }
        if (nearest.lengthSqr() < 1e-12) return null;
        Vec3 direction = unit(nearest);
        // A path genuinely surrounding the rear point has no supporting plane.
        // Preserve that geometry instead of cutting a real branch away.
        if (!forward(path, tip, direction, radius)) return null;
        // Preserve the physical rear tangent wherever the rest of the curve
        // permits it. Limit this blend continuously before a folded section
        // crosses the rear plane; do not switch abruptly between two axes.
        Vec3 tangent = directions[0];
        double blend = 1;
        for (int i = 0; i < count; i++) {
            double supported = directions[i].dot(direction);
            double desired = directions[i].dot(tangent);
            if (desired < supported * .8)
                blend = Math.min(blend, supported * .2 / (supported - desired));
        }
        return unit(direction.scale(1 - blend).add(tangent.scale(blend)));
    }
    private static double square(double x) { return x * x; }
    private static Vec3 floats(Vec3 v) { return new Vec3((float) v.x, (float) v.y, (float) v.z); }
    private static Vec3 vector(float[] p, int at) { return new Vec3(p[at], p[at + 1], p[at + 2]); }
    private static Vec3 unit(Vec3 v) { return v.scale(1 / v.length()); }
}
