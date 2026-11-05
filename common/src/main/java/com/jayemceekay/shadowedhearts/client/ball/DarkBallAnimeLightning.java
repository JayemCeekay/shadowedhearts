package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Ball-local lightning: angular, branched paths held for one short electrical strike. */
final class DarkBallAnimeLightning {
    static final int ARC_COUNT = 3;
    private static final int SEGMENTS = 16;
    private static final double STRIKE_SECONDS = .085;

    private DarkBallAnimeLightning() {
    }

    static int strikeIndex(float age, int arc) {
        return (int) Math.floor(strikeTime(age, arc));
    }

    static float strikeStrength(float age, int arc) {
        if (!Float.isFinite(age) || age < 0) return 0;
        double time = strikeTime(age, arc);
        double phase = time - Math.floor(time);
        // Hold a bright discharge, then extinguish it before the next path appears.
        return (float) (smooth(0, .07, phase) * (1 - smooth(.40, .84, phase)));
    }

    static Bolt create(int seed, int arc, int strike, double radius) {
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("Lightning radius must be finite and positive");
        }
        int boltSeed = mix(seed ^ arc * 0x9E3779B9 ^ strike * 0x85EBCA6B);
        Vec3 start = unitVector(boltSeed);
        Vec3 reference = Math.abs(start.y) < .8 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = start.cross(reference).normalize();
        Vec3 other = start.cross(side).normalize();
        double roll = unit(boltSeed + 1) * Math.PI * 2;
        Vec3 tangent = side.scale(Math.cos(roll)).add(other.scale(Math.sin(roll)));
        Vec3 normal = start.cross(tangent).normalize();
        double sweep = 1.25 + unit(boltSeed + 2) * .8;
        double[] offsets = new double[SEGMENTS + 1];
        displace(offsets, 0, SEGMENTS, .34, boltSeed);
        List<Vec3> trunk = new ArrayList<>(SEGMENTS + 1);
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            // Irregular segment lengths and multi-scale transverse offsets produce forks and
            // sharp elbows without moving the entire stroke continuously around an orbit.
            if (i > 0 && i < SEGMENTS) t += signed(boltSeed + i * 71) * .22 / SEGMENTS;
            double angle = t * sweep;
            Vec3 direction = start.scale(Math.cos(angle)).add(tangent.scale(Math.sin(angle)))
                    .add(normal.scale(offsets[i])).normalize();
            double shell = 1.02 + unit(boltSeed + i * 193) * .11;
            trunk.add(direction.scale(radius * shell));
        }

        List<List<Vec3>> branches = new ArrayList<>(2);
        for (int fork = 0; fork < 2; fork++) {
            int branchSeed = mix(boltSeed + 0x632BE5AB * (fork + 1));
            int rootIndex = (fork == 0 ? 4 : 9) + (int) (unit(branchSeed) * 3);
            Vec3 root = trunk.get(rootIndex);
            Vec3 radial = root.normalize();
            Vec3 forward = trunk.get(rootIndex + 1).subtract(trunk.get(rootIndex - 1));
            forward = forward.subtract(radial.scale(forward.dot(radial))).normalize();
            double sign = fork == 0 ? 1 : -1;
            Vec3 outward = normal.scale(sign * (.30 + unit(branchSeed + 1) * .18))
                    .add(forward.scale(.20 + unit(branchSeed + 2) * .12));
            double tipRadius = radius * (1.42 + unit(branchSeed + 3) * .14);
            List<Vec3> points = new ArrayList<>(5);
            points.add(root);
            for (int i = 1; i <= 4; i++) {
                double t = i * .25;
                double kink = i == 4 ? 0 : signed(branchSeed + i * 313) * .10;
                Vec3 direction = radial.add(outward.scale(t)).add(forward.scale(kink)).normalize();
                points.add(direction.scale(root.length() + (tipRadius - root.length()) * t));
            }
            branches.add(List.copyOf(points));
        }
        return new Bolt(List.copyOf(trunk), List.copyOf(branches));
    }

    private static void displace(double[] offsets, int first, int last, double amplitude, int seed) {
        if (last - first <= 1) return;
        int middle = (first + last) / 2;
        double noise = signed(seed + middle * 149 + (last - first) * 467);
        noise = Math.copySign(.35 + .65 * Math.abs(noise), noise);
        offsets[middle] = (offsets[first] + offsets[last]) * .5 + noise * amplitude;
        displace(offsets, first, middle, amplitude * .58, seed);
        displace(offsets, middle, last, amplitude * .58, seed);
    }

    private static Vec3 unitVector(int seed) {
        double y = signed(seed + 11);
        double angle = unit(seed + 19) * Math.PI * 2;
        double horizontal = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
    }

    private static double strikeTime(float age, int arc) {
        return (Float.isFinite(age) ? Math.max(0, age) : 0) / STRIKE_SECONDS + arc * .37;
    }

    private static double smooth(double from, double to, double value) {
        double t = Math.max(0, Math.min(1, (value - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }

    private static double unit(int seed) {
        return (mix(seed) >>> 8) / 16777216.0;
    }

    private static double signed(int seed) {
        return unit(seed) * 2 - 1;
    }

    private static int mix(int value) {
        value = (value ^ (value >>> 16)) * 0x7FEB352D;
        value = (value ^ (value >>> 15)) * 0x846CA68B;
        return value ^ (value >>> 16);
    }

    record Bolt(List<Vec3> trunk, List<List<Vec3>> branches) {
    }
}
