package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraReaderScannerInfoFrameLayoutTest {
    private static final AuraReaderHudRect NO_SAFE_ZONE =
            new AuraReaderHudRect(0, 0, 0, 0);

    @Test
    void nativeGeometryMatchesEveryCobblemonTierAndSide() {
        assertGeometry(0, -120, -80, 92, 55, -74, -75, -1);
        assertGeometry(0, 28, -80, 92, 55, 74, -75, 1);
        assertGeometry(1, -177, -26, 120, 20, -131, -22, -1);
        assertGeometry(1, 57, -26, 120, 20, 131, -22, 1);
        assertGeometry(2, -177, 6, 120, 20, -131, 14, -1);
        assertGeometry(2, 57, 6, 120, 20, 131, 14, 1);
        assertGeometry(3, -120, 25, 92, 55, -74, 67, -1);
        assertGeometry(3, 28, 25, 92, 55, 74, 67, 1);
    }

    @Test
    void innerTierTextCentersExcludeTheStem() {
        AuraReaderScannerInfoFrameLayout.NativeGeometry left =
                AuraReaderScannerInfoFrameLayout.geometry(
                        2, AuraReaderScannerInfoFrameLayout.Side.LEFT);
        AuraReaderScannerInfoFrameLayout.NativeGeometry right =
                AuraReaderScannerInfoFrameLayout.geometry(
                        2, AuraReaderScannerInfoFrameLayout.Side.RIGHT);

        assertEquals(46, left.textCenterXOffset() - left.xOffset());
        assertEquals(74, right.textCenterXOffset() - right.xOffset());
        assertEquals(
                AuraReaderScannerInfoFrameLayout.INNER_FRAME_STEM_WIDTH,
                74 - 46);
    }

    @Test
    void auraAndTargetUsePreferredSidesAtOneUniformScale() {
        AuraReaderScannerInfoFrameLayout.Layout layout =
                AuraReaderScannerInfoFrameLayout.resolve(request(
                        960,
                        540,
                        480.0f,
                        270.0f,
                        1.5f,
                        new AuraReaderHudRect(0, 0, 190, 540),
                        new AuraReaderHudRect(0, 440, 960, 540)));

        AuraReaderScannerInfoFrameLayout.FramePlacement aura =
                layout.auraFrame().orElseThrow();
        AuraReaderScannerInfoFrameLayout.FramePlacement target =
                layout.targetFrame().orElseThrow();

        assertEquals(AuraReaderScannerInfoFrameLayout.Side.RIGHT, aura.side());
        assertEquals(0, aura.tier());
        assertEquals(522.0f, aura.screenLeft(), 0.001f);
        assertEquals(150.0f, aura.screenTop(), 0.001f);
        assertEquals(138.0f, aura.screenWidth(), 0.001f);
        assertEquals(82.5f, aura.screenHeight(), 0.001f);
        assertEquals(591.0f, aura.screenTextCenterX(), 0.001f);
        assertEquals(157.5f, aura.screenTextY(), 0.001f);
        assertEquals(new AuraReaderHudRect(522, 150, 660, 233), aura.bounds());

        assertEquals(AuraReaderScannerInfoFrameLayout.Side.LEFT, target.side());
        assertEquals(2, target.tier());
        assertEquals(214.5f, target.screenLeft(), 0.001f);
        assertEquals(279.0f, target.screenTop(), 0.001f);
        assertEquals(180.0f, target.screenWidth(), 0.001f);
        assertEquals(30.0f, target.screenHeight(), 0.001f);
        assertEquals(283.5f, target.screenTextCenterX(), 0.001f);
        assertEquals(291.0f, target.screenTextY(), 0.001f);
        assertEquals(new AuraReaderHudRect(214, 279, 395, 309), target.bounds());

        assertEquals(2, layout.visibleFrames().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> layout.visibleFrames().clear());
    }

    @Test
    void partySafeZoneFlipsPreferredLeftTargetToRight() {
        AuraReaderScannerInfoFrameLayout.Layout layout =
                AuraReaderScannerInfoFrameLayout.resolve(request(
                        640,
                        360,
                        320.0f,
                        180.0f,
                        1.0f,
                        new AuraReaderHudRect(0, 0, 280, 360),
                        new AuraReaderHudRect(0, 300, 640, 360)));

        AuraReaderScannerInfoFrameLayout.FramePlacement target =
                layout.targetFrame().orElseThrow();
        assertEquals(AuraReaderScannerInfoFrameLayout.Side.RIGHT, target.side());
        assertEquals(new AuraReaderHudRect(377, 186, 497, 206), target.bounds());
        assertEquals(131, target.geometry().textCenterXOffset());
    }

    @Test
    void viewportClippingFlipsPreferredRightAuraToLeft() {
        Optional<AuraReaderScannerInfoFrameLayout.FramePlacement> placement =
                AuraReaderScannerInfoFrameLayout.choose(
                        0,
                        AuraReaderScannerInfoFrameLayout.Side.RIGHT,
                        request(
                                300,
                                240,
                                190.0f,
                                120.0f,
                                1.0f,
                                NO_SAFE_ZONE,
                                NO_SAFE_ZONE));

        AuraReaderScannerInfoFrameLayout.FramePlacement aura =
                placement.orElseThrow();
        assertEquals(AuraReaderScannerInfoFrameLayout.Side.LEFT, aura.side());
        assertEquals(new AuraReaderHudRect(70, 40, 162, 95), aura.bounds());
    }

    @Test
    void frameIsOmittedWhenBottomSafeZoneBlocksBothSides() {
        AuraReaderScannerInfoFrameLayout.Layout layout =
                AuraReaderScannerInfoFrameLayout.resolve(request(
                        640,
                        360,
                        320.0f,
                        180.0f,
                        1.0f,
                        NO_SAFE_ZONE,
                        new AuraReaderHudRect(0, 190, 640, 360)));

        assertTrue(layout.auraFrame().isPresent());
        assertTrue(layout.targetFrame().isEmpty());
    }

    @Test
    void rejectsUnknownTiersAndInvalidScale() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AuraReaderScannerInfoFrameLayout.geometry(
                        4, AuraReaderScannerInfoFrameLayout.Side.LEFT));
        assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        640,
                        360,
                        320.0f,
                        180.0f,
                        0.0f,
                        NO_SAFE_ZONE,
                        NO_SAFE_ZONE));
    }

    private static void assertGeometry(
            int tier,
            int x,
            int y,
            int width,
            int height,
            int textX,
            int textY,
            int sideSign) {
        AuraReaderScannerInfoFrameLayout.Side side = sideSign < 0
                ? AuraReaderScannerInfoFrameLayout.Side.LEFT
                : AuraReaderScannerInfoFrameLayout.Side.RIGHT;
        assertEquals(
                new AuraReaderScannerInfoFrameLayout.NativeGeometry(
                        tier,
                        side,
                        x,
                        y,
                        width,
                        height,
                        textX,
                        textY),
                AuraReaderScannerInfoFrameLayout.geometry(tier, side));
    }

    private static AuraReaderScannerInfoFrameLayout.Request request(
            int viewportWidth,
            int viewportHeight,
            float scannerCenterX,
            float scannerCenterY,
            float scannerScale,
            AuraReaderHudRect partySafeZone,
            AuraReaderHudRect bottomSafeZone) {
        return new AuraReaderScannerInfoFrameLayout.Request(
                viewportWidth,
                viewportHeight,
                scannerCenterX,
                scannerCenterY,
                scannerScale,
                partySafeZone,
                bottomSafeZone);
    }
}
