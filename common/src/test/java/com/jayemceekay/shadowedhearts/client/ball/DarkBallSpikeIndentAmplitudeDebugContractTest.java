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

class DarkBallSpikeIndentAmplitudeDebugContractTest {
    private static final String RESOURCE_ROOT =
            "assets/shadowedhearts/shaders/core/darkball/";

    @Test
    void modeThirteenCarriesActualSignedAmplitudeWithoutChangingProduction()
            throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        String surfaceJson = resource(
                loader, "dark_ball_surface_splat.json");
        String vertex = resource(
                loader, "dark_ball_surface_splat.vsh");
        String fragment = resource(
                loader, "dark_ball_surface_splat.fsh");
        String resolveJson = resource(
                loader, "dark_ball_surface_splat_resolve.json");
        String resolve = resource(
                loader, "dark_ball_surface_splat_resolve.fsh");
        String composite = resource(
                loader, "dark_ball_volume_composite.fsh");
        String renderer = source(
                "client/ball/DarkBallSurfaceSplatRenderer.java");
        String densityFbo = source(
                "client/ball/DarkBallDensityFBO.java");
        String settings = source(
                "client/ball/DarkBallDeformationSettings.java");

        assertTrue(settings.contains(
                "SPIKE_INDENT_AMPLITUDE_DEBUG_MODE = 13"));
        assertTrue(renderer.contains(
                ".spikeIndentAmplitudeDebugEnabled()"));
        assertTrue(compact(renderer).contains(
                "?-1.0f:0.0f"));
        assertTrue(compact(densityFbo).contains(
                ".spikeIndentAmplitudeDebugEnabled()?2:1"));

        assertTrue(vertex.contains(
                "if (SplatDiagnosticMode < -0.5)"));
        assertTrue(compact(vertex).contains(
                "liveEdgeDisplacement-neutralEdgeDisplacement"));
        assertTrue(vertex.contains(
                "neutralSheetSafeDisplacement"));
        assertTrue(vertex.contains(
                "BODY_INDENT_MAX_DISPLACEMENT"));
        assertTrue(vertex.contains(
                "BODY_SPIKE_MAX_DISPLACEMENT"));
        assertTrue(compact(vertex).contains(
                "deformedSurface=signedNormalizedAmplitude*0.5+0.5"));

        assertTrue(compact(fragment).contains(
                "currentWeight*deformedSurface"),
                "mode 13 must reuse the established A lane instead of "
                        + "adding another interpolator or render target");
        assertTrue(resolve.contains("if (ResolveMode == 2)"));
        assertTrue(resolve.contains("encodedAmplitude"));
        assertTrue(compact(resolve).contains(
                "fragColor=vec4(clamp(currentCoverage,0.0,1.0),"
                        + "0.0,0.0,encodedAmplitude)"));

        assertTrue(composite.contains(
                "spikeIndentAmplitudeDebugColor"));
        assertTrue(composite.contains(
                "DeformationDebugMode == 13"));
        assertTrue(composite.contains(
                "vec3 indentation = vec3(0.02, 0.68, 1.00)"));
        assertTrue(composite.contains(
                "vec3 spike = vec3(1.00, 0.10, 0.015)"));

        assertFalse(surfaceJson.contains("AmplitudeDebugMode"));
        assertFalse(resolveJson.contains("AmplitudeDebugMode"));
        assertTrue(resolve.contains("if (ResolveMode == 0)"));
        assertTrue(compact(resolve).contains(
                "fragColor=vec4(clamp(currentCoverage,0.0,1.0),"
                        + "0.0,clamp(remainingCoverage,0.0,1.0),"
                        + "depthPayload)"),
                "the production resolve contract must remain unchanged");
    }

    @Test
    void clientConfigExposesAnOptInAmplitudeHeatmap() throws IOException {
        String clientConfig = source("config/ClientConfig.java");
        String interfaceConfig = source("config/IClientConfig.java");

        assertTrue(clientConfig.contains(
                ".define(\"visualizeSpikeIndentAmplitude\", false)"));
        assertTrue(clientConfig.contains(
                "darkBallVisualizeSpikeIndentAmplitude()"));
        assertTrue(interfaceConfig.contains(
                "darkBallVisualizeSpikeIndentAmplitude() { return false; }"));
    }

    private static String resource(ClassLoader loader, String name)
            throws IOException {
        String path = RESOURCE_ROOT + name;
        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertNotNull(stream, "missing shader resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String source(String packageRelativePath)
            throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + packageRelativePath).normalize(),
                StandardCharsets.UTF_8);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
