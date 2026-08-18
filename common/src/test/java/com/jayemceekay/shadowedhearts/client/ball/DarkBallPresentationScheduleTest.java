package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallPresentationScheduleTest {
    private static final float EPSILON = 0.00001f;

    @Test
    void onTimeSurfelActivationKeepsTheOriginalSchedule() {
        float activationAge =
                DarkBallCaptureVfx.SURFACE_SPLAT_SELECTION_DEADLINE
                        - 0.01f;
        for (int step = 0; step <= 32; step++) {
            float age = DarkBallCaptureVfx.VFX_END
                    * step / 32.0f;
            assertEquals(
                    age,
                    DarkBallCaptureVfx.surfacePresentationAgeAt(
                            age, activationAge),
                    EPSILON);
        }
    }

    @Test
    void lateSurfelActivationCompressesOnlyTheRemainingChoreography() {
        float activationAge =
                DarkBallCaptureVfx.SURFACE_SPLAT_SELECTION_DEADLINE
                        + DarkBallCaptureVfx
                        .SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS
                        * 0.40f;
        float ramp =
                DarkBallCaptureVfx.lateActivationRampSecondsAt(
                        activationAge);

        assertEquals(
                activationAge,
                DarkBallCaptureVfx.surfacePresentationAgeAt(
                        activationAge, activationAge),
                EPSILON);
        assertEquals(
                DarkBallCaptureVfx.SIPHON_START,
                DarkBallCaptureVfx.surfacePresentationAgeAt(
                        activationAge + ramp, activationAge),
                EPSILON,
                "collapse must wait for the late surfel activation ramp");
        assertEquals(
                DarkBallCaptureVfx.VFX_END,
                DarkBallCaptureVfx.surfacePresentationAgeAt(
                        DarkBallCaptureVfx.VFX_END, activationAge),
                EPSILON);

        float previous = activationAge;
        for (int step = 1; step <= 64; step++) {
            float age = activationAge
                    + (DarkBallCaptureVfx.VFX_END - activationAge)
                    * step / 64.0f;
            float presented =
                    DarkBallCaptureVfx.surfacePresentationAgeAt(
                            age, activationAge);
            assertTrue(presented >= previous - EPSILON);
            previous = presented;
        }
    }

    @Test
    void extremelyLateActivationUsesABoundedShortRamp() {
        float activationAge =
                DarkBallCaptureVfx.VFX_END - 0.03f;
        float ramp =
                DarkBallCaptureVfx.lateActivationRampSecondsAt(
                        activationAge);

        assertTrue(ramp
                < DarkBallCaptureVfx
                .SURFACE_SPLAT_LATE_ACTIVATION_RAMP_SECONDS);
        assertTrue(ramp <= 0.03f + EPSILON);
        float previous = activationAge;
        for (int step = 1; step <= 16; step++) {
            float age = activationAge
                    + (DarkBallCaptureVfx.VFX_END - activationAge)
                    * step / 16.0f;
            float presented =
                    DarkBallCaptureVfx.surfacePresentationAgeAt(
                            age, activationAge);
            assertTrue(presented >= previous - EPSILON,
                    "an extremely late result must never rewind to an "
                            + "earlier phase");
            previous = presented;
        }
        assertEquals(
                DarkBallCaptureVfx.VFX_END,
                DarkBallCaptureVfx.surfacePresentationAgeAt(
                        DarkBallCaptureVfx.VFX_END, activationAge),
                EPSILON);
    }

    @Test
    void proxyDepthIsAvailableEarlyAndFadesMonotonicallyBeforeAbsorption() {
        assertTrue(DarkBallCaptureVfx.PROXY_DEPTH_FADE_START
                < DarkBallCaptureVfx.BALL_ABSORB_END);
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.proxyDepthStrengthAt(
                        DarkBallCaptureVfx.CONVERSION_START),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.proxyDepthStrengthAt(
                        DarkBallCaptureVfx.PROXY_DEPTH_FADE_START),
                EPSILON);
        assertEquals(
                0.0f,
                DarkBallCaptureVfx.proxyDepthStrengthAt(
                        DarkBallCaptureVfx.BALL_ABSORB_END),
                EPSILON);

        float previous = 1.0f;
        for (int step = 0; step <= 64; step++) {
            float age = DarkBallCaptureVfx.PROXY_DEPTH_FADE_START
                    + (DarkBallCaptureVfx.BALL_ABSORB_END
                    - DarkBallCaptureVfx.PROXY_DEPTH_FADE_START)
                    * step / 64.0f;
            float strength =
                    DarkBallCaptureVfx.proxyDepthStrengthAt(age);
            assertTrue(strength <= previous + EPSILON,
                    "proxy depth must fade out instead of activating late");
            previous = strength;
        }
    }

    @Test
    void depthLightingWaitsForBothCleanHitHandoffAndSurfelActivation() {
        assertEquals(
                0.0f,
                DarkBallCaptureVfx.depthHighlightBlendAt(
                        DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END,
                        1.0f),
                EPSILON);
        assertEquals(
                0.0f,
                DarkBallCaptureVfx.depthHighlightBlendAt(
                        DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL,
                        0.0f),
                EPSILON,
                "proxy depth alone must not light the exact hit silhouette");
        assertEquals(
                0.5f,
                DarkBallCaptureVfx.depthHighlightBlendAt(
                        DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL,
                        0.5f),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.depthHighlightBlendAt(
                        DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL,
                        1.0f),
                EPSILON);
    }

    @Test
    void preCollapseInteriorGuardRetiresAtTheSiphonBoundary() {
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.preCollapseInteriorGuardAt(
                        DarkBallCaptureVfx.CONVERSION_SOLID),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.preCollapseInteriorGuardAt(
                        Math.nextDown(
                                DarkBallCaptureVfx.SIPHON_START)),
                EPSILON);
        assertEquals(
                0.0f,
                DarkBallCaptureVfx.preCollapseInteriorGuardAt(
                        DarkBallCaptureVfx.SIPHON_START),
                EPSILON);
        assertEquals(
                0.0f,
                DarkBallCaptureVfx.preCollapseInteriorGuardAt(
                        DarkBallCaptureVfx.SIPHON_END),
                EPSILON);

        assertEquals(
                0.0f,
                DarkBallCaptureVfx.preCollapseInteriorGuardAt(
                        DarkBallCaptureVfx.SIPHON_START
                                + DarkBallCaptureVfx
                                .MAX_TICK_DELTA_SECONDS),
                EPSILON);
    }

    @Test
    void rapidRevealIsPresentationOnlyAndMonotone() {
        assertEquals(0.0f, DarkBallCaptureVfx.siphonProgressAt(
                DarkBallCaptureVfx.SIPHON_START), EPSILON);
        assertEquals(1.0f, DarkBallCaptureVfx.siphonProgressAt(
                DarkBallCaptureVfx.SIPHON_END), EPSILON);

        Vector3f[] nodes = straightNodes();
        float[] trailing = filled(nodes.length, 1.0f);
        DarkBallSiphonSurfaceMesh hidden =
                DarkBallSiphonSurfaceMesh.build(
                        nodes, 0.18f, 0.23f, 0.10f,
                        DarkBallCaptureVfx.SIPHON_LEADING_EASE_START,
                        trailing);
        DarkBallSiphonSurfaceMesh revealed =
                DarkBallSiphonSurfaceMesh.build(
                        nodes, 0.18f, 0.23f, 0.10f,
                        DarkBallCaptureVfx.SIPHON_LEADING_EASE_END,
                        trailing);
        for (float window : hidden.materialWindow()) {
            assertEquals(0.0f, window, EPSILON,
                    "the eased siphon must begin from a fully hidden root");
        }
        for (float window : revealed.materialWindow()) {
            assertEquals(1.0f, window, EPSILON,
                    "the complete tube must be visible at the rapid reveal "
                            + "endpoint");
        }

        float[] previous = hidden.materialWindow().clone();
        for (int step = 1; step <= 64; step++) {
            float progress = DarkBallCaptureVfx.SIPHON_LEADING_EASE_START
                    + (DarkBallCaptureVfx.SIPHON_LEADING_EASE_END
                    - DarkBallCaptureVfx.SIPHON_LEADING_EASE_START)
                    * step / 64.0f;
            float[] current = DarkBallSiphonSurfaceMesh.build(
                    nodes, 0.18f, 0.23f, 0.10f,
                    progress, trailing).materialWindow();
            for (int vertex = 0; vertex < current.length; vertex++) {
                assertTrue(current[vertex]
                                >= previous[vertex] - EPSILON,
                        "leading coverage must never reverse");
            }
            previous = current;
        }
    }

    @Test
    void bodyRetiresAheadOfTheRapidRootCollarDrain() {
        float previousBody = 1.0f;
        float previousRoot = 1.0f;
        float bodyCutoffAge = Float.NaN;
        float rootCutoffAge = Float.NaN;
        for (float age = DarkBallCaptureVfx.SIPHON_COLLAPSE_START;
             age <= DarkBallCaptureVfx.BALL_ABSORB_END;
             age += 0.0005f) {
            float body = DarkBallCaptureVfx.bodyPresentationGateAt(age);
            float root = DarkBallCaptureVfx.siphonTrailingGateAt(
                    age, 0.0f);
            assertTrue(body <= root + EPSILON,
                    "the body must remain hidden beneath the root collar");
            assertTrue(body <= previousBody + EPSILON);
            assertTrue(root <= previousRoot + EPSILON);
            if (!Float.isFinite(bodyCutoffAge)
                    && body
                    < DarkBallCaptureVfx.MATERIAL_COVERAGE_DEPTH_CUTOFF) {
                bodyCutoffAge = age;
            }
            if (!Float.isFinite(rootCutoffAge)
                    && root
                    < DarkBallCaptureVfx.MATERIAL_COVERAGE_DEPTH_CUTOFF) {
                rootCutoffAge = age;
            }
            previousBody = body;
            previousRoot = root;
        }

        assertTrue(Float.isFinite(bodyCutoffAge));
        assertTrue(Float.isFinite(rootCutoffAge));
        assertTrue(rootCutoffAge - bodyCutoffAge >= 2.0f / 30.0f,
                "the opaque collar should conceal body retirement for at "
                        + "least two 30 FPS frames");
        assertFalse(DarkBallCaptureVfx.bodyPresentationRequiredAt(
                DarkBallCaptureVfx.BALL_ABSORB_END));
    }

    @Test
    void lateReleaseFrontRetiresTheMiniatureBodyUnderAnOpaqueCollar() {
        float collapseDuration =
                DarkBallCaptureVfx.SIPHON_COLLAPSE_START
                        - DarkBallCaptureVfx.SIPHON_START;
        float seventyPercentAge =
                DarkBallCaptureVfx.SIPHON_START
                        + collapseDuration * 0.70f;
        float ninetyPercentAge =
                DarkBallCaptureVfx.SIPHON_START
                        + collapseDuration * 0.90f;

        assertTrue(DarkBallCaptureVfx.bodyReleaseFrontAt(
                        seventyPercentAge)
                        < DarkBallCaptureVfx
                        .BODY_RELEASE_RETIRE_FRONT_START);
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.bodyPresentationGateAt(
                        seventyPercentAge),
                EPSILON,
                "substantial collapse must retain the full body material");

        assertTrue(DarkBallCaptureVfx.bodyReleaseFrontAt(
                        ninetyPercentAge)
                        > DarkBallCaptureVfx
                        .BODY_RELEASE_RETIRE_FRONT_START);
        assertTrue(
                DarkBallCaptureVfx.bodyEffectFadeAt(ninetyPercentAge)
                        < DarkBallCaptureVfx
                        .MATERIAL_COVERAGE_DEPTH_CUTOFF,
                "the final homothetic-looking remnant must retire before "
                        + "the phase boundary");
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.siphonTrailingGateAt(
                        ninetyPercentAge, 0.0f),
                EPSILON,
                "the opaque inlet collar must still conceal retirement");
    }

    @Test
    void splatBodyRetiresFromTheLastLocalSectionDuringInitialRetraction() {
        float previousOwnership = 1.0f;
        float splatRetirementAge = Float.NaN;
        for (int step = 0; step <= 4096; step++) {
            float age = DarkBallCaptureVfx.SIPHON_START
                    + (DarkBallCaptureVfx.BALL_ABSORB_END
                    - DarkBallCaptureVfx.SIPHON_START)
                    * step / 4096.0f;
            float ownership =
                    DarkBallCaptureVfx
                            .splatLastSectionBodyOwnershipAt(age);

            assertTrue(ownership <= previousOwnership + EPSILON,
                    "the inlet section must retire monotonically");
            assertEquals(
                    ownership
                            > DarkBallCaptureVfx
                            .SPLAT_BODY_ACTIVITY_EPSILON,
                    DarkBallCaptureVfx
                            .splatBodyPresentationRequiredAt(age));
            if (!Float.isFinite(splatRetirementAge)
                    && ownership
                    <= DarkBallCaptureVfx
                    .SPLAT_BODY_ACTIVITY_EPSILON) {
                splatRetirementAge = age;
            }
            previousOwnership = ownership;
        }

        assertTrue(Float.isFinite(splatRetirementAge));
        assertTrue(splatRetirementAge
                        > DarkBallCaptureVfx.SIPHON_COLLAPSE_START,
                "the final local section should overlap the opening beat "
                        + "of siphon retraction");
        assertTrue(splatRetirementAge
                        < DarkBallCaptureVfx.SPLAT_SINK_COMPLETE_AGE,
                "the visible retirement feather must finish before the "
                        + "release front reaches its terminal coordinate");
        assertTrue(
                DarkBallCaptureVfx.splatBodyPresentationRequiredAt(
                        DarkBallCaptureVfx.SIPHON_COLLAPSE_START));
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.siphonTrailingGateAt(
                        DarkBallCaptureVfx.SIPHON_COLLAPSE_START,
                        0.0f),
                EPSILON,
                "the siphon collar must still be intact when the overlap "
                        + "begins");
        assertTrue(
                DarkBallCaptureVfx.siphonTrailingGateAt(
                        splatRetirementAge, 0.0f) > 0.95f,
                "the opaque root collar must conceal final body retirement");
        assertEquals(
                0.0f,
                DarkBallCaptureVfx.splatLastSectionBodyOwnershipAt(
                        DarkBallCaptureVfx.BALL_ABSORB_END),
                EPSILON);
    }

    @Test
    void splatReleaseFinishesAfterAControlledRetractionOverlap() {
        assertTrue(DarkBallCaptureVfx.SPLAT_SINK_COMPLETE_AGE
                > DarkBallCaptureVfx.SIPHON_COLLAPSE_START);
        assertEquals(
                DarkBallCaptureVfx.SPLAT_RETRACTION_OVERLAP_SECONDS,
                DarkBallCaptureVfx.SPLAT_SINK_COMPLETE_AGE
                        - DarkBallCaptureVfx.SIPHON_COLLAPSE_START,
                EPSILON);
        assertTrue(
                DarkBallCaptureVfx.splatLastSectionBodyOwnershipAt(
                        DarkBallCaptureVfx.SIPHON_COLLAPSE_START)
                        > DarkBallCaptureVfx.SPLAT_BODY_ACTIVITY_EPSILON,
                "some inlet-side body ownership should survive at the "
                        + "start of retraction");
        assertEquals(
                -1.0f,
                DarkBallCaptureVfx.splatBodyReleaseFrontAt(
                        DarkBallCaptureVfx.SIPHON_START),
                EPSILON);
        assertEquals(
                1.30f,
                DarkBallCaptureVfx.splatBodyReleaseFrontAt(
                        DarkBallCaptureVfx.SPLAT_SINK_COMPLETE_AGE),
                EPSILON);
        assertEquals(
                0.0f,
                DarkBallCaptureVfx.splatLastSectionBodyOwnershipAt(
                        DarkBallCaptureVfx.SPLAT_SINK_COMPLETE_AGE),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallCaptureVfx.siphonTrailingGateAt(
                        DarkBallCaptureVfx.SIPHON_COLLAPSE_START,
                        0.0f),
                EPSILON,
                "retraction should begin from an intact root collar");
    }

    @Test
    void inletNarrowingStartsWithRetractionWithoutBreathingTheEndpoint() {
        assertEquals(1.0f,
                DarkBallCaptureVfx.siphonInletRadiusScaleAt(
                        DarkBallCaptureVfx.SIPHON_START),
                EPSILON);
        assertEquals(1.0f,
                DarkBallCaptureVfx.siphonInletRadiusScaleAt(
                        DarkBallCaptureVfx.SIPHON_COLLAPSE_START),
                EPSILON);
        assertTrue(
                DarkBallCaptureVfx.siphonInletRadiusScaleAt(
                        DarkBallCaptureVfx.SPLAT_SINK_COMPLETE_AGE)
                        < 1.0f,
                "the inlet should already be contracting by terminal "
                        + "release-front completion");
        assertEquals(
                DarkBallCaptureVfx.SIPHON_CONTRACTED_INLET_RADIUS_SCALE,
                DarkBallCaptureVfx.siphonInletRadiusScaleAt(
                        DarkBallCaptureVfx.BALL_ABSORB_END),
                EPSILON);

        float previousScale = 1.0f;
        for (int step = 0; step <= 128; step++) {
            float age = DarkBallCaptureVfx.SIPHON_COLLAPSE_START
                    + (DarkBallCaptureVfx.BALL_ABSORB_END
                    - DarkBallCaptureVfx.SIPHON_COLLAPSE_START)
                    * step / 128.0f;
            float scale =
                    DarkBallCaptureVfx.siphonInletRadiusScaleAt(age);
            assertTrue(scale <= previousScale + EPSILON);
            assertTrue(scale
                    >= DarkBallCaptureVfx
                    .SIPHON_CONTRACTED_INLET_RADIUS_SCALE - EPSILON);
            previousScale = scale;
        }

        float bodyRadius = 4.0f;
        float voxelSize = 0.01f;
        float endRadius = 0.12f;
        float fullRoot = DarkBallProjectedEffectBounds.siphonRadius(
                0.0f, bodyRadius, voxelSize, endRadius, 1.0f);
        float contractedRoot =
                DarkBallProjectedEffectBounds.siphonRadius(
                        0.0f, bodyRadius, voxelSize, endRadius,
                        DarkBallCaptureVfx
                                .SIPHON_CONTRACTED_INLET_RADIUS_SCALE);
        float fullEnd = DarkBallProjectedEffectBounds.siphonRadius(
                1.0f, bodyRadius, voxelSize, endRadius, 1.0f);
        float contractedEnd =
                DarkBallProjectedEffectBounds.siphonRadius(
                        1.0f, bodyRadius, voxelSize, endRadius,
                        DarkBallCaptureVfx
                                .SIPHON_CONTRACTED_INLET_RADIUS_SCALE);
        assertTrue(contractedRoot < fullRoot * 0.45f);
        assertEquals(fullEnd, contractedEnd, EPSILON,
                "the Dark Ball endpoint must not breathe with the inlet");
    }

    @Test
    void surfaceSplatShaderUsesThePresentationSchedule()
            throws IOException {
        String transport = resource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_surface_splat.vsh")
                .replaceAll("\\s+", " ");

        assertTrue(transport.contains(
                "uniform float ReleaseFront;"));
        assertTrue(transport.contains(
                "float remaining = releaseRemaining(Color.g);"));
        assertTrue(transport.contains(
                "float directProgress = saturate(localPathProgress);"));
    }

    private static Vector3f[] straightNodes() {
        Vector3f[] nodes =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        for (int node = 0; node < nodes.length; node++) {
            nodes[node] = new Vector3f(node * 0.45f, 0.0f, 0.0f);
        }
        return nodes;
    }

    private static float[] filled(int count, float value) {
        float[] result = new float[count];
        for (int index = 0; index < result.length; index++) {
            result[index] = value;
        }
        return result;
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream =
                     DarkBallPresentationScheduleTest.class
                             .getClassLoader()
                             .getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
