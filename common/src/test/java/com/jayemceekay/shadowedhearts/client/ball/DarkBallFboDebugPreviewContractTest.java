package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallFboDebugPreviewContractTest {

    @Test
    void previewRunsOnlyAfterACompletedComposite() throws IOException {
        String emitters = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "BallEmitters.java");
        String transaction = sourceBetween(
                emitters,
                "private static DarkBallPreviewResult"
                        + " renderDarkBallComposites(",
                "private record DarkBallPreviewResult(");
        int renderQueue = emitters.indexOf(
                "DarkBallPreviewResult previewResult = renderDarkBallComposites(");
        int publish = emitters.indexOf(
                "DarkBallFboDebugPreview.afterCompositeFrame(");
        int laterBloom = emitters.indexOf(
                "// Bloom pass:", publish);
        int composite = transaction.indexOf(
                "compositeRendered = DarkBallDensityFBO.composite();");
        int previewFrame = transaction.indexOf(
                "previewFrame = DarkBallDensityFBO.previewFrame();");

        assertTrue(composite >= 0);
        assertTrue(previewFrame > composite,
                "debug attachments must not be sampled during their write pass");
        assertTrue(transaction.substring(composite, previewFrame).contains(
                "compositeRendered"),
                "only a successful per-capture composite may publish scratch"
                        + " attachments");
        assertTrue(publish > renderQueue,
                "the queue must complete before its nearest-capture preview is"
                        + " exported");
        assertTrue(laterBloom > publish,
                "the final-composite tile must be captured before later bloom");
    }

    @Test
    void deepTraceExportUsesAStateSafeFixedDiagnosticAtlas()
            throws IOException {
        String preview = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallFboDebugPreview.java");
        String schedule = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallFboPreviewSchedule.java");

        assertTrue(preview.contains("ATLAS_WIDTH = 960"));
        assertTrue(preview.contains("ATLAS_HEIGHT = 720"));
        assertTrue(preview.contains("ATLAS_COLUMNS = 4"));
        assertTrue(preview.contains("ATLAS_ROWS = 4"));
        assertTrue(preview.contains("TILE_LABEL_SCALE = 0.55f"));
        assertTrue(preview.contains(
                "availableRenderedWidth / TILE_LABEL_SCALE"));
        assertTrue(preview.contains(
                "graphics.pose().scale("));
        assertTrue(preview.contains(
                "\"pre-merge splat identities\""));
        assertTrue(preview.contains(
                "\"pre-merge splat overlap\""));
        assertTrue(preview.contains("Screenshot.grab("));
        assertTrue(preview.contains(
                "shadowedhearts.darkBallFboCaptureMidpoints"));
        assertTrue(preview.contains(
                "shadowedhearts.darkBallFboCaptureSamplesPerPhase"));
        assertTrue(preview.contains(
                "private static final boolean HUD_ENABLED ="));
        assertTrue(preview.contains(
                "System.getProperty(\r\n"
                        + "                    HUD_PROPERTY")
                || preview.contains(
                "System.getProperty(\n"
                        + "                    HUD_PROPERTY"));
        assertTrue(preview.contains(
                "System.getProperty(\r\n"
                        + "                    CAPTURE_PROPERTY, \"false\")")
                || preview.contains(
                "System.getProperty(\n"
                        + "                    CAPTURE_PROPERTY, \"false\")"));
        assertTrue(schedule.contains("manifest.jsonl"));
        assertTrue(preview.contains("\"shotNumber\""));
        assertTrue(preview.contains("\"phaseSampleNumber\""));
        assertTrue(preview.contains("\"actualAgeSeconds\""));
        assertTrue(preview.contains("\"renderPath\""));
        assertTrue(preview.contains("\"selectionPath\""));
        assertTrue(preview.contains("\"presentationAuthority\""));
        assertTrue(preview.contains("\"schemaVersion\", 9"));
        assertTrue(preview.contains("\"captureDirectory\""));
        assertTrue(preview.contains(
                "ensureScreenshotArtifactParent(filename)"));
        assertTrue(preview.contains("\"vfxDurationSeconds\""));
        assertTrue(preview.contains("\"sweepMode\""));
        assertTrue(preview.contains("\"sweepPath\""));
        assertTrue(preview.contains("\"sweepStrength\""));
        assertTrue(preview.contains("\"sweepNodeCount\""));
        assertTrue(preview.contains("\"sweepSliceCount\""));
        assertTrue(preview.contains("\"sweepPinchCenter\""));
        assertTrue(preview.contains("\"sweepPinchStrength\""));
        assertTrue(preview.contains(
                "\"sweepMinimumSectionScale\""));
        assertTrue(preview.contains(
                "\"sweepMaximumSectionScale\""));
        assertTrue(preview.contains("\"sweepAuthority\""));
        assertTrue(preview.contains("\"bridgePresentationActive\""));
        assertTrue(preview.contains("\"bridgeMaterialActivation\""));
        assertTrue(preview.contains("\"bridgeRetractionProgress\""));
        assertTrue(preview.contains("\"bridgeRetractionFront\""));
        assertTrue(preview.contains("\"bridgeFirstActiveRing\""));
        assertTrue(preview.contains("\"bridgeLastActiveRing\""));
        assertTrue(preview.contains("\"bridgeActiveRingCount\""));
        assertTrue(preview.contains("\"bridgePathLength\""));
        assertTrue(preview.contains("\"bridgePathStartRadius\""));
        assertTrue(preview.contains("\"bridgePathShoulderRadius\""));
        assertTrue(preview.contains("\"bridgeInletRadius\""));
        assertTrue(preview.contains(
                "\"bridgeProjectedPixelAreaFraction\""));
        assertTrue(preview.contains("\"projectedPixelAreaFraction\""));
        assertTrue(preview.contains("\"choreographyDeltaSeconds\""));
        assertTrue(preview.contains("\"turbulenceAmplitudeBlend\""));
        assertTrue(preview.contains("\"turbulenceComplexityBlend\""));
        assertTrue(preview.contains("\"spikeFlowTime\""));
        assertTrue(preview.contains("\"siphonProgress\""));
        assertTrue(preview.contains("\"bodyReleaseFront\""));
        assertTrue(preview.contains("\"bodyPresentationGate\""));
        assertTrue(preview.contains(
                "\"splatBodySubmittedCurrentFrame\""));
        assertTrue(preview.contains(
                "\"splatLastSectionBodyOwnership\""));
        assertTrue(preview.contains(
                "\"splatUploadOccurredCurrentFrame\""));
        assertTrue(preview.contains("\"splatUploadGeneration\""));
        assertTrue(preview.contains("\"finalCollapse\""));
        assertTrue(preview.contains("\"siphonRootGate\""));
        assertTrue(preview.contains("\"siphonTipGate\""));
        assertTrue(preview.contains("\"voxelReadyAgeSeconds\""));
        assertTrue(preview.contains("\"voxelBuildMillis\""));
        assertTrue(preview.contains("\"surfaceReadyAgeSeconds\""));
        assertTrue(preview.contains("\"surfaceBuildMillis\""));
        assertTrue(preview.contains(
                "\"directRendererActivationAgeSeconds\""));
        assertTrue(preview.contains("\"splatRequestedBudget\""));
        assertTrue(preview.contains("\"splatSampleCount\""));
        assertTrue(preview.contains(
                "\"splatPreparationUploadCpuMicros\""));
        assertTrue(preview.contains(
                "\"splatPreparationUploadTimingScope\""));
        assertTrue(preview.contains("\"splatDrawCpuMicros\""));
        assertTrue(preview.contains(
                "\"splatDrawSubmittedCurrentFrame\""));
        assertTrue(preview.contains("\"splatDrawTimingState\""));
        assertTrue(preview.contains("\"splatResolveCpuMicros\""));
        assertTrue(preview.contains(
                "\"splatResolveSubmittedCurrentFrame\""));
        assertTrue(preview.contains("\"splatResolveTimingState\""));
        assertTrue(preview.contains("\"siphonBuildCpuMicros\""));
        assertTrue(preview.contains("\"siphonDrawCpuMicros\""));
        assertTrue(preview.contains("\"depthRestoreCpuMicros\""));
        assertTrue(preview.contains("\"splatDrawGpuMicros\""));
        assertTrue(preview.contains("\"splatResolveGpuMicros\""));
        assertTrue(preview.contains("\"siphonDrawGpuMicros\""));
        assertTrue(preview.contains("\"depthRestoreGpuMicros\""));
        assertTrue(preview.contains(
                "\"asynchronous-time-elapsed-query\""));
        assertTrue(preview.contains("\"rawTargetWidth\""));
        assertTrue(preview.contains("\"rawTargetHeight\""));
        assertTrue(preview.contains("\"reducedCompositeUsed\""));
        assertTrue(preview.contains("\"manifestRecordOrder\""));
        assertTrue(preview.contains("\"manifestSortKey\""));
        assertTrue(preview.contains("\"focusCropMinU\""));
        assertTrue(preview.contains("\"focus final composite\""));
        assertTrue(preview.contains("nextOutputShotNumber++"));
        assertTrue(preview.contains("\"scheduledPhaseShotCount\""));
        assertFalse(preview.contains("\"totalScheduledShots\""));
        assertTrue(preview.contains("\"mainFramebufferWidth\""));
        assertTrue(preview.contains("\"densityTargetWidth\""));
        assertTrue(preview.contains("\"expectedSourceWidth\""));
        assertTrue(preview.contains("\"sourceKind\""));
        assertTrue(preview.contains("\"screenshotSaved\""));
        assertTrue(preview.contains("\"screenshotMessageKey\""));
        assertTrue(preview.contains("Util.ioPool()"));
        assertTrue(preview.contains(
                "catch (IOException | SecurityException failure)"));
        assertFalse(preview.contains("glReadPixels"));
        assertFalse(preview.contains("glGetTexImage"));
        assertFalse(preview.contains("downloadTexture"));
        assertFalse(preview.contains("takeScreenshot"));

        int readback = preview.indexOf("Screenshot.grab(");
        int stateCapture = preview.lastIndexOf(
                "GlState screenshotState = GlState.capture();",
                readback);
        int stateRestore = preview.indexOf(
                "screenshotState.restore();",
                readback);
        assertTrue(stateCapture >= 0 && stateCapture < readback);
        assertTrue(stateRestore > readback,
                "synchronous GPU readback must restore caller GL state");
        assertTrue(preview.contains("GL11.GL_PACK_ALIGNMENT"));
        assertTrue(preview.contains("textureZeroBinding"));
        assertTrue(preview.contains("GL20.GL_CURRENT_PROGRAM"));
    }

    @Test
    void liveHudIsThrottledAndNeverSamplesAStaleAttachment()
            throws IOException {
        String preview = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallFboDebugPreview.java");

        assertTrue(preview.contains(
                "LIVE_UPDATE_INTERVAL_SECONDS = 1.0f / 12.0f"));
        assertTrue(preview.contains(
                "!DarkBallDensityFBO.isPreviewFrameCurrent(frame)"));
        assertTrue(preview.contains(
                "shouldRefreshHud(\n"
                        + "                capture, frame, compositeSource)"));
        assertTrue(preview.contains(
                "previous.compositeSource().texture()"));
        assertTrue(preview.contains(
                "previous.compositeSource().width()"));
        assertTrue(preview.contains(
                "previous.compositeSource().height()"));
    }

    @Test
    void previewPublicationIsGuardedAndWorldExitCleansUp()
            throws IOException {
        String density = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String emitters = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "BallEmitters.java");

        assertTrue(density.contains(
                "compositeRendered && DarkBallFboDebugPreview.isEnabled()"));
        assertTrue(emitters.contains("DarkBallDensityFBO.destroy();"),
                "world replacement/exit must release full-resolution targets");
    }

    @Test
    void shaderDecodesEveryMaterialAndDepthPayload()
            throws IOException {
        String fragment = resource(
                "/assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_fbo_preview.fsh");
        String descriptor = resource(
                "/assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_fbo_preview.json");

        assertTrue(descriptor.contains("\"PreviewMode\""));
        assertTrue(descriptor.contains("\"PreviewUvMin\""));
        assertTrue(descriptor.contains("\"PreviewUvMax\""));
        for (int mode = 1; mode <= 8; mode++) {
            assertTrue(fragment.contains(
                    "PreviewMode == " + mode),
                    "missing decoder mode " + mode);
        }
        assertTrue(fragment.indexOf("PreviewMode == 8")
                        < fragment.indexOf("texture(Sampler0"),
                "the N/A tile must not sample an unavailable texture");
        assertTrue(fragment.contains("checkerCell"));
        assertTrue(fragment.contains("crossDistance"));
        assertTrue(fragment.contains("(1.0 - source.r) * 384.0"),
                "hardware depth needs visible near-one expansion");
        assertTrue(fragment.contains("source.b >= 0.0"));
        assertTrue(fragment.contains("source.a >= 0.0"));
    }

    @Test
    void unavailableSourcesUseTheProceduralAtlasPlaceholder()
            throws IOException {
        String preview = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallFboDebugPreview.java");
        String density = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");

        assertTrue(preview.contains("drawOptionalAtlasTile("));
        assertTrue(preview.contains(": 8);"));
        assertTrue(preview.contains(
                "\"pre-merge-splat-diagnostic-not-produced\""));
        assertTrue(preview.contains(
                "frame.splatDiagnosticValid()"));
        assertTrue(preview.contains(
                "frame.splatDiagnosticTexture()"));
        assertTrue(preview.contains("\"available\""));
        assertTrue(density.contains(
                "beginSurfaceSplatDiagnosticPass()"));
        assertTrue(density.contains(
                "markSurfaceSplatDiagnosticRendered()"));
    }

    @Test
    void preMergeSplatInspectionIsIsolatedAndCapturedBeforeResolve()
            throws IOException {
        String capture = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java");
        String pipeline = javaSource(
                "com/jayemceekay/shadowedhearts/client/render/"
                        + "DensityFboPipeline.java");
        String previewFragment = resource(
                "/assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_fbo_preview.fsh");

        int diagnostic = capture.indexOf(
                ".renderDiagnostic(camera)");
        int resolve = capture.indexOf(
                ".resolveSurfaceSplatField(bodyBounds)");
        assertTrue(diagnostic >= 0);
        assertTrue(resolve > diagnostic,
                "the diagnostic must inspect submitted splats before the "
                        + "production field is resolved");
        assertTrue(capture.substring(diagnostic, resolve).contains(
                "DarkBallDensityFBO.resumeDensityPass()"),
                "the isolated diagnostic framebuffer must be left before "
                        + "the production resolve");
        assertTrue(pipeline.contains(
                "private RenderTarget splatDiagnosticTarget;"));
        assertTrue(pipeline.contains(
                "beginSplatDiagnosticPass(boolean clearTarget)"));
        assertTrue(previewFragment.contains("PreviewMode == 9"));
        assertTrue(previewFragment.contains("PreviewMode == 10"));
        assertTrue(previewFragment.contains(
                "source.rgb / source.a"));
    }

    @Test
    void preMergeTilesExportAsFullResolutionSynchronizedSeries()
            throws IOException {
        String preview = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallFboDebugPreview.java");

        assertTrue(preview.contains(
                "queueSplatTileSeries(attempt);"));
        assertTrue(preview.contains(
                "\"-t%02d-%s.png\""));
        assertTrue(preview.contains(
                "\"individual-tile-series\""));
        assertTrue(preview.contains(
                "\"parentAtlasScreenshotFilename\""));
        assertTrue(preview.contains(
                "\"manifestArtifactSortKey\""));
        assertTrue(preview.contains(
                "frame.width(), frame.height()"));
        assertTrue(preview.contains(
                "splatTileExportTarget.width"));
        assertTrue(preview.contains(
                "splatTileExportTarget.height"));
        assertTrue(preview.contains(
                "new SplatTileCaptureAttempt("));
        assertTrue(preview.contains(
                "splatTileExportTarget.destroyBuffers()"));
    }


    @Test
    void focusCropAndAuthorityFollowThePresentedRepresentation()
            throws IOException {
        String preview = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallFboDebugPreview.java");
        String density = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String capture = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java")
                .replace("\r\n", "\n");

        assertTrue(preview.contains(
                "DIRECT_RENDERER_ACTIVATION_TRACKER.observe("));
        assertTrue(preview.contains(
                "submitRetainedPreActivationScreenshot("));
        assertTrue(preview.contains(
                "transition.withTargetAge("));
        assertTrue(preview.contains(
                "retained.capture().age()"));
        assertTrue(preview.contains(
                "\"directRendererActivationObservedPreviousAgeSeconds\""));
        assertTrue(preview.contains(
                "submitDirectRendererActivationScreenshot("));
        assertTrue(preview.contains(
                "\"directRendererActivationEventCapacity\""));
        assertTrue(preview.contains(
                "\"directRendererActivationDetectedAgeSeconds\""));
        assertTrue(preview.contains(
                "\"direct-renderer-activation-event\""));
        assertTrue(density.contains("if (!directVolumeRendered)"));
        assertTrue(density.contains("getCompositeBodyUvBounds()"));
        assertTrue(density.contains("previewEffectBounds.minU()"));
        assertTrue(capture.contains("\"exact-mask\""));
        assertTrue(capture.contains("\"forced-exact-mask\""));
        assertTrue(capture.contains("\"exact-mask-forced\""));
        assertTrue(capture.contains("\"splat-activation\""));
        assertTrue(capture.contains(
                "String presentationAuthority = switch"));
    }

    @Test
    void surfelBoundsTrackOnlyTheRepresentationsActuallyPresented()
            throws IOException {
        String capture = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java")
                .replace("\r\n", "\n");

        assertTrue(capture.contains(
                "DarkBallProjectedEffectBounds.forSurfelAndSiphon("));
        assertTrue(capture.contains(
                "bodyPresentationActive,\n"
                        + "                        siphonProgress > 0.0f"));
        assertTrue(capture.contains(
                "DarkBallDensityFBO.markDirectVolumeRendered("));
        assertTrue(capture.contains(
                "geometryBounds,\n                "
                        + "bodyPresentationActive)"));
        assertFalse(capture.contains(
                "projectedBridgeBounds("));
        assertFalse(capture.contains("activeBridgeResult"));
    }

    @Test
    void captureDiagnosticsUseOnlyAStablePostCompositeFrame()
            throws IOException {
        String capture = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java");

        assertTrue(capture.contains(
                ".isPreviewFrameCurrent(previewFrame)"));
        assertTrue(capture.contains(
                "previewFrame.reducedCompositeUsed()"));
        assertTrue(capture.contains(
                "previewFrame.effectMinU()"));
        assertTrue(capture.contains(
                "Float projectedPixelAreaFraction"));
        assertTrue(capture.contains(
                "Boolean reducedCompositeUsed"));
    }

    private static String javaSource(String source) throws IOException {
        return Files.readString(
                Path.of("src/main/java").resolve(source),
                StandardCharsets.UTF_8);
    }

    private static String sourceBetween(
            String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        assertTrue(start >= 0, "missing source token " + startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(end > start, "missing source token " + endToken);
        return source.substring(start, end);
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream =
                     DarkBallFboDebugPreviewContractTest.class
                             .getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
