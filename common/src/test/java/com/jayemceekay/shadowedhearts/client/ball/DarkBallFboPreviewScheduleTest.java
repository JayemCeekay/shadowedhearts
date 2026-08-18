package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallFboPreviewScheduleTest {
    private static final float EPSILON = 0.00001f;

    @Test
    void oneSamplePerPhasePreservesTheSixLegacyMidpoints() {
        List<DarkBallFboPreviewSchedule.Phase> phases =
                DarkBallFboPreviewSchedule.phases();
        List<DarkBallFboPreviewSchedule.CapturePoint> points =
                DarkBallFboPreviewSchedule.capturePoints(1);

        assertEquals(6, phases.size());
        assertEquals(6, points.size());
        for (int index = 0; index < phases.size(); index++) {
            DarkBallFboPreviewSchedule.Phase phase = phases.get(index);
            DarkBallFboPreviewSchedule.CapturePoint point =
                    points.get(index);
            assertEquals(index, phase.index());
            assertEquals(phase, point.phase());
            assertEquals(0, point.sampleIndex());
            assertEquals(1, point.sampleCount());
            assertEquals(
                    (phase.startAge() + phase.endAge()) * 0.5f,
                    point.targetAge(),
                    EPSILON);
            assertTrue(Float.isFinite(point.targetAge()));
            assertTrue(phase.startAge() < point.targetAge());
            assertTrue(point.targetAge() < phase.endAge());
            assertTrue(point.targetAge() < DarkBallCaptureVfx.VFX_END);
        }
    }

    @Test
    void fiveRequestedSamplesProduceTwentySixAdaptiveInteriorShots() {
        List<DarkBallFboPreviewSchedule.CapturePoint> points =
                DarkBallFboPreviewSchedule.capturePoints(5);
        assertEquals(26, points.size());

        float previousTarget = -1.0f;
        for (int index = 0; index < points.size(); index++) {
            DarkBallFboPreviewSchedule.CapturePoint point =
                    points.get(index);
            assertEquals(index, point.globalIndex());
            assertEquals(index + 1, point.shotNumber());
            assertTrue(Float.isFinite(point.targetAge()));
            assertTrue(point.targetAge() > previousTarget,
                    "globally sorted targets must be strictly ordered");
            assertTrue(point.phase().startAge() < point.targetAge());
            assertTrue(point.targetAge() < point.phase().endAge());
            assertTrue(point.targetAge() < DarkBallCaptureVfx.VFX_END);
            previousTarget = point.targetAge();
        }

        for (DarkBallFboPreviewSchedule.Phase phase
                : DarkBallFboPreviewSchedule.phases()) {
            List<DarkBallFboPreviewSchedule.CapturePoint> phasePoints =
                    points.stream()
                            .filter(point -> point.phase().equals(phase))
                            .toList();
            int expectedCount = phase.index() == 5 ? 1 : 5;
            assertEquals(expectedCount, phasePoints.size());
            for (int sample = 0; sample < phasePoints.size(); sample++) {
                DarkBallFboPreviewSchedule.CapturePoint point =
                        phasePoints.get(sample);
                assertEquals(sample, point.sampleIndex());
                assertEquals(expectedCount, point.sampleCount());
                float expectedFraction =
                        (sample + 0.5f) / expectedCount;
                float actualFraction =
                        (point.targetAge() - phase.startAge())
                                / (phase.endAge() - phase.startAge());
                assertEquals(expectedFraction, actualFraction, EPSILON);
            }
        }

        DarkBallFboPreviewSchedule.CapturePoint fade =
                points.stream()
                        .filter(point -> point.phase().index() == 5)
                        .findFirst()
                        .orElseThrow();
        assertEquals(DarkBallCaptureVfx.VFX_END * 0.995f,
                fade.targetAge(), EPSILON);

        int overlappingTracks = 0;
        for (int index = 1; index < points.size(); index++) {
            if (points.get(index - 1).phase().index()
                    > points.get(index).phase().index()) {
                overlappingTracks++;
            }
        }
        assertTrue(overlappingTracks > 0,
                "formation and turbulence are overlapping diagnostic tracks");
    }

    @Test
    void defaultTrackerUsesTheFiveSampleDeepTraceAndClampsOverrides() {
        DarkBallFboPreviewSchedule.Tracker tracker =
                new DarkBallFboPreviewSchedule.Tracker();

        assertEquals(26, tracker.capturePointCount());
        assertEquals(6,
                DarkBallFboPreviewSchedule.capturePoints(0).size());
        assertEquals(26,
                DarkBallFboPreviewSchedule.capturePoints(99).size());
    }

    @Test
    void trackerQueuesCrossingsOnceAndWaitsForSubmission() {
        DarkBallFboPreviewSchedule.Tracker tracker =
                new DarkBallFboPreviewSchedule.Tracker(5);
        DarkBallFboPreviewSchedule.CapturePoint first =
                DarkBallFboPreviewSchedule.capturePoints(5).getFirst();

        tracker.observe(41L, first.targetAge() - 0.001f);
        assertNull(tracker.pendingCapture());
        assertEquals(1, tracker.runOrdinal());

        tracker.observe(41L, first.targetAge());
        assertEquals(first, tracker.pendingCapture());
        assertEquals(1, tracker.pendingCount());
        assertEquals(0, tracker.submittedCount());

        tracker.observe(41L, first.targetAge() + 0.05f);
        assertEquals(1, tracker.pendingCount(),
                "repeated frames must not duplicate a sample");

        DarkBallFboPreviewSchedule.CapturePoint pending =
                tracker.pendingCapture();
        tracker.markSubmitted(pending);
        assertNull(tracker.pendingCapture());
        assertEquals(1, tracker.submittedCount());

        tracker.observe(41L, first.targetAge() + 0.051f);
        assertNull(tracker.pendingCapture(),
                "a submitted sample must never be queued again");
    }

    @Test
    void overlappingTracksQueueInChronologicalOrderOneAtATime() {
        List<DarkBallFboPreviewSchedule.CapturePoint> points =
                DarkBallFboPreviewSchedule.capturePoints(5);
        int closeIndex = -1;
        for (int index = 0; index < points.size() - 1; index++) {
            float gap = points.get(index + 1).targetAge()
                    - points.get(index).targetAge();
            if (gap < DarkBallFboPreviewSchedule
                    .MAX_CAPTURE_LATENESS_SECONDS) {
                closeIndex = index;
                break;
            }
        }
        assertTrue(closeIndex >= 0);
        DarkBallFboPreviewSchedule.CapturePoint first =
                points.get(closeIndex);
        DarkBallFboPreviewSchedule.CapturePoint second =
                points.get(closeIndex + 1);

        DarkBallFboPreviewSchedule.Tracker tracker =
                new DarkBallFboPreviewSchedule.Tracker(5);
        tracker.observe(70L, first.targetAge() - 0.001f);
        tracker.observe(70L, second.targetAge());
        assertEquals(2, tracker.pendingCount());
        assertEquals(first, tracker.pendingCapture());

        tracker.markSubmitted(tracker.pendingCapture());
        assertEquals(second, tracker.pendingCapture());
        assertEquals(1, tracker.submittedCount());
    }

    @Test
    void stalePendingCaptureIsEvictedBeforeItCanBeMislabelled() {
        DarkBallFboPreviewSchedule.Tracker tracker =
                new DarkBallFboPreviewSchedule.Tracker(5);
        DarkBallFboPreviewSchedule.CapturePoint first =
                DarkBallFboPreviewSchedule.capturePoints(5).getFirst();

        tracker.observe(81L, first.targetAge());
        assertEquals(first, tracker.pendingCapture());
        List<DarkBallFboPreviewSchedule.CapturePoint> skipped =
                tracker.observe(
                        81L,
                        first.targetAge()
                                + DarkBallFboPreviewSchedule
                                .MAX_CAPTURE_LATENESS_SECONDS
                                + 0.001f);

        assertEquals(List.of(first), skipped);
        assertNull(
                tracker.pendingCapture(),
                "evicting a stale shot must not pull a future target "
                        + "forward");
        DarkBallFboPreviewSchedule.CapturePoint second =
                DarkBallFboPreviewSchedule.capturePoints(5).get(1);
        tracker.observe(81L, second.targetAge());
        assertEquals(second, tracker.pendingCapture());
        assertEquals(1, tracker.skippedCount());
        assertEquals(0, tracker.submittedCount());
    }

    @Test
    void lateFirstObservationSkipsEveryHistoricalShot() {
        DarkBallFboPreviewSchedule.Tracker tracker =
                new DarkBallFboPreviewSchedule.Tracker(5);
        List<DarkBallFboPreviewSchedule.CapturePoint> skipped =
                tracker.observe(
                        72L,
                        DarkBallCaptureVfx.VFX_END
                                + DarkBallFboPreviewSchedule
                                .MAX_CAPTURE_LATENESS_SECONDS
                                + 0.01f);

        assertEquals(26, skipped.size());
        assertEquals(26, tracker.skippedCount());
        assertEquals(0, tracker.pendingCount());
        assertNull(tracker.pendingCapture());
    }

    @Test
    void newGenerationOrRewoundAgeStartsANewRun() {
        DarkBallFboPreviewSchedule.Tracker tracker =
                new DarkBallFboPreviewSchedule.Tracker(1);
        float firstTarget =
                DarkBallFboPreviewSchedule.capturePoints(1)
                        .getFirst().targetAge();

        tracker.observe(10L, firstTarget);
        tracker.markSubmitted(tracker.pendingCapture());
        assertEquals(1, tracker.runOrdinal());
        assertEquals(1, tracker.submittedCount());

        tracker.observe(11L, firstTarget + 0.01f);
        assertEquals(2, tracker.runOrdinal());
        assertEquals(0, tracker.submittedCount());
        assertEquals(
                DarkBallFboPreviewSchedule.capturePoints(1).getFirst(),
                tracker.pendingCapture());
        tracker.markSubmitted(tracker.pendingCapture());

        tracker.observe(11L, 0.0f);
        assertEquals(3, tracker.runOrdinal());
        assertEquals(0, tracker.submittedCount());
        assertNull(tracker.pendingCapture());
    }

    @Test
    void filenamesCarryGlobalAndPhaseSampleMetadata() {
        List<DarkBallFboPreviewSchedule.CapturePoint> points =
                DarkBallFboPreviewSchedule.capturePoints(5);
        DarkBallFboPreviewSchedule.CapturePoint point = points.get(9);
        String filename = DarkBallFboPreviewSchedule.captureFilename(
                "Session 01",
                43,
                7,
                2,
                "Splat / Fused",
                "Splat Activation",
                12,
                "Phase Sample",
                point,
                point.targetAge() + 0.012f);

        String normalizedFilename = filename.replace('\\', '/');
        assertTrue(normalizedFilename.matches(
                "[a-z0-9-]+/[a-z0-9-]+\\.png"));
        assertTrue(normalizedFilename.startsWith(
                "dark-ball-fbo-session-session-01-pokemon-43-ball-7-run-002/"));
        assertTrue(filename.contains("shot-012"));
        assertTrue(filename.contains("path-splat-fused"));
        assertTrue(filename.contains("authority-splat-activation"));
        assertTrue(filename.contains("capture-phase-sample"));
        assertTrue(filename.contains(String.format(
                java.util.Locale.ROOT,
                "phase-%02d-%s",
                point.phase().index() + 1,
                point.phase().slug())));
        assertTrue(filename.contains(String.format(
                java.util.Locale.ROOT,
                "sample-%02d-of-%02d",
                point.sampleNumber(),
                point.sampleCount())));
        assertTrue(filename.contains(String.format(
                java.util.Locale.ROOT,
                "target-%05dms",
                Math.round(point.targetAge() * 1000.0f))));
        assertTrue(filename.contains(String.format(
                java.util.Locale.ROOT,
                "actual-%05dms",
                Math.round((point.targetAge() + 0.012f) * 1000.0f))));

        String manifest = DarkBallFboPreviewSchedule.manifestFilename(
                "Session 01", 43, 7, 2);
        String normalizedManifest = manifest.replace('\\', '/');
        assertTrue(normalizedManifest.matches(
                "[a-z0-9-]+/manifest\\.jsonl"));
        assertTrue(normalizedManifest.contains("run-002/manifest"));
    }

    @Test
    void directRendererTrackerPreservesSplatBeforeAfterAndSettledFrames() {
        DarkBallFboPreviewSchedule.DirectRendererActivationTracker tracker =
                new DarkBallFboPreviewSchedule
                        .DirectRendererActivationTracker();

        assertTrue(tracker.observe(
                44L, 1.0f, "exact-mask").isEmpty());
        List<DarkBallFboPreviewSchedule.DirectRendererActivationCapture>
                handoff =
                tracker.observe(44L, 1.05f, "splat-activation");
        assertEquals(2, handoff.size());
        assertEquals(
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind.BEFORE,
                handoff.get(0).kind());
        assertEquals(1.0f, handoff.get(0).targetAge(), EPSILON);
        assertEquals(
                1.0f,
                handoff.get(0).observedPreviousAge(),
                EPSILON);
        assertEquals(
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind.AFTER,
                handoff.get(1).kind());
        assertEquals(1.05f, handoff.get(1).targetAge(), EPSILON);
        assertEquals(
                1.0f,
                handoff.get(1).observedPreviousAge(),
                EPSILON);

        DarkBallFboPreviewSchedule.DirectRendererActivationCapture retained =
                handoff.get(0).withTargetAge(0.96f);
        assertEquals(0.96f, retained.targetAge(), EPSILON);
        assertEquals(1.05f, retained.activationAge(), EPSILON);
        assertEquals(1.0f, retained.observedPreviousAge(), EPSILON);

        assertTrue(tracker.observe(
                44L,
                1.05f
                        + DarkBallFboPreviewSchedule
                        .DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS
                        - 0.001f,
                "splat-activation").isEmpty());
        List<DarkBallFboPreviewSchedule.DirectRendererActivationCapture>
                settled =
                tracker.observe(
                        44L,
                        1.05f
                                + DarkBallFboPreviewSchedule
                                .DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS,
                        "splat");
        assertEquals(1, settled.size());
        assertEquals(
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind.SETTLED,
                settled.getFirst().kind());
        assertEquals(
                1.0f,
                settled.getFirst().observedPreviousAge(),
                EPSILON);
        assertTrue(tracker.observe(
                44L, 2.0f, "splat").isEmpty(),
                "settled capture must be emitted only once");
    }

    @Test
    void directRendererTrackerIgnoresUnknownHistoricalAuthorities() {
        DarkBallFboPreviewSchedule.DirectRendererActivationTracker tracker =
                new DarkBallFboPreviewSchedule
                        .DirectRendererActivationTracker();

        tracker.observe(91L, 0.4f, "exact-mask");
        assertTrue(tracker.observe(
                91L, 0.5f, "obsolete-volume-activation").isEmpty());
        assertTrue(tracker.observe(
                91L, 1.0f, "obsolete-volume").isEmpty());
    }

    @Test
    void directRendererTrackerStillCapturesSettledRecoveryFrame() {
        DarkBallFboPreviewSchedule.DirectRendererActivationTracker tracker =
                new DarkBallFboPreviewSchedule
                        .DirectRendererActivationTracker();

        tracker.observe(92L, 1.0f, "exact-mask");
        tracker.observe(92L, 1.05f, "splat-activation");
        List<DarkBallFboPreviewSchedule.DirectRendererActivationCapture>
                settled =
                tracker.observe(
                        92L,
                        1.05f
                                + DarkBallFboPreviewSchedule
                                .DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS,
                        "exact-mask");

        assertEquals(1, settled.size());
        assertEquals(
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind.SETTLED,
                settled.getFirst().kind());
    }

    @Test
    void directRendererTrackerCapturesSplatBeforeAfterAndSettledFrames() {
        DarkBallFboPreviewSchedule.DirectRendererActivationTracker tracker =
                new DarkBallFboPreviewSchedule
                        .DirectRendererActivationTracker();

        assertTrue(tracker.observe(
                93L, 0.80f, "exact-mask").isEmpty());
        List<DarkBallFboPreviewSchedule.DirectRendererActivationCapture>
                handoff =
                tracker.observe(93L, 0.85f, "splat-activation");

        assertEquals(2, handoff.size());
        assertEquals(
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind.BEFORE,
                handoff.get(0).kind());
        assertEquals(0.80f, handoff.get(0).targetAge(), EPSILON);
        assertEquals(
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind.AFTER,
                handoff.get(1).kind());
        assertEquals(0.85f, handoff.get(1).targetAge(), EPSILON);

        assertTrue(tracker.observe(
                93L,
                0.85f
                        + DarkBallFboPreviewSchedule
                        .DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS
                        - 0.001f,
                "splat-activation").isEmpty());
        List<DarkBallFboPreviewSchedule.DirectRendererActivationCapture>
                settled =
                tracker.observe(
                        93L,
                        0.85f
                                + DarkBallFboPreviewSchedule
                                .DIRECT_RENDERER_ACTIVATION_SETTLE_SECONDS,
                        "splat");

        assertEquals(1, settled.size());
        assertEquals(
                DarkBallFboPreviewSchedule
                        .DirectRendererActivationCaptureKind.SETTLED,
                settled.getFirst().kind());
        assertEquals(
                0.85f,
                settled.getFirst().activationAge(),
                EPSILON);
        assertEquals(
                0.80f,
                settled.getFirst().observedPreviousAge(),
                EPSILON);
    }

    @Test
    void magnifiedCropPreservesTileAspectAndClampsToViewport() {
        DarkBallFboPreviewSchedule.UvCrop crop =
                DarkBallFboPreviewSchedule.magnifiedCrop(
                        0.48f, 0.45f, 0.50f, 0.50f,
                        1920, 1080,
                        240, 180,
                        24);

        assertTrue(crop.minU() >= 0.0f);
        assertTrue(crop.minV() >= 0.0f);
        assertTrue(crop.maxU() <= 1.0f);
        assertTrue(crop.maxV() <= 1.0f);
        float pixelWidth = (crop.maxU() - crop.minU()) * 1920.0f;
        float pixelHeight = (crop.maxV() - crop.minV()) * 1080.0f;
        assertEquals(4.0f / 3.0f, pixelWidth / pixelHeight, 0.0001f);
        assertTrue(pixelWidth < 1920.0f);
        assertTrue(pixelHeight < 1080.0f);

        DarkBallFboPreviewSchedule.UvCrop edgeCrop =
                DarkBallFboPreviewSchedule.magnifiedCrop(
                        0.0f, 0.0f, 0.01f, 0.01f,
                        1920, 1080,
                        240, 180,
                        24);
        assertEquals(0.0f, edgeCrop.minU(), EPSILON);
        assertEquals(0.0f, edgeCrop.minV(), EPSILON);
    }
}
