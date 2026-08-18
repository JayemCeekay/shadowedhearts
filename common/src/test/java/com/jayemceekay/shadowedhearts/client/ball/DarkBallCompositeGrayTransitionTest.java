package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallCompositeGrayTransitionTest {

    @Test
    void bodyTransitionUsesCleanTenPercentPresentationFraction()
            throws IOException {
        String shader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String compactShader = shader.replaceAll("\\s+", " ");

        assertTrue(shader.contains(
                "uniform float BodyGrayTransitionFraction;"));
        assertTrue(shader.contains(
                "const float MAX_GRAY_TRANSITION_OUTPUT_PIXELS = 96.0;"));
        assertTrue(compactShader.contains(
                "MAX_GRAY_TRANSITION_OUTPUT_PIXELS"
                        + " * outputPixelsToSourcePixels;"));
        assertTrue(compactShader.contains(
                "ProjectedBodyRadiusPixels * BodyGrayTransitionFraction"));
        assertTrue(compactShader.contains(
                "insideRimReachPixels"
                        + " + bodyGrayTransitionPixels"));
        assertTrue(compactShader.contains(
                "siphonInnerReachPixels, siphonDominance"));
        assertTrue(shader.contains(
                "const float THIN_FEATURE_MAX_GRAY_TRANSITION_OUTPUT_PIXELS"
                        + " = 5.0;"));
        assertTrue(shader.contains(
                "const float THIN_FEATURE_MIN_INSIDE_RIM_OUTPUT_PIXELS"
                        + " = 0.85;"));
        assertTrue(compactShader.contains(
                "float localizedBodyGrayTransitionPixels = mix("
                        + " min(bodyGrayTransitionPixels,"
                        + " localThinGrayLimitPixels),"
                        + " bodyGrayTransitionPixels,"
                        + " smoothstep(0.55, 0.94, broadBodyRegion));"));
        assertTrue(compactShader.contains(
                "float grayReachPixels = mix("
                        + "localizedBodyGrayTransitionPixels,"
                        + " resolvedSiphonGrayTransitionPixels,"
                        + " siphonDominance);"));
        assertTrue(compactShader.contains(
                "float grayOuterSupport = smoothstep(0.04, 0.96, fill);"));
        assertTrue(compactShader.contains(
                "float grayToBlackFade = 1.0"
                        + " - smoothstep(0.0, 1.0,"
                        + " grayInwardProgress);"));
        assertTrue(compactShader.contains(
                "grayToBlackFade *= boundaryFound;"));
        assertTrue(compactShader.contains(
                "float grayBand = grayOuterSupport * grayToBlackFade"));
        assertTrue(shader.contains(
                "const float PURPLE_RIM_REACH_OUTPUT_PIXELS = 4.0;"));
        assertTrue(shader.contains(
                "const float PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS = 1.5;"));
        assertTrue(shader.contains(
                "vec2 nearStep = texel * outsideRimReachPixels;"));
    }

    @Test
    void siphonTransitionTracksFortyPercentOfLocalProjectedRadius()
            throws IOException {
        String compositeShader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String siphonSurfaceShader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_siphon_surface_mesh.fsh");
        String edgeShader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_edge_tongues.fsh");
        String compactComposite = compositeShader.replaceAll("\\s+", " ");
        String compactSiphonSurface =
                siphonSurfaceShader.replaceAll("\\s+", " ");

        assertTrue(compactSiphonSurface.contains(
                "fragColor = vec4( 0.0, siphonWindow,"
                        + " 2.0 + siphonLocalRadius, surfaceDistance);"));
        assertTrue(compositeShader.contains(
                "float bodyStorageCoverage(float encodedStorage)"));
        assertTrue(edgeShader.contains(
                "float bodyStorageCoverage(float encodedStorage)"));
        assertTrue(compactComposite.contains(
                "float projectedSiphonRadiusPixels = siphonWorldRadius"
                        + " * (float(textureSize(Sampler0, 0).y) * 0.5)"
                        + " / verticalProjectionDenominator;"));
        assertTrue(compactComposite.contains(
                "projectedSiphonRadiusPixels"
                        + " * SIPHON_GRAY_TRANSITION_FRACTION"));
        assertTrue(compositeShader.contains(
                "const float SIPHON_GRAY_TRANSITION_FRACTION = 0.40;"));
        assertTrue(compactComposite.contains(
                "insideRimReachPixels"
                        + " + siphonGrayTransitionPixels;"));
        assertTrue(compositeShader.contains(
                "const float"
                        + " SIPHON_FALLBACK_GRAY_TRANSITION_OUTPUT_PIXELS"
                        + " = 1.05;"));
        assertTrue(compactComposite.contains(
                "SIPHON_FALLBACK_GRAY_TRANSITION_OUTPUT_PIXELS"
                        + " * outputPixelsToSourcePixels;"));
        assertTrue(compactComposite.contains(
                "float siphonFallbackInnerReachPixels ="
                        + " insideRimReachPixels"
                        + " + siphonFallbackGrayTransitionPixels;"));
        assertTrue(compactComposite.contains(
                "mix( siphonFallbackInnerReachPixels,"
                        + " proportionalSiphonInnerReachPixels,"
                        + " siphonRadiusAvailable)"));
    }

    @Test
    void grayLayerUsesDenseSilhouetteOpacityWithoutSmokeTransparency()
            throws IOException {
        String shader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String compactShader = shader.replaceAll("\\s+", " ");

        assertFalse(shader.contains("GRAY_SMOKE_MIN_OPACITY"));
        assertFalse(shader.contains("graySmokeAmount"));
        assertFalse(shader.contains("grayOpacityMultiplier"));
        assertTrue(compactShader.contains(
                "float opacity = max(fill * 0.985,"
                        + " purpleRim * 0.98);"));
        assertTrue(compactShader.contains(
                "opacity = max(opacity, purpleRim * 0.98);"));
        assertTrue(compactShader.contains(
                "smoothstep(0.55, 0.85, erodedInner)"));
    }

    @Test
    void finalCompositeUsesFrozenMaskForCoverageButNeverReplaysItsRgb()
            throws IOException {
        String shader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String compactShader = shader.replaceAll("\\s+", " ");

        assertTrue(compactShader.contains(
                "vec4 frozenSnapshot = texture(MaskSampler, texCoord0);"));
        assertTrue(compactShader.contains(
                "float frozenCoverage = smoothstep(0.015, 0.12,"
                        + " frozenSnapshot.a);"));
        assertTrue(compactShader.contains(
                "float combinedWeight = frozenWeight"
                        + " + silhouetteWeight;"));
        assertTrue(compactShader.contains(
                "fragColor = vec4(color,"
                        + " clamp(combinedWeight, 0.0, 1.0));"));
        assertFalse(shader.contains("frozenSnapshot.rgb"));
        assertFalse(shader.contains("frozenColor"));
        assertFalse(shader.contains("crossfadedColor"));
    }

    @Test
    void adaptiveWidthUsesScreenSpaceDistanceWithDirectionalFallback()
            throws IOException {
        String shader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String compactShader = shader.replaceAll("\\s+", " ");

        assertEquals(2, countOccurrences(shader,
                "coverageAt(texCoord0 + directions[i] *"));
        assertTrue(shader.contains("vec2 directions[8]"));
        assertTrue(shader.contains("float escapeWeights[12]"));
        assertTrue(shader.contains("vec2 escapeDirections[12]"));
        assertTrue(compactShader.contains(
                "for (int i = 8; i < 12; i++)"));
        assertTrue(compactShader.contains(
                "escapeDirections[i] * innerStep"));
        assertTrue(compactShader.contains(
                "for (int i = 0; i < 12; i++)"));
        assertTrue(shader.contains("HALF_ANGLE_COS"));
        assertTrue(shader.contains("coarseAlternateDirection"));
        assertTrue(shader.contains("vec2 strongestEscapeDirection"));
        assertTrue(shader.contains("vec2 alternateEscapeDirection"));
        assertFalse(shader.contains("innerEscapeVector"));
        assertFalse(shader.contains("grayGradientDirection"));
        assertTrue(compactShader.contains(
                "for (int probeIndex = 1; probeIndex <= 8;"
                        + " probeIndex++)"));
        assertTrue(compactShader.contains(
                "previousCoverage >= 0.5 && probeCoverage < 0.5"));
        assertTrue(compactShader.contains(
                "for (int searchIndex = 0; searchIndex < 4;"
                        + " searchIndex++)"));
        assertTrue(compactShader.contains(
                "float boundarySearchReachPixels"
                        + " = resolvedInsideRimReachPixels"
                        + " + grayReachPixels;"));
        assertTrue(shader.contains("uniform sampler2D DistanceSampler;"));
        assertTrue(shader.contains("uniform int DistanceFieldAvailable;"));
        assertTrue(shader.contains(
                "float silhouetteBoundaryDistanceOutputPixels("));
        assertTrue(compactShader.contains(
                "(seedUv - uv) * ScreenSize"));
        assertTrue(compactShader.contains(
                "if (DistanceFieldAvailable != 0)"));
        assertTrue(compactShader.contains(
                "distanceOutputPixels * outputPixelsToSourcePixels"));
        assertTrue(compactShader.contains(
                "boundaryDistancePixels = min(strongestUsableDistance,"
                        + " alternateUsableDistance);"));
        assertTrue(compactShader.contains(
                "float grayInwardProgress = clamp((boundaryDistancePixels"
                        + " - resolvedInsideRimReachPixels)"
                        + " / max(grayReachPixels, 0.0001), 0.0, 1.0);"));
        assertTrue(compactShader.contains(
                "vec2 boundaryToInteriorPixels = (texCoord0"
                        + " - nearestBoundarySeedUv) * ScreenSize;"));
        assertTrue(compactShader.contains(
                "min(axialSupport, transverseSupport)"));
        assertTrue(compactShader.contains(
                "float outsideRim = mix(morphologyOutsideRim,"
                        + " distanceOutsideRim, distanceFieldUsable);"));
        assertEquals(3, countOccurrences(shader,
                "boundaryDistanceAlongDirection("));
        assertTrue(compactShader.contains(
                "boundaryFound = max(strongestBoundaryFound,"
                        + " alternateBoundaryFound);"));
        assertTrue(compactShader.contains(
                "boundaryFound = max(boundaryFound,"
                        + " conservativeBoundarySupport * 0.55);"));
        assertTrue(shader.contains("stableGrain"));
        assertTrue(shader.contains("animatedGrain(grainSeed)"));
        assertTrue(compactShader.contains(
                "float grayGradient = smoothstep(0.08, 0.92, grayBand);"));
        assertTrue(compactShader.contains(
                "vec3 grayColor = mix(grayShadowColor, grayLightColor,"
                        + " grayGradient) * grayVariation;"));
    }

    @Test
    void projectedRadiusUniformIsDeclaredInShaderMetadata()
            throws IOException {
        String shader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String metadata = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.json");

        assertTrue(shader.contains(
                "uniform float ProjectedBodyRadiusPixels;"));
        assertTrue(metadata.contains(
                "\"name\": \"ProjectedBodyRadiusPixels\""));
        assertTrue(metadata.contains(
                "\"type\": \"float\", \"count\": 1"));
        assertTrue(shader.contains(
                "uniform float BodyGrayTransitionFraction;"));
        assertTrue(metadata.contains(
                "\"name\": \"BodyGrayTransitionFraction\""));
        assertTrue(metadata.contains(
                "\"values\": [ 0.10 ]"));
    }

    @Test
    void purpleRimIsFourFinalScreenPixelsAtEverySourceResolution()
            throws IOException {
        String shader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String metadata = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.json");
        String compactShader = shader.replaceAll("\\s+", " ");

        assertTrue(shader.contains("uniform vec2 ScreenSize;"));
        assertTrue(shader.contains(
                "const float PURPLE_RIM_REACH_OUTPUT_PIXELS = 4.0;"));
        assertTrue(shader.contains(
                "const float PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS = 1.5;"));
        assertTrue(compactShader.contains(
                "float outputPixelsToSourcePixels ="
                        + " float(textureSize(Sampler0, 0).y)"
                        + " / max(ScreenSize.y, 1.0);"));
        assertTrue(compactShader.contains(
                "float rimReachPixels = PURPLE_RIM_REACH_OUTPUT_PIXELS"
                        + " * outputPixelsToSourcePixels;"));
        assertTrue(compactShader.contains(
                "float outsideRimReachPixels"
                        + " = PURPLE_RIM_OUTSIDE_OUTPUT_PIXELS"
                        + " * outputPixelsToSourcePixels;"));
        assertTrue(compactShader.contains(
                "float insideRimReachPixels = max("
                        + " rimReachPixels - outsideRimReachPixels, 0.0);"));
        assertTrue(compactShader.contains(
                "vec2 nearStep = texel * outsideRimReachPixels;"));
        assertTrue(compactShader.contains(
                "float surfaceRim = fill * (1.0 - smoothstep("
                        + " resolvedInsideRimReachPixels * 0.72,"
                        + " max(resolvedInsideRimReachPixels, 0.0001),"
                        + " boundaryDistancePixels)) * 0.96;"));
        assertTrue(metadata.contains("\"name\": \"ScreenSize\""));
        assertTrue(metadata.contains(
                "\"type\": \"float\", \"count\": 2"));

        assertEquals(4.0f,
                sourcePixelReach(4.0f, 1080, 1080), 0.000001f);
        assertEquals(2.0f,
                sourcePixelReach(4.0f, 540, 1080), 0.000001f,
                "a half-resolution source must use two source pixels");
        assertEquals(4.0f,
                sourcePixelReach(4.0f, 540, 1080)
                        * 1080.0f / 540.0f,
                0.000001f,
                "the half-resolution source reach must display as four pixels");
        assertEquals(4.0f, 1.5f + 2.5f, 0.000001f,
                "the perceived rim remains four output pixels while only"
                        + " 1.5 pixels dilate outside the exact silhouette");
    }

    @Test
    void projectedBodyGradientIsIndependentFromCollapseEnvelope()
            throws IOException {
        String densityFbo = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String compactDensityFbo = densityFbo.replaceAll("\\s+", " ");

        assertTrue(compactDensityFbo.contains(
                "projectedBodyBounds.inscribedRadiusPixels("
                        + " PIPELINE.getTargetWidth(),"
                        + " PIPELINE.getTargetHeight()));"));
        assertFalse(densityFbo.contains(
                        "getCompositeBodyCollapseEnvelopeScale()"),
                "material styling must not inherit geometry-collapse scale");
        assertTrue(compactDensityFbo.contains(
                "bodyGrayTransitionFraction.set("
                        + " CLEAN_BODY_GRAY_TRANSITION_FRACTION);"));
        assertTrue(compactDensityFbo.contains(
                "geometryBounds = geometryBounds.union("
                        + " DarkBallProjectedEffectBounds.UvBounds.conservative("
                        + " bodyBounds.x, bodyBounds.y,"
                        + " bodyBounds.z, bodyBounds.w));"),
                "the exact source silhouette must remain in composite bounds "
                        + "while body material can contribute");
        assertTrue(compactDensityFbo.contains(
                "if (directVolumeRendered"
                        + " && directVolumeExactBodyBoundsRequired)"),
                "siphon-only retirement must stop unioning the original body "
                        + "AABB into late composite work");
    }

    @Test
    void bodyDepthSpecularIsPerimeterBoundedGuardedAndFresnelFree()
            throws IOException {
        String shader = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String metadata = shaderResource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.json");
        String compactShader = shader.replaceAll("\\s+", " ");

        assertTrue(shader.contains("uniform float DepthHighlightBlend;"));
        assertTrue(metadata.contains("\"name\": \"DepthHighlightBlend\""));
        assertTrue(compactShader.contains(
                "float highlightSignal = clamp(depthHighlight"
                        + " * clamp(DepthHighlightBlend, 0.0, 1.0),"
                        + " 0.0, 1.0);"));
        assertFalse(shader.contains(
                "smoothstep(0.14, 0.62, DeformationBlend)"));
        assertTrue(compactShader.contains(
                "if (centerDepthValid > 0.001"
                        + " && centerDepthMaterial.g"
                        + " > centerDepthMaterial.r + 0.02)"));
        assertTrue(shader.contains(
                "uniform float BodyContourSheenStrength;"));
        assertTrue(metadata.contains(
                "\"name\": \"BodyContourSheenStrength\""));
        assertTrue(shader.contains(
                "uniform float BodyDepthSpecularStrength;"));
        assertTrue(metadata.contains(
                "\"name\": \"BodyDepthSpecularStrength\""));
        assertTrue(compactShader.contains(
                "vec2 contourToBoundaryPixels ="
                        + " (nearestBoundarySeedUv - texCoord0)"
                        + " * ScreenSize;"));
        assertTrue(compactShader.contains(
                "* distanceFieldUsable"
                        + " * clamp(BodyContourSheenStrength, 0.0, 0.06),"
                        + " 0.06);"));
        assertTrue(shader.contains(
                "float blackInnerEdgeProfile"));
        assertTrue(shader.contains(
                "float bodyContourSheenMask"));
        assertTrue(shader.contains(
                "float bodyDepthShoulder"));
        assertTrue(shader.indexOf("float bodyDepthShoulder")
                        > shader.indexOf("float blackInnerEdgeProfile"),
                "body depth work must be scheduled only after the narrow "
                        + "inner-edge carrier is known");
        assertTrue(compactShader.contains(
                "float bodyFourSideSupport = min(min("
                        + "bodyLeftSurface.w, bodyRightSurface.w),"
                        + " min(bodyDownSurface.w, bodyUpSurface.w));"));
        assertTrue(shader.contains("float bodyPlanarityConfidence"));
        assertTrue(shader.contains("float bodyShapeConfidence"));
        assertTrue(shader.contains("float bodyRoughSpecular"));
        assertFalse(shader.contains("bodyCrescentSpecular"),
                "the body must not restore the unstable Fresnel crescent");
        assertTrue(compactShader.contains(
                "clamp(BodyDepthSpecularStrength, 0.0, 0.12)"));
        assertTrue(shader.contains("float bodySpecularCap = 0.085"));
        assertTrue(shader.contains(
                "float combinedBodyHighlightMask = min"));
        assertTrue(shader.contains(
                "+ bodyDepthSpecularMask, 0.10);"));
        assertTrue(shader.contains(
                "color = mix(color, bodySheenColor,"
                        + " combinedBodyHighlightMask);"));
        assertFalse(shader.contains("bodyHighlightMask"));
        assertTrue(shader.contains(
                "float highlightMask = siphonHighlightMask;"));
        assertTrue(shader.contains(
                "float grayVariation = mix(0.985, 1.015, grain);"));

        String densityFbo = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String clientConfig = javaSource(
                "com/jayemceekay/shadowedhearts/config/ClientConfig.java");
        String interfaceConfig = javaSource(
                "com/jayemceekay/shadowedhearts/config/IClientConfig.java");
        assertTrue(densityFbo.contains(
                "shader.getUniform(\"BodyContourSheenStrength\")"));
        assertTrue(densityFbo.contains(
                ".darkBallBodyContourSheenStrength()"));
        assertTrue(densityFbo.contains(
                "shader.getUniform(\"BodyDepthSpecularStrength\")"));
        assertTrue(densityFbo.contains(
                ".darkBallBodyDepthSpecularStrength()"));
        assertTrue(clientConfig.contains(
                "\"bodyContourSheenStrength\""));
        assertTrue(clientConfig.contains(
                "\"bodyDepthSpecularStrength\""));
        assertTrue(interfaceConfig.contains(
                "default float darkBallBodyContourSheenStrength()"));
        assertTrue(interfaceConfig.contains(
                "default float darkBallBodyDepthSpecularStrength()"));
    }

    private static String shaderResource(String resource) throws IOException {
        ClassLoader loader =
                DarkBallCompositeGrayTransitionTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            assertNotNull(stream, "missing shader resource " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String javaSource(String source) throws IOException {
        java.nio.file.Path sourcePath = java.nio.file.Path.of(
                "src/main/java").resolve(source);
        return java.nio.file.Files.readString(
                sourcePath, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static float sourcePixelReach(float outputPixelReach,
                                          int sourceHeight,
                                          int outputHeight) {
        return outputPixelReach * sourceHeight
                / (float) Math.max(outputHeight, 1);
    }
}
