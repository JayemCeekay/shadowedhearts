package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSurfaceSplatRuntimeContractTest {

    @Test
    void localTransportOwnsSplatRetirementWithoutChangingOtherPaths()
            throws IOException {
        String source = captureSource();
        String splatPath = splatPath(source);

        assertFalse(splatPath.contains("bodyPresentationGateAt(age)"),
                "the splat path must not inherit a global fallback-path "
                        + "retirement gate");
        assertTrue(splatPath.contains(
                "DarkBallSplatTransportMath.sample("));
        assertTrue(splatPath.contains(".bodyOwnership();"));
        assertTrue(splatPath.contains(
                "float bodyEffectFade = formation * fade;"));
        assertFalse(source.contains("renderSurfaceMeshPath("),
                "the surfel-only runtime must not retain an indexed body "
                        + "rollback path");
    }

    @Test
    void resolvedSplatsHandOffDirectlyToTheRegularSiphon()
            throws IOException {
        String source = captureSource();
        String splatPath = splatPath(source);

        int resolve = splatPath.indexOf(
                "resolveSurfaceSplatField(bodyBounds)");
        int siphonBuild = splatPath.indexOf(
                "buildSiphonSurfaceMesh(siphonProgress)", resolve);

        assertTrue(resolve >= 0);
        assertTrue(siphonBuild > resolve,
                "the regular siphon must consume the resolved splat handoff");
        assertFalse(splatPath.contains("sweepBridgeRenderer.render("));
        assertFalse(splatPath.contains("projectedBridgeBounds("));
        assertFalse(splatPath.contains("bridgeRendered"));
        assertTrue(splatPath.contains(
                "\", resolve=3x3, transport=bone-tree-routed"));
        assertFalse(source.contains("sweepBridgeRenderer"),
                "the surfel-to-siphon handoff must not recreate a terminal "
                        + "bridge renderer");
    }

    @Test
    void splatBodyBypassesTheSharedSweepAndKeepsTightBounds()
            throws IOException {
        String source = captureSource();
        String splatPath = splatPath(source);
        String compactSplatPath =
                splatPath.replaceAll("\\s+", "");

        assertTrue(splatPath.contains(
                "surfaceSplatRenderer.render("));
        assertFalse(splatPath.contains("activeSweepPose"));
        assertFalse(splatPath.contains("buildCurrentSweepPose("));
        assertTrue(compactSplatPath.contains(
                "newVector3f(controlA).sub(inletLocal),releaseFront"),
                "the splat footprint must receive the regular siphon's "
                        + "actual inlet tangent");
        assertTrue(splatPath.contains(
                "currentSurfaceSplatSpikeFlowTime()"),
                "collapse transport must freeze the turbulent rest carrier");
        assertTrue(splatPath.contains(
                "currentDirectBodySpikeFlowTime()"),
                "the live spike/indent presentation carrier must continue "
                        + "through collapse");
        assertTrue(source.contains(
                "DarkBallSurfaceSplatRenderer.prepareSamples("),
                "sampling and neighborhood preparation must run in the "
                        + "existing asynchronous surface-build stage");
        assertFalse(source.contains(
                ".buildPresentationOnly("),
                "direct splats must not spend asynchronous preparation time "
                        + "building an unused mesh sweep");
        assertFalse(splatPath.contains(
                "projectedSweepBounds("),
                "the removed global hook must not enlarge splat resolve or "
                        + "composite bounds");
        assertTrue(splatPath.contains(
                "transport=bone-tree-routed"));
    }

    @Test
    void rendererBindsTheJavaTransportDefaultsAndReportsDrawOnlyTiming()
            throws IOException {
        String renderer = Files.readString(
                Path.of("src/main/java/com/jayemceekay/"
                        + "shadowedhearts/client/ball/"
                        + "DarkBallSurfaceSplatRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(renderer.contains(
                "DarkBallSplatTransportMath.DEFAULT_PARAMETERS"));
        for (String uniform : new String[]{
                "TransportActivationHalfWidth",
                "TransportFrontSpan",
                "TransportMinimumCrossSection",
                "TransportThroatCoordinate",
                "TransportTerminalCoordinate",
                "TransportRetirementWidth",
                "TransportCollarExitCoordinate",
                "TransportCollarExitWidth"}) {
            assertTrue(renderer.contains(
                    "setFloat(shader, \"" + uniform + "\""),
                    "missing Java binding for " + uniform);
        }
        assertTrue(renderer.contains(
                "setFloat(shader, \"DeformationFlowTime\""));

        String splatPath = splatPath(captureSource());
        assertTrue(splatPath.contains(
                "surfaceSplatRenderer.lastDrawCpuMicros()"));
        assertTrue(splatPath.contains(
                "surfaceSplatBodySubmittedThisFrame ="));
        assertTrue(splatPath.contains(
                "uploadOccurredLastRender()"));
    }

    @Test
    void enclosedPunctureGuardIsBoundToSuccessfulPreCollapseSplats()
            throws IOException {
        String capture = captureSource();
        String densityFbo = Files.readString(
                Path.of("src/main/java/com/jayemceekay/"
                        + "shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java"),
                StandardCharsets.UTF_8);
        String splatPath = splatPath(capture);

        assertTrue(densityFbo.contains(
                "shader.getUniform(\"PreCollapseInteriorGuard\")"));
        assertTrue(densityFbo.contains(
                "getCompositePreCollapseInteriorGuard()"));
        assertTrue(capture.contains(
                "compositePreCollapseInteriorGuard = 0.0f;"),
                "per-frame state must not leak from a previous capture");
        assertTrue(capture.contains(
                "static float preCollapseInteriorGuardAt("));
        assertTrue(capture.contains(
                "public static float "
                        + "getCompositePreCollapseInteriorGuard()"));
        assertTrue(splatPath.contains(
                "surfaceSplatBodySubmittedThisFrame)"));
        assertTrue(splatPath.contains(
                "preCollapseInteriorGuardAt(presentationAge)"));
        assertTrue(splatPath.indexOf(
                        "compositePreCollapseInteriorGuard = Math.max(")
                        > splatPath.indexOf(
                        "if (!drawsSucceeded || !depthRestored)"),
                "only a completed splat transaction may enable the guard");
    }

    @Test
    void diagnosticsUseExplicitLocalTransportState()
            throws IOException {
        String preview = Files.readString(
                Path.of("src/main/java/com/jayemceekay/"
                        + "shadowedhearts/client/ball/"
                        + "DarkBallFboDebugPreview.java"),
                StandardCharsets.UTF_8);
        int start = preview.indexOf(
                "private static String splatDrawTimingState(");
        int end = preview.indexOf(
                "private static String splatResolveTimingState(", start);
        assertTrue(start >= 0);
        assertTrue(end > start);
        String drawState = preview.substring(start, end);

        assertTrue(preview.contains(
                "\"splatLastSectionBodyOwnership\""));
        assertTrue(preview.contains(
                "\"splatBodySubmittedCurrentFrame\""));
        assertTrue(drawState.contains(
                "capture.splatLastSectionBodyOwnership()"));
        assertTrue(drawState.contains(
                "capture.splatBodySubmittedCurrentFrame()"));
        assertFalse(drawState.contains(
                "capture.bodyPresentationGate()"));
    }

    private static String splatPath(String source) {
        int start = source.indexOf(
                "private boolean renderSurfaceSplatPath(");
        int end = source.indexOf(
                "private static long elapsedMicros(", start);
        assertTrue(start >= 0);
        assertTrue(end > start);
        return source.substring(start, end);
    }

    private static String captureSource() throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/"
                        + "shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java"),
                StandardCharsets.UTF_8);
    }
}
