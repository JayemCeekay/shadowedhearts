package com.jayemceekay.shadowedhearts.common.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallCaptureTimingsTest {
    @Test
    void fiveSecondDefaultVfxCompletesBeforeHitReleases() {
        assertEquals(5.0f, DarkBallCaptureTimings.VFX_DURATION_SECONDS);
        assertTrue(DarkBallCaptureTimings.HIT_TO_FALL_DELAY_SECONDS
                > DarkBallCaptureTimings.VFX_DURATION_SECONDS);
        assertEquals(5.35f, DarkBallCaptureTimings.HIT_TO_FALL_DELAY_SECONDS,
                0.0001f);
    }

    @Test
    void timingScalePreservesTheOriginalPhaseRatios() {
        assertEquals(5.0f,
                DarkBallCaptureTimings.BASE_VFX_DURATION_SECONDS
                        * DarkBallCaptureTimings.TIMING_SCALE,
                0.0001f);
    }

    @Test
    void namedAndNumericDiagnosticProfilesResolveDeterministically() {
        assertEquals(30.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("30"));
        assertEquals(30.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds(
                        "diagnostic"));
        assertEquals(30.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("trace"));
        assertEquals(30.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds(
                        "ultraslow"));
        assertEquals(30.35f,
                DarkBallCaptureTimings.configuredHitToFallDelaySeconds(
                        "diagnostic", 30.0f));
        assertEquals(10.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("legacy"));
        assertEquals(7.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("movie"));
        assertEquals(5.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("5"));
        assertEquals(3.7f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("original"));
        assertEquals(1.85f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("cobblemon"));
        assertEquals(2.2f,
                DarkBallCaptureTimings.configuredHitToFallDelaySeconds(
                        "cobblemon", 1.85f));
        assertEquals(5.35f,
                DarkBallCaptureTimings.configuredHitToFallDelaySeconds(
                        "5", 5.0f));
        assertEquals(5.0f,
                DarkBallCaptureTimings.configuredVfxDurationSeconds("unknown"));
    }
}
