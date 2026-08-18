package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSplatThinSheetPlanTest {
    @Test
    void opposingFacesShareOneCarrierAndReleaseState() {
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(2);
        setSample(
                plan,
                0,
                0.0f, 0.0f, -0.05f,
                0.0f, 0.0f, -1.0f,
                0.10f,
                0.42f,
                0.44f);
        setSample(
                plan,
                1,
                0.0f, 0.0f, 0.05f,
                0.0f, 0.0f, 1.0f,
                0.12f,
                0.46f,
                0.48f);

        DarkBallSplatThinSheetPlan.Result result =
                DarkBallSplatThinSheetPlan.stabilize(plan, 0.10f);

        assertEquals(1, result.pairCount());
        assertEquals(2, result.pairedSamples());
        assertTrue(result.meanPairScore() > 0.70f);
        assertEquals(0.0f, plan.positions()[2], 1.0e-6f);
        assertEquals(0.0f, plan.positions()[5], 1.0e-6f);
        assertEquals(
                plan.releaseOrder()[0],
                plan.releaseOrder()[1],
                0.0f);
        assertEquals(
                plan.transportOrder()[0],
                plan.transportOrder()[1],
                0.0f);
        assertTrue(
                plan.thinSheetScore()[0]
                        >= DarkBallSplatThinSheetPlan.MIN_PAIR_SCORE);
        assertEquals(
                plan.thinSheetScore()[0],
                plan.thinSheetScore()[1],
                0.0f);
    }

    @Test
    void nearbyOpposingFoldIsNotPairedButRetainsThinSurfaceRisk() {
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(2);
        setSample(
                plan,
                0,
                -0.05f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f,
                0.10f,
                0.50f,
                0.50f);
        setSample(
                plan,
                1,
                0.05f, 0.0f, 0.0f,
                0.0f, 0.0f, -1.0f,
                0.10f,
                0.50f,
                0.50f);

        DarkBallSplatThinSheetPlan.Result result =
                DarkBallSplatThinSheetPlan.stabilize(plan, 0.10f);

        assertEquals(0, result.pairCount());
        assertTrue(plan.thinSheetScore()[0] >= 0.99f);
        assertTrue(plan.thinSheetScore()[1] >= 0.99f);
        assertEquals(-0.05f, plan.positions()[0], 0.0f);
        assertEquals(0.05f, plan.positions()[3], 0.0f);
    }

    @Test
    void unmatchedThinSampleStillReceivesHoleResistantPolicy() {
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(2);
        setSample(
                plan,
                0,
                0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f,
                0.16f,
                0.25f,
                0.25f);
        setSample(
                plan,
                1,
                4.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.80f,
                0.75f,
                0.75f);

        DarkBallSplatThinSheetPlan.Result result =
                DarkBallSplatThinSheetPlan.stabilize(plan, 0.10f);

        assertEquals(0, result.pairCount());
        assertTrue(
                plan.thinSheetScore()[0]
                        >= DarkBallSplatThinSheetPlan.MIN_PAIR_SCORE);
        assertEquals(0.0f, plan.thinSheetScore()[1], 0.0f);
    }

    @Test
    void packedMetadataPreservesBoneNodeAndSheetConfidence() {
        int packed =
                DarkBallSurfaceSplatRenderer
                        .encodeAttachmentAndThinSheetMetadata(
                                27, 1.0f);

        assertEquals(27, packed & 31);
        assertEquals(7, packed >> 5 & 7);
    }

    private static void setSample(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            int sample,
            float x,
            float y,
            float z,
            float normalX,
            float normalY,
            float normalZ,
            float thickness,
            float release,
            float transport) {
        int triple = sample * 3;
        plan.positions()[triple] = x;
        plan.positions()[triple + 1] = y;
        plan.positions()[triple + 2] = z;
        plan.normals()[triple] = normalX;
        plan.normals()[triple + 1] = normalY;
        plan.normals()[triple + 2] = normalZ;
        plan.localThickness()[sample] = thickness;
        plan.releaseOrder()[sample] = release;
        plan.presentationReleaseOrder()[sample] = release;
        plan.transportOrder()[sample] = transport;
    }
}
