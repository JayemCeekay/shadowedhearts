package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSplatNeighborhoodPlanTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    void neighborhoodPlanIsDeterministicBoundedAndFarthestFirstAnchored() {
        float[] positions = {
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                2.0f, 0.0f, 0.0f,
                3.0f, 0.0f, 0.0f,
                4.0f, 0.0f, 0.0f,
                5.0f, 0.0f, 0.0f,
                6.0f, 0.0f, 0.0f
        };
        float[] release = {
                0.0f, 0.18f, 0.34f, 0.86f, 0.67f, 0.82f, 0.9999f
        };

        DarkBallSplatNeighborhoodPlan.Metadata first =
                DarkBallSplatNeighborhoodPlan.build(
                        positions, release);
        DarkBallSplatNeighborhoodPlan.Metadata second =
                DarkBallSplatNeighborhoodPlan.build(
                        positions, release);

        assertArrayEquals(
                first.smoothedReleaseOrder(),
                second.smoothedReleaseOrder());
        assertArrayEquals(
                first.tangentAngle(),
                second.tangentAngle());
        assertArrayEquals(
                first.tangentConfidence(),
                second.tangentConfidence());
        assertEquals(
                release[0],
                first.smoothedReleaseOrder()[0],
                EPSILON);
        assertEquals(
                release[release.length - 1],
                first.smoothedReleaseOrder()[release.length - 1],
                EPSILON);
        for (int sample = 0; sample < release.length; sample++) {
            assertTrue(
                    Math.abs(first.smoothedReleaseOrder()[sample]
                            - release[sample])
                            <= DarkBallSplatNeighborhoodPlan
                            .MAX_RELEASE_DEVIATION
                            + EPSILON);
            assertTrue(
                    first.spacingScale()[sample]
                            >= DarkBallSplatNeighborhoodPlan
                            .MIN_SPACING_SCALE);
            assertTrue(
                    first.spacingScale()[sample]
                            <= DarkBallSplatNeighborhoodPlan
                            .MAX_SPACING_SCALE);
            assertTrue(first.coherence()[sample] >= 0.0f);
            assertTrue(first.coherence()[sample] <= 1.0f);
            assertTrue(first.tangentAngle()[sample] >= 0.0f);
            assertTrue(first.tangentAngle()[sample] < Math.PI);
            assertTrue(first.tangentConfidence()[sample] >= 0.0f);
            assertTrue(first.tangentConfidence()[sample] <= 1.0f);
        }
        for (int firstSample = 0;
             firstSample < release.length;
             firstSample++) {
            for (int secondSample = 0;
                 secondSample < release.length;
                 secondSample++) {
                if (release[firstSample] < release[secondSample]) {
                    assertTrue(
                            first.smoothedReleaseOrder()[firstSample]
                                    <= first.smoothedReleaseOrder()[
                                    secondSample] + EPSILON,
                            "neighborhood smoothing must preserve the "
                                    + "original farthest-first rank");
                }
            }
        }
    }

    @Test
    void sparseSampleReceivesLargerButCappedCoverageFootprint() {
        float[] positions = {
                0.0f, 0.0f, 0.0f,
                0.10f, 0.0f, 0.0f,
                0.20f, 0.0f, 0.0f,
                0.30f, 0.0f, 0.0f,
                0.40f, 0.0f, 0.0f,
                3.0f, 0.0f, 0.0f,
                6.0f, 0.0f, 0.0f
        };
        float[] release = {
                0.0f, 0.15f, 0.30f, 0.45f, 0.60f, 0.80f, 0.9999f
        };

        DarkBallSplatNeighborhoodPlan.Metadata metadata =
                DarkBallSplatNeighborhoodPlan.build(
                        positions, release);

        assertTrue(metadata.spacingScale()[6]
                > metadata.spacingScale()[2]);
        assertEquals(
                metadata.spacingScale()[6]
                        * metadata.spacingScale()[6],
                metadata.areaScale()[6],
                EPSILON);
        assertTrue(metadata.areaScale()[6]
                <= DarkBallSplatNeighborhoodPlan.MAX_AREA_SCALE);
    }

    @Test
    void principalDirectionFollowsAThinLinearFeature() {
        float[] positions = {
                0.0f, -3.0f, 0.0f,
                0.0f, -2.0f, 0.0f,
                0.0f, -1.0f, 0.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 2.0f, 0.0f,
                0.0f, 3.0f, 0.0f
        };
        float[] normals = {
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f
        };
        float[] release = {
                0.0f, 0.16f, 0.32f, 0.48f, 0.64f, 0.80f, 0.9999f
        };

        DarkBallSplatNeighborhoodPlan.Metadata metadata =
                DarkBallSplatNeighborhoodPlan.build(
                        positions,
                        normals,
                        release);

        assertEquals(
                Math.PI * 0.5,
                metadata.tangentAngle()[3],
                1.0e-4);
        assertTrue(
                metadata.tangentConfidence()[3] > 0.95f,
                "a linear appendage should receive a stable shared axis");
    }

    @Test
    void oppositeThinSurfaceFacesDoNotBecomeNeighbors() {
        float[] positions = {
                -1.5f, 0.0f, 0.00f,
                -0.5f, 0.0f, 0.00f,
                0.5f, 0.0f, 0.00f,
                1.5f, 0.0f, 0.00f,
                -1.5f, 0.0f, 0.02f,
                -0.5f, 0.0f, 0.02f,
                0.5f, 0.0f, 0.02f,
                1.5f, 0.0f, 0.02f
        };
        float[] normals = {
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, -1.0f,
                0.0f, 0.0f, -1.0f,
                0.0f, 0.0f, -1.0f,
                0.0f, 0.0f, -1.0f
        };
        float[] release = {
                0.0f, 0.05f, 0.10f, 0.15f,
                0.85f, 0.90f, 0.95f, 0.9999f
        };

        DarkBallSplatNeighborhoodPlan.Metadata metadata =
                DarkBallSplatNeighborhoodPlan.build(
                        positions,
                        normals,
                        release);

        assertTrue(
                metadata.smoothedReleaseOrder()[1] < 0.20f,
                "the front face must not inherit release timing from the "
                        + "opposite side of a thin feature");
        assertTrue(
                metadata.smoothedReleaseOrder()[6] > 0.80f,
                "the rear face must retain its independent release wave");
        assertTrue(
                DarkBallSplatNeighborhoodPlan
                        .MIN_SURFACE_NEIGHBOR_NORMAL_DOT < 0.0f,
                "sharp creases remain valid while reversed faces are rejected");
    }
}
