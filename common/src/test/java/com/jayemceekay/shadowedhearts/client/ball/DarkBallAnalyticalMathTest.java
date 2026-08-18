package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallAnalyticalMathTest {
    @Test
    void turbulenceReachesFullStrengthBeforeLongPreDepletionHold() {
        assertEquals(DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL,
                DarkBallCaptureVfx.TURBULENCE_FULL, 0.000001f);
        assertEquals(DarkBallCaptureVfx.VFX_END * 0.15f,
                DarkBallCaptureVfx.TURBULENCE_FULL
                - DarkBallCaptureVfx.TURBULENCE_START, 0.000001f);
        assertEquals(DarkBallCaptureVfx.VFX_END * 0.25f,
                DarkBallCaptureVfx.SIPHON_START
                - DarkBallCaptureVfx.TURBULENCE_FULL, 0.000001f);
        assertEquals(0.0f, DarkBallCaptureVfx.turbulenceBlendAt(
                DarkBallCaptureVfx.TURBULENCE_START), 0.000001f);
        assertEquals(1.0f, DarkBallCaptureVfx.turbulenceBlendAt(
                DarkBallCaptureVfx.TURBULENCE_FULL), 0.000001f);
        float rampMidpoint = (DarkBallCaptureVfx.TURBULENCE_START
                + DarkBallCaptureVfx.TURBULENCE_FULL) * 0.5f;
        assertEquals(0.25f, DarkBallCaptureVfx.turbulenceBlendAt(
                rampMidpoint), 0.000001f);
        assertEquals(0.0f,
                DarkBallCaptureVfx.turbulenceComplexityBlendAt(
                        DarkBallCaptureVfx.TURBULENCE_START),
                0.000001f);
        assertEquals(0.5f,
                DarkBallCaptureVfx.turbulenceComplexityBlendAt(rampMidpoint),
                0.000001f);
        assertEquals(1.0f,
                DarkBallCaptureVfx.turbulenceComplexityBlendAt(
                        DarkBallCaptureVfx.TURBULENCE_FULL),
                0.000001f);
        assertEquals(1.0f,
                DarkBallCaptureVfx.turbulenceComplexityBlendAt(
                        DarkBallCaptureVfx.SIPHON_START),
                0.000001f);
        assertEquals(DarkBallCaptureVfx.VFX_END * 0.97f,
                DarkBallCaptureVfx.SIPHON_END, 0.000001f);
        assertEquals(DarkBallCaptureVfx.VFX_END * 0.99f,
                DarkBallCaptureVfx.BALL_ABSORB_END,
                0.000001f);
    }

    @Test
    void frozenModelCrossfadeOverlapsTheLongExpansionRamp() {
        assertEquals(0.0f, DarkBallCaptureVfx.silhouetteHandoffBlendAt(
                DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_START), 0.000001f);
        assertEquals(1.0f, DarkBallCaptureVfx.silhouetteHandoffBlendAt(
                DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END), 0.000001f);
        assertTrue(DarkBallCaptureVfx.TURBULENCE_START >= 0.0f);
        assertTrue(DarkBallCaptureVfx.TURBULENCE_START
                > DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_START);
        assertTrue(DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END
                < DarkBallCaptureVfx.TURBULENCE_FULL);

        float expansionAtHandoffStart = DarkBallCaptureVfx.turbulenceBlendAt(
                DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_START);
        float expansionAtHandoffEnd = DarkBallCaptureVfx.turbulenceBlendAt(
                DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END);
        assertEquals(0.0f, expansionAtHandoffStart, 0.000001f);
        assertTrue(expansionAtHandoffEnd > expansionAtHandoffStart);
        assertTrue(expansionAtHandoffEnd < 1.0f);

        float previous = 0.0f;
        for (int step = 0; step <= 32; step++) {
            float age = DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_START
                    + (DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END
                    - DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_START)
                    * step / 32.0f;
            float blend = DarkBallCaptureVfx.silhouetteHandoffBlendAt(age);
            assertTrue(blend >= previous - 0.000001f,
                    "the frozen-model handoff must be monotonic");
            previous = blend;
        }
    }

    @Test
    void signedBodyOwnershipUsesADedicatedVisibleExpansionRamp() {
        float crossfadeMidpoint =
                (DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_START
                        + DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END)
                        * 0.5f;
        assertEquals(0.0f, DarkBallCaptureVfx.signedBodyAuthorityAt(
                crossfadeMidpoint), 0.000001f);
        assertEquals(0.0f, DarkBallCaptureVfx.signedBodyAuthorityAt(
                DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END), 0.000001f);
        assertTrue(DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL
                > DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END);
        assertEquals(DarkBallCaptureVfx.VFX_END * 0.11f,
                DarkBallCaptureVfx.VISIBLE_EXPANSION_RAMP_SECONDS,
                0.000001f);
        assertTrue(DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL
                < DarkBallCaptureVfx.SIPHON_START);

        float authorityRampMidpoint =
                (DarkBallCaptureVfx.FROZEN_MODEL_CROSSFADE_END
                        + DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL) * 0.5f;
        assertEquals(0.5f, DarkBallCaptureVfx.signedBodyAuthorityAt(
                authorityRampMidpoint), 0.000001f);
        assertEquals(1.0f, DarkBallCaptureVfx.signedBodyAuthorityAt(
                DarkBallCaptureVfx.VISIBLE_EXPANSION_FULL), 0.000001f);
    }

    @Test
    void siphonDrainStartsWithANarrowBodyDepletionOverlap() {
        assertEquals(DarkBallCaptureVfx.VFX_END * 0.075f,
                DarkBallCaptureVfx.SIPHON_END
                - DarkBallCaptureVfx.SIPHON_COLLAPSE_START, 0.000001f);
        assertEquals(0.0f, DarkBallCaptureVfx.finalCollapseAt(
                DarkBallCaptureVfx.SIPHON_COLLAPSE_START), 0.000001f);
        assertTrue(DarkBallCaptureVfx.finalCollapseAt(
                DarkBallCaptureVfx.SIPHON_END) > 0.0f);
        assertEquals(1.0f, DarkBallCaptureVfx.finalCollapseAt(
                DarkBallCaptureVfx.BALL_ABSORB_END), 0.000001f);

        float siphonAtOverlapStart =
                (DarkBallCaptureVfx.SIPHON_COLLAPSE_START
                        - DarkBallCaptureVfx.SIPHON_START)
                        / (DarkBallCaptureVfx.SIPHON_END
                        - DarkBallCaptureVfx.SIPHON_START);
        assertTrue(DarkBallAdvectedDensityField.releaseRemaining(
                1.0f, siphonAtOverlapStart, 0.0f) > 0.5f,
                "the trailing drain must begin while inlet-side body material remains");

        float bodyFinishAge = Float.NaN;
        float visibleDrainStartAge = Float.NaN;
        for (float sampleAge = DarkBallCaptureVfx.SIPHON_COLLAPSE_START;
             sampleAge <= DarkBallCaptureVfx.SIPHON_END;
             sampleAge += 0.001f) {
            if (!Float.isFinite(visibleDrainStartAge)
                    && DarkBallCaptureVfx.siphonTrailingGateAt(
                    sampleAge, 0.0f) < 0.99f) {
                visibleDrainStartAge = sampleAge;
            }
            if (!Float.isFinite(bodyFinishAge)
                    && DarkBallCaptureVfx.bodyReleaseFrontAt(sampleAge) >= 1.0f) {
                bodyFinishAge = sampleAge;
            }
        }
        assertTrue(Float.isFinite(bodyFinishAge)
                && Float.isFinite(visibleDrainStartAge));
        float visibleOverlap = bodyFinishAge - visibleDrainStartAge;
        float timingScaleFromTenSeconds =
                DarkBallCaptureVfx.VFX_END / 10.0f;
        assertTrue(visibleOverlap
                        >= 0.03f * timingScaleFromTenSeconds
                        && visibleOverlap
                        <= 0.09f * timingScaleFromTenSeconds,
                "expected a narrow visible overlap, got " + visibleOverlap);
        assertTrue(DarkBallCaptureVfx.siphonTrailingGateAt(
                bodyFinishAge, 0.0f) < 0.95f,
                "the root should already be visibly draining at body completion");
        float previousBodyRetractionGate = 1.0f;
        for (int step = 0; step <= 64; step++) {
            float age = DarkBallCaptureVfx.SIPHON_COLLAPSE_START
                    + (DarkBallCaptureVfx.BALL_ABSORB_END
                    - DarkBallCaptureVfx.SIPHON_COLLAPSE_START)
                    * step / 64.0f;
            float bodyRetractionGate =
                    DarkBallCaptureVfx.siphonTrailingGateAt(age, 0.0f);
            assertTrue(bodyRetractionGate
                            <= previousBodyRetractionGate + 0.000001f,
                    "body retirement must follow the root trailing gate");
            previousBodyRetractionGate = bodyRetractionGate;
        }
        assertEquals(0.0f, DarkBallCaptureVfx.siphonTrailingGateAt(
                DarkBallCaptureVfx.BALL_ABSORB_END, 0.0f), 0.000001f);

        float previousRemaining = 1.0f;
        for (int step = 0; step <= 64; step++) {
            float age = DarkBallCaptureVfx.SIPHON_COLLAPSE_START
                    + (DarkBallCaptureVfx.BALL_ABSORB_END
                    - DarkBallCaptureVfx.SIPHON_COLLAPSE_START)
                    * step / 64.0f;
            float siphon = Math.max(0.0f, Math.min(1.0f,
                    (age - DarkBallCaptureVfx.SIPHON_START)
                            / (DarkBallCaptureVfx.SIPHON_END
                            - DarkBallCaptureVfx.SIPHON_START)));
            float remaining = DarkBallAdvectedDensityField.releaseRemaining(
                    1.0f, siphon, DarkBallCaptureVfx.finalCollapseAt(age));
            assertTrue(remaining <= previousRemaining + 0.000001f,
                    "overlap must not restore depleted inlet material");
            previousRemaining = remaining;
        }
    }

    @Test
    void siphonTrailingRetractionSpansTheLateDrainAndFinishesBeforeFade() {
        assertEquals(0.96f,
                DarkBallCaptureVfx.SIPHON_TRAILING_EASE_END,
                0.000001f);

        float drainDuration =
                DarkBallCaptureVfx.BALL_ABSORB_END
                        - DarkBallCaptureVfx.SIPHON_COLLAPSE_START;
        float midDrainAge =
                DarkBallCaptureVfx.SIPHON_COLLAPSE_START
                        + drainDuration * 0.50f;
        float lateDrainAge =
                DarkBallCaptureVfx.SIPHON_COLLAPSE_START
                        + drainDuration * 0.80f;

        assertEquals(0.0f,
                DarkBallCaptureVfx.siphonTrailingGateAt(
                        midDrainAge, 0.0f),
                0.000001f,
                "the inlet should still retract with a rapid root-side snap");
        assertTrue(DarkBallCaptureVfx.siphonTrailingGateAt(
                        midDrainAge, 1.0f) > 0.99f,
                "the distal tube must remain visible at mid drain");
        assertTrue(DarkBallCaptureVfx.siphonTrailingGateAt(
                        lateDrainAge, 1.0f)
                        > DarkBallCaptureVfx.MATERIAL_COVERAGE_DEPTH_CUTOFF,
                "the siphon must remain materially visible at 80% drain");
        assertEquals(0.0f,
                DarkBallCaptureVfx.siphonTrailingGateAt(
                        DarkBallCaptureVfx.BALL_ABSORB_END, 1.0f),
                0.000001f,
                "the trailing tip must retire before the final fade");
    }

    @Test
    void bodyCollapseEnvelopeRemainsApproximateAndMonotonic() {
        assertEquals(1.0f,
                DarkBallCaptureVfx.bodyCollapseEnvelopeScale(0.0f),
                0.000001f);
        assertEquals(1.0f,
                DarkBallCaptureVfx.bodyCollapseEnvelopeScale(0.02f),
                0.000001f);
        assertEquals(DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN,
                DarkBallCaptureVfx.bodyCollapseEnvelopeScale(0.98f),
                0.000001f);
        assertEquals(DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN,
                DarkBallCaptureVfx.bodyCollapseEnvelopeScale(1.0f),
                0.000001f);
        assertEquals(1.0f,
                DarkBallCaptureVfx.bodyCollapseEnvelopeScaleAt(
                DarkBallCaptureVfx.SIPHON_START), 0.000001f);
        assertEquals(DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN,
                DarkBallCaptureVfx.bodyCollapseEnvelopeScaleAt(
                        DarkBallCaptureVfx.SIPHON_END), 0.000001f);

        float midpointFront = 0.5f * 1.16f - 0.045f;
        float expectedMidpointEnvelope = 1.0f
                - (1.0f
                - DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN)
                * midpointFront;
        assertEquals(expectedMidpointEnvelope,
                DarkBallCaptureVfx.bodyCollapseEnvelopeScale(0.5f),
                0.000001f);

        float previousEnvelope = 1.0f;
        for (int step = 0; step <= 64; step++) {
            float progress = step / 64.0f;
            float envelope =
                    DarkBallCaptureVfx.bodyCollapseEnvelopeScale(progress);
            assertTrue(envelope <= previousEnvelope + 0.000001f,
                    "collapse envelope must be monotonic");
            assertTrue(envelope
                    >= DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN
                    - 0.000001f);
            previousEnvelope = envelope;
        }
    }

    @Test
    void bodyCollapseInverseWarpRoundTripsWithoutFoldingOrExpansion() {
        float retainedSpan = DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN;
        float releaseFront = 0.65f;
        assertTrue(forwardCollapseOrder(0.0f, releaseFront) > 0.0f,
                "the farthest layer must move first");
        assertEquals(releaseFront,
                forwardCollapseOrder(releaseFront, releaseFront),
                0.000001f,
                "the arriving front remains the anchored leading layer");
        assertEquals(0.80f,
                forwardCollapseOrder(0.80f, releaseFront),
                0.000001f,
                "nearer layers must wait for the release front");

        for (int frontStep = 0; frontStep <= 20; frontStep++) {
            float front = frontStep / 20.0f;
            float previousDisplayedOrder = -Float.MAX_VALUE;
            for (int orderStep = 0; orderStep <= 128; orderStep++) {
                float sourceOrder = orderStep / 128.0f;
                float displayedOrder =
                        forwardCollapseOrder(sourceOrder, front);
                float recoveredSourceOrder =
                        inverseCollapseSourceOrder(displayedOrder, front);
                float compressionScale =
                        collapseCompressionScaleForSourceOrder(
                                recoveredSourceOrder, front);

                assertEquals(sourceOrder, recoveredSourceOrder, 0.00002f,
                        "the inverse order map must recover its source layer");
                assertTrue(displayedOrder
                                >= previousDisplayedOrder - 0.000001f,
                        "the forward order map must not fold");
                assertTrue(Float.isFinite(compressionScale));
                assertTrue(compressionScale >= retainedSpan - 0.000001f);
                assertTrue(compressionScale <= 1.0f + 0.000001f);

                float displayedRadius = 1.0f - displayedOrder;
                float sourceRadius = 1.0f - sourceOrder;
                if (sourceRadius > 0.0001f) {
                    assertEquals(sourceRadius,
                            displayedRadius / compressionScale,
                            0.00002f,
                            "inverse radial warp must reconstruct the source");
                    assertTrue(displayedRadius / compressionScale
                                    >= displayedRadius - 0.000001f,
                            "inverse lookup must never expand geometry");
                }
                previousDisplayedOrder = displayedOrder;
            }
        }

        float outerOrder = 0.20f;
        float innerOrder = 0.35f;
        float outerDisplayed =
                forwardCollapseOrder(outerOrder, releaseFront);
        float innerDisplayed =
                forwardCollapseOrder(innerOrder, releaseFront);
        assertEquals((innerOrder - outerOrder)
                        * retainedSpan,
                innerDisplayed - outerDisplayed, 0.000001f,
                "released neighbor spacing should compress to eight percent");

        assertEquals(0.402f, localCollapseScale(0.0f, releaseFront),
                0.000001f);
        assertEquals(0.4825f, localCollapseScale(0.20f, releaseFront),
                0.000001f);
        assertEquals(0.724f, localCollapseScale(0.50f, releaseFront),
                0.000001f);
        assertEquals(1.0f, localCollapseScale(releaseFront, releaseFront),
                0.000001f);

        for (float sourceOrder : new float[]{0.0f, 0.25f, 0.50f, 0.75f}) {
            assertEquals(retainedSpan,
                    localCollapseScale(sourceOrder, 1.0f),
                    0.000002f,
                    "terminal released layers retain eight percent");
        }
    }

    @Test
    void bodyCollapseInverseWarpExtrapolatesOutsideCompressedBand() {
        float releaseFront = 0.65f;
        float displayedOrder = 0.30f;
        float sourceOrder = inverseCollapseSourceOrder(
                displayedOrder, releaseFront);
        float compressionScale = collapseCompressionScaleForSourceOrder(
                sourceOrder, releaseFront);

        assertEquals(-3.725f, sourceOrder, 0.00001f,
                "empty outer interval must extrapolate beyond the source");
        assertEquals(0.14814815f, compressionScale, 0.00001f);
        assertTrue((1.0f - displayedOrder) / compressionScale > 1.0f,
                "outer samples must look beyond the body instead of clamping"
                        + " onto and stretching its farthest layer");
    }

    @Test
    void inverseVortonAxialStrainCannotFold() {
        float halfLength = 0.73f;
        for (int phaseStep = -10; phaseStep <= 10; phaseStep++) {
            float signedStrain = halfLength * 0.22f * phaseStep / 10.0f;
            for (int axialStep = -40; axialStep <= 40; axialStep++) {
                float axial = halfLength * axialStep / 10.0f;
                float denominator = (float) Math.pow(
                        axial * axial + halfLength * halfLength, 1.5);
                float derivative = 1.0f - signedStrain
                        * halfLength * halfLength / denominator;
                assertTrue(derivative >= 0.78f - 0.000001f);
            }
        }
    }

    @Test
    void qualityProfilesMatchDesignBudgets() {
        assertEquals(2.0f, DarkBallVfxQuality.LOW.shellWidthVoxels());
        assertEquals(2, DarkBallVfxQuality.LOW.vortexCells());
        assertEquals(3.0f, DarkBallVfxQuality.MEDIUM.shellWidthVoxels());
        assertEquals(3, DarkBallVfxQuality.MEDIUM.vortexCells());
        assertEquals(4.5f, DarkBallVfxQuality.HIGH.shellWidthVoxels());
        assertEquals(4, DarkBallVfxQuality.HIGH.vortexCells());
        assertEquals(DarkBallVfxQuality.MEDIUM, DarkBallVfxQuality.parse("invalid"));
    }

    @Test
    void cohesiveClosingBridgesNarrowGapsWithoutBroadDilation() {
        int voxelCount = DarkBallVolumeGrid.X_SLICES
                * DarkBallVolumeGrid.SLICE_SIZE
                * DarkBallVolumeGrid.SLICE_SIZE;
        boolean[] source = new boolean[voxelCount];
        source[DarkBallVolumeVoxelizer.voxelIndex(0, 0, 0)] = true;
        for (int x = 44; x <= 51; x++) {
            for (int z = 28; z <= 35; z++) {
                for (int y = 25; y <= 33; y++) {
                    if (y != 29) {
                        source[DarkBallVolumeVoxelizer.voxelIndex(x, y, z)] = true;
                    }
                }
            }
        }

        boolean[] closed = DarkBallVolumeVoxelizer.closeVoxels(source, 2);

        assertTrue(closed[DarkBallVolumeVoxelizer.voxelIndex(48, 29, 31)],
                "two-voxel closing should bridge a one-voxel silhouette gap");
        assertTrue(!closed[DarkBallVolumeVoxelizer.voxelIndex(48, 20, 31)],
                "closing must not become an unrestricted exterior dilation");
        for (int i = 0; i < source.length; i++) {
            if (source[i]) {
                assertTrue(closed[i], "closing must retain original material at " + i);
            }
        }
    }

    @Test
    void bezierEndpointsAndTangentsStayContinuous() {
        Vector3f root = new Vector3f(0.25f, -0.15f, 0.12f);
        Vector3f intake = new Vector3f(4.5f, 0.2f, -0.08f);
        float radius = 1.1f;

        Vector3f start = DarkBallCaptureMath.siphonCurvePoint(root, intake, radius, 0.0f);
        Vector3f end = DarkBallCaptureMath.siphonCurvePoint(root, intake, radius, 1.0f);
        assertTrue(start.distance(root) < 0.00001f);
        assertTrue(end.distance(intake) < 0.00001f);

        Vector3f previous = start;
        for (int i = 1; i <= 32; i++) {
            float t = i / 32.0f;
            Vector3f point = DarkBallCaptureMath.siphonCurvePoint(root, intake, radius, t);
            Vector3f tangent = DarkBallCaptureMath.siphonCurveTangent(root, intake, radius, t);
            assertTrue(tangent.lengthSquared() > 0.000001f);
            assertTrue(point.distance(previous) > 0.0f);
            previous = point;
        }
    }

    @Test
    void siphonSinkSupportCannotGrowPastTheDarkBall() {
        assertEquals(DarkBallCaptureMath.SIPHON_SINK_RADIUS,
                DarkBallCaptureMath.siphonEndRadius(),
                0.000001f);

        assertEquals(1.0f, DarkBallCaptureMath.siphonSinkGate(
                -DarkBallCaptureMath.SIPHON_SINK_FEATHER), 0.000001f);
        float feathered = DarkBallCaptureMath.siphonSinkGate(
                -DarkBallCaptureMath.SIPHON_SINK_FEATHER * 0.5f);
        assertTrue(feathered > 0.0f && feathered < 1.0f);
        assertEquals(0.0f, DarkBallCaptureMath.siphonSinkGate(0.0f),
                0.000001f);
        assertEquals(0.0f, DarkBallCaptureMath.siphonSinkGate(4.0f),
                0.000001f);

        Vector3f sink = new Vector3f(2.0f, 0.4f, -0.2f);
        Vector3f terminalDirection = new Vector3f(0.8f, -0.3f, 0.1f).normalize();
        Vector3f beforeSink = new Vector3f(sink).fma(
                -DarkBallCaptureMath.SIPHON_SINK_FEATHER, terminalDirection);
        Vector3f pastSink = new Vector3f(sink).fma(0.05f, terminalDirection);
        assertEquals(1.0f, DarkBallCaptureMath.siphonSinkGate(
                new Vector3f(beforeSink).sub(sink).dot(terminalDirection)),
                0.000001f);
        assertEquals(0.0f, DarkBallCaptureMath.siphonSinkGate(
                new Vector3f(pastSink).sub(sink).dot(terminalDirection)),
                0.000001f);
    }

    @Test
    void analyticalAtlasLayoutHasOneTexelPerCell() {
        assertEquals(DarkBallVolumeGrid.X_SLICES, DarkBallAnalyticalVolume.X_SIZE);
        assertEquals(DarkBallVolumeGrid.SLICE_SIZE, DarkBallAnalyticalVolume.Y_SIZE);
        assertEquals(DarkBallVolumeGrid.SLICE_SIZE, DarkBallAnalyticalVolume.Z_SIZE);
        assertEquals(96 * 64, DarkBallAnalyticalVolume.ATLAS_WIDTH);
        assertEquals(64, DarkBallAnalyticalVolume.ATLAS_HEIGHT);
        assertEquals(DarkBallAnalyticalVolume.CELL_COUNT,
                DarkBallAnalyticalVolume.ATLAS_WIDTH * DarkBallAnalyticalVolume.ATLAS_HEIGHT);
    }

    @Test
    void siphonPlasmaPathEvolvesContinuouslyWithPinnedShortCollars() {
        Vector3f root = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f controlA = new Vector3f(1.0f, 0.55f, 0.15f);
        Vector3f controlB = new Vector3f(3.0f, -0.35f, 0.10f);
        Vector3f end = new Vector3f(4.0f, 0.0f, 0.0f);
        float bodyRadius = 1.0f;

        Vector3f[] initial =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        Vector3f[] repeated =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        Vector3f[] previousFrame =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        Vector3f[] nextFrame =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        Vector3f[] later =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        float sampleTime = 1.25f;
        float frameStep = 1.0f / 480.0f;
        DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                bodyRadius, sampleTime, initial);
        DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                bodyRadius, sampleTime, repeated);
        DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                bodyRadius, sampleTime - frameStep, previousFrame);
        DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                bodyRadius, sampleTime + frameStep, nextFrame);
        DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                bodyRadius, sampleTime + 0.125f, later);

        assertTrue(initial[0].distance(root) < 0.000001f);
        assertTrue(initial[DarkBallSiphonBoltPath.NODE_COUNT - 1]
                .distance(end) < 0.000001f);
        float expectedCollarLength = root.distance(end)
                * DarkBallSiphonBoltPath.COLLAR_CHORD_FRACTION;
        assertEquals(expectedCollarLength, initial[1].distance(root),
                0.000001f);
        assertEquals(expectedCollarLength,
                initial[DarkBallSiphonBoltPath.NODE_COUNT - 2]
                        .distance(end),
                0.000001f);

        boolean shortIntervalMoved = false;
        boolean longIntervalMoved = false;
        boolean nonRigidMotion = false;
        Vector3f firstMotion = null;
        for (int node = 0;
             node < DarkBallSiphonBoltPath.NODE_COUNT;
             node++) {
            assertTrue(Float.isFinite(initial[node].x)
                            && Float.isFinite(initial[node].y)
                            && Float.isFinite(initial[node].z),
                    "plasma node must remain finite at " + node);
            assertTrue(initial[node].distance(repeated[node]) < 0.000001f,
                    "identical inputs must be deterministic at node " + node);
            if (node >= 2
                    && node <= DarkBallSiphonBoltPath.NODE_COUNT - 3) {
                float shortMotion = initial[node].distance(nextFrame[node]);
                float longMotion = initial[node].distance(later[node]);
                assertTrue(shortMotion
                                < DarkBallSiphonBoltPath.bendAmplitude(
                                bodyRadius, root.distance(end)) * 0.16f,
                        "continuous plasma motion jumped at node " + node);
                Vector3f secondDifference = new Vector3f(nextFrame[node])
                        .sub(new Vector3f(initial[node]).mul(2.0f))
                        .add(previousFrame[node]);
                float acceleration = secondDifference.length()
                        / (frameStep * frameStep);
                assertTrue(acceleration < bodyRadius * 40.0f,
                        "gradient-noise acceleration jumped at node " + node
                                + ": " + acceleration);
                shortIntervalMoved |= shortMotion > 0.000001f;
                longIntervalMoved |= longMotion > 0.001f;
                Vector3f motion = new Vector3f(nextFrame[node])
                        .sub(initial[node]);
                if (firstMotion == null) {
                    firstMotion = motion;
                } else {
                    nonRigidMotion |= motion.distance(firstMotion)
                            > 0.000001f;
                }
            }
        }
        assertTrue(shortIntervalMoved,
                "continuous plasma motion must not contain held time steps");
        assertTrue(longIntervalMoved,
                "the centerline must keep evolving over visible intervals");
        assertTrue(nonRigidMotion,
                "multi-octave deformation must not reduce to rigid translation");

        for (float time : new float[]{
                0.137f, 0.511f, 1.003f, 1.789f, 4.321f}) {
            DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                    bodyRadius, time, initial);
            DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                    bodyRadius, time + frameStep, nextFrame);
            boolean moved = false;
            for (int node = 2;
                 node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
                 node++) {
                moved |= initial[node].distance(nextFrame[node]) > 0.0000001f;
            }
            assertTrue(moved,
                    "continuous gradient noise must not hold at time " + time);
        }
    }

    @Test
    void siphonPlasmaPathUsesBoundedDomainWarpedGradientNoise()
            throws ReflectiveOperationException {
        assertArrayEquals(new float[]{0.70f, 1.30f, 2.05f},
                privateFloatArray("BROAD_PATH_SCALES"), 0.000001f);
        assertArrayEquals(new float[]{0.60f, 0.99f, 1.40f},
                privateFloatArray("BROAD_TIME_SCALES"), 0.000001f);
        assertArrayEquals(new float[]{0.56f, 0.29f, 0.15f},
                privateFloatArray("BROAD_WEIGHTS"), 0.000001f);
        assertArrayEquals(new float[]{2.40f, 3.25f, 4.20f},
                privateFloatArray("FINE_PATH_SCALES"), 0.000001f);
        assertArrayEquals(new float[]{1.53f, 2.15f, 2.82f},
                privateFloatArray("FINE_TIME_SCALES"), 0.000001f);
        assertArrayEquals(new float[]{0.28f, 0.44f, 0.28f},
                privateFloatArray("FINE_WEIGHTS"), 0.000001f);
        assertEquals(0.55f, privateFloat("BROAD_DISPLACEMENT_WEIGHT"),
                0.000001f);
        assertEquals(0.84f, privateFloat("FINE_DISPLACEMENT_WEIGHT"),
                0.000001f);
        assertEquals(2.10f, privateFloat("FINE_CONTRAST_GAIN"),
                0.000001f);
        assertEquals(1.55f, privateFloat("GRADIENT_NOISE_GAIN"),
                0.000001f);
        assertEquals(0.040f, privateFloat("PATH_DOMAIN_WARP_STRENGTH"),
                0.000001f);
        assertEquals(0.070f, privateFloat("TIME_DOMAIN_WARP_STRENGTH"),
                0.000001f);
        assertEquals(0.47f, privateFloat("PATH_DOMAIN_WARP_TIME_SCALE"),
                0.000001f);
        assertEquals(0.36f, privateFloat("TIME_DOMAIN_WARP_TIME_SCALE"),
                0.000001f);
        assertEquals(0.010f, privateFloat("LONGITUDINAL_JITTER_FRACTION"),
                0.000001f);
        assertEquals(0.12f, privateFloat("ENDPOINT_FADE_FRACTION"),
                0.000001f);

        assertTrue(hasDeclaredMethod("gradientNoiseBand"));
        assertTrue(hasDeclaredMethod("domainWarp"));
        assertTrue(hasDeclaredMethod("gradientNoise2d"));
        assertTrue(hasDeclaredMethod("gradientDot"));
        assertTrue(hasDeclaredMethod("latticeHash"));
        assertTrue(hasDeclaredMethod("boundedContrast"));
        assertFalse(hasDeclaredMethod("waveField"),
                "traveling sine-band generator must stay removed");

        float previousContrast = -1.0f;
        for (float value : new float[]{
                -4.0f, -1.0f, -0.25f, 0.0f, 0.25f, 1.0f, 4.0f}) {
            float contrasted = invokeBoundedContrast(
                    value, privateFloat("FINE_CONTRAST_GAIN"));
            assertTrue(contrasted > previousContrast,
                    "fine contrast must remain smooth and monotonic");
            assertTrue(Math.abs(contrasted) <= 1.0f,
                    "fine contrast must remain bounded");
            assertEquals(-contrasted, invokeBoundedContrast(
                    -value, privateFloat("FINE_CONTRAST_GAIN")), 0.000001f,
                    "fine contrast should not bias either transverse direction");
            previousContrast = contrasted;
        }

        float collar = DarkBallSiphonBoltPath.COLLAR_CHORD_FRACTION;
        float fade = privateFloat("ENDPOINT_FADE_FRACTION");
        assertEquals(0.0f, invokeEndpointEnvelope(collar), 0.000001f);
        assertEquals(0.5f,
                invokeEndpointEnvelope(collar + fade * 0.5f), 0.000001f);
        assertEquals(1.0f,
                invokeEndpointEnvelope(collar + fade), 0.000001f);
        assertEquals(0.5f,
                invokeEndpointEnvelope(1.0f - collar - fade * 0.5f),
                0.000001f);
        assertEquals(0.0f,
                invokeEndpointEnvelope(1.0f - collar), 0.000001f);

        Vector3f root = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f controlA = new Vector3f(10.0f / 3.0f, 0.0f, 0.0f);
        Vector3f controlB = new Vector3f(20.0f / 3.0f, 0.0f, 0.0f);
        Vector3f end = new Vector3f(10.0f, 0.0f, 0.0f);
        float bodyRadius = 1.0f;
        float radialCap = DarkBallSiphonBoltPath.bendAmplitude(
                bodyRadius, root.distance(end));
        Vector3f[] nodes =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        for (float time : new float[]{0.0f, 0.37f, 1.25f, 3.91f, 12.75f}) {
            DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                    bodyRadius, time, nodes);
            float previousT = -1.0f;
            for (int node = 0; node < nodes.length; node++) {
                float pathT = nodes[node].x / end.x;
                assertTrue(pathT > previousT,
                        "longitudinal noise reversed node order at " + node);
                previousT = pathT;
                if (node >= 2 && node <= nodes.length - 3) {
                    float baseT = node / (nodes.length - 1.0f);
                    assertTrue(Math.abs(pathT - baseT) <= 0.010001f,
                            "longitudinal noise exceeded its 1% bound");
                    float radial = (float) Math.hypot(
                            nodes[node].y, nodes[node].z);
                    assertTrue(radial < radialCap,
                            "radial noise exceeded its asymptotic cap");
                }
            }
        }
    }

    @Test
    void siphonFineNoiseProducesReadableButPrismSafeZigzags() {
        Vector3f root = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f controlA = new Vector3f(10.0f / 3.0f, 0.0f, 0.0f);
        Vector3f controlB = new Vector3f(20.0f / 3.0f, 0.0f, 0.0f);
        Vector3f end = new Vector3f(10.0f, 0.0f, 0.0f);
        Vector3f[] nodes =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        float turnSum = 0.0f;
        float maximumTurn = 0.0f;
        float chordCappedMaximumTurn = 0.0f;
        int turnCount = 0;
        int decisiveTurns = 0;
        int alternatingTurns = 0;
        int alternatingPairs = 0;

        for (int sample = 0; sample <= 80; sample++) {
            float time = sample * 0.0375f;
            DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                    1.0f, time, nodes);
            Vector3f previousCurvature = null;
            for (int node = 3; node <= nodes.length - 4; node++) {
                float turn = turnRadians(
                        nodes[node - 1], nodes[node], nodes[node + 1]);
                turnSum += turn;
                maximumTurn = Math.max(maximumTurn, turn);
                turnCount++;
                decisiveTurns += turn >= Math.toRadians(6.0) ? 1 : 0;

                Vector3f curvature = new Vector3f(nodes[node + 1])
                        .sub(new Vector3f(nodes[node]).mul(2.0f))
                        .add(nodes[node - 1]);
                if (previousCurvature != null
                        && previousCurvature.lengthSquared() > 0.000001f
                        && curvature.lengthSquared() > 0.000001f) {
                    alternatingPairs++;
                    alternatingTurns += previousCurvature.dot(curvature) < 0.0f
                            ? 1 : 0;
                }
                previousCurvature = curvature;
            }

            DarkBallSiphonBoltPath.populate(root, controlA, controlB, end,
                    100.0f, time, nodes);
            for (int node = 3; node <= nodes.length - 4; node++) {
                chordCappedMaximumTurn = Math.max(
                        chordCappedMaximumTurn,
                        turnRadians(nodes[node - 1],
                                nodes[node], nodes[node + 1]));
            }
        }

        float averageTurn = turnSum / turnCount;
        float decisiveRatio = decisiveTurns / (float) turnCount;
        float alternatingRatio =
                alternatingTurns / (float) alternatingPairs;
        assertTrue(averageTurn > Math.toRadians(6.5),
                "fine noise needs a readable average elbow");
        assertTrue(maximumTurn > Math.toRadians(17.0),
                "fine noise needs occasional decisive elbows");
        assertTrue(decisiveRatio > 0.52f,
                "most sampled elbows should exceed six degrees");
        assertTrue(alternatingRatio > 0.42f,
                "curvature should reverse often enough to read as a zigzag");
        assertTrue(maximumTurn < Math.toRadians(25.0),
                "body-scaled bends must remain controlled");
        assertTrue(chordCappedMaximumTurn < Math.toRadians(40.0),
                "chord-capped bends must remain safe for prism overlap");
    }

    @Test
    void exactMaskPassRepairsOnlyBoundedInteriorPunctures()
            throws IOException {
        String edgeShader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/dark_ball_edge_tongues.fsh");
        String edgeJson = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/dark_ball_edge_tongues.json");

        assertTrue(edgeShader.contains("uniform int EdgeTonguesEnabled;"));
        assertTrue(edgeJson.contains("\"name\": \"EdgeTonguesEnabled\""));
        assertTrue(edgeShader.contains(
                "uniform float SignedBodyAuthority;"));
        assertTrue(edgeJson.contains(
                "\"name\": \"SignedBodyAuthority\""));
        assertTrue(edgeShader.contains(
                "uniform float PreCollapseInteriorGuard;"));
        assertTrue(edgeJson.contains(
                "\"name\": \"PreCollapseInteriorGuard\""));
        assertTrue(edgeShader.contains(
                "uniform sampler2D SurfelFrontDepthSampler;"));
        assertTrue(edgeJson.contains(
                "\"name\": \"SurfelFrontDepthSampler\""));
        assertTrue(edgeShader.contains(
                "uniform int SurfelFrontDepthAvailable;"));
        assertTrue(edgeJson.contains(
                "\"name\": \"SurfelFrontDepthAvailable\""));
        assertTrue(edgeShader.contains(
                "float resolvedDeformedDepth("));
        assertTrue(edgeShader.contains(
                "float depthSeparation = abs(surfelDepth - proxyDepth);"));
        assertTrue(edgeShader.contains(
                "return mix(proxyDepth, surfelDepth, depthBlend);"));
        assertTrue(edgeShader.contains(
                "return clamp(SignedBodyAuthority, 0.0, 1.0);"));
        assertTrue(edgeShader.contains(
                "float storedBodyCoverage ="
                        + " bodyStorageCoverage(rawMaterial.b);"));
        assertTrue(edgeShader.contains(
                "float remainingCoverage = mix(1.0,"
                        + " abs(storedBodyCoverage),"));
        assertTrue(edgeShader.contains(
                "bool intentionalCarve = storedBodyCoverage < -0.002;"));
        assertTrue(edgeShader.contains(
                "float exactClippedBody = min(exactCoverage, rawMaterial.r);"));
        assertTrue(edgeShader.contains(
                "float boundedInteriorPunctureRepair("));
        assertTrue(edgeShader.contains(
                "clamp(PreCollapseInteriorGuard, 0.0, 1.0)"));
        assertTrue(edgeShader.contains(
                "const float INTERIOR_RING_REQUIRED_SUPPORT = 7.5;"));
        assertTrue(edgeShader.contains(
                "resolvedRingSupport < INTERIOR_RING_REQUIRED_SUPPORT"));
        assertTrue(edgeShader.contains(
                "exactRingSupport"));
        assertTrue(edgeShader.contains(
                "liveCoreSeed(textureMaskAt(upRightUv))"));
        assertTrue(edgeShader.contains(
                "ProxyFrontAvailable == 0"));
        assertTrue(edgeShader.contains(
                "return guardAuthority\n"
                        + "            * min(exactCoverage,"
                        + " supportedCoverage);"));
        assertTrue(edgeShader.contains(
                "activeBodyCoverage = max(activeBodyCoverage,"
                        + " interiorRepair);"));
        assertFalse(edgeShader.contains(
                "float resolvedCoverage = max(exactCoverage, resolvedShell);"));
        assertFalse(edgeShader.contains(
                "activeBodyCoverage = max(activeBodyCoverage,"
                        + " exactCoverage);"));
    }

    @Test
    void frozenSnapshotReplayMatchesVanillaEntityLightingInputs()
            throws IOException {
        assertShaderConstants(
                "assets/shadowedhearts/shaders/core/darkball/dark_ball_mask.vsh",
                "#moj_import <light.glsl>",
                "minecraft_mix_light(",
                "texelFetch(Sampler1, UV1, 0)",
                "texelFetch(Sampler2, UV2 / 16, 0)");
        assertShaderConstants(
                "assets/shadowedhearts/shaders/core/darkball/dark_ball_mask.fsh",
                "mix(overlayColor.rgb, litColor, overlayColor.a)",
                "litColor *= lightMapColor.rgb;");
        assertShaderConstants(
                "assets/shadowedhearts/shaders/core/darkball/dark_ball_mask.json",
                "\"name\": \"Sampler1\"",
                "\"name\": \"Light0_Direction\"",
                "\"name\": \"Light1_Direction\"");
    }

    @Test
    void upperInletPercentilesInterpolateWithoutSelectingOnlyTheHighestTip() {
        float[] sortedHeights = {-2.0f, 0.0f, 2.0f, 4.0f, 6.0f};
        assertEquals(3.6f, DarkBallAnalyticalVolume.sortedPercentile(
                sortedHeights, sortedHeights.length, 0.70f), 0.000001f);
        assertEquals(4.72f, DarkBallAnalyticalVolume.sortedPercentile(
                sortedHeights, sortedHeights.length, 0.84f), 0.000001f);
        assertTrue(DarkBallAnalyticalVolume.sortedPercentile(
                sortedHeights, sortedHeights.length, 0.84f)
                < sortedHeights[sortedHeights.length - 1]);
    }

    private static float forwardCollapseOrder(float sourceOrder,
                                              float releaseFront) {
        if (sourceOrder >= releaseFront) {
            return sourceOrder;
        }
        return (1.0f - DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN)
                * releaseFront
                + DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN
                * sourceOrder;
    }

    private static float inverseCollapseSourceOrder(float displayedOrder,
                                                    float releaseFront) {
        if (displayedOrder >= releaseFront) {
            return displayedOrder;
        }
        return (displayedOrder
                - (1.0f
                - DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN)
                * releaseFront)
                / DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN;
    }

    private static float collapseCompressionScaleForSourceOrder(
            float sourceOrder, float releaseFront) {
        if (releaseFront <= 0.0001f) {
            return 1.0f;
        }
        float targetOrder = Math.max(0.0f, Math.min(1.0f, releaseFront));
        if (sourceOrder >= targetOrder) {
            return 1.0f;
        }
        float orderedAdvance = Math.max(0.0f, Math.min(1.0f,
                (targetOrder - sourceOrder)
                        / Math.max(1.0f - sourceOrder, 0.0001f)));
        float scale = 1.0f
                - (1.0f
                - DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN)
                * orderedAdvance;
        return Math.max(DarkBallCaptureVfx.BODY_COLLAPSE_RETAINED_SPAN,
                Math.min(1.0f, scale));
    }

    private static float localCollapseScale(float sourceOrder,
                                            float releaseFront) {
        float displayedOrder =
                forwardCollapseOrder(sourceOrder, releaseFront);
        float recoveredSourceOrder =
                inverseCollapseSourceOrder(displayedOrder, releaseFront);
        return collapseCompressionScaleForSourceOrder(
                recoveredSourceOrder, releaseFront);
    }

    private static void assertShaderConstants(String resource,
                                              String... expectedDeclarations)
            throws IOException {
        String source = shaderResource(resource);
        for (String expectedDeclaration : expectedDeclarations) {
            assertTrue(source.contains(expectedDeclaration),
                    resource + " must declare " + expectedDeclaration);
        }
    }

    private static String shaderResource(String resource) throws IOException {
        ClassLoader loader = DarkBallAnalyticalMathTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            assertNotNull(stream, "missing shader resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String javaSource(String source) throws IOException {
        java.nio.file.Path sourcePath = java.nio.file.Path.of(
                "src/main/java").resolve(source);
        return java.nio.file.Files.readString(
                sourcePath, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String sourceBetween(String source, String startToken,
                                        String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(start >= 0, "missing source token " + startToken);
        assertTrue(end > start, "missing source token " + endToken);
        return source.substring(start, end);
    }

    private static float[] privateFloatArray(String fieldName)
            throws ReflectiveOperationException {
        var field = DarkBallSiphonBoltPath.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((float[]) field.get(null)).clone();
    }

    private static float privateFloat(String fieldName)
            throws ReflectiveOperationException {
        var field = DarkBallSiphonBoltPath.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getFloat(null);
    }

    private static boolean hasDeclaredMethod(String methodName) {
        for (var method : DarkBallSiphonBoltPath.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static float invokeEndpointEnvelope(float pathCoordinate)
            throws ReflectiveOperationException {
        var method = DarkBallSiphonBoltPath.class.getDeclaredMethod(
                "endpointEnvelope", float.class);
        method.setAccessible(true);
        return (float) method.invoke(null, pathCoordinate);
    }

    private static float invokeBoundedContrast(float value, float gain)
            throws ReflectiveOperationException {
        var method = DarkBallSiphonBoltPath.class.getDeclaredMethod(
                "boundedContrast", float.class, float.class);
        method.setAccessible(true);
        return (float) method.invoke(null, value, gain);
    }

    private static float turnRadians(Vector3f before,
                                     Vector3f corner,
                                     Vector3f after) {
        Vector3f incoming = new Vector3f(corner).sub(before).normalize();
        Vector3f outgoing = new Vector3f(after).sub(corner).normalize();
        return (float) Math.acos(Math.max(-1.0f,
                Math.min(1.0f, incoming.dot(outgoing))));
    }

    @Test
    void advectedReservoirUsesExactMonotonicDepletionHandoff() {
        float[] orders = {0.0f, 0.2f, 0.5f, 0.8f, 1.0f};
        for (float order : orders) {
            float previousRemaining = 1.0f;
            float releasedTotal = 0.0f;
            for (int step = 1; step <= 32; step++) {
                float siphon = step / 32.0f;
                float remaining = DarkBallAdvectedDensityField.releaseRemaining(
                        order, siphon, 0.0f);
                float released = DarkBallAdvectedDensityField.releasedFraction(
                        order, (step - 1) / 32.0f, siphon, 0.0f, 0.0f);
                assertTrue(remaining <= previousRemaining + 0.000001f);
                assertEquals(previousRemaining - remaining, released, 0.000001f);
                releasedTotal += released;
                previousRemaining = remaining;
            }
            assertEquals(1.0f, previousRemaining + releasedTotal, 0.00001f);
        }
    }

    @Test
    void releaseOrderDepletesFarMaterialBeforeTheSiphonEntrance() {
        assertEquals(0.0f, DarkBallAnalyticalVolume.farToOutletReleaseOrder(1.0f));
        assertEquals(0.5f, DarkBallAnalyticalVolume.farToOutletReleaseOrder(0.5f));
        assertEquals(1.0f, DarkBallAnalyticalVolume.farToOutletReleaseOrder(0.0f));

        float earlySiphon = 0.25f;
        float farRemaining = DarkBallAdvectedDensityField.releaseRemaining(
                DarkBallAnalyticalVolume.farToOutletReleaseOrder(1.0f),
                earlySiphon, 0.0f);
        float inletRemaining = DarkBallAdvectedDensityField.releaseRemaining(
                DarkBallAnalyticalVolume.farToOutletReleaseOrder(0.0f),
                earlySiphon, 0.0f);
        assertTrue(farRemaining < inletRemaining);
    }

    @Test
    void visualTransportOrderUsesOneMonotonicEuclideanMetric() {
        assertEquals(1.0f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(0.0f, 4.0f),
                0.000001f);
        assertEquals(0.75f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(1.0f, 4.0f),
                0.000001f);
        assertEquals(0.5f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(2.0f, 4.0f),
                0.000001f);
        assertEquals(0.0f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(4.0f, 4.0f),
                0.000001f);
        assertEquals(1.0f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(-1.0f, 4.0f),
                0.000001f);
        assertEquals(0.0f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(8.0f, 4.0f),
                0.000001f);

        float previous = 1.0f;
        for (int step = 0; step <= 8; step++) {
            float order = DarkBallAnalyticalVolume.euclideanTransportOrder(
                    step, 4.0f);
            assertTrue(Float.isFinite(order));
            assertTrue(order >= 0.0f && order <= 1.0f);
            assertTrue(order <= previous + 0.000001f);
            previous = order;
        }

        assertEquals(1.0f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(0.0f, 0.0f),
                0.000001f);
        assertEquals(0.0f,
                DarkBallAnalyticalVolume.euclideanTransportOrder(1.0f, 0.0f),
                0.000001f);
    }

    @Test
    void skippedProgressStillReleasesOutstandingReservoirExactlyOnce() {
        float order = 0.57f;
        float first = DarkBallAdvectedDensityField.releasedFraction(
                order, 0.0f, 0.72f, 0.0f, 0.0f);
        float second = DarkBallAdvectedDensityField.releasedFraction(
                order, 0.72f, 0.72f, 0.0f, 0.0f);
        assertTrue(first > 0.0f);
        assertEquals(0.0f, second, 0.000001f);
        assertEquals(1.0f - DarkBallAdvectedDensityField.releaseRemaining(
                order, 0.72f, 0.0f), first, 0.000001f);
    }

    @Test
    void finalCollapseCanOnlyDepleteTheAnchoredReservoir() {
        float order = 0.93f;
        float siphon = 0.78f;
        float previous = DarkBallAdvectedDensityField.releaseRemaining(order, siphon, 0.0f);
        for (int step = 1; step <= 8; step++) {
            float collapse = step / 8.0f;
            float remaining = DarkBallAdvectedDensityField.releaseRemaining(
                    order, siphon, collapse);
            assertTrue(remaining <= previous + 0.000001f);
            previous = remaining;
        }
    }

    @Test
    void oneBallLifecycleCanStartOnlyOneDarkCaptureSequence() {
        BallEmitters.OneShotStartLatch latch = new BallEmitters.OneShotStartLatch();
        AtomicInteger starts = new AtomicInteger();

        assertTrue(!latch.tryStart(false, () -> {
            starts.incrementAndGet();
            return true;
        }));
        assertTrue(!latch.tryStart(true, () -> false));
        assertTrue(latch.tryStart(true, () -> {
            starts.incrementAndGet();
            return true;
        }));
        assertTrue(!latch.tryStart(false, () -> true));
        assertTrue(!latch.tryStart(true, () -> {
            starts.incrementAndGet();
            return true;
        }));
        assertEquals(1, starts.get());

        BallEmitters.OneShotStartLatch freshLifecycle = new BallEmitters.OneShotStartLatch();
        assertTrue(freshLifecycle.tryStart(true, () -> true));
    }
}
