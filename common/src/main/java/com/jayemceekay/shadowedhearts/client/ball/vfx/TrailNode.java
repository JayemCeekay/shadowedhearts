package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.world.phys.Vec3;

/** A single trail node left behind by a moving projectile head. */
public final class TrailNode {
    public Vec3 position;
    public Vec3 tangent;
    public float age;
    public final float lifetime;

    public TrailNode(Vec3 position, Vec3 tangent, float lifetime) {
        this.position = position;
        this.tangent  = tangent;
        this.age      = 0f;
        this.lifetime = lifetime;
    }

    public boolean tick(float dt) {
        age += dt;
        return age >= lifetime;
    }

    public float life() { return 1f - age / lifetime; }
}
