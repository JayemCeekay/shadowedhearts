package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowPokemonAuraPreviewSimulationTest {

    @Test
    void previewPixelScaleIsInvariantUnderProfileYaw() {
        float angle = (float) Math.toRadians(45.0);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);

        // Columns of S * R_y, matching transformDirection on the three basis
        // vectors. X/Y render at 20 pixels per simulation unit while profile Z
        // deliberately uses a different reciprocal scale.
        Vector3f axisX = new Vector3f(20.0f * cosine, 0.0f, -5.0f * sine);
        Vector3f axisY = new Vector3f(0.0f, 20.0f, 0.0f);
        Vector3f axisZ = new Vector3f(20.0f * sine, 0.0f, 5.0f * cosine);

        assertEquals(
                20.0f,
                ShadowPokemonAuraGuiRenderer.previewPixelScale(
                        axisX, axisY, axisZ),
                1.0e-4f);
    }

    @Test
    void previewClockUsesCappedTwentyHertzFixedSteps() {
        assertEquals(
                new ShadowPokemonAuraSystem.PreviewClockAdvance(0, 49_999_999L),
                ShadowPokemonAuraSystem.previewClockAdvance(0L, 49_999_999L)
        );
        assertEquals(
                new ShadowPokemonAuraSystem.PreviewClockAdvance(1, 0L),
                ShadowPokemonAuraSystem.previewClockAdvance(0L, 50_000_000L)
        );
        assertEquals(
                new ShadowPokemonAuraSystem.PreviewClockAdvance(1, 25_000_000L),
                ShadowPokemonAuraSystem.previewClockAdvance(25_000_000L, 50_000_000L)
        );
        assertEquals(
                new ShadowPokemonAuraSystem.PreviewClockAdvance(5, 0L),
                ShadowPokemonAuraSystem.previewClockAdvance(0L, 2_000_000_000L)
        );
        assertEquals(
                new ShadowPokemonAuraSystem.PreviewClockAdvance(0, 12_000_000L),
                ShadowPokemonAuraSystem.previewClockAdvance(12_000_000L, -1L)
        );
    }

    @Test
    void inactivePreviewOwnerClearIsIdempotentAndOwnerLocal() {
        ShadowPokemonAuraSystem.PreviewInstance first =
                new ShadowPokemonAuraSystem.PreviewInstance();
        ShadowPokemonAuraSystem.PreviewInstance second =
                new ShadowPokemonAuraSystem.PreviewInstance();

        first.clear();
        second.clear();
        int secondGeneration = second.generation();
        first.clear();

        assertEquals(0, first.generation());
        assertEquals(secondGeneration, second.generation());
        assertEquals(0, first.particleCount());
        assertFalse(first.hasPose());
    }

    @Test
    void canonicalPreviewPoseRoundTripsTheFullGuiPoseStackTransform() {
        Matrix4f root = new Matrix4f()
                .translation(141.0f, 82.0f, -20.0f)
                .rotateXYZ(0.23f, 5.67f, 0.0f)
                .scale(37.0f, 37.0f, -37.0f);
        Matrix4f localPart = new Matrix4f()
                .translation(0.35f, -1.10f, 0.22f)
                .rotateXYZ(-0.31f, 0.18f, 0.42f)
                .scale(1.1f, 0.8f, 1.3f);
        Matrix4f renderedPart = new Matrix4f(root).mul(localPart);
        Matrix4f rawToAura = new Matrix4f().scaling(-1.35f, -1.35f, 1.35f);

        Matrix4f canonical = ShadowPokemonAuraSystem.previewCanonicalPose(
                new Matrix4f(root).invert(),
                rawToAura,
                renderedPart
        );
        Matrix4f roundTrip = ShadowPokemonAuraSystem.previewSimulationToGui(
                root,
                rawToAura
        ).mul(canonical);

        float[] expected = renderedPart.get(new float[16]);
        float[] actual = roundTrip.get(new float[16]);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 0.0001f, "matrix element " + i);
        }
    }

    @Test
    void previewAndWorldShareEmissionAndSamplingButNotStateOrRandomStreams()
            throws IOException {
        String source = Files.readString(
                Path.of(
                        "src/main/java/com/jayemceekay/shadowedhearts/client/aura/ShadowPokemonAuraSystem.java"),
                StandardCharsets.UTF_8
        );
        String preview = between(
                source,
                "public static final class PreviewInstance",
                "@FunctionalInterface");
        String sharedEmitter = between(
                source,
                "private static void emitModelAura",
                "private static void spawnBoneAuraPuffs");

        assertTrue(source.contains(
                "emitModelAura(frame, capture.state, anchors, capture.strength, capture.quality, WORLD_PARTICLES)"));
        assertTrue(source.contains(
                "emitModelAura(frame, sourceState, latestAnchors, strength, 1.0f, particles)"));
        assertTrue(source.contains("sampleAlpha(puff, partial)"));
        assertTrue(source.contains("sampleSize(puff, partial)"));
        assertTrue(source.contains(
                "new ParticleRuntime(random, new ArrayList<>(), () -> MAX_PREVIEW_PUFFS)"));
        assertTrue(source.contains(
                "private static final ParticleRuntime WORLD_PARTICLES"));
        assertTrue(preview.contains("RandomSource random = RandomSource.create(seed)"));
        assertTrue(preview.contains(
                "new ParticleRuntime(random, new ArrayList<>(), () -> MAX_PREVIEW_PUFFS)"));
        assertFalse(preview.contains("WORLD_PARTICLES"));
        assertFalse(preview.contains("ACTIVE."));
        assertFalse(preview.contains("SOURCES."));
        assertFalse(preview.contains("RANDOM,"));
        assertTrue(sharedEmitter.contains("spawnBoneAuraPuffs("));
        assertTrue(sharedEmitter.contains("spawnBodyAnchorFillPuffs("));
        assertTrue(sharedEmitter.contains("spawnSmallModelUpperAnchorFillPuffs("));
        assertTrue(sharedEmitter.contains("spawnBodyVolumeAuraPuffs("));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0, "Missing start marker: " + start);
        assertTrue(endIndex > startIndex, "Missing end marker: " + end);
        return source.substring(startIndex, endIndex);
    }
}
