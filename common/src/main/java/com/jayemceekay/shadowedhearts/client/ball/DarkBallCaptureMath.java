package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;


final class DarkBallCaptureMath {
    private DarkBallCaptureMath() {
    }

    static float remapClamped(float x, float a, float b) {
        return Mth.clamp((x - a) / (b - a), 0f, 1f);
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    static Vec3 stableSide(Vec3 captureDir) {
        Vec3 side = new Vec3(0, 1, 0).cross(captureDir);
        if (side.lengthSqr() < 1e-6) {
            side = new Vec3(1, 0, 0).cross(captureDir);
        }
        return side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
    }

}
