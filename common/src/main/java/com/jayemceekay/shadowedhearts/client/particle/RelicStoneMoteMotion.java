package com.jayemceekay.shadowedhearts.client.particle;

import net.minecraft.world.phys.Vec3;

/** A complete, bounded curl during each relic mote's life, independent of the world clock. */
final class RelicStoneMoteMotion {
    private RelicStoneMoteMotion() {}

    static Vec3 driftDelta(int age, int lifetime, float seed, float radius) {
        return driftOffset(age, lifetime, seed, radius)
                .subtract(driftOffset(age - 1, lifetime, seed, radius));
    }

    static Vec3 driftOffset(int age, int lifetime, float seed, float radius) {
        double progress = Math.clamp(age / (double) Math.max(1, lifetime), 0.0, 1.0);
        double phase = seed * Math.PI * 2.0;
        double angle = phase + progress * Math.PI * 2.0;
        return new Vec3(radius * (Math.cos(angle) - Math.cos(phase)), 0.0,
                radius * (Math.sin(angle) - Math.sin(phase)));
    }
}
