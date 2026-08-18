package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSplatTransportMathTest {
    private static final float EPSILON = 0.00001f;

    @Test
    void activationFollowsTheLocalReleaseFront() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float width = parameters.activationHalfWidth();

        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.sectionActivation(
                        0.50f,
                        0.50f - width,
                        parameters),
                EPSILON);
        assertEquals(
                0.5f,
                DarkBallSplatTransportMath.sectionActivation(
                        0.50f,
                        0.50f,
                        parameters),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallSplatTransportMath.sectionActivation(
                        0.50f,
                        0.50f + width,
                        parameters),
                EPSILON);

        float previous = 0.0f;
        for (int step = 0; step <= 200; step++) {
            float front = -0.25f + step * 0.01f;
            float current =
                    DarkBallSplatTransportMath.sectionActivation(
                            0.50f,
                            front,
                            parameters);
            assertTrue(current + EPSILON >= previous);
            previous = current;
        }
    }

    @Test
    void sectionDoesNotTravelUntilActivationIsComplete() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float section = 0.35f;
        float travelStart =
                section + parameters.activationHalfWidth();

        DarkBallSplatTransportMath.Sample atTravelStart =
                DarkBallSplatTransportMath.sample(
                        section,
                        travelStart,
                        parameters);
        assertEquals(1.0f, atTravelStart.activation(), EPSILON);
        assertEquals(0.0f, atTravelStart.travelProgress(), EPSILON);
        assertEquals(
                section,
                atTravelStart.transportedCoordinate(),
                EPSILON);

        DarkBallSplatTransportMath.Sample afterTravelStarts =
                DarkBallSplatTransportMath.sample(
                        section,
                        travelStart
                                + parameters.travelFrontSpan() * 0.25f,
                        parameters);
        assertTrue(afterTravelStarts.travelProgress() > 0.0f);
        assertTrue(afterTravelStarts.transportedCoordinate() > section);
        assertTrue(
                afterTravelStarts.transportedCoordinate()
                        <= travelStart
                        + parameters.travelFrontSpan() * 0.25f);
    }

    @Test
    void transportFollowsTheReleaseFrontWithoutJumpingToTheTerminal() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float section = 0.20f;
        float previousCoordinate = section;
        float previousScale = 1.0f;

        for (int step = 0; step <= 200; step++) {
            float front = -0.20f + step * 0.01f;
            DarkBallSplatTransportMath.Sample sample =
                    DarkBallSplatTransportMath.sample(
                            section,
                            front,
                            parameters);
            assertTrue(
                    sample.transportedCoordinate() + EPSILON
                            >= previousCoordinate);
            assertTrue(
                    sample.crossSectionScale()
                            <= previousScale + EPSILON);
            assertTrue(sample.transportedCoordinate() >= section);
            assertTrue(
                    sample.transportedCoordinate()
                            <= parameters.terminalCoordinate());
            assertTrue(
                    sample.transportedCoordinate()
                            <= Math.max(section, Math.min(
                            front,
                            parameters.terminalCoordinate()))
                            + EPSILON,
                    "transport must never outrun the advancing front");
            assertTrue(
                    sample.crossSectionScale()
                            >= parameters.minimumCrossSectionScale());
            previousCoordinate = sample.transportedCoordinate();
            previousScale = sample.crossSectionScale();
        }

        float finishedEasingFront =
                section
                        + parameters.activationHalfWidth()
                        + parameters.travelFrontSpan();
        DarkBallSplatTransportMath.Sample finishedEasing =
                DarkBallSplatTransportMath.sample(
                        section,
                        finishedEasingFront,
                        parameters);
        assertEquals(
                finishedEasingFront,
                finishedEasing.transportedCoordinate(),
                EPSILON);
        assertTrue(
                finishedEasing.transportedCoordinate()
                        < parameters.throatCoordinate(),
                "finishing the local ease must not jump to the inlet");
        assertTrue(
                finishedEasing.crossSectionScale()
                        > parameters.minimumCrossSectionScale(),
                "a short physical move must not fully narrow the section");

        DarkBallSplatTransportMath.Sample atTerminal =
                DarkBallSplatTransportMath.sample(
                        section,
                        parameters.terminalCoordinate(),
                        parameters);
        assertEquals(
                parameters.terminalCoordinate(),
                atTerminal.transportedCoordinate(),
                EPSILON);
        assertEquals(
                parameters.minimumCrossSectionScale(),
                atTerminal.crossSectionScale(),
                EPSILON);
    }

    @Test
    void narrowingUsesActualNormalizedDistanceAlongThePath() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float section = 0.35f;
        float terminal = parameters.terminalCoordinate();
        float halfway = (section + terminal) * 0.5f;

        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.normalizedPathProgress(
                        section,
                        section,
                        parameters),
                EPSILON);
        assertEquals(
                0.5f,
                DarkBallSplatTransportMath.normalizedPathProgress(
                        section,
                        halfway,
                        parameters),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallSplatTransportMath.normalizedPathProgress(
                        section,
                        terminal,
                        parameters),
                EPSILON);

        float expectedHalfScale = (1.0f
                + parameters.minimumCrossSectionScale()) * 0.5f;
        assertEquals(
                expectedHalfScale,
                DarkBallSplatTransportMath.crossSectionScale(
                        0.5f,
                        parameters),
                EPSILON);

        float front = section
                + parameters.activationHalfWidth()
                + parameters.travelFrontSpan();
        DarkBallSplatTransportMath.Sample sample =
                DarkBallSplatTransportMath.sample(
                        section,
                        front,
                        parameters);
        assertEquals(
                (sample.transportedCoordinate() - section)
                        / (terminal - section),
                sample.normalizedPathProgress(),
                EPSILON);
        assertEquals(
                DarkBallSplatTransportMath.crossSectionScale(
                        sample.normalizedPathProgress(),
                        parameters),
                sample.crossSectionScale(),
                EPSILON);
    }

    @Test
    void everySplatRemainingInletDistanceIsMonotonicAcrossTheReleaseFront() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float[] representativeSections = {
                0.0f, 0.07f, 0.23f, 0.50f, 0.79f, 0.96f, 1.0f
        };
        float[] representativeRestDistances = {
                0.04f, 0.31f, 1.0f, 3.75f, 12.0f
        };

        for (float section : representativeSections) {
            for (float restDistance : representativeRestDistances) {
                float previousRemaining = restDistance;
                for (int step = 0; step <= 3000; step++) {
                    float front = -0.25f + step * 0.0005f;
                    DarkBallSplatTransportMath.Sample sample =
                            DarkBallSplatTransportMath.sample(
                                    section,
                                    front,
                                    parameters);
                    float remaining =
                            DarkBallSplatTransportMath
                                    .remainingInletDistance(
                                            restDistance,
                                            sample.normalizedPathProgress());
                    assertTrue(
                            remaining <= previousRemaining + EPSILON,
                            "remaining inlet distance increased for section "
                                    + section + " at front " + front);
                    assertTrue(remaining >= -EPSILON);
                    previousRemaining = remaining;
                }
            }
        }
    }

    @Test
    void kelvinletPinchAndDirectSinkRemainMonotonicTogether() {
        float[] axialDistances = {
                -4.0f, -0.25f, 0.0f, 0.75f, 6.0f
        };
        float[] transverseDistances = {
                0.0f, 0.08f, 0.75f, 3.0f, 12.0f
        };
        for (float axial : axialDistances) {
            for (float transverse : transverseDistances) {
                for (float coherence : new float[]{0.0f, 0.5f, 1.0f}) {
                    float previous = Float.POSITIVE_INFINITY;
                    float previousPinch = 1.0f;
                    for (int step = 0; step <= 2000; step++) {
                        float progress = step / 2000.0f;
                        float pinch =
                                DarkBallSplatTransportMath
                                        .kelvinletPinchScale(
                                                progress,
                                                coherence);
                        float remaining =
                                DarkBallSplatTransportMath
                                        .remainingPinchedInletDistance(
                                                axial,
                                                transverse,
                                                progress,
                                                coherence);
                        assertTrue(pinch <= previousPinch + EPSILON);
                        assertTrue(remaining <= previous + EPSILON);
                        assertTrue(remaining >= -EPSILON);
                        previousPinch = pinch;
                        previous = remaining;
                    }
                }
            }
        }
    }

    @Test
    void siphonFootprintHandoffIsRapidBoundedAndMonotonic() {
        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.siphonHandoffBlend(
                        DarkBallSplatTransportMath
                                .HANDOFF_START_PROGRESS),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallSplatTransportMath.siphonHandoffBlend(
                        DarkBallSplatTransportMath
                                .HANDOFF_FULL_PROGRESS),
                EPSILON);
        float previous = 0.0f;
        for (int step = 0; step <= 1000; step++) {
            float blend =
                    DarkBallSplatTransportMath.siphonHandoffBlend(
                            step / 1000.0f);
            assertTrue(blend + EPSILON >= previous);
            assertTrue(blend >= 0.0f && blend <= 1.0f);
            previous = blend;
        }
    }

    @Test
    void inactiveSplatsRemainAtRestAndTerminalSplatsReachTheInlet() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float[] representativeSections = {
                0.0f, 0.15f, 0.42f, 0.73f, 1.0f
        };
        float restDistance = 6.25f;

        for (float section : representativeSections) {
            float beforeTravel = section
                    + parameters.activationHalfWidth();
            DarkBallSplatTransportMath.Sample inactive =
                    DarkBallSplatTransportMath.sample(
                            section,
                            beforeTravel,
                            parameters);
            assertEquals(
                    section,
                    inactive.transportedCoordinate(),
                    EPSILON);
            assertEquals(
                    restDistance,
                    DarkBallSplatTransportMath.remainingInletDistance(
                            restDistance,
                            inactive.normalizedPathProgress()),
                    EPSILON);

            DarkBallSplatTransportMath.Sample terminal =
                    DarkBallSplatTransportMath.sample(
                            section,
                            Math.max(
                                    parameters.terminalCoordinate(),
                                    section
                                            + parameters
                                            .activationHalfWidth()
                                            + parameters
                                            .travelFrontSpan()),
                            parameters);
            assertEquals(
                    1.0f,
                    terminal.normalizedPathProgress(),
                    EPSILON);
            assertEquals(
                    0.0f,
                    DarkBallSplatTransportMath.remainingInletDistance(
                            restDistance,
                            terminal.normalizedPathProgress()),
                    EPSILON);
        }
    }

    @Test
    void nearInletCurlIsSmallEndpointZeroAndRadiusPreserving() {
        float radialX = 2.75f;
        float radialY = -1.125f;
        float originalRadius =
                (float) Math.hypot(radialX, radialY);

        for (float signedStrength : new float[]{
                -2.0f, -1.0f, -0.35f, 0.0f, 0.65f, 1.0f, 2.0f
        }) {
            assertEquals(
                    0.0f,
                    DarkBallSplatTransportMath.nearInletCurlAngle(
                            0.0f,
                            signedStrength),
                    EPSILON);
            assertEquals(
                    0.0f,
                    DarkBallSplatTransportMath.nearInletCurlAngle(
                            DarkBallSplatTransportMath
                                    .CURL_START_PROGRESS,
                            signedStrength),
                    EPSILON);
            assertEquals(
                    0.0f,
                    DarkBallSplatTransportMath.nearInletCurlAngle(
                            1.0f,
                            signedStrength),
                    EPSILON);

            for (int step = 0; step <= 2000; step++) {
                float pathProgress = step / 2000.0f;
                DarkBallSplatTransportMath.CurlOffset curled =
                        DarkBallSplatTransportMath.applyNearInletCurl(
                                radialX,
                                radialY,
                                pathProgress,
                                signedStrength);
                assertTrue(
                        Math.abs(curled.angleRadians())
                                <= DarkBallSplatTransportMath
                                .MAX_CURL_ANGLE_RADIANS
                                + EPSILON);
                assertEquals(
                        originalRadius,
                        (float) Math.hypot(curled.x(), curled.y()),
                        EPSILON,
                        "curl must rotate without changing radius");
            }
        }
    }

    @Test
    void remainingDistanceRejectsInvalidRestDistances() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DarkBallSplatTransportMath.remainingInletDistance(
                        -0.01f,
                        0.5f));
        assertThrows(
                IllegalArgumentException.class,
                () -> DarkBallSplatTransportMath.remainingInletDistance(
                        Float.NaN,
                        0.5f));
    }

    @Test
    void bodyOwnershipCannotRetireBeforeThroatCrossing() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float throat = parameters.throatCoordinate();

        assertEquals(
                1.0f,
                DarkBallSplatTransportMath.bodyOwnership(
                        throat - 0.25f,
                        parameters),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallSplatTransportMath.bodyOwnership(
                        throat,
                        parameters),
                EPSILON);
        assertTrue(
                DarkBallSplatTransportMath.bodyOwnership(
                        throat
                                + parameters.retirementWidth() * 0.5f,
                        parameters)
                        < 1.0f);
        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.bodyOwnership(
                        throat + parameters.retirementWidth(),
                        parameters),
                EPSILON);
    }

    @Test
    void transportCannotRetireAheadOfTheReleaseFront() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float section = 0.15f;

        DarkBallSplatTransportMath.Sample beforeThroat =
                DarkBallSplatTransportMath.sample(
                        section,
                        parameters.throatCoordinate() - 0.01f,
                        parameters);
        assertTrue(
                beforeThroat.transportedCoordinate()
                        < parameters.throatCoordinate());
        assertEquals(
                1.0f,
                beforeThroat.bodyOwnership(),
                EPSILON);
        assertEquals(
                0.0f,
                beforeThroat.terminalCollarOwnership(),
                EPSILON);

        DarkBallSplatTransportMath.Sample afterRetirement =
                DarkBallSplatTransportMath.sample(
                        section,
                        parameters.throatCoordinate()
                                + parameters.retirementWidth(),
                        parameters);
        assertEquals(
                1.0f,
                afterRetirement.bodyOwnership(),
                EPSILON,
                "crossing the throat must not retire a surfel before its "
                        + "complete anatomical route reaches the inlet");
        assertEquals(
                1.0f,
                afterRetirement.terminalCollarOwnership(),
                EPSILON);
    }

    @Test
    void bodyAndCollarMaintainContinuousTerminalOwnership() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float throat = parameters.throatCoordinate();
        float collarFadeStart =
                parameters.collarExitCoordinate()
                        - parameters.collarExitWidth();

        for (int step = 0; step <= 100; step++) {
            float coordinate = throat
                    + (collarFadeStart - throat) * step / 100.0f;
            float body =
                    DarkBallSplatTransportMath.bodyOwnership(
                            coordinate,
                            parameters);
            float collar =
                    DarkBallSplatTransportMath
                            .terminalCollarOwnership(
                                    coordinate,
                                    parameters);
            assertEquals(
                    1.0f,
                    body + collar,
                    EPSILON,
                    "body retirement must hand ownership to the collar");
        }

        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.bodyOwnership(
                        parameters.collarExitCoordinate(),
                        parameters),
                EPSILON);
        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.terminalCollarOwnership(
                        parameters.collarExitCoordinate(),
                        parameters),
                EPSILON);
    }

    @Test
    void terminalCollarOwnsANonzeroWindowAfterBodyRetirement() {
        DarkBallSplatTransportMath.Parameters parameters =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;
        float throat = parameters.throatCoordinate();
        float retired =
                throat + parameters.retirementWidth();
        float middle = (retired
                + parameters.collarExitCoordinate()
                - parameters.collarExitWidth()) * 0.5f;

        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.terminalCollarOwnership(
                        throat,
                        parameters),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallSplatTransportMath.terminalCollarOwnership(
                        retired,
                        parameters),
                EPSILON);
        assertEquals(
                1.0f,
                DarkBallSplatTransportMath.terminalCollarOwnership(
                        middle,
                        parameters),
                EPSILON);
        assertEquals(
                0.0f,
                DarkBallSplatTransportMath.terminalCollarOwnership(
                        parameters.collarExitCoordinate(),
                        parameters),
                EPSILON);
        assertTrue(
                parameters.collarExitCoordinate()
                        - retired > 0.0f);
    }

    @Test
    void deterministicSamplesClampOnlyTheSourceSectionCoordinate() {
        DarkBallSplatTransportMath.Sample first =
                DarkBallSplatTransportMath.sample(-0.25f, 0.40f);
        DarkBallSplatTransportMath.Sample second =
                DarkBallSplatTransportMath.sample(-0.25f, 0.40f);

        assertEquals(first, second);
        assertTrue(first.transportedCoordinate() >= 0.0f);
        assertThrows(
                IllegalArgumentException.class,
                () -> DarkBallSplatTransportMath.sample(
                        Float.NaN,
                        0.40f));
        assertThrows(
                IllegalArgumentException.class,
                () -> DarkBallSplatTransportMath.sample(
                        0.25f,
                        Float.POSITIVE_INFINITY));
    }

    @Test
    void parametersRequireARealTerminalOwnershipInterval() {
        DarkBallSplatTransportMath.Parameters defaults =
                DarkBallSplatTransportMath.DEFAULT_PARAMETERS;

        assertThrows(
                IllegalArgumentException.class,
                () -> new DarkBallSplatTransportMath.Parameters(
                        defaults.activationHalfWidth(),
                        defaults.travelFrontSpan(),
                        defaults.minimumCrossSectionScale(),
                        defaults.throatCoordinate(),
                        defaults.terminalCoordinate(),
                        defaults.retirementWidth(),
                        defaults.throatCoordinate()
                                + defaults.retirementWidth(),
                        defaults.collarExitWidth()));
    }
}
