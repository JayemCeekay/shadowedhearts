package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowAuraStyleProfileTest {

    @Test
    void registryHasOneImmutableProfilePerStyleAndDefaultsToSignature() {
        assertEquals(EnumSet.allOf(ShadowAuraStyle.class),
                ShadowAuraStyleProfiles.all().stream()
                        .map(ShadowAuraStyleProfile::style)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(ShadowAuraStyle.values().length,
                ShadowAuraStyleProfiles.all().size());

        for (ShadowAuraStyle style : ShadowAuraStyle.values()) {
            ShadowAuraStyleProfile profile =
                    ShadowAuraStyleProfiles.forStyle(style);
            assertEquals(style, profile.style());
            assertSame(profile, ShadowAuraStyleProfiles.forStyle(style));
        }
        assertSame(ShadowAuraStyleProfiles.SIGNATURE,
                ShadowAuraStyleProfiles.forStyle(null));
        assertThrows(UnsupportedOperationException.class,
                () -> ShadowAuraStyleProfiles.all().add(
                        ShadowAuraStyleProfiles.SIGNATURE));
    }

    @Test
    void signatureProfileIsTheIdentityBaseline() {
        ShadowAuraStyleProfile signature = ShadowAuraStyleProfiles.SIGNATURE;

        assertIdentity(signature.emission().rateScale());
        assertIdentity(signature.emission().boneBudgetScale());
        assertIdentity(signature.emission().bodyAnchorFillScale());
        assertIdentity(signature.emission().bodyVolumeFillScale());
        assertIdentity(signature.emission().upperBodyFillScale());
        assertIdentity(signature.emission().fallbackEmitterScale());

        assertIdentityPuff(signature.broadHaze());
        assertIdentityPuff(signature.coreHaze());
        assertIdentityPuff(signature.wisp());
        assertIdentityPuff(signature.hotFleck());
        assertFalse(signature.burst().enabled());
        assertFalse(signature.filament().enabled());

        assertIdentity(signature.render().broadChannelScale());
        assertIdentity(signature.render().heatChannelScale());
        assertIdentity(signature.render().wispChannelScale());
        assertIdentity(signature.render().coverageScale());
        assertIdentity(signature.render().opacityScale());
        assertIdentity(signature.render().blurRadiusScale());
        assertIdentity(signature.render().pixelSizeScale());
    }

    @Test
    void colosseumProfileTargetsLargeSlowCloudsWithRestrainedWisps() {
        ShadowAuraStyleProfile signature = ShadowAuraStyleProfiles.SIGNATURE;
        ShadowAuraStyleProfile colosseum = ShadowAuraStyleProfiles.COLOSSEUM;

        assertEquals(new ShadowAuraStyleProfile.EmissionTuning(
                        0.62f, 0.70f, 0.30f,
                        0.24f, 0.18f, 0.55f),
                colosseum.emission());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        1.35f, 1.65f, 1.65f, 0.80f, 0.42f),
                colosseum.broadHaze());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        0.72f, 1.35f, 1.45f, 0.70f, 0.50f),
                colosseum.coreHaze());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        0.20f, 1.10f, 1.15f, 0.70f, 0.65f),
                colosseum.wisp());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        1.70f, 0.95f, 1.35f, 1.15f, 0.70f),
                colosseum.hotFleck());
        assertRenderScales(
                colosseum.render(),
                1.10f, 0.85f, 0.65f, 1.00f,
                0.90f, 1.22f, 1.00f);
        assertTrue(colosseum.emission().rateScale()
                < signature.emission().rateScale());
        assertTrue(colosseum.emission().bodyVolumeFillScale()
                < signature.emission().bodyVolumeFillScale());
        assertTrue(colosseum.broadHaze().spawnWeight()
                > signature.broadHaze().spawnWeight());
        assertTrue(colosseum.broadHaze().sizeScale()
                > signature.broadHaze().sizeScale());
        assertTrue(colosseum.broadHaze().lifetimeScale()
                > signature.broadHaze().lifetimeScale());
        assertTrue(colosseum.broadHaze().driftScale()
                < signature.broadHaze().driftScale());
        assertTrue(colosseum.wisp().spawnWeight()
                < signature.wisp().spawnWeight());
        assertTrue(colosseum.hotFleck().spawnWeight()
                > signature.hotFleck().spawnWeight());
        assertTrue(colosseum.render().blurRadiusScale()
                > signature.render().blurRadiusScale());
        assertFalse(colosseum.burst().enabled());
        assertFalse(colosseum.filament().enabled());
    }

    @Test
    void xdProfileTargetsSparseBurstsAndCoherentBrightFilaments() {
        ShadowAuraStyleProfile xd = ShadowAuraStyleProfiles.XD_FAITHFUL;

        assertEquals(new ShadowAuraStyleProfile.EmissionTuning(
                        0.42f, 0.48f, 0.0f,
                        0.0f, 0.0f, 0.32f),
                xd.emission());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        0.55f, 1.18f, 0.82f, 0.80f, 0.78f),
                xd.broadHaze());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        0.72f, 1.12f, 0.82f, 0.92f, 0.84f),
                xd.coreHaze());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        0.32f, 0.88f, 0.72f, 0.96f, 0.96f),
                xd.wisp());
        assertEquals(new ShadowAuraStyleProfile.PuffTuning(
                        0.82f, 0.86f, 0.78f, 1.18f, 0.92f),
                xd.hotFleck());
        assertRenderScales(
                xd.render(),
                0.76f, 1.24f, 1.06f, 0.58f,
                0.90f, 0.86f, 1.00f);
        assertEquals(0.0f, xd.emission().bodyAnchorFillScale());
        assertEquals(0.0f, xd.emission().bodyVolumeFillScale());
        assertEquals(0.0f, xd.emission().upperBodyFillScale());
        assertTrue(xd.emission().rateScale() < 0.5f);
        assertTrue(xd.render().coverageScale()
                < ShadowAuraStyleProfiles.SIGNATURE.render().coverageScale());
        assertTrue(xd.render().heatChannelScale()
                > ShadowAuraStyleProfiles.SIGNATURE.render().heatChannelScale());

        ShadowAuraStyleProfile.BurstTuning burst = xd.burst();
        assertTrue(burst.enabled());
        assertEquals(0.28f, burst.spawnChancePerTick());
        assertEquals(4, burst.targetVisibleMin());
        assertEquals(8, burst.targetVisibleMax());
        assertEquals(0.20f, burst.sizeMinModelScale());
        assertEquals(0.36f, burst.sizeMaxModelScale());
        assertEquals(24, burst.lifetimeMinTicks());
        assertEquals(40, burst.lifetimeMaxTicks());
        assertEquals(1, burst.moteCountMin());
        assertEquals(3, burst.moteCountMax());

        ShadowAuraStyleProfile.FilamentTuning filament = xd.filament();
        assertTrue(filament.enabled());
        assertEquals(0.92f, filament.clusterChance());
        assertEquals(4, filament.segmentCountMin());
        assertEquals(7, filament.segmentCountMax());
        assertEquals(1, filament.branchCountMin());
        assertEquals(3, filament.branchCountMax());
        assertEquals(0.014f, filament.coreWidthModelScale());
        assertEquals(3.2f, filament.haloWidthScale());
        assertEquals(1.35f, filament.intensityScale());
        assertEquals(0.92f, filament.lifetimeScale());
        assertEquals(0.90f, filament.driftFollow());
    }

    @Test
    void nestedTuningRecordsRejectInvalidOrInconsistentValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShadowAuraStyleProfile.EmissionTuning(
                        Float.NaN, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f));
        assertThrows(IllegalArgumentException.class,
                () -> new ShadowAuraStyleProfile.PuffTuning(
                        1.0f, -0.1f, 1.0f, 1.0f, 1.0f));
        assertThrows(IllegalArgumentException.class,
                () -> new ShadowAuraStyleProfile.BurstTuning(
                        true, 0.5f, 8, 4,
                        0.2f, 0.4f, 24, 40, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ShadowAuraStyleProfile.BurstTuning(
                        true, 0.5f, 0, 4,
                        0.2f, 0.4f, 24, 40, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new ShadowAuraStyleProfile.FilamentTuning(
                        true, 1.1f, 4, 7, 1, 3,
                        0.014f, 3.2f, 1.0f, 1.0f, 0.9f));
        assertThrows(IllegalArgumentException.class,
                () -> new ShadowAuraStyleProfile.FilamentTuning(
                        true, 0.9f, 0, 7, 1, 3,
                        0.014f, 3.2f, 1.0f, 1.0f, 0.9f));
        assertThrows(IllegalArgumentException.class,
                () -> new ShadowAuraStyleProfile.RenderTuning(
                        1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, Float.POSITIVE_INFINITY, 1.0f));
    }

    private static void assertIdentityPuff(
            ShadowAuraStyleProfile.PuffTuning tuning) {
        assertIdentity(tuning.spawnWeight());
        assertIdentity(tuning.sizeScale());
        assertIdentity(tuning.lifetimeScale());
        assertIdentity(tuning.densityScale());
        assertIdentity(tuning.driftScale());
    }

    private static void assertIdentity(float value) {
        assertEquals(1.0f, value);
    }

    private static void assertRenderScales(
            ShadowAuraStyleProfile.RenderTuning tuning,
            float broad,
            float heat,
            float wisp,
            float coverage,
            float opacity,
            float blurRadius,
            float pixelSize) {
        assertEquals(broad, tuning.broadChannelScale());
        assertEquals(heat, tuning.heatChannelScale());
        assertEquals(wisp, tuning.wispChannelScale());
        assertEquals(coverage, tuning.coverageScale());
        assertEquals(opacity, tuning.opacityScale());
        assertEquals(blurRadius, tuning.blurRadiusScale());
        assertEquals(pixelSize, tuning.pixelSizeScale());
    }
}
