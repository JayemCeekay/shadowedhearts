package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSilhouetteDistanceFieldTest {

    @Test
    void seedAndJumpPassBuildNearestProjectedUnionBoundary()
            throws IOException {
        String seed = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_silhouette_distance_seed.fsh");
        String jump = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_silhouette_distance_jump.fsh");
        String compactSeed = compact(seed);
        String compactJump = compact(jump);

        assertTrue(seed.contains("texelFetch(Sampler0"));
        assertTrue(compactSeed.contains(
                "float sourceCoverage[16];"));
        assertTrue(compactSeed.contains(
                "sourceCoverage[y * 4 + x] = coverageAtTexel("));
        assertTrue(compactSeed.contains(
                "sourceCoverage[sourceIndex + 1]"));
        assertTrue(compactSeed.contains(
                "sourceCoverage[sourceIndex + 4]"));
        assertTrue(seed.contains("considerBoundarySegment("));
        assertTrue(compactSeed.contains(
                "float crossingBlend = clamp((0.5 - firstCoverage)"
                        + " / (secondCoverage - firstCoverage),"
                        + " 0.0, 1.0);"));
        assertTrue(compactSeed.contains(
                "vec2 crossingUv = mix(firstUv, secondUv,"
                        + " crossingBlend);"));
        assertTrue(compactSeed.contains(
                "? vec4(bestSeedUv, 0.0, 1.0)"));
        assertTrue(seed.contains(
                "vec4(-1.0, -1.0, 0.0, 0.0)"));
        assertTrue(jump.contains("uniform float JumpStep;"));
        assertTrue(compactJump.contains(
                "vec2 candidateDelta = (candidateSeed - texCoord0)"
                        + " * fieldSize;"));
        assertTrue(compactJump.contains(
                "if (candidateDistanceSquared < bestDistanceSquared)"));
    }

    @Test
    void pipelineBuildsBoundedHalfResolutionRg32fField()
            throws IOException {
        String pipeline = javaSource(
                "com/jayemceekay/shadowedhearts/client/render/"
                        + "DensityFboPipeline.java");
        String densityFbo = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String compactPipeline = compact(pipeline);
        String compactFbo = compact(densityFbo);

        assertTrue(compactPipeline.contains(
                "int distanceWidth = Math.max(1, mw / 2);"));
        assertTrue(pipeline.contains("GL30.GL_RG32F"));
        assertTrue(pipeline.contains("GL30.GL_RG"));
        assertTrue(compactPipeline.contains(
                "while (jumpStep < requiredDistancePixels"
                        + " && jumpStep < 16384)"));
        assertTrue(compactPipeline.contains(
                "drawScreenQuad(minimumU, minimumV,"
                        + " maximumU, maximumV);"));
        assertTrue(densityFbo.contains(
                "PIPELINE.buildSilhouetteDistanceField("));
        assertTrue(compactFbo.contains(
                "silhouetteDistanceRendered"
                        + " && silhouetteDistanceTexture != 0"));
    }

    @Test
    void compositeUsesEuclideanDistanceAndKeepsSafeFallback()
            throws IOException {
        String composite = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String metadata = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.json");
        String compactComposite = compact(composite);

        assertTrue(metadata.contains("\"name\": \"DistanceSampler\""));
        assertTrue(metadata.contains(
                "\"name\": \"DistanceFieldAvailable\""));
        assertTrue(compactComposite.contains(
                "float candidateDistance = length("
                        + " (seedUv - uv) * ScreenSize);"));
        assertTrue(compactComposite.contains(
                "if (DistanceFieldAvailable != 0)"));
        assertTrue(composite.contains(
                "boundaryDistanceAlongDirection("),
                "unsupported drivers must retain the former safe fallback");
    }

    @Test
    void shadersAreRegisteredOnBothLoaders() throws IOException {
        String modShaders = javaSource(
                "com/jayemceekay/shadowedhearts/client/ModShaders.java");
        String fabric = rootSource(
                "fabric/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/fabric/ModShadersPlatformImpl.java");
        String neoforge = rootSource(
                "neoforge/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/neoforge/ModShadersPlatformImpl.java");

        assertTrue(modShaders.contains(
                "DARK_BALL_SILHOUETTE_DISTANCE_SEED"));
        assertTrue(modShaders.contains(
                "DARK_BALL_SILHOUETTE_DISTANCE_JUMP"));
        assertTrue(fabric.contains(
                "dark_ball_silhouette_distance_seed"));
        assertTrue(fabric.contains(
                "dark_ball_silhouette_distance_jump"));
        assertTrue(neoforge.contains(
                "dark_ball_silhouette_distance_seed"));
        assertTrue(neoforge.contains(
                "dark_ball_silhouette_distance_jump"));
    }

    private static String javaSource(String source) throws IOException {
        return Files.readString(
                Path.of("src/main/java").resolve(source),
                StandardCharsets.UTF_8);
    }

    private static String resourceSource(String source)
            throws IOException {
        return Files.readString(
                Path.of("src/main/resources").resolve(source),
                StandardCharsets.UTF_8);
    }

    private static String rootSource(String source) throws IOException {
        return Files.readString(
                Path.of("..").resolve(source),
                StandardCharsets.UTF_8);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
    }
}
