package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkBallSurfaceSplatInsetTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    void thickSurfaceUsesFortyFivePercentVoxelInset() {
        assertEquals(
                0.09f,
                DarkBallSurfaceSplatRenderer.surfaceInsetDistance(
                        0.20f,
                        1.00f),
                1.0e-6f);
    }

    @Test
    void thinSurfaceIsLimitedToFifteenPercentThickness() {
        assertEquals(
                0.015f,
                DarkBallSurfaceSplatRenderer.surfaceInsetDistance(
                        0.20f,
                        0.10f),
                1.0e-6f);
    }

    @Test
    void invalidOrNonPositiveInputsCannotMoveSamplesOutward() {
        assertEquals(
                0.0f,
                DarkBallSurfaceSplatRenderer.surfaceInsetDistance(
                        Float.NaN,
                        1.00f),
                0.0f);
        assertEquals(
                0.0f,
                DarkBallSurfaceSplatRenderer.surfaceInsetDistance(
                        0.20f,
                        Float.NaN),
                0.0f);
        assertEquals(
                0.0f,
                DarkBallSurfaceSplatRenderer.surfaceInsetDistance(
                        -0.20f,
                        -1.00f),
                0.0f);
    }

    @Test
    void routePreparationUsesTheExactUploadedInsetCarrier() {
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(1);
        plan.positions()[0] = 1.0f;
        plan.positions()[1] = 2.0f;
        plan.positions()[2] = 3.0f;
        plan.normals()[0] = 1.0f;
        plan.localThickness()[0] = 2.0f;

        float[] inset =
                DarkBallSurfaceSplatRenderer.insetSamplePositions(
                        plan,
                        0.4f);
        float expectedInset =
                DarkBallSurfaceSplatRenderer.surfaceInsetDistance(
                        0.4f,
                        2.0f);

        assertEquals(1.0f - expectedInset, inset[0], EPSILON);
        assertEquals(2.0f, inset[1], EPSILON);
        assertEquals(3.0f, inset[2], EPSILON);
        assertEquals(1.0f, plan.positions()[0], EPSILON,
                "route preparation must not mutate prepared samples");
    }
}
