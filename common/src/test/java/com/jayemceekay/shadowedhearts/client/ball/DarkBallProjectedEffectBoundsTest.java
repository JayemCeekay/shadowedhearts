package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallProjectedEffectBoundsTest {
    private static final float EPSILON = 0.00001f;
    private static final Vector3d ORIGIN = new Vector3d();
    private static final Vector3d AXIS_X = new Vector3d(1.0, 0.0, 0.0);
    private static final Vector3d AXIS_Y = new Vector3d(0.0, 1.0, 0.0);
    private static final Vector3d AXIS_Z = new Vector3d(0.0, 0.0, 1.0);

    @Test
    void centeredBodyProjectsToExpectedNormalizedRectangle() {
        DarkBallProjectedEffectBounds.Projection projection =
                standardProjection(ORIGIN);
        projection.includeOrientedBox(
                ORIGIN, AXIS_X, AXIS_Y, AXIS_Z,
                -1.0f, -1.0f, -5.0f,
                1.0f, 1.0f, -4.0f);

        DarkBallProjectedEffectBounds.UvBounds bounds =
                projection.finish(800, 800, 0);

        assertEquals(0.375f, bounds.minU(), EPSILON);
        assertEquals(0.375f, bounds.minV(), EPSILON);
        assertEquals(0.625f, bounds.maxU(), EPSILON);
        assertEquals(0.625f, bounds.maxV(), EPSILON);
    }

    @Test
    void orientedBodyBoundsContainEveryProjectedFaceSample() {
        Vector3d axis = new Vector3d(0.819152, 0.0, -0.573576);
        Vector3d side = new Vector3d(0.573576, 0.0, 0.819152);
        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        Vector3d root = new Vector3d(-0.4, 0.2, -6.0);
        DarkBallProjectedEffectBounds.Projection projection =
                standardProjection(ORIGIN);
        projection.includeOrientedBox(
                root, axis, side, up,
                -0.7f, -1.2f, -0.8f,
                1.8f, 1.2f, 0.8f);
        DarkBallProjectedEffectBounds.UvBounds bounds =
                projection.finish(1920, 1080, 0);

        for (int xStep = 0; xStep <= 8; xStep++) {
            for (int yStep = 0; yStep <= 8; yStep++) {
                float x = mix(-0.7f, 1.8f, xStep / 8.0f);
                float y = mix(-1.2f, 1.2f, yStep / 8.0f);
                assertProjectedInside(bounds,
                        localToWorld(root, axis, side, up,
                                x, y, -0.8f));
                assertProjectedInside(bounds,
                        localToWorld(root, axis, side, up,
                                x, y, 0.8f));
            }
        }
    }

    @Test
    void nearPlaneCrossingFallsBackToFullViewport() {
        DarkBallProjectedEffectBounds.Projection projection =
                standardProjection(ORIGIN);
        projection.includeOrientedBox(
                ORIGIN, AXIS_X, AXIS_Y, AXIS_Z,
                -0.4f, -0.4f, -0.5f,
                0.4f, 0.4f, 0.2f);

        assertSame(
                DarkBallProjectedEffectBounds.UvBounds.fullScreen(),
                projection.finish(1280, 720, 0));
    }

    @Test
    void fullyOffscreenOrBehindGeometryUsesSafeFullViewportFallback() {
        DarkBallProjectedEffectBounds.Projection offscreen =
                standardProjection(ORIGIN);
        offscreen.includeOrientedBox(
                ORIGIN, AXIS_X, AXIS_Y, AXIS_Z,
                20.0f, -0.5f, -6.0f,
                21.0f, 0.5f, -5.0f);
        assertSame(
                DarkBallProjectedEffectBounds.UvBounds.fullScreen(),
                offscreen.finish(1280, 720, 0));

        DarkBallProjectedEffectBounds.Projection behind =
                standardProjection(ORIGIN);
        behind.includeOrientedBox(
                ORIGIN, AXIS_X, AXIS_Y, AXIS_Z,
                -0.5f, -0.5f, 2.0f,
                0.5f, 0.5f, 3.0f);
        assertSame(
                DarkBallProjectedEffectBounds.UvBounds.fullScreen(),
                behind.finish(1280, 720, 0));
    }

    @Test
    void siphonBoundsIncludeTubeWidthBeyondItsCenterline() {
        Vector3f[] nodes = new Vector3f[
                DarkBallSiphonBoltPath.NODE_COUNT];
        for (int index = 0; index < nodes.length; index++) {
            float t = index / (nodes.length - 1.0f);
            nodes[index] = new Vector3f(
                    mix(-2.0f, 2.0f, t), 0.0f, -5.0f);
        }

        DarkBallProjectedEffectBounds.Projection projection =
                standardProjection(ORIGIN);
        projection.includeSiphonPrisms(
                ORIGIN, AXIS_X, AXIS_Y, AXIS_Z,
                nodes, 1.0f, 0.05f, 0.10f);
        DarkBallProjectedEffectBounds.UvBounds bounds =
                projection.finish(1600, 900, 0);

        // The centerline endpoints project to U=0.30 and U=0.70. The bound
        // must extend beyond both by the square prism's finite tube radius.
        assertTrue(bounds.minU() < 0.30f);
        assertTrue(bounds.maxU() > 0.70f);
        assertTrue(bounds.minV() < 0.50f);
        assertTrue(bounds.maxV() > 0.50f);
    }

    @Test
    void contractedInletProducesTighterConservativeSiphonBounds() {
        Vector3f[] nodes = new Vector3f[
                DarkBallSiphonBoltPath.NODE_COUNT];
        for (int index = 0; index < nodes.length; index++) {
            float t = index / (nodes.length - 1.0f);
            nodes[index] = new Vector3f(
                    mix(-1.2f, 1.2f, t), 0.0f, -5.0f);
        }

        DarkBallProjectedEffectBounds.Projection full =
                standardProjection(ORIGIN);
        full.includeSiphonPrisms(
                ORIGIN, AXIS_X, AXIS_Y, AXIS_Z,
                nodes, 4.0f, 0.01f, 0.12f, 1.0f);
        DarkBallProjectedEffectBounds.UvBounds fullBounds =
                full.finish(1600, 900, 0);

        DarkBallProjectedEffectBounds.Projection contracted =
                standardProjection(ORIGIN);
        contracted.includeSiphonPrisms(
                ORIGIN, AXIS_X, AXIS_Y, AXIS_Z,
                nodes, 4.0f, 0.01f, 0.12f,
                DarkBallCaptureVfx
                        .SIPHON_CONTRACTED_INLET_RADIUS_SCALE);
        DarkBallProjectedEffectBounds.UvBounds contractedBounds =
                contracted.finish(1600, 900, 0);

        assertTrue(contractedBounds.minU() >= fullBounds.minU());
        assertTrue(contractedBounds.maxU() <= fullBounds.maxU());
        assertTrue(contractedBounds.minV() >= fullBounds.minV());
        assertTrue(contractedBounds.maxV() <= fullBounds.maxV());
        float fullArea = (fullBounds.maxU() - fullBounds.minU())
                * (fullBounds.maxV() - fullBounds.minV());
        float contractedArea =
                (contractedBounds.maxU() - contractedBounds.minU())
                        * (contractedBounds.maxV()
                        - contractedBounds.minV());
        assertTrue(contractedArea < fullArea);
    }

    @Test
    void fixedPresentationScheduleContainsAllNodesAndThreeMidpoints() {
        float previous = -1.0f;
        int midpointCount = 0;
        int segmentCount = DarkBallSiphonBoltPath.NODE_COUNT - 1;
        for (int boundary = 0;
             boundary <= DarkBallProjectedEffectBounds
                     .PRESENTATION_PRISM_COUNT;
             boundary++) {
            float curveT = DarkBallProjectedEffectBounds
                    .siphonPresentationCurveT(boundary);
            assertTrue(curveT > previous);
            float pathUnits = curveT * segmentCount;
            float fractional = pathUnits - (float) Math.floor(pathUnits);
            if (Math.abs(fractional - 0.5f) < EPSILON) {
                midpointCount++;
            }
            previous = curveT;
        }
        assertEquals(3, midpointCount);
        assertEquals(1.0f, previous, EPSILON);
    }

    @Test
    void pixelExpansionUsesSourceResolutionAndClampsOutward() {
        DarkBallProjectedEffectBounds.UvBounds geometry =
                new DarkBallProjectedEffectBounds.UvBounds(
                        0.10f, 0.20f, 0.90f, 0.80f);

        DarkBallProjectedEffectBounds.UvBounds fullResolution =
                geometry.expandByPixels(4, 800, 400);
        assertEquals(0.095f, fullResolution.minU(), EPSILON);
        assertEquals(0.190f, fullResolution.minV(), EPSILON);
        assertEquals(0.905f, fullResolution.maxU(), EPSILON);
        assertEquals(0.810f, fullResolution.maxV(), EPSILON);

        DarkBallProjectedEffectBounds.UvBounds halfResolution =
                geometry.expandByPixels(4, 400, 200);
        assertEquals(0.090f, halfResolution.minU(), EPSILON);
        assertEquals(0.180f, halfResolution.minV(), EPSILON);
        assertEquals(0.910f, halfResolution.maxU(), EPSILON);
        assertEquals(0.820f, halfResolution.maxV(), EPSILON);
    }

    @Test
    void compositePaddingCoversFourPixelRimAndFilterGuard() {
        int finalScreenRimPixels = 4;
        assertEquals(5,
                DarkBallProjectedEffectBounds.COMPOSITE_PADDING_PIXELS);
        assertTrue(DarkBallProjectedEffectBounds.COMPOSITE_PADDING_PIXELS
                        > finalScreenRimPixels,
                "bounded composite needs one source-pixel filter guard"
                        + " beyond the full-resolution rim");
    }

    @Test
    void projectedBodyRadiusUsesTheShorterScreenExtent() {
        DarkBallProjectedEffectBounds.UvBounds body =
                new DarkBallProjectedEffectBounds.UvBounds(
                        0.20f, 0.25f, 0.80f, 0.75f);

        assertEquals(250.0f,
                body.inscribedRadiusPixels(1000, 1000), EPSILON);
        assertEquals(125.0f,
                body.inscribedRadiusPixels(500, 500), EPSILON);
        assertEquals(0.0f,
                body.inscribedRadiusPixels(0, 1000), EPSILON);
    }

    @Test
    void analyticalAndTextureExactBodyBoundsUnionConservatively() {
        DarkBallProjectedEffectBounds.UvBounds analytical =
                new DarkBallProjectedEffectBounds.UvBounds(
                        0.20f, 0.25f, 0.62f, 0.70f);
        DarkBallProjectedEffectBounds.UvBounds exactMask =
                DarkBallProjectedEffectBounds.UvBounds.conservative(
                        0.16f, 0.30f, 0.68f, 0.76f);

        DarkBallProjectedEffectBounds.UvBounds union =
                analytical.union(exactMask);
        assertEquals(0.16f, union.minU(), EPSILON);
        assertEquals(0.25f, union.minV(), EPSILON);
        assertEquals(0.68f, union.maxU(), EPSILON);
        assertEquals(0.76f, union.maxV(), EPSILON);

        assertSame(
                DarkBallProjectedEffectBounds.UvBounds.fullScreen(),
                analytical.union(
                        DarkBallProjectedEffectBounds.UvBounds.conservative(
                                Float.NaN, 0.0f, 1.0f, 1.0f)));
    }

    @Test
    void farWorldBoundsContainTrueAndShaderQuantizedProjections() {
        Vector3d farCamera =
                new Vector3d(30_000_000.9, 72.5, 30_000_000.9);
        DarkBallProjectedEffectBounds.Projection farProjection =
                standardProjection(farCamera);
        farProjection.includeOrientedBox(
                new Vector3d(
                        farCamera.x + 1.1,
                        farCamera.y,
                        farCamera.z - 5.0),
                AXIS_X, AXIS_Y, AXIS_Z,
                -0.25f, -0.25f, -0.25f,
                0.25f, 0.25f, 0.25f);
        DarkBallProjectedEffectBounds.UvBounds farBounds =
                farProjection.finish(1920, 1080, 0);

        // The intended double-relative box reaches only about U=0.642. The
        // shader sees float(root)-float(camera)=(2,-4), so its local box
        // reaches U=0.800. The submitted rectangle must contain both.
        assertTrue(farBounds.minU() < 0.59f);
        assertTrue(farBounds.maxU() >= 0.80f - EPSILON);
        assertTrue(farBounds.minV() < 0.47f);
        assertTrue(farBounds.maxV() > 0.53f);
    }

    @Test
    void affineProjectionAtFarCoordinatesContainsShaderEyeOffsetLoss() {
        Matrix4f projectionMatrix = new Matrix4f()
                .perspective(
                        (float) Math.toRadians(78.0),
                        16.0f / 9.0f, 0.1f, 100.0f)
                .translate(0.114f, -0.160f, -0.035f);
        Vector3d camera =
                new Vector3d(30_000_000.9, 72.5, 30_000_000.9);
        Vector3d root =
                new Vector3d(camera.x + 1.1, camera.y, camera.z - 5.0);
        DarkBallProjectedEffectBounds.Projection projection =
                DarkBallProjectedEffectBounds.begin(
                        projectionMatrix, new Quaternionf(), camera);
        projection.includeOrientedBox(
                root, AXIS_X, AXIS_Y, AXIS_Z,
                -0.25f, -0.25f, -0.25f,
                0.25f, 0.25f, 0.25f);
        DarkBallProjectedEffectBounds.UvBounds bounds =
                projection.finish(1920, 1080, 0);

        Matrix4f inverseProjection =
                new Matrix4f(projectionMatrix).invert();
        Vector4f homogeneousEye = inverseProjection.transform(
                new Vector4f(0.0f, 0.0f, 1.0f, 0.0f));
        Vector3f eyeView = new Vector3f(
                homogeneousEye.x / homogeneousEye.w,
                homogeneousEye.y / homogeneousEye.w,
                homogeneousEye.z / homogeneousEye.w);
        float cameraX = (float) camera.x;
        float cameraY = (float) camera.y;
        float cameraZ = (float) camera.z;
        Vector3f retainedEye = new Vector3f(
                (cameraX + eyeView.x) - cameraX,
                (cameraY + eyeView.y) - cameraY,
                (cameraZ + eyeView.z) - cameraZ);
        Vector3f correction = new Vector3f(eyeView).sub(retainedEye);
        assertTrue(correction.lengthSquared() > 0.0001f,
                "test setup must lose a measurable shader eye offset");

        Vector3f quantizedRootView = new Vector3f(
                (float) root.x - cameraX,
                (float) root.y - cameraY,
                (float) root.z - cameraZ);
        for (int corner = 0; corner < 8; corner++) {
            Vector3f view = new Vector3f(quantizedRootView)
                    .add((corner & 1) == 0 ? -0.25f : 0.25f,
                            (corner & 2) == 0 ? -0.25f : 0.25f,
                            (corner & 4) == 0 ? -0.25f : 0.25f)
                    .add(correction);
            Vector4f clip = projectionMatrix.transform(
                    new Vector4f(view, 1.0f));
            float u = clip.x / clip.w * 0.5f + 0.5f;
            float v = clip.y / clip.w * 0.5f + 0.5f;
            assertUvInside(bounds, u, v);
        }
    }

    @Test
    void nonfiniteGeometryFallsBackToFullViewport() {
        DarkBallProjectedEffectBounds.Projection projection =
                standardProjection(ORIGIN);
        projection.includeOrientedBox(
                new Vector3d(Double.NaN, 0.0, -5.0),
                AXIS_X, AXIS_Y, AXIS_Z,
                -1.0f, -1.0f, -1.0f,
                1.0f, 1.0f, 1.0f);

        assertSame(
                DarkBallProjectedEffectBounds.UvBounds.fullScreen(),
                projection.finish(1280, 720, 0));
    }

    private static DarkBallProjectedEffectBounds.Projection
    standardProjection(Vector3d cameraPosition) {
        return DarkBallProjectedEffectBounds.begin(
                new Matrix4f().perspective(
                        (float) Math.toRadians(90.0),
                        1.0f, 0.1f, 100.0f),
                new Quaternionf(),
                cameraPosition);
    }

    private static Vector3d localToWorld(
            Vector3d root, Vector3d axis, Vector3d side, Vector3d up,
            float x, float y, float z) {
        return new Vector3d(root)
                .fma(x, axis)
                .fma(y, side)
                .fma(z, up);
    }

    private static void assertProjectedInside(
            DarkBallProjectedEffectBounds.UvBounds bounds,
            Vector3d world) {
        // Tests use an identity camera and a 90-degree square projection.
        float u = (float) (world.x / -world.z * 0.5 + 0.5);
        float v = (float) (world.y / -world.z * 0.5 + 0.5);
        assertUvInside(bounds, u, v);
    }

    private static void assertUvInside(
            DarkBallProjectedEffectBounds.UvBounds bounds,
            float u, float v) {
        assertTrue(u >= bounds.minU() - EPSILON
                        && u <= bounds.maxU() + EPSILON,
                "projected U " + u + " was outside " + bounds);
        assertTrue(v >= bounds.minV() - EPSILON
                        && v <= bounds.maxV() + EPSILON,
                "projected V " + v + " was outside " + bounds);
    }

    private static float mix(float start, float end, float amount) {
        return start + (end - start) * amount;
    }
}
