package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraReaderHudLayoutResolverTest {
    @Test
    void wideSixteenByNineLayoutUsesPreferredSidesAndSafeZones() {
        AuraReaderHudLayoutResolver.Layout layout =
                AuraReaderHudLayoutResolver.resolve(request(
                        1920,
                        1080,
                        188,
                        12,
                        104,
                        8,
                        AuraReaderHudLayoutPreset.DEFAULT,
                        41L));

        assertEquals(
                1920,
                layout.compassRail().left()
                        + layout.compassRail().right(),
                "the rail center must remain at the screen center");
        assertTrue(layout.targetPanel().right() <= layout.reticle().left());
        assertTrue(layout.auraPanel().left() >= layout.reticle().right());
        assertFalse(layout.targetPanel().intersects(layout.partySafeZone()));
        assertFalse(layout.targetPanel().intersects(layout.bottomSafeZone()));
        assertFalse(layout.auraPanel().intersects(layout.bottomSafeZone()));
        assertFalse(layout.targetPanel().intersects(layout.auraPanel()));
        assertAllInsideViewport(layout, 1920, 1080);
    }

    @Test
    void smallFourByThreeLayoutCompactsWithoutEnteringReservedSpace() {
        AuraReaderHudLayoutResolver.Layout layout =
                AuraReaderHudLayoutResolver.resolve(request(
                        400,
                        300,
                        84,
                        8,
                        84,
                        6,
                        AuraReaderHudLayoutPreset.COMPACT,
                        99L));

        assertEquals(
                400,
                layout.compassRail().left()
                        + layout.compassRail().right());
        assertFalse(layout.targetPanel().intersects(layout.partySafeZone()));
        assertFalse(layout.targetPanel().intersects(layout.bottomSafeZone()));
        assertFalse(layout.auraPanel().intersects(layout.bottomSafeZone()));
        assertFalse(layout.targetPanel().intersects(layout.auraPanel()));
        assertAllInsideViewport(layout, 400, 300);
    }

    @Test
    void ultrawidePresetKeepsPanelsNearTheReticle() {
        AuraReaderHudLayoutResolver.Layout layout =
                AuraReaderHudLayoutResolver.resolve(request(
                        3440,
                        1440,
                        210,
                        12,
                        110,
                        16,
                        AuraReaderHudLayoutPreset.ULTRAWIDE,
                        8L));

        int auraCenter = (layout.auraPanel().left()
                + layout.auraPanel().right()) / 2;
        int screenCenter = 3440 / 2;
        assertTrue(Math.abs(auraCenter - screenCenter) < 3440 / 4);
        assertTrue(layout.reticle().width() <= 460);
        assertAllInsideViewport(layout, 3440, 1440);
    }

    @Test
    void partyCollisionMovesTargetAwayFromPreferredLeftSlot() {
        AuraReaderHudLayoutResolver.Layout layout =
                AuraReaderHudLayoutResolver.resolve(request(
                        640,
                        360,
                        242,
                        12,
                        90,
                        8,
                        AuraReaderHudLayoutPreset.DEFAULT,
                        101L));

        assertFalse(layout.targetPanel().intersects(layout.partySafeZone()));
        assertFalse(layout.targetPanel().intersects(layout.bottomSafeZone()));
        assertFalse(layout.targetPanel().intersects(layout.reticle()));
        assertTrue(
                layout.targetPanel().right() > layout.reticle().left(),
                "a blocked reticle-left slot must use a fallback anchor");
    }

    @Test
    void identicalRequestsProduceIdenticalFallbackLayouts() {
        AuraReaderHudLayoutResolver.Request request = request(
                640,
                360,
                242,
                12,
                90,
                8,
                AuraReaderHudLayoutPreset.DEFAULT,
                0x5A5A5A5AL);

        assertEquals(
                AuraReaderHudLayoutResolver.resolve(request),
                AuraReaderHudLayoutResolver.resolve(request));
    }

    @Test
    void unequalEndCapsCannotPullTheCompassRailOffCenter() {
        AuraReaderHudLayoutResolver.Layout layout =
                AuraReaderHudLayoutResolver.resolve(request(
                        1365,
                        768,
                        150,
                        12,
                        100,
                        10,
                        AuraReaderHudLayoutPreset.STREAMER,
                        2L));

        assertNotEquals(layout.modeCap().width(), layout.chargeCap().width());
        assertEquals(
                1365,
                layout.compassRail().left()
                        + layout.compassRail().right());
    }

    @Test
    void reticleStaysOnTheCrosshairAtLargeGuiScale() {
        AuraReaderHudLayoutResolver.Layout layout =
                AuraReaderHudLayoutResolver.resolve(request(
                        427,
                        240,
                        72,
                        12,
                        104,
                        8,
                        AuraReaderHudLayoutPreset.DEFAULT,
                        7L));

        assertEquals(427 / 2, (layout.reticle().left() + layout.reticle().right()) / 2);
        assertEquals(240 / 2, (layout.reticle().top() + layout.reticle().bottom()) / 2);
        assertTrue(layout.reticle().width() > 0);
    }

    private static AuraReaderHudLayoutResolver.Request request(
            int screenWidth,
            int screenHeight,
            int partyOverlayWidth,
            int partyOverlayMargin,
            int bottomSafeMargin,
            int rightOverlayMargin,
            AuraReaderHudLayoutPreset preset,
            long seed) {
        return new AuraReaderHudLayoutResolver.Request(
                screenWidth,
                screenHeight,
                1.0f,
                0.42f,
                8,
                1.0f,
                bottomSafeMargin,
                partyOverlayWidth,
                partyOverlayMargin,
                8,
                rightOverlayMargin,
                preset,
                seed);
    }

    private static void assertAllInsideViewport(
            AuraReaderHudLayoutResolver.Layout layout,
            int screenWidth,
            int screenHeight) {
        List<AuraReaderHudRect> rectangles = List.of(
                layout.compassRail(),
                layout.modeCap(),
                layout.chargeCap(),
                layout.signalPopover(),
                layout.reticle(),
                layout.targetPanel(),
                layout.auraPanel(),
                layout.bottomSafeZone(),
                layout.partySafeZone());
        for (AuraReaderHudRect rectangle : rectangles) {
            assertTrue(rectangle.left() >= 0, rectangle.toString());
            assertTrue(rectangle.top() >= 0, rectangle.toString());
            assertTrue(rectangle.right() <= screenWidth, rectangle.toString());
            assertTrue(rectangle.bottom() <= screenHeight, rectangle.toString());
        }
    }
}
