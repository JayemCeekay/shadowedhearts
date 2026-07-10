package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A single suction mote spiralling from Pokémon bounding box into the ball. */
public final class SuctionMote {
    private static final float TAU = (float) (Math.PI * 2.0);

    public final Vec3 startPos;
    public final float phase;          // per-mote angular phase offset
    public final float startRadius;    // initial spiral radius
    public final float delay;          // 0–1 delay for per-particle staggered return
    public float life;
    public final float maxLife;
    public final int r, g, b;

    public SuctionMote(Vec3 startPos, float phase, float startRadius, float delay,
                       float life, int r, int g, int b) {
        this.startPos    = startPos;
        this.phase       = phase;
        this.startRadius = startRadius;
        this.delay       = delay;
        this.life        = life;
        this.maxLife     = life;
        this.r = r; this.g = g; this.b = b;
    }

    public boolean tick(float dt) {
        life -= dt;
        return life <= 0f;
    }

    public float t()     { return 1f - (life / maxLife); }
    public float alpha() { return Mth.clamp((1f - t()) * 2f, 0f, 1f); }

    /**
     * Computes world-space position for this mote given the current ball position
     * and a set of basis vectors for the spiral plane.
     * The per-particle {@code delay} causes outer fragments to lag behind the
     * dense center, creating natural stretching.
     */
    public Vec3 computePos(Vec3 ballPos, Vec3 right, Vec3 up) {
        float rawT  = this.t();
        // Apply per-particle delay: dense-center motes (delay~0) lead,
        // outer fragments (delay~0.6) lag behind
        float localT = Mth.clamp((rawT - delay) / (1f - delay), 0f, 1f);
        float eased  = localT * localT * localT;  // easeInCubic for "sucked in" feel

        Vec3 basePos = startPos.lerp(ballPos, eased);

        // Shrinking spiral
        float angle  = rawT * TAU * 3f + phase;
        float radius = startRadius * (1f - localT);

        return basePos
                .add(right.scale(Math.cos(angle) * radius))
                .add(up.scale(Math.sin(angle) * radius));
    }
}
