package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.world.phys.Vec3;

/** A small spark emitted from the ball seam on each shake. */
public final class ShakeSpark {
    public Vec3 pos;
    public final Vec3 vel;
    public float age;
    public final float life;
    public final int r, g, b;

    public ShakeSpark(Vec3 pos, Vec3 vel, float life, int r, int g, int b) {
        this.pos = pos;
        this.vel = vel;
        this.life = life;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public boolean tick(float dt) {
        age += dt;
        pos = pos.add(vel.scale(dt));
        return age >= life;
    }

    public float alpha() {
        float t = age / life;
        // Quick bright flash then rapid fade — needle sparks should pop then vanish
        return t < 0.1f ? t / 0.1f : 1f - ((t - 0.1f) / 0.9f);
    }
}
