package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A single velocity-aligned needle spark. */
public final class NeedleSpark {
    public Vec3 pos;
    public final Vec3 vel;
    public float life;           // current remaining life (seconds)
    public final float maxLife;
    public final float length;   // world-units along velocity axis
    public final float width;    // world-units perpendicular
    public final int r, g, b;

    public NeedleSpark(Vec3 origin, Vec3 vel, float life, float length, float width, int r, int g, int b) {
        this.pos     = origin;
        this.vel     = vel;
        this.life    = life;
        this.maxLife = life;
        this.length  = length;
        this.width   = width;
        this.r = r; this.g = g; this.b = b;
    }

    public boolean tick(float dt) {
        pos  = pos.add(vel.scale(dt));
        life -= dt;
        return life <= 0f;
    }

    public float alpha() {
        float t = life / maxLife;
        // fade in fast, linger, then fade out
        return Mth.clamp(t * 2.5f, 0f, 1f) * t;
    }
}
