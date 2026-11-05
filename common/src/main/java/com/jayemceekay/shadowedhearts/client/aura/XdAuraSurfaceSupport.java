package com.jayemceekay.shadowedhearts.client.aura;

import net.minecraft.world.phys.Vec3;

/** Stable, area-weighted supports on a thin posed region's six box faces. */
final class XdAuraSurfaceSupport {
    private XdAuraSurfaceSupport() {}

    record Sample(Vec3 offset, Vec3 normal) {}

    /** Dimensions and returned vectors use the region's unposed local axes. */
    static Sample sample(float x, float y, float z, int index, int count) {
        double[] size = {dimension(x), dimension(y), dimension(z)};
        double[] faceArea = {size[1] * size[2], size[0] * size[2], size[0] * size[1]};
        double area = 2 * (faceArea[0] + faceArea[1] + faceArea[2]);
        if (area <= 1.0e-12) return new Sample(Vec3.ZERO, new Vec3(0, 1, 0));
        int samples = Math.max(1, count);
        int sample = Math.floorMod(index, samples);
        // Every stratum is visited once, in an interleaved order rather than
        // emitting an entire broad face before reaching the opposite side.
        int stride = samples / 2 + 1;
        while (gcd(stride, samples) != 1) stride++;
        int stratum = (int) ((long) sample * stride % samples);
        double choice = (stratum + 0.5) / samples * area;
        int face = 0;
        for (; face < 5 && choice >= faceArea[face / 2]; face++) {
            choice -= faceArea[face / 2];
        }
        int axis = face / 2;
        double sign = (face & 1) == 0 ? -1 : 1;
        double u = 2 * radicalInverse(sample + 1, 2) - 1;
        double v = 2 * radicalInverse(sample + 1, 3) - 1;
        double[] offset = new double[3];
        double[] normal = new double[3];
        offset[axis] = sign * size[axis] * 0.5;
        offset[(axis + 1) % 3] = u * size[(axis + 1) % 3] * 0.5;
        offset[(axis + 2) % 3] = v * size[(axis + 2) % 3] * 0.5;
        normal[axis] = sign;
        return new Sample(new Vec3(offset[0], offset[1], offset[2]),
                new Vec3(normal[0], normal[1], normal[2]));
    }

    private static double dimension(float value) {
        return Float.isFinite(value) ? Math.max(0, value) : 0;
    }

    private static double radicalInverse(int value, int base) {
        double result = 0, scale = 1.0 / base;
        for (; value > 0; value /= base, scale /= base) result += (value % base) * scale;
        return result;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }
}
