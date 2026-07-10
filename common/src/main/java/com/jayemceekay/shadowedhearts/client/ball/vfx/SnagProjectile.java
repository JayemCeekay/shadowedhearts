package com.jayemceekay.shadowedhearts.client.ball.vfx;

import com.jayemceekay.shadowedhearts.client.ball.SnagCaptureVfx;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

/** One of five energy projectiles that arc from ball to Pokémon. */
public final class SnagProjectile {
    public final Vec3 p0, p1, p2, p3;
    public final float launchDelay;
    public final float speedMultiplier;
    public final float baseTailLifetime;
    public final ArrayDeque<TrailNode> trail = new ArrayDeque<>();
    public Vec3 lastTrailPos;
    public boolean impacted;

    public SnagProjectile(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                          float launchDelay, float speedMultiplier, float baseTailLifetime) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.launchDelay      = launchDelay;
        this.speedMultiplier  = speedMultiplier;
        this.baseTailLifetime = baseTailLifetime;
        this.lastTrailPos     = p0;
        this.impacted         = false;
    }

    /** Computes headT (0–1 along curve) from VFX age. */
    public float getHeadT(float age) {
        float localAge = (age - SnagCaptureVfx.BEAM_START - launchDelay) * speedMultiplier;
        float beamDuration = SnagCaptureVfx.BEAM_END - SnagCaptureVfx.BEAM_START;
        float progress = Mth.clamp(localAge / beamDuration, 0f, 1f);
        return SnagCaptureVfx.projectileHeadT(progress);
    }
}
