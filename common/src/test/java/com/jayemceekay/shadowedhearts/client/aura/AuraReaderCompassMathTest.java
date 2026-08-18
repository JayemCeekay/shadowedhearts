package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuraReaderCompassMathTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void convertsWorldDirectionsToMinecraftYaw() {
        assertEquals(-90.0f, AuraReaderCompassMath.worldYaw(1.0, 0.0), EPSILON, "east");
        assertEquals(0.0f, AuraReaderCompassMath.worldYaw(0.0, 1.0), EPSILON, "south");
        assertEquals(90.0f, AuraReaderCompassMath.worldYaw(-1.0, 0.0), EPSILON, "west");
        assertEquals(180.0f, AuraReaderCompassMath.worldYaw(0.0, -1.0), EPSILON, "north");
    }

    @Test
    void normalizesSignedDeltasAcrossTheWrapBoundary() {
        assertEquals(-170.0f, AuraReaderCompassMath.normalizeSignedDelta(190.0f), EPSILON);
        assertEquals(170.0f, AuraReaderCompassMath.normalizeSignedDelta(-190.0f), EPSILON);
        assertEquals(20.0f, AuraReaderCompassMath.signedDelta(170.0f, -170.0f), EPSILON);
        assertEquals(-20.0f, AuraReaderCompassMath.signedDelta(-170.0f, 170.0f), EPSILON);
    }

    @Test
    void mapsVisibleArcAndPinsOutsideBearingsToEdges() {
        assertTrue(AuraReaderCompassMath.isInsideVisibleArc(45.0f, 90.0f));
        assertFalse(AuraReaderCompassMath.isInsideVisibleArc(45.1f, 90.0f));
        assertEquals(-0.5f, AuraReaderCompassMath.normalizedXPosition(-22.5f, 90.0f), EPSILON);
        assertEquals(1.0f, AuraReaderCompassMath.normalizedXPosition(80.0f, 90.0f), EPSILON);
        assertEquals(-1, AuraReaderCompassMath.edgeSide(-80.0f));
        assertEquals(1, AuraReaderCompassMath.edgeSide(80.0f));
        assertEquals(0, AuraReaderCompassMath.edgeSide(0.0f));
    }

    @Test
    void interpolatesAcrossNorthByTheShortestRoute() {
        assertEquals(170.0f, AuraReaderCompassMath.lerpShortestAngle(170.0f, -170.0f, 0.0f), EPSILON);
        assertEquals(180.0f, AuraReaderCompassMath.lerpShortestAngle(170.0f, -170.0f, 0.5f), EPSILON);
        assertEquals(-170.0f, AuraReaderCompassMath.lerpShortestAngle(170.0f, -170.0f, 1.0f), EPSILON);
    }

    @Test
    void fadesOnlyNearBothCompassEdges() {
        assertEquals(1.0f, AuraReaderCompassMath.edgeFade(0.0f, 0.72f), EPSILON);
        assertEquals(1.0f, AuraReaderCompassMath.edgeFade(-0.72f, 0.72f), EPSILON);
        assertEquals(
                AuraReaderCompassMath.edgeFade(0.86f, 0.72f),
                AuraReaderCompassMath.edgeFade(-0.86f, 0.72f),
                EPSILON
        );
        assertEquals(0.0f, AuraReaderCompassMath.edgeFade(1.0f, 0.72f), EPSILON);
        assertEquals(0.0f, AuraReaderCompassMath.edgeFade(-1.0f, 0.72f), EPSILON);
    }

    @Test
    void bendsTheCompassUpwardWithoutMovingItsEnds() {
        assertEquals(-4.0f, AuraReaderCompassMath.upwardCurveOffset(0.0f, 4.0f), EPSILON);
        assertEquals(-3.0f, AuraReaderCompassMath.upwardCurveOffset(0.5f, 4.0f), EPSILON);
        assertEquals(-3.0f, AuraReaderCompassMath.upwardCurveOffset(-0.5f, 4.0f), EPSILON);
        assertEquals(0.0f, AuraReaderCompassMath.upwardCurveOffset(1.0f, 4.0f), EPSILON);
        assertEquals(0.0f, AuraReaderCompassMath.upwardCurveOffset(-1.0f, 4.0f), EPSILON);
    }

    @Test
    void widensTheCompassCenterAndTapersBothEnds() {
        assertEquals(14.0f, AuraReaderCompassMath.taperedRailThickness(0.0f, 14.0f, 3.0f), EPSILON);
        assertEquals(11.25f, AuraReaderCompassMath.taperedRailThickness(0.5f, 14.0f, 3.0f), EPSILON);
        assertEquals(
                AuraReaderCompassMath.taperedRailThickness(0.75f, 14.0f, 3.0f),
                AuraReaderCompassMath.taperedRailThickness(-0.75f, 14.0f, 3.0f),
                EPSILON
        );
        assertEquals(3.0f, AuraReaderCompassMath.taperedRailThickness(1.0f, 14.0f, 3.0f), EPSILON);
        assertEquals(3.0f, AuraReaderCompassMath.taperedRailThickness(-1.0f, 14.0f, 3.0f), EPSILON);
    }
}
