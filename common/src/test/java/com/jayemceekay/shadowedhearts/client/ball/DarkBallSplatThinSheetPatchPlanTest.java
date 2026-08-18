package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSplatThinSheetPatchPlanTest {
    @Test
    void opposingFacesBecomeOneCoherentLogicalPatch() {
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(8);
        for (int point = 0; point < 4; point++) {
            writeSample(
                    plan,
                    point,
                    point,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f);
            writeSample(
                    plan,
                    point + 4,
                    point,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    -1.0f);
        }

        DarkBallSplatThinSheetPatchPlan.Result result =
                DarkBallSplatThinSheetPatchPlan.apply(plan, 1.0f);

        assertEquals(1, result.patchCount());
        assertEquals(8, result.patchedSamples());
        assertEquals(8, result.largestPatch());
        assertTrue(result.coherentTangentSamples() >= 8);
        int patch = plan.thinSheetPatchId()[0];
        assertTrue(patch >= 0);
        assertTrue(Arrays.stream(plan.thinSheetPatchId())
                .allMatch(value -> value == patch));
        for (float confidence : plan.tangentConfidence()) {
            assertTrue(confidence >= 0.70f);
        }
    }

    @Test
    void perpendicularNearbySheetsRemainSeparate() {
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(6);
        for (int sample = 0; sample < 3; sample++) {
            writeSample(
                    plan,
                    sample,
                    sample,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f);
            writeSample(
                    plan,
                    sample + 3,
                    0.0f,
                    sample,
                    0.10f,
                    1.0f,
                    0.0f,
                    0.0f);
        }

        DarkBallSplatThinSheetPatchPlan.Result result =
                DarkBallSplatThinSheetPatchPlan.apply(plan, 1.0f);

        assertEquals(2, result.patchCount());
        assertNotEquals(
                plan.thinSheetPatchId()[0],
                plan.thinSheetPatchId()[3]);
    }

    @Test
    void oversizedConnectedPatchUsesLocalInsteadOfGlobalTangents() {
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(13);
        int sample = 0;
        for (int x = -6; x <= 0; x++) {
            writeSample(
                    plan,
                    sample++,
                    x,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f);
        }
        for (int y = 1; y <= 6; y++) {
            writeSample(
                    plan,
                    sample++,
                    0.0f,
                    y,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f);
        }

        DarkBallSplatThinSheetPatchPlan.Result result =
                DarkBallSplatThinSheetPatchPlan.apply(plan, 1.0f);

        assertEquals(1, result.patchCount());
        float horizontalAngle = plan.tangentAngle()[0];
        float verticalAngle = plan.tangentAngle()[12];
        assertTrue(lineAngleDistance(horizontalAngle, 0.0f) < 0.20f);
        assertTrue(lineAngleDistance(
                verticalAngle,
                (float) Math.PI * 0.5f) < 0.20f);
    }

    @Test
    void featureSelectionReservesSamplesAcrossThinPatches() {
        int candidatesPerPatch = 12;
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan candidates =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(
                        candidatesPerPatch * 2);
        for (int sample = 0;
             sample < candidatesPerPatch;
             sample++) {
            writeSample(
                    candidates,
                    sample,
                    sample * 0.4f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f);
            writeSample(
                    candidates,
                    sample + candidatesPerPatch,
                    100.0f + sample * 0.4f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f);
        }
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan selected =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(12);

        assertEquals(
                12,
                DarkBallSurfaceSplatRenderer
                        .selectPatchAwareBlueNoiseSubset(
                                candidates,
                                0,
                                candidates.releaseOrder().length,
                                selected,
                                0,
                                12,
                                1.0f));
        int firstPatch = 0;
        int secondPatch = 0;
        for (int sample = 0;
             sample < selected.releaseOrder().length;
             sample++) {
            if (selected.positions()[sample * 3] < 50.0f) {
                firstPatch++;
            } else {
                secondPatch++;
            }
        }
        assertTrue(firstPatch >= 4);
        assertTrue(secondPatch >= 4);
    }

    private static void writeSample(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            int sample,
            float x,
            float y,
            float z,
            float nx,
            float ny,
            float nz) {
        int triple = sample * 3;
        plan.positions()[triple] = x;
        plan.positions()[triple + 1] = y;
        plan.positions()[triple + 2] = z;
        plan.normals()[triple] = nx;
        plan.normals()[triple + 1] = ny;
        plan.normals()[triple + 2] = nz;
        plan.localThickness()[sample] = 0.25f;
        plan.releaseOrder()[sample] = 0.5f;
        plan.presentationReleaseOrder()[sample] = 0.5f;
        plan.transportOrder()[sample] = 0.5f;
        plan.spacingScale()[sample] = 1.0f;
        plan.areaScale()[sample] = 1.0f;
        plan.thinSheetScore()[sample] = 1.0f;
    }

    private static float lineAngleDistance(
            float first,
            float second) {
        float difference = Math.abs(first - second)
                % (float) Math.PI;
        return Math.min(
                difference,
                (float) Math.PI - difference);
    }
}
