package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Expanding ground pulse ring. */
public final class GroundPulse {
    public final Vec3 center;
    public final float groundY;
    public float age;
    public final float life;
    public final float maxRadius;
    public final int r, g, b;

    public GroundPulse(Vec3 center, float groundY, float life, float maxRadius, int r, int g, int b) {
        this.center = center;
        this.groundY = groundY;
        this.life = life;
        this.maxRadius = maxRadius;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public boolean tick(float dt) {
        age += dt;
        return age >= life;
    }

    public float progress() { return Mth.clamp(age / life, 0f, 1f); }

    public float alpha() {
        float t = progress();
        return 1f - t * t;
    }

    public float radius() { return maxRadius * progress(); }
}
