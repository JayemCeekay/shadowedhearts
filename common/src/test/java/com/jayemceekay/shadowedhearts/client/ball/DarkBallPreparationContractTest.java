package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallPreparationContractTest {

    @Test
    void preparationUsesABoundedDedicatedContinuationChain()
            throws IOException {
        String capture = source();

        assertTrue(capture.contains(
                "Executors.newFixedThreadPool(2"));
        assertTrue(capture.contains(
                "\"ShadowedHearts-DarkBall-Prep-\""));
        assertTrue(capture.contains(
                "surfaceMeshBuildFuture = "
                        + "voxelBuildFuture.thenApplyAsync("));
        assertTrue(capture.contains(
                "}, PREPARATION_EXECUTOR);"));
        assertFalse(capture.contains("Util.backgroundExecutor()"));
    }

    @Test
    void lateValidPreparationIsRemappedInsteadOfRejected()
            throws IOException {
        String capture = source();

        assertTrue(capture.contains(
                "lateReadiness ? \"late-remapped\" : \"on-time\""));
        assertTrue(capture.contains(
                "surfacePresentationAgeAt("));
        assertFalse(capture.contains(
                "completed after the pre-collapse deadline"));
    }

    @Test
    void intentionalExactMaskModeBypassesSurfelContinuation()
            throws IOException {
        String capture = source();

        int forcedBranch = capture.indexOf(
                "if (forceExactMaskOnly)");
        int continuation = capture.indexOf(
                "surfaceMeshBuildFuture = "
                        + "voxelBuildFuture.thenApplyAsync(");
        assertTrue(forcedBranch >= 0);
        assertTrue(continuation > forcedBranch);
        assertTrue(capture.contains(
                "selectForcedExactMaskPath();"));
        assertTrue(capture.contains(
                "surfaceMeshPath = SurfaceMeshPath.FAILED;"));
        assertTrue(capture.contains(
                "intentional exact-mask-only diagnostic"));
    }

    private static String source() throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/"
                        + "shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java"),
                StandardCharsets.UTF_8);
    }
}
