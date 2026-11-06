package com.jayemceekay.shadowedhearts.client.aura.shadow.style.colosseum;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Whole posed-model volume shared by Colosseum smoke and its luminous accents. */
public record ColosseumAuraVolume(AABB modelBounds, Vec3 center,
                                  double radiusX, double radiusY, double radiusZ,
                                  float puffScale) {
    public static ColosseumAuraVolume fromBounds(AABB bounds) {
        if (bounds == null || !Double.isFinite(bounds.minX + bounds.minY + bounds.minZ
                + bounds.maxX + bounds.maxY + bounds.maxZ)) {
            bounds = new AABB(-.5, 0, -.5, .5, 1, .5);
        }
        double width = Math.max(.15, bounds.getXsize());
        double height = Math.max(.15, bounds.getYsize());
        double depth = Math.max(.15, bounds.getZsize());
        float scale = (float) Math.max(.24, Math.min(3.0, Math.cbrt(width * height * depth) * .58));
        Vec3 center = bounds.getCenter().add(0, scale * .20, 0);
        return new ColosseumAuraVolume(bounds, center,
                width * .5 + scale * .80, height * .5 + scale * .30,
                depth * .5 + scale * .80, scale);
    }

    /** Independent uniform volume samples, including the gaps between anatomical parts. */
    public Vec3 sample(double directionYRoll, double azimuthRoll, double radialRoll) {
        double y = unit(directionYRoll) * 2 - 1;
        double ring = Math.sqrt(Math.max(0, 1 - y * y));
        double angle = unit(azimuthRoll) * Math.PI * 2;
        double radius = Math.cbrt(unit(radialRoll));
        return center.add(Math.cos(angle) * ring * radius * radiusX,
                y * radius * radiusY, Math.sin(angle) * ring * radius * radiusZ);
    }

    /**
     * Feed rising smoke across the filled footprint near the posed model's base.
     * Height is independent of radius: a low birth must not collapse onto the
     * ellipsoid's bottom pole. Floating models retain their own underside.
     */
    public Vec3 sampleSmokeBase(double heightRoll, double azimuthRoll, double radialRoll) {
        double angle = unit(azimuthRoll) * Math.PI * 2;
        double radius = Math.sqrt(unit(radialRoll));
        double height = Math.max(.15, modelBounds.getYsize());
        // Normal bodies feed from their lower third. Stretch that band only
        // when a very tall/slender pose exceeds the plume's typical rise.
        double bandHeight = Math.max(height * .30, height - puffScale * 3.0);
        double y = modelBounds.minY + puffScale * .04
                + bandHeight * Math.pow(unit(heightRoll), 1.5);
        // Tighten only smoke births, preserving the support of elongated bodies
        // and the wider independent volume used by luminous motes.
        double smokeRadiusX = Math.max(modelBounds.getXsize() * .5, radiusX * .88);
        double smokeRadiusZ = Math.max(modelBounds.getZsize() * .5, radiusZ * .88);
        return new Vec3(center.x + Math.cos(angle) * radius * smokeRadiusX, y,
                center.z + Math.sin(angle) * radius * smokeRadiusZ);
    }

    public AABB bounds() {
        return new AABB(center.x - radiusX, center.y - radiusY, center.z - radiusZ,
                center.x + radiusX, center.y + radiusY, center.z + radiusZ);
    }

    public double normalizedDistanceSquared(Vec3 point) {
        double x = (point.x - center.x) / radiusX;
        double y = (point.y - center.y) / radiusY;
        double z = (point.z - center.z) / radiusZ;
        return x * x + y * y + z * z;
    }

    public int smokeTarget(float quality) {
        double area = 4 * Math.PI * Math.pow((Math.pow(radiusX * radiusY, 1.6)
                + Math.pow(radiusX * radiusZ, 1.6) + Math.pow(radiusY * radiusZ, 1.6)) / 3, 1 / 1.6);
        int target = (int) Math.ceil(60 + area / (puffScale * puffScale) * 1.3);
        float q = Float.isFinite(quality) ? Math.max(0, Math.min(1, quality)) : 0;
        return Math.max(80, Math.min(240, Math.round(target * (.70f + .30f * q))));
    }

    /** Smoothly admit extra coverage for large or elongated posed models. */
    public float largeModelWeight() {
        double width = modelBounds.getXsize(), height = modelBounds.getYsize(), depth = modelBounds.getZsize();
        double metric = Math.max(Math.cbrt(width * height * depth),
                Math.max(width, Math.max(height, depth)) * .35);
        double t = Math.max(0, Math.min(1, (metric - 2.0) / 3.0));
        return (float) (t * t * (3 - 2 * t));
    }

    /** Extra infill rate; the existing primary smoke target remains unchanged. */
    public double infillRateMultiplier() {
        return .40 * largeModelWeight();
    }

    public double plumeFadeStart() {
        return modelBounds.maxY + Math.max(modelBounds.getYsize() * .10, puffScale * .20);
    }

    public double plumeFadeEnd() {
        return modelBounds.maxY + Math.max(modelBounds.getYsize() * .55, puffScale);
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : .5;
    }
}
