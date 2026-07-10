package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.world.phys.Vec3;

/** A bright orange/white highlight spark rendered via sparkGlowAdditive. */
public final class HighlightSpark {
    public Vec3 pos;
    public final Vec3 vel;
    public float age;
    public final float life;
    public final int r, g, b;

    public HighlightSpark(Vec3 pos, Vec3 vel, float life, int r, int g, int b) {
        this.pos = pos;
        this.vel = vel;
        this.life = life;
        this.r = r; this.g = g; this.b = b;
    }

    public boolean tick(float dt) {
        age += dt;
        pos = pos.add(vel.scale(dt));
        return age >= life;
    }

    public float alpha() {
        float t = age / life;
        // Quick bright flash then rapid fade
        return t < 0.1f ? t / 0.1f : 1f - ((t - 0.1f) / 0.9f);
    }
}
