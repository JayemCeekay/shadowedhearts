package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;

final class DarkBallCaptureMath {
    static final float TAU = (float) (Math.PI * 2.0);
    /**
     * The siphon terminates in a fixed-size Dark Ball, so its final cross-section
     * must not continue scaling with the captured Pokemon.
     */
    static final float SIPHON_SINK_RADIUS = 0.085f;
    static final float SIPHON_SINK_FEATHER = 0.025f;
    private static final float SIPHON_CURVE_BEND = 0.28f;

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

    static Vec3 randomInsideUnitSphere(Random rng) {
        for (int i = 0; i < 24; i++) {
            Vec3 p = new Vec3(
                    rng.nextDouble() * 2.0 - 1.0,
                    rng.nextDouble() * 2.0 - 1.0,
                    rng.nextDouble() * 2.0 - 1.0
            );
            if (p.lengthSqr() <= 1.0) return p;
        }
        return randomUnitVector(rng).scale(Math.cbrt(rng.nextDouble()));
    }

    static Vec3 randomUnitVector(Random rng) {
        double theta = rng.nextDouble() * TAU;
        double y = -1.0 + rng.nextDouble() * 2.0;
        double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new Vec3(Math.cos(theta) * ring, y, Math.sin(theta) * ring).normalize();
    }

    static Vector3f physicalIntakeLocal(float captureLength, float bodyRadius) {
        return new Vector3f(Math.max(captureLength - bodyRadius * 0.025f, bodyRadius * 0.25f), 0f, 0f);
    }

    static Vector3f siphonCurvePoint(Vector3f root, Vector3f intake, float bodyRadius, float t) {
        float u = Mth.clamp(t, 0f, 1f);
        float inv = 1f - u;
        return new Vector3f(root).mul(inv * inv * inv)
                .add(siphonCurveControlALocal(root, intake, bodyRadius).mul(3f * inv * inv * u))
                .add(siphonCurveControlBLocal(root, intake, bodyRadius).mul(3f * inv * u * u))
                .add(new Vector3f(intake).mul(u * u * u));
    }

    static Vector3f siphonCurveTangent(Vector3f root, Vector3f intake, float bodyRadius, float t) {
        float u = Mth.clamp(t, 0f, 1f);
        float inv = 1f - u;
        Vector3f controlA = siphonCurveControlALocal(root, intake, bodyRadius);
        Vector3f controlB = siphonCurveControlBLocal(root, intake, bodyRadius);
        Vector3f tangent = new Vector3f(controlA).sub(root).mul(3f * inv * inv)
                .add(new Vector3f(controlB).sub(controlA).mul(6f * inv * u))
                .add(new Vector3f(intake).sub(controlB).mul(3f * u * u));
        if (tangent.lengthSquared() < 0.0000001f) {
            tangent.set(intake).sub(root);
        }
        return tangent;
    }

    static Vector3f siphonCurveControlLocal(Vector3f root, Vector3f intake, float bodyRadius) {
        return siphonCurveControlALocal(root, intake, bodyRadius);
    }

    static Vector3f siphonCurveControlALocal(Vector3f root, Vector3f intake, float bodyRadius) {
        Vector3f control = new Vector3f(root).lerp(intake, 0.34f);
        control.y += bodyRadius * SIPHON_CURVE_BEND;
        control.z *= 0.42f;
        return control;
    }

    static Vector3f siphonCurveControlBLocal(Vector3f root, Vector3f intake, float bodyRadius) {
        Vector3f control = new Vector3f(root).lerp(intake, 0.82f);
        control.y += bodyRadius * SIPHON_CURVE_BEND * 0.36f;
        control.z *= 0.16f;
        return control;
    }

    static float siphonEndRadius() {
        return SIPHON_SINK_RADIUS;
    }

    static float siphonSinkGate(float terminalAxialDistance) {
        return 1.0f - smoothstep(-SIPHON_SINK_FEATHER, 0.0f,
                terminalAxialDistance);
    }

}
