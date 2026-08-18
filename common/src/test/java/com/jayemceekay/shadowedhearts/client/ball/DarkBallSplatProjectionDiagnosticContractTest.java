package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSplatProjectionDiagnosticContractTest {

    @Test
    void projectionTraceIsCaptureScopedAndPhaseRateLimited()
            throws IOException {
        String renderer = javaSource(
                "client/ball/DarkBallSurfaceSplatRenderer.java");
        String capture = javaSource(
                "client/ball/DarkBallCaptureVfx.java");

        assertTrue(renderer.contains(
                "ENABLE_PROJECTION_DIAGNOSTIC_LOGGING = true"));
        assertTrue(renderer.contains("projectionDiagnosticStageMask"));
        assertTrue(renderer.contains("DIAGNOSTIC_TURBULENCE"));
        assertTrue(renderer.contains("DIAGNOSTIC_COLLAPSE"));
        assertTrue(renderer.contains(
                "logProjectionDiagnosticsIfNeeded("));
        assertTrue(renderer.contains(
                "Dark Ball splat projection trace"));
        assertTrue(renderer.contains(
                "Dark Ball splat base projection"));
        assertTrue(renderer.contains("parameters.pokemonId()"));
        assertTrue(renderer.contains("parameters.ballId()"));
        assertTrue(renderer.contains("parameters.presentationAge()"));
        assertTrue(renderer.contains("GL30.GL_DRAW_FRAMEBUFFER_BINDING"));
        assertTrue(renderer.contains("GL11.GL_VIEWPORT"));
        assertTrue(renderer.contains("maximumEstimatedReachPixels"));
        assertTrue(renderer.contains(
                "Dark Ball splat applied GPU state"));
        assertTrue(renderer.contains(
                "Dark Ball splat applied uniforms"));
        assertTrue(renderer.contains("shader.apply()"));
        assertTrue(renderer.contains("GL20.glGetUniformfv"));
        assertTrue(renderer.contains("GL30.GL_VERTEX_ARRAY_BINDING"));

        assertTrue(capture.contains(
                "bodyEffectFade,\n"
                        + "                                 pokemonId,\n"
                        + "                                 ballId,\n"
                        + "                                 presentationAge,"));
    }

    @Test
    void irisTraceComparesLogicalAndPhysicalTargets()
            throws IOException {
        String handler = javaSource("client/aura/IrisHandler.java");
        String implementation = javaSource(
                "client/aura/IrisHandlerImpl.java");
        String emitters = javaSource("client/ball/BallEmitters.java");

        assertTrue(handler.contains("public final int diffuseWidth;"));
        assertTrue(handler.contains("public final int diffuseHeight;"));
        assertTrue(implementation.contains("diffuseTarget.getWidth()"));
        assertTrue(implementation.contains("diffuseTarget.getHeight()"));
        assertTrue(emitters.contains(
                "Dark Ball Iris transaction trace"));
        assertTrue(emitters.contains("irisTransactionDiagnosticLogged"));
        assertTrue(emitters.contains("snapshot.diffuseWidth"));
        assertTrue(emitters.contains("snapshot.diffuseHeight"));
        assertTrue(emitters.contains("matrixMaximumDelta("));
    }

    private static String javaSource(String relative) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/")
                        .resolve(relative),
                StandardCharsets.UTF_8);
    }
}
