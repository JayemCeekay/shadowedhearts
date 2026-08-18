package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSiphonBoltPathTest {
    private static final float EPSILON = 0.00001f;

    @Test
    void endpointsAndShortTangentCollarsRemainPinnedDuringContinuousMotion() {
        Vector3f root = new Vector3f(0.2f, -0.3f, 0.1f);
        Vector3f controlA = new Vector3f(1.8f, 0.9f, 0.4f);
        Vector3f controlB = new Vector3f(5.2f, 0.6f, -0.2f);
        Vector3f end = new Vector3f(6.4f, 0.1f, 0.3f);
        Vector3f[] early = destination();
        Vector3f[] late = destination();

        DarkBallSiphonBoltPath.populate(
                root, controlA, controlB, end, 1.25f, 0.0f, early);
        DarkBallSiphonBoltPath.populate(
                root, controlA, controlB, end, 1.25f, 9.75f, late);

        int endpointIndex = DarkBallSiphonBoltPath.NODE_COUNT - 1;
        int terminalCollarIndex = endpointIndex - 1;
        assertEquals(10, DarkBallSiphonBoltPath.ANIMATED_BEND_COUNT);
        assertEquals(14, DarkBallSiphonBoltPath.NODE_COUNT);
        assertVectorEquals(root, early[0]);
        assertVectorEquals(end, early[endpointIndex]);
        assertVectorEquals(early[0], late[0]);
        assertVectorEquals(early[1], late[1]);
        assertVectorEquals(early[terminalCollarIndex],
                late[terminalCollarIndex]);
        assertVectorEquals(early[endpointIndex], late[endpointIndex]);

        Vector3f rootCollar = new Vector3f(early[1]).sub(early[0]).normalize();
        Vector3f rootTangent = new Vector3f(controlA).sub(root).normalize();
        assertTrue(rootCollar.dot(rootTangent) > 1.0f - EPSILON);
        float expectedCollarLength = root.distance(end)
                * DarkBallSiphonBoltPath.COLLAR_CHORD_FRACTION;
        assertEquals(0.035f,
                DarkBallSiphonBoltPath.COLLAR_CHORD_FRACTION, EPSILON);
        assertEquals(expectedCollarLength, early[0].distance(early[1]), EPSILON);

        Vector3f terminalCollar = new Vector3f(early[endpointIndex])
                .sub(early[terminalCollarIndex]).normalize();
        Vector3f terminalTangent = new Vector3f(end)
                .sub(controlB).normalize();
        assertTrue(terminalCollar.dot(terminalTangent) > 1.0f - EPSILON);
        assertEquals(expectedCollarLength,
                early[terminalCollarIndex].distance(early[endpointIndex]),
                EPSILON);
    }

    @Test
    void motionEvolvesContinuouslyWithoutHoldsOrRestrikeBoundaries() {
        StraightGuide guide = new StraightGuide();
        Vector3f[] previous = destination();
        guide.populate(0.0f, previous);
        float accumulatedMotion = 0.0f;
        float largestNodeStep = 0.0f;
        float smallestStep = Float.POSITIVE_INFINITY;
        float stepSeconds = 1.0f / 60.0f;

        for (int sample = 1; sample <= 4 * 60; sample++) {
            Vector3f[] current = destination();
            guide.populate(sample * stepSeconds, current);
            float stepDistance = interiorDistance(previous, current);
            accumulatedMotion += stepDistance;
            smallestStep = Math.min(smallestStep, stepDistance);
            for (int node = 2;
                 node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
                 node++) {
                largestNodeStep = Math.max(largestNodeStep,
                        previous[node].distance(current[node]));
            }
            previous = current;
        }

        float amplitude = DarkBallSiphonBoltPath.bendAmplitude(
                guide.bodyRadius, guide.root.distance(guide.end));
        assertTrue(accumulatedMotion > amplitude * 12.0f,
                "the plasma arc should visibly evolve throughout the sample");
        assertTrue(smallestStep > 0.00001f,
                "continuous motion must not contain held strike intervals");
        assertTrue(largestNodeStep < amplitude * 0.16f,
                "no node may teleport more than 16% of bend amplitude per "
                        + "60 Hz frame; measured " + largestNodeStep);
    }

    @Test
    void denseTemporalSamplingKeepsRapidMotionSmoothlyAccelerated() {
        StraightGuide guide = new StraightGuide();
        float stepSeconds = 1.0f / 480.0f;
        Vector3f[] previous = destination();
        Vector3f[] current = destination();
        guide.populate(0.0f, previous);
        guide.populate(stepSeconds, current);
        float greatestAcceleration = 0.0f;

        for (int sample = 2; sample <= 4 * 480; sample++) {
            Vector3f[] next = destination();
            guide.populate(sample * stepSeconds, next);
            for (int node = 2;
                 node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
                 node++) {
                Vector3f secondDifference = new Vector3f(next[node])
                        .sub(new Vector3f(current[node]).mul(2.0f))
                        .add(previous[node]);
                greatestAcceleration = Math.max(greatestAcceleration,
                        secondDifference.length()
                                / (stepSeconds * stepSeconds));
            }
            previous = current;
            current = next;
        }

        assertTrue(greatestAcceleration < guide.bodyRadius * 40.0f,
                "rapid gradient-noise motion must remain smoothly "
                        + "accelerated; measured " + greatestAcceleration
                        + " BodyRadius/s^2");
    }

    @Test
    void temporalVelocityRemainsSmoothAcrossFormerEpochBoundaries() {
        StraightGuide guide = new StraightGuide();
        float sampleRadius = 0.001f;
        float largestSecondDifference = 0.0f;

        for (int boundary = 1; boundary <= 60; boundary++) {
            float time = boundary / 9.0f;
            Vector3f[] before = destination();
            Vector3f[] center = destination();
            Vector3f[] after = destination();
            guide.populate(time - sampleRadius, before);
            guide.populate(time, center);
            guide.populate(time + sampleRadius, after);

            for (int node = 2;
                 node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
                 node++) {
                Vector3f secondDifference = new Vector3f(after[node])
                        .sub(new Vector3f(center[node]).mul(2.0f))
                        .add(before[node]);
                largestSecondDifference = Math.max(
                        largestSecondDifference, secondDifference.length());
            }
        }

        assertTrue(largestSecondDifference < 0.0005f,
                "gradient fields must remain differentiable across lattice cells");
    }

    @Test
    void evolutionIsSpatiallyNonRigidAndDoesNotRepeatAsATravelingCycle() {
        StraightGuide guide = new StraightGuide();
        Vector3f[] start = destination();
        Vector3f[] shortlyAfter = destination();
        Vector3f[] laterStart = destination();
        Vector3f[] laterAfter = destination();
        guide.populate(0.37f, start);
        guide.populate(0.54f, shortlyAfter);
        guide.populate(1.37f, laterStart);
        guide.populate(1.54f, laterAfter);

        Vector3f meanDisplacement = new Vector3f();
        for (int node = 2;
             node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
             node++) {
            meanDisplacement.add(
                    new Vector3f(shortlyAfter[node]).sub(start[node]));
        }
        meanDisplacement.div(DarkBallSiphonBoltPath.ANIMATED_BEND_COUNT);

        float totalMotion = 0.0f;
        float nonRigidResidual = 0.0f;
        float repeatedIntervalError = 0.0f;
        for (int node = 2;
             node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
             node++) {
            Vector3f earlyDisplacement =
                    new Vector3f(shortlyAfter[node]).sub(start[node]);
            Vector3f laterDisplacement =
                    new Vector3f(laterAfter[node]).sub(laterStart[node]);
            totalMotion += earlyDisplacement.length();
            nonRigidResidual += new Vector3f(earlyDisplacement)
                    .sub(meanDisplacement).length();
            repeatedIntervalError += earlyDisplacement
                    .distance(laterDisplacement);
        }

        float amplitude = DarkBallSiphonBoltPath.bendAmplitude(
                guide.bodyRadius, guide.root.distance(guide.end));
        assertTrue(totalMotion > amplitude * 0.35f,
                "the sampled interval must contain visible evolution");
        assertTrue(nonRigidResidual > totalMotion * 0.32f,
                "motion must deform the path instead of translating it rigidly");
        assertTrue(repeatedIntervalError > amplitude * 0.28f,
                "later evolution must not replay a fixed one-second cycle");
    }

    @Test
    void fineBandsProduceDecisiveBoundedAlternatingZigzags() {
        StraightGuide guide = new StraightGuide();
        int greatestCurvatureSignChanges = 0;
        float greatestFineCurvature = 0.0f;
        float turnSum = 0.0f;
        float maximumTurn = 0.0f;
        int turnCount = 0;
        int decisiveTurns = 0;
        int alternatingTurns = 0;
        int alternatingPairs = 0;
        float chordCappedMaximumTurn = 0.0f;

        for (int sample = 0; sample <= 80; sample++) {
            Vector3f[] nodes = destination();
            guide.populate(sample * 0.0375f, nodes);
            int signChanges = 0;
            float previousCurvature = 0.0f;
            Vector3f previousCurvatureVector = null;
            for (int node = 3;
                 node <= DarkBallSiphonBoltPath.NODE_COUNT - 4;
                 node++) {
                float curvature = nodes[node + 1].y
                        - 2.0f * nodes[node].y
                        + nodes[node - 1].y;
                greatestFineCurvature = Math.max(
                        greatestFineCurvature, Math.abs(curvature));
                if (previousCurvature != 0.0f
                        && Math.signum(previousCurvature)
                        != Math.signum(curvature)) {
                    signChanges++;
                }
                previousCurvature = curvature;
                Vector3f incoming = new Vector3f(nodes[node])
                        .sub(nodes[node - 1]).normalize();
                Vector3f outgoing = new Vector3f(nodes[node + 1])
                        .sub(nodes[node]).normalize();
                float turn = (float) Math.acos(Math.max(-1.0f,
                        Math.min(1.0f, incoming.dot(outgoing))));
                turnSum += turn;
                maximumTurn = Math.max(maximumTurn, turn);
                turnCount++;
                decisiveTurns += turn >= Math.toRadians(6.0)
                        ? 1 : 0;
                Vector3f curvatureVector = new Vector3f(nodes[node + 1])
                        .sub(new Vector3f(nodes[node]).mul(2.0f))
                        .add(nodes[node - 1]);
                if (previousCurvatureVector != null
                        && previousCurvatureVector.lengthSquared() > 0.000001f
                        && curvatureVector.lengthSquared() > 0.000001f) {
                    alternatingPairs++;
                    alternatingTurns +=
                            previousCurvatureVector.dot(curvatureVector) < 0.0f
                                    ? 1 : 0;
                }
                previousCurvatureVector = curvatureVector;
            }
            greatestCurvatureSignChanges = Math.max(
                    greatestCurvatureSignChanges, signChanges);

            Vector3f[] chordCappedNodes = destination();
            DarkBallSiphonBoltPath.populate(
                    guide.root, guide.controlA, guide.controlB, guide.end,
                    100.0f, sample * 0.0375f, chordCappedNodes);
            for (int node = 3;
                 node <= DarkBallSiphonBoltPath.NODE_COUNT - 4;
                 node++) {
                Vector3f incoming =
                        new Vector3f(chordCappedNodes[node])
                                .sub(chordCappedNodes[node - 1]).normalize();
                Vector3f outgoing =
                        new Vector3f(chordCappedNodes[node + 1])
                                .sub(chordCappedNodes[node]).normalize();
                chordCappedMaximumTurn = Math.max(
                        chordCappedMaximumTurn,
                        (float) Math.acos(Math.max(-1.0f,
                                Math.min(1.0f, incoming.dot(outgoing)))));
            }
        }
        float averageTurn = turnSum / turnCount;
        float decisiveRatio = decisiveTurns / (float) turnCount;
        float alternatingRatio =
                alternatingTurns / (float) alternatingPairs;

        assertTrue(greatestCurvatureSignChanges >= 3,
                "fine bands should create multiple resolved bends");
        assertTrue(greatestFineCurvature > 0.025f,
                "fine electrical structure must remain visually meaningful");
        assertTrue(averageTurn > Math.toRadians(6.5),
                "the control path needs a readable average elbow");
        assertTrue(maximumTurn > Math.toRadians(17.0),
                "the control path needs occasional decisive elbows");
        assertTrue(decisiveRatio > 0.52f,
                "most sampled elbows should exceed six degrees");
        assertTrue(alternatingRatio > 0.42f,
                "curvature should reverse often enough to read as a zigzag");
        assertTrue(maximumTurn < Math.toRadians(25.0),
                "body-scaled bends must not kink excessively");
        assertTrue(chordCappedMaximumTurn < Math.toRadians(40.0),
                "chord-capped bends must remain safe for prism overlap; measured "
                        + Math.toDegrees(chordCappedMaximumTurn) + " degrees");
    }

    @Test
    void denseSamplingPreservesRadialBoundsLongitudinalOrderAndFiniteSegments() {
        StraightGuide guide = new StraightGuide();
        float chordLength = guide.root.distance(guide.end);
        float bendAmplitude = DarkBallSiphonBoltPath.bendAmplitude(
                guide.bodyRadius, chordLength);
        Vector3f[] nodes = destination();

        for (int sample = 0; sample <= 12 * 180; sample++) {
            float time = sample / 180.0f;
            guide.populate(time, nodes);

            float previousT = -1.0f;
            for (int node = 2;
                 node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
                 node++) {
                float radialDistance = (float) Math.hypot(
                        nodes[node].y, nodes[node].z);
                assertTrue(radialDistance <= bendAmplitude + EPSILON,
                        "bend exceeded its body/chord displacement cap");

                float baseT = node
                        / (DarkBallSiphonBoltPath.NODE_COUNT - 1.0f);
                float observedT = nodes[node].x / chordLength;
                assertTrue(Math.abs(observedT - baseT) <= 0.01001f,
                        "bend exceeded longitudinal jitter bounds");
                assertTrue(observedT > previousT,
                        "animated bend order must remain strict");
                previousT = observedT;
            }

            for (int segment = 0; segment < nodes.length - 1; segment++) {
                Vector3f difference = new Vector3f(nodes[segment + 1])
                        .sub(nodes[segment]);
                assertTrue(Float.isFinite(difference.x)
                                && Float.isFinite(difference.y)
                                && Float.isFinite(difference.z),
                        "every generated segment must remain finite");
                assertTrue(difference.x > EPSILON,
                        "straight-guide segments must advance toward the ball");
            }
        }
    }

    @Test
    void sixteenPresentationPrismsRemainFiniteAndTurnSafelyAlongTheCurve() {
        StraightGuide guide = new StraightGuide();
        float greatestTypicalTurn = 0.0f;
        float greatestChordCappedTurn = 0.0f;

        for (int sample = 0; sample <= 8 * 120; sample++) {
            float time = sample / 120.0f;
            Vector3f[] typicalNodes = destination();
            guide.populate(time, typicalNodes);
            greatestTypicalTurn = Math.max(greatestTypicalTurn,
                    maximumPresentationTurn(typicalNodes));

            Vector3f[] chordCappedNodes = destination();
            DarkBallSiphonBoltPath.populate(
                    guide.root, guide.controlA, guide.controlB, guide.end,
                    100.0f, time, chordCappedNodes);
            greatestChordCappedTurn = Math.max(greatestChordCappedTurn,
                    maximumPresentationTurn(chordCappedNodes));
        }

        assertTrue(greatestTypicalTurn > Math.toRadians(24.5),
                "node-aware presentation should expose decisive typical elbows; "
                        + "measured " + Math.toDegrees(greatestTypicalTurn)
                        + " degrees");
        assertTrue(greatestTypicalTurn < Math.toRadians(30.0),
                "typical presentation elbows must remain controlled; measured "
                        + Math.toDegrees(greatestTypicalTurn) + " degrees");
        assertTrue(greatestChordCappedTurn < Math.toRadians(62.0),
                "16-prism presentation must retain a bounded stress envelope; "
                        + "measured "
                        + Math.toDegrees(greatestChordCappedTurn)
                        + " degrees");
    }

    @Test
    void nodeAwarePresentationPartitionPreservesEveryHermiteJoint() {
        float previous = -1.0f;
        int midpointCount = 0;
        for (int boundary = 0; boundary <= 16; boundary++) {
            float curveT = presentationBoundaryT(boundary);
            assertTrue(Float.isFinite(curveT));
            assertTrue(curveT > previous,
                    "presentation boundaries must remain strictly ordered");
            previous = curveT;
        }
        assertEquals(0.0f, presentationBoundaryT(0), EPSILON);
        assertEquals(1.0f, presentationBoundaryT(16), EPSILON);

        for (int node = 0; node <= 13; node++) {
            float expected = node / 13.0f;
            boolean found = false;
            for (int boundary = 0; boundary <= 16; boundary++) {
                found |= Math.abs(presentationBoundaryT(boundary) - expected)
                        <= EPSILON;
            }
            assertTrue(found, "missing Hermite node boundary " + node);
        }

        for (int interval = 0; interval < 13; interval++) {
            float midpoint = (interval + 0.5f) / 13.0f;
            boolean found = false;
            for (int boundary = 0; boundary <= 16; boundary++) {
                found |= Math.abs(presentationBoundaryT(boundary) - midpoint)
                        <= EPSILON;
            }
            if (found) {
                midpointCount++;
                assertTrue(interval == 3 || interval == 6 || interval == 9,
                        "only fixed animated intervals may be subdivided");
            }
        }
        assertEquals(3, midpointCount);
    }

    @Test
    void presentationPrismsHaveStaticOwnershipButLiveVariableLengths() {
        StraightGuide guide = new StraightGuide();
        Vector3f[] early = destination();
        Vector3f[] late = destination();
        guide.populate(0.31f, early);
        guide.populate(1.47f, late);

        float shortestParametricSpan = Float.POSITIVE_INFINITY;
        float longestParametricSpan = 0.0f;
        float accumulatedLiveLengthChange = 0.0f;
        for (int prism = 0; prism < 16; prism++) {
            float startT = presentationBoundaryT(prism);
            float endT = presentationBoundaryT(prism + 1);
            float parametricSpan = endT - startT;
            shortestParametricSpan = Math.min(
                    shortestParametricSpan, parametricSpan);
            longestParametricSpan = Math.max(
                    longestParametricSpan, parametricSpan);

            float earlyLength = presentationCurvePoint(early, startT)
                    .distance(presentationCurvePoint(early, endT));
            float lateLength = presentationCurvePoint(late, startT)
                    .distance(presentationCurvePoint(late, endT));
            assertTrue(Float.isFinite(earlyLength) && earlyLength > EPSILON);
            assertTrue(Float.isFinite(lateLength) && lateLength > EPSILON);
            accumulatedLiveLengthChange += Math.abs(lateLength - earlyLength);
        }

        assertTrue(longestParametricSpan
                        > shortestParametricSpan * 1.9f,
                "node-aware ownership intentionally mixes whole and half "
                        + "Hermite intervals");
        assertTrue(accumulatedLiveLengthChange > 0.01f,
                "prism endpoints must follow the animated curve so individual "
                        + "chords can shorten or lengthen");
    }

    @Test
    void shortenedHermiteTangentsKeepCanonicalDoglegAngular() {
        float angle = (float) Math.toRadians(30.0);
        Vector3f[] nodes = {
                new Vector3f(-1.0f, 0.0f, 0.0f),
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f((float) Math.cos(angle),
                        (float) Math.sin(angle), 0.0f)
        };
        Vector3f left = presentationCurvePoint(nodes, 0.41f);
        Vector3f right = presentationCurvePoint(nodes, 0.59f);
        Vector3f incoming = new Vector3f(nodes[1]).sub(left).normalize();
        Vector3f outgoing = new Vector3f(right).sub(nodes[1]).normalize();
        float visibleTurn = (float) Math.acos(Math.max(-1.0f,
                Math.min(1.0f, incoming.dot(outgoing))));

        assertTrue(visibleTurn >= Math.toRadians(22.0)
                        && visibleTurn <= Math.toRadians(26.0),
                "30-degree dogleg should retain a decisive finite-window elbow; "
                        + "measured " + Math.toDegrees(visibleTurn)
                        + " degrees");
    }

    @Test
    void movingBallControlsDoNotReseedTheProceduralField() {
        StraightGuide guide = new StraightGuide();
        Vector3f[] nearBall = destination();
        Vector3f[] fartherBall = destination();
        float time = 1.6444f;

        guide.populate(time, nearBall);
        DarkBallSiphonBoltPath.populate(
                guide.root,
                new Vector3f(4.0f, 0.0f, 0.0f),
                new Vector3f(8.0f, 0.0f, 0.0f),
                new Vector3f(12.0f, 0.0f, 0.0f),
                guide.bodyRadius, time, fartherBall);

        for (int node = 2;
             node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
             node++) {
            assertEquals(nearBall[node].y, fartherBall[node].y, EPSILON,
                    "moving controls must preserve the primary noise sample");
            assertEquals(nearBall[node].z, fartherBall[node].z, EPSILON,
                    "moving controls must preserve the secondary noise sample");
        }
    }

    @Test
    void distinctCaptureRootProducesADistinctDeterministicField() {
        StraightGuide guide = new StraightGuide();
        Vector3f translation = new Vector3f(13.25f, -4.5f, 7.75f);
        Vector3f[] first = destination();
        Vector3f[] translatedCapture = destination();
        float time = 1.6444f;

        guide.populate(time, first);
        DarkBallSiphonBoltPath.populate(
                new Vector3f(guide.root).add(translation),
                new Vector3f(guide.controlA).add(translation),
                new Vector3f(guide.controlB).add(translation),
                new Vector3f(guide.end).add(translation),
                guide.bodyRadius, time, translatedCapture);

        float localDifference = 0.0f;
        for (int node = 2;
             node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
             node++) {
            localDifference += first[node].distance(
                    new Vector3f(translatedCapture[node]).sub(translation));
        }
        assertTrue(localDifference > 0.05f,
                "capture-fixed roots must seed distinct procedural fields");
    }

    @Test
    void replayIsDeterministicAndExtremeTimesStayFinite() {
        Vector3f root = new Vector3f(-0.4f, 0.2f, 0.7f);
        Vector3f controlA = new Vector3f(0.8f, 1.1f, 0.1f);
        Vector3f controlB = new Vector3f(2.2f, -0.4f, 0.9f);
        Vector3f end = new Vector3f(3.6f, 0.3f, 0.2f);
        Vector3f[] expected = destination();
        Vector3f[] reused = destination();

        DarkBallSiphonBoltPath.populate(
                root, controlA, controlB, end,
                1.4f, 6.25f, expected);
        float[] unrelatedTimes = {
                0.0f, 0.125f, 19.75f, Float.MAX_VALUE,
                Float.NaN, -4.0f, Float.NEGATIVE_INFINITY
        };
        for (float unrelatedTime : unrelatedTimes) {
            DarkBallSiphonBoltPath.populate(
                    root, controlA, controlB, end,
                    1.4f, unrelatedTime, reused);
            for (Vector3f node : reused) {
                assertTrue(Float.isFinite(node.x));
                assertTrue(Float.isFinite(node.y));
                assertTrue(Float.isFinite(node.z));
            }
        }
        DarkBallSiphonBoltPath.populate(
                root, controlA, controlB, end,
                1.4f, 6.25f, reused);
        for (int node = 0; node < DarkBallSiphonBoltPath.NODE_COUNT; node++) {
            assertVectorEquals(expected[node], reused[node]);
        }
    }

    @Test
    void bendAmplitudeUsesAggressiveBodyScaleAndBoundedChordCap() {
        assertEquals(0.40f,
                DarkBallSiphonBoltPath.bendAmplitude(1.0f, 10.0f),
                EPSILON);
        assertEquals(0.95f,
                DarkBallSiphonBoltPath.bendAmplitude(100.0f, 10.0f),
                EPSILON);
        assertEquals(0.0f,
                DarkBallSiphonBoltPath.bendAmplitude(
                        Float.NaN, Float.POSITIVE_INFINITY),
                EPSILON);
    }

    @Test
    void populateReusesDestinationVectorsAndProducesFiniteDegeneratePath() {
        Vector3f point = new Vector3f(2.0f, -1.0f, 0.5f);
        Vector3f[] destination = destination();
        Vector3f[] identities = destination.clone();

        DarkBallSiphonBoltPath.populate(
                point, point, point, point,
                Float.NaN, Float.POSITIVE_INFINITY, destination);

        for (int node = 0; node < DarkBallSiphonBoltPath.NODE_COUNT; node++) {
            assertSame(identities[node], destination[node]);
            assertVectorEquals(point, destination[node]);
        }
    }

    private static float interiorDistance(Vector3f[] first,
                                          Vector3f[] second) {
        float distance = 0.0f;
        for (int node = 2;
             node <= DarkBallSiphonBoltPath.NODE_COUNT - 3;
             node++) {
            distance += first[node].distance(second[node]);
        }
        return distance;
    }

    private static float maximumPresentationTurn(Vector3f[] nodes) {
        Vector3f prismStart = presentationCurvePoint(nodes, 0.0f);
        Vector3f previousAxis = null;
        float greatestTurn = 0.0f;
        for (int prism = 0; prism < 16; prism++) {
            Vector3f prismEnd = presentationCurvePoint(nodes,
                    presentationBoundaryT(prism + 1));
            Vector3f axis = new Vector3f(prismEnd).sub(prismStart);
            assertTrue(Float.isFinite(axis.x)
                            && Float.isFinite(axis.y)
                            && Float.isFinite(axis.z),
                    "presentation prism axis must remain finite");
            assertTrue(axis.lengthSquared() > EPSILON * EPSILON,
                    "presentation prism must not collapse");
            axis.normalize();
            if (previousAxis != null) {
                greatestTurn = Math.max(greatestTurn,
                        (float) Math.acos(Math.max(-1.0f,
                                Math.min(1.0f, previousAxis.dot(axis)))));
            }
            previousAxis = axis;
            prismStart = prismEnd;
        }
        return greatestTurn;
    }

    private static float presentationBoundaryT(int boundaryIndex) {
        if (boundaryIndex <= 0) {
            return 0.0f;
        }
        if (boundaryIndex >= 16) {
            return 1.0f;
        }
        if (boundaryIndex <= 3) {
            return boundaryIndex / 13.0f;
        }
        int offsetIndex = boundaryIndex - 4;
        int block = offsetIndex / 4;
        int remainder = offsetIndex - block * 4;
        float pathUnits = 3.0f + block * 3.0f
                + (remainder == 0 ? 0.5f : remainder);
        return pathUnits / 13.0f;
    }

    private static Vector3f presentationCurvePoint(Vector3f[] nodes,
                                                   float curveT) {
        int segmentCount = nodes.length - 1;
        float scaled = Math.max(0.0f, Math.min(1.0f, curveT))
                * segmentCount;
        int segment = Math.min((int) Math.floor(scaled),
                segmentCount - 1);
        float segmentT = Math.max(0.0f,
                Math.min(1.0f, scaled - segment));
        Vector3f startTangent =
                presentationNodeTangent(nodes, segment);
        Vector3f endTangent =
                presentationNodeTangent(nodes, segment + 1);
        float t2 = segmentT * segmentT;
        float t3 = t2 * segmentT;
        return new Vector3f(nodes[segment])
                .mul(2.0f * t3 - 3.0f * t2 + 1.0f)
                .fma(t3 - 2.0f * t2 + segmentT, startTangent)
                .fma(-2.0f * t3 + 3.0f * t2, nodes[segment + 1])
                .fma(t3 - t2, endTangent);
    }

    private static Vector3f presentationNodeTangent(Vector3f[] nodes,
                                                    int index) {
        if (index <= 0) {
            return new Vector3f(nodes[1]).sub(nodes[0]).mul(0.68f);
        }
        if (index >= nodes.length - 1) {
            return new Vector3f(nodes[nodes.length - 1])
                    .sub(nodes[nodes.length - 2]).mul(0.68f);
        }

        Vector3f previousLeg =
                new Vector3f(nodes[index]).sub(nodes[index - 1]);
        Vector3f nextLeg =
                new Vector3f(nodes[index + 1]).sub(nodes[index]);
        float previousLength = previousLeg.length();
        float nextLength = nextLeg.length();
        Vector3f previousDirection =
                new Vector3f(previousLeg).normalize();
        Vector3f nextDirection = new Vector3f(nextLeg).normalize();
        Vector3f tangentDirection =
                new Vector3f(previousDirection).add(nextDirection);
        if (tangentDirection.lengthSquared() <= EPSILON * EPSILON) {
            tangentDirection.set(nextDirection);
        } else {
            tangentDirection.normalize();
        }
        float alignment = Math.max(-1.0f,
                Math.min(1.0f, previousDirection.dot(nextDirection)));
        float smoothT = Math.max(0.0f,
                Math.min(1.0f, (alignment - 0.94f) / 0.055f));
        smoothT = smoothT * smoothT * (3.0f - 2.0f * smoothT);
        float cornerRetention = 0.16f + 0.42f * smoothT;
        return tangentDirection.mul(
                Math.min(previousLength, nextLength) * cornerRetention);
    }

    private static Vector3f[] destination() {
        Vector3f[] destination =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        for (int node = 0; node < destination.length; node++) {
            destination[node] = new Vector3f(
                    node + 20.0f, node + 30.0f, node + 40.0f);
        }
        return destination;
    }

    private static void assertVectorEquals(Vector3f expected,
                                           Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    private static final class StraightGuide {
        private final Vector3f root = new Vector3f(0.0f, 0.0f, 0.0f);
        private final Vector3f controlA =
                new Vector3f(10.0f / 3.0f, 0.0f, 0.0f);
        private final Vector3f controlB =
                new Vector3f(20.0f / 3.0f, 0.0f, 0.0f);
        private final Vector3f end = new Vector3f(10.0f, 0.0f, 0.0f);
        private final float bodyRadius = 1.0f;

        private void populate(float time, Vector3f[] destination) {
            DarkBallSiphonBoltPath.populate(
                    root, controlA, controlB, end,
                    bodyRadius, time, destination);
        }
    }
}
