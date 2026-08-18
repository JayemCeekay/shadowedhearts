package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSurfaceSplatTimingContractTest {

    @Test
    void drawTimerExcludesImmutablePreparationAndNeverWaitsForGpu()
            throws IOException {
        String renderer = rendererSource();

        int upload = renderer.indexOf("if (!uploadIfNeeded(");
        int drawStart = renderer.indexOf(
                "long drawStarted = System.nanoTime();");
        int draw = renderer.indexOf(
                "vertexBuffer.drawWithShader(", drawStart);
        int publish = renderer.indexOf(
                "lastDrawCpuMicros = elapsedMicros(drawStarted);",
                draw);

        assertTrue(upload >= 0);
        assertTrue(drawStart > upload,
                "draw timing must exclude sample preparation/VBO upload");
        assertTrue(draw > drawStart);
        assertTrue(publish > draw);
        assertFalse(renderer.contains("glFinish("));
        assertFalse(renderer.contains("glFlush("));
        assertFalse(renderer.contains("GL_QUERY_RESULT"));
        assertFalse(renderer.contains("GL_TIME_ELAPSED"));
    }

    @Test
    void uploadMetadataDistinguishesCurrentRenderFromHistoricalValue()
            throws IOException {
        String renderer = rendererSource();

        int renderReset = renderer.indexOf(
                "uploadOccurredLastRender = false;");
        int uploadSuccess = renderer.indexOf(
                "uploadedSampleCount = sampleCount;");
        int generation = renderer.indexOf(
                "uploadGeneration++;", uploadSuccess);
        int currentRender = renderer.indexOf(
                "uploadOccurredLastRender = true;", generation);

        assertTrue(renderReset >= 0);
        assertTrue(uploadSuccess > renderReset);
        assertTrue(generation > uploadSuccess);
        assertTrue(currentRender > generation);
        assertTrue(renderer.contains(
                "long lastPreparationUploadCpuMicros()"));
        assertTrue(renderer.contains("long lastDrawCpuMicros()"));
        assertTrue(renderer.contains("long uploadGeneration()"));
        assertTrue(renderer.contains(
                "boolean uploadOccurredLastRender()"));
    }

    @Test
    void gpuStageTimingNeverReadsAnUnavailableResult()
            throws IOException {
        String timer = Files.readString(
                Path.of("src/main/java/com/jayemceekay/"
                        + "shadowedhearts/client/ball/"
                        + "DarkBallGpuTimer.java"),
                StandardCharsets.UTF_8);

        int availability = timer.indexOf(
                "GL15.GL_QUERY_RESULT_AVAILABLE");
        int unavailableGuard = timer.indexOf(
                "if (available == 0)", availability);
        int result = timer.indexOf(
                "GL15.GL_QUERY_RESULT)", unavailableGuard);

        assertTrue(availability >= 0);
        assertTrue(unavailableGuard > availability);
        assertTrue(result > unavailableGuard,
                "the timer must poll availability before requesting a result");
        assertFalse(timer.contains("glFinish("));
        assertFalse(timer.contains("glFlush("));
        assertTrue(timer.contains("QUERY_RING_SIZE = 4"));
    }

    private static String rendererSource() throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/"
                        + "shadowedhearts/client/ball/"
                        + "DarkBallSurfaceSplatRenderer.java"),
                StandardCharsets.UTF_8);
    }
}
