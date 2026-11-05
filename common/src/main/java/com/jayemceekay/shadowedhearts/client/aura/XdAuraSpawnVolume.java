package com.jayemceekay.shadowedhearts.client.aura;

import net.minecraft.world.phys.Vec3;

/** Filled regional clouds preserve an elongated pose without tracing its skin. */
final class XdAuraSpawnVolume {
    private XdAuraSpawnVolume() {}

    static Vec3 sample(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ,
                       float x, float y, float z, float scale,
                       double heightRoll, double angleRoll, double radialRoll, boolean lowerBias) {
        double height = unit(heightRoll) * 2 - 1;
        double ring = Math.sqrt(Math.max(0, 1 - height * height));
        double angle = unit(angleRoll) * Math.PI * 2;
        double radius = Math.cbrt(unit(radialRoll));
        double margin = Math.max(.16, dimension(scale) * 1.20);
        Vec3 offset = axisX.scale(Math.cos(angle) * ring * radius * (dimension(x) * .5 + margin))
                .add(axisY.scale(height * radius * (dimension(y) * .5 + margin)))
                .add(axisZ.scale(Math.sin(angle) * ring * radius * (dimension(z) * .5 + margin)));
        // Hovering species may keep a quieter upper envelope. Bias the actual
        // world-up coordinate, even when an authored region is rotated.
        if (lowerBias) offset = new Vec3(offset.x, offset.y * .70 - margin * .30, offset.z);
        return center.add(offset);
    }

    /** Test captured solid boxes in their posed axes, before rendering rejects a hidden birth. */
    static boolean contains(Vec3 point, Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ,
                            float x, float y, float z) {
        Vec3 offset = point.subtract(center);
        return Math.abs(offset.dot(axisX)) < dimension(x) * .5
                && Math.abs(offset.dot(axisY)) < dimension(y) * .5
                && Math.abs(offset.dot(axisZ)) < dimension(z) * .5;
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : .5;
    }

    private static double dimension(float value) {
        return Float.isFinite(value) ? Math.max(0, value) : 0;
    }
}
