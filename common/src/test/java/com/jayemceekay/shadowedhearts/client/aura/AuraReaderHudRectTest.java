package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraReaderHudRectTest {
    @Test
    void dimensionsUseExclusiveRightAndBottomEdges() {
        AuraReaderHudRect rectangle = new AuraReaderHudRect(4, 7, 19, 23);

        assertEquals(15, rectangle.width());
        assertEquals(16, rectangle.height());
    }

    @Test
    void touchingExclusiveEdgesDoNotIntersect() {
        AuraReaderHudRect left = new AuraReaderHudRect(0, 0, 10, 10);
        AuraReaderHudRect touching = new AuraReaderHudRect(10, 2, 14, 8);
        AuraReaderHudRect overlapping = new AuraReaderHudRect(9, 2, 14, 8);

        assertFalse(left.intersects(touching));
        assertTrue(left.intersects(overlapping));
    }

    @Test
    void clampPreservesSizeWhenPossibleAndShrinksWhenRequired() {
        assertEquals(
                new AuraReaderHudRect(0, 6, 10, 14),
                new AuraReaderHudRect(-5, 6, 5, 14).clamp(20, 20));
        assertEquals(
                new AuraReaderHudRect(0, 0, 6, 4),
                new AuraReaderHudRect(-5, -5, 10, 10).clamp(6, 4));
    }

    @Test
    void invalidBoundsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuraReaderHudRect(5, 0, 4, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuraReaderHudRect(0, 5, 1, 4));
    }
}
