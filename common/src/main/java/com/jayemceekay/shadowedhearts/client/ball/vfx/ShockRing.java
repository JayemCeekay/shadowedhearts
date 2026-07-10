package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A single expanding shock ring (camera-facing annulus). */
public final class ShockRing {
    public final Vec3 origin;
    public float life;
    public final float maxLife;
    public final float maxRadius;
    public final int r, g, b;

    public ShockRing(Vec3 origin, float life, float maxRadius, int r, int g, int b) {
        this.origin    = origin;
        this.life      = life;
        this.maxLife   = life;
        this.maxRadius = maxRadius;
        this.r = r; this.g = g; this.b = b;
    }

    public boolean tick(float dt) {
        life -= dt;
        return life <= 0f;
    }

    /** 0→1 progress as the ring expands. */
    public float progress() { return 1f - (life / maxLife); }

    public float alpha() {
        float p = progress();
        // sharp rise then fade
        return Mth.clamp((1f - p) * 2.5f, 0f, 1f);
    }

    public float radius() { return maxRadius * progress(); }
}
