package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Lightweight siphon-path state retained under its historical name so the
 * surfel cutover can stay narrowly scoped.
 *
 * <p>The former ping-pong density simulation and its fullscreen advection
 * shaders are gone. The active Dark Ball body is the fused surface-splat
 * field; this object now owns only the moving ball endpoint, frozen inlet,
 * and deterministic siphon bolt nodes shared by the splat and siphon-mesh
 * renderers.</p>
 */
final class DarkBallAdvectedDensityField {
    /*
     * Retired atlas dimensions remain as diagnostic-layout metadata. The FBO
     * preview marks those tiles unavailable instead of sampling textures.
     */
    static final int X_SIZE = DarkBallAnalyticalVolume.X_SIZE;
    static final int Y_SIZE = DarkBallAnalyticalVolume.Y_SIZE;
    static final int Z_SIZE = DarkBallAnalyticalVolume.Z_SIZE;
    static final int ATLAS_WIDTH = X_SIZE * Z_SIZE;
    static final int ATLAS_HEIGHT = Y_SIZE;
    static final int SIPHON_X_SIZE = 48;
    static final int SIPHON_Y_SIZE = 12;
    static final int SIPHON_Z_SIZE = 12;
    static final int SIPHON_ATLAS_WIDTH = SIPHON_X_SIZE * SIPHON_Z_SIZE;
    static final int SIPHON_ATLAS_HEIGHT = SIPHON_Y_SIZE;

    private final DarkBallVolumeBuildResult volume;
    private final DarkBallAnalyticalVolume shape;
    private final DarkBallVfxQuality quality;
    private final Vector3f ballLocal = new Vector3f();
    private final Vector3f siphonRootLocal = new Vector3f();
    private final Vector3f siphonRootTangent =
            new Vector3f(1.0f, 0.0f, 0.0f);
    private final Vector3f[] siphonBoltNodes =
            new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];

    private DarkBallAdvectedDensityField(
            DarkBallVolumeBuildResult volume,
            DarkBallAnalyticalVolume shape,
            DarkBallVfxQuality quality) {
        this.volume = volume;
        this.shape = shape;
        this.quality = quality == null
                ? DarkBallVfxQuality.MEDIUM
                : quality;
    }

    static DarkBallAdvectedDensityField build(
            DarkBallVolumeBuildResult volume,
            DarkBallAnalyticalVolume shape,
            Vector3f initialBallLocal,
            DarkBallVfxQuality quality) {
        if (volume == null || shape == null || !isFinite(initialBallLocal)) {
            return null;
        }
        DarkBallAdvectedDensityField result =
                new DarkBallAdvectedDensityField(volume, shape, quality);
        result.ballLocal.set(initialBallLocal);
        result.initializeFrozenSiphonRoot();
        return result;
    }

    Vector3f outletLocal() {
        return shape.outletLocal();
    }

    Vector3f siphonRootLocal() {
        return new Vector3f(siphonRootLocal);
    }

    Vector3f siphonControlALocal() {
        float handleLength = Math.max(
                volume.bodyRadius() * 0.32f,
                siphonRootLocal.distance(ballLocal) * 0.34f);
        return new Vector3f(siphonRootLocal)
                .fma(handleLength, siphonRootTangent);
    }

    Vector3f siphonControlBLocal() {
        return DarkBallCaptureMath.siphonCurveControlBLocal(
                siphonRootLocal,
                ballLocal,
                volume.bodyRadius());
    }

    void updateSiphonBoltPath(
            Vector3f controlA,
            Vector3f controlB,
            float captureTime) {
        DarkBallSiphonBoltPath.populate(
                siphonRootLocal,
                controlA,
                controlB,
                ballLocal,
                volume.bodyRadius(),
                captureTime,
                siphonBoltNodes);
    }

    Vector3f siphonBoltNode(int index) {
        if (index < 0
                || index >= siphonBoltNodes.length
                || siphonBoltNodes[index] == null) {
            throw new IllegalArgumentException(
                    "invalid uninitialized bolt node " + index);
        }
        return siphonBoltNodes[index];
    }

    boolean updateBallLocal(Vector3f value) {
        if (!isFinite(value)) {
            return false;
        }
        ballLocal.set(value);
        return true;
    }

    void destroy() {
        // No GPU targets remain after the surfel-only cutover.
    }

    String summary() {
        return "route=surface-splats+indexed-siphon"
                + ", boltNodes=" + DarkBallSiphonBoltPath.NODE_COUNT
                + ", quality=" + quality.name().toLowerCase(
                        java.util.Locale.ROOT)
                + ", rootSdf="
                + String.format(
                        java.util.Locale.ROOT,
                        "%.4f",
                        shape.signedDistanceLocal(siphonRootLocal));
    }

    static float releaseRemaining(
            float releaseOrder,
            float siphonProgress,
            float finalCollapse) {
        if (siphonProgress <= 0.001f) {
            return 1.0f;
        }
        float front = Mth.clamp(
                siphonProgress * 1.16f
                        - 0.045f
                        + finalCollapse * 0.16f,
                0.0f,
                1.30f);
        float width = 0.130f
                + (1.0f - Math.min(siphonProgress, 1.0f)) * 0.045f;
        return smoothstep(front - width, front + width, releaseOrder);
    }

    static float deformationAttachment(
            float releaseOrder,
            float siphonProgress,
            float finalCollapse) {
        float remaining = releaseRemaining(
                releaseOrder,
                siphonProgress,
                finalCollapse);
        return smoothstep(0.58f, 0.92f, remaining);
    }

    static float releasedFraction(
            float releaseOrder,
            float previousSiphon,
            float siphon,
            float previousCollapse,
            float collapse) {
        float previous = releaseRemaining(
                releaseOrder,
                previousSiphon,
                previousCollapse);
        float current = releaseRemaining(
                releaseOrder,
                siphon,
                collapse);
        return Math.max(0.0f, previous - current);
    }

    static int atlasTexelX(int x, int z) {
        return z * X_SIZE + x;
    }

    static int siphonAtlasTexelX(int x, int z) {
        return z * SIPHON_X_SIZE + x;
    }

    private void initializeFrozenSiphonRoot() {
        Vector3f outlet = shape.outletLocal();
        Vector3f towardInitialBall = new Vector3f(ballLocal).sub(outlet);
        if (towardInitialBall.lengthSquared() < 0.000001f) {
            towardInitialBall.set(1.0f, 0.0f, 0.0f);
        } else {
            towardInitialBall.normalize();
        }
        siphonRootLocal.set(shape.interiorSiphonRootLocal(
                ballLocal,
                volume.bodyRadius() * 0.22f));
        Vector3f initialControl =
                DarkBallCaptureMath.siphonCurveControlALocal(
                        siphonRootLocal,
                        ballLocal,
                        volume.bodyRadius());
        siphonRootTangent.set(initialControl).sub(siphonRootLocal);
        if (siphonRootTangent.lengthSquared() < 0.000001f) {
            siphonRootTangent.set(towardInitialBall);
        } else {
            siphonRootTangent.normalize();
        }
    }

    private static boolean isFinite(Vector3f value) {
        return value != null
                && Float.isFinite(value.x)
                && Float.isFinite(value.y)
                && Float.isFinite(value.z);
    }

    private static float smoothstep(
            float edge0,
            float edge1,
            float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0f : 1.0f;
        }
        float t = Mth.clamp(
                (value - edge0) / (edge1 - edge0),
                0.0f,
                1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
