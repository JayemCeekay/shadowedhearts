package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSurfaceSplatSamplingTest {
    @Test
    void manualBudgetOverridesPresetsAndClampsToExperimentalCeiling() {
        assertEquals(
                1350,
                DarkBallSurfaceSplatRenderer.resolveSampleBudget(
                        DarkBallVfxQuality.LOW, 0));
        assertEquals(
                2200,
                DarkBallSurfaceSplatRenderer.resolveSampleBudget(
                        DarkBallVfxQuality.MEDIUM, -1));
        assertEquals(
                3200,
                DarkBallSurfaceSplatRenderer.resolveSampleBudget(
                        DarkBallVfxQuality.HIGH, 0));
        assertEquals(
                6400,
                DarkBallSurfaceSplatRenderer.resolveSampleBudget(
                        DarkBallVfxQuality.LOW, 6400));
        assertEquals(
                DarkBallSurfaceSplatRenderer.MAX_MANUAL_SURFEL_BUDGET,
                DarkBallSurfaceSplatRenderer.resolveSampleBudget(
                        DarkBallVfxQuality.HIGH, 50_000));
    }

    @Test
    void blueNoiseSelectionRejectsDenseCandidateClumpsDeterministically() {
        float[] candidateX = {
                0.00f, 0.01f, 0.02f,
                5.00f, 5.01f,
                10.00f, 10.01f
        };
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan candidates =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(
                        candidateX.length);
        for (int candidate = 0;
             candidate < candidateX.length;
             candidate++) {
            candidates.positions()[candidate * 3] =
                    candidateX[candidate];
            candidates.normals()[candidate * 3 + 2] = 1.0f;
        }

        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan first =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(3);
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan second =
                new DarkBallSurfaceSplatRenderer.SurfaceSamplePlan(3);
        assertEquals(
                3,
                DarkBallSurfaceSplatRenderer.selectBlueNoiseSubset(
                        candidates,
                        0,
                        candidateX.length,
                        first,
                        0,
                        3));
        assertEquals(
                3,
                DarkBallSurfaceSplatRenderer.selectBlueNoiseSubset(
                        candidates,
                        0,
                        candidateX.length,
                        second,
                        0,
                        3));
        assertArrayEquals(
                first.positions(),
                second.positions(),
                0.0f,
                "candidate selection must remain stable between captures");

        float[] retainedX = {
                first.positions()[0],
                first.positions()[3],
                first.positions()[6]
        };
        Arrays.sort(retainedX);
        assertTrue(retainedX[0] < 0.1f);
        assertTrue(retainedX[1] > 4.9f
                && retainedX[1] < 5.1f);
        assertTrue(retainedX[2] > 9.9f);
    }

    @Test
    void fixedBudgetReservesSamplesForThinFeatures() {
        float[] positions = {
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                3.0f, 0.0f, 0.0f,
                4.0f, 0.0f, 0.0f,
                3.0f, 1.0f, 0.0f,
                100.0f, 0.0f, 0.0f,
                101.0f, 0.0f, 0.0f,
                100.0f, 1.0f, 0.0f,
                103.0f, 0.0f, 0.0f,
                104.0f, 0.0f, 0.0f,
                103.0f, 1.0f, 0.0f
        };
        float[] normals = new float[positions.length];
        for (int vertex = 0; vertex < positions.length / 3; vertex++) {
            normals[vertex * 3 + 2] = 1.0f;
        }
        float[] thickness = {
                10.0f, 10.0f, 10.0f,
                10.0f, 10.0f, 10.0f,
                0.1f, 0.1f, 0.1f,
                0.1f, 0.1f, 0.1f
        };
        float[] release = new float[12];
        float[] transport = new float[12];
        for (int vertex = 0; vertex < release.length; vertex++) {
            release[vertex] = vertex / 12.0f;
            transport[vertex] = release[vertex];
        }
        int[] indices = {
                0, 1, 2,
                3, 4, 5,
                6, 7, 8,
                9, 10, 11
        };
        DarkBallSurfaceMesh mesh = new DarkBallSurfaceMesh(
                positions,
                normals,
                thickness,
                release,
                transport,
                indices,
                DarkBallSurfaceMesh.Validation.empty());

        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan =
                DarkBallSurfaceSplatRenderer.prepareSamples(
                        mesh,
                        DarkBallVfxQuality.LOW);
        DarkBallSurfaceSplatRenderer.SurfaceSamplePlan limitedPlan =
                DarkBallSurfaceSplatRenderer.prepareSamples(
                        mesh,
                        5,
                        0.0f);

        assertEquals(12, plan.releaseOrder().length);
        assertEquals(
                5,
                limitedPlan.releaseOrder().length,
                "the explicit manual budget should control retained density");
        long thinSamples = 0;
        for (int sample = 0; sample < 12; sample++) {
            if (plan.positions()[sample * 3] > 50.0f) {
                thinSamples++;
            }
        }
        assertEquals(
                8,
                thinSamples,
                "the 30 percent feature reserve should augment the four "
                        + "area-distributed thin samples");
        assertTrue(
                Arrays.stream(toDouble(plan.tangentConfidence()))
                        .allMatch(value -> value >= 0.0 && value <= 1.0));
    }

    @Test
    void disconnectedSmallFeaturesReceiveComponentBalancedWeight() {
        int[] indices = {
                0, 1, 2,
                3, 4, 5,
                6, 7, 8
        };
        double[] rawWeights = {100.0, 1.0, 1.0};

        DarkBallSurfaceSplatRenderer.ComponentFeatureWeights balanced =
                DarkBallSurfaceSplatRenderer
                        .balanceFeatureWeightsByComponent(
                                9,
                                indices,
                                rawWeights);

        assertEquals(3, balanced.componentCount());
        assertEquals(3, balanced.activeComponents());
        assertTrue(
                balanced.weights()[1] >= 7.9,
                "a small disconnected ribbon must receive a bounded "
                        + "feature-reserve boost");
        assertTrue(balanced.weights()[2] >= 7.9);
        assertTrue(
                balanced.weights()[0] > balanced.weights()[1],
                "the torso should retain more feature weight than one "
                        + "tiny component");
    }

    private static double[] toDouble(float[] values) {
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index];
        }
        return result;
    }
}
