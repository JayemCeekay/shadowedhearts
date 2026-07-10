package com.jayemceekay.shadowedhearts.client.ball.vfx;

import net.minecraft.util.Mth;

/** A purple glowing ring that follows the absorption particle mass center. */
public final class ConvergenceRing {
    public float life;
    public final float maxLife;
    public final float maxRadius;

    public ConvergenceRing(float life, float radius) {
        this.life   = life;
        this.maxLife = life;
        this.maxRadius = radius;
    }

    public boolean tick(float dt) {
        life -= dt;
        return life <= 0f;
    }

    public float radius() {
        float p = 1f - (life / maxLife); // 0 at spawn → 1 at death

        final float growEnd = 0.15f;
        final float shrinkDuration = 0.15f;

        // Grow from 0 to full size over the first 15% of life.
        if (p < growEnd) {
            float growT = Mth.clamp(p / growEnd, 0f, 1f);

            // Quadratic ease-out.
            float easedGrow = 1f - (1f - growT) * (1f - growT);

            return maxRadius * easedGrow;
        }

        // Quickly shrink after reaching full size.
        float shrinkT = Mth.clamp(
                (p - growEnd) / shrinkDuration,
                0f,
                1f
        );

        // Quadratic ease-in: initially subtle, then rapidly collapses.
        float easedShrink = shrinkT * shrinkT;

        return maxRadius * (1f - easedShrink);
    }

    public float alpha() {
        float p = 1f - (life / maxLife);
        // Quick rise, hold, then fade out in the last 20%
        if (p < 0.10f) return p / 0.10f;
        if (p > 0.80f) return (1f - p) / 0.20f;
        return 1f;
    }
}
