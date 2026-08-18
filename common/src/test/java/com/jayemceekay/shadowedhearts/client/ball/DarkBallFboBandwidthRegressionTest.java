package com.jayemceekay.shadowedhearts.client.ball;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallFboBandwidthRegressionTest {

    @Test
    void darkBallShaderUniformDefaultsMatchDeclaredCounts()
            throws IOException {
        Path shaderDirectory = Path.of(
                "src/main/resources/assets/shadowedhearts/shaders/core/darkball");
        try (Stream<Path> resources = Files.list(shaderDirectory)) {
            for (Path resource : resources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                JsonObject shader = JsonParser.parseString(
                        Files.readString(resource, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                JsonArray uniforms = shader.getAsJsonArray("uniforms");
                if (uniforms == null) {
                    continue;
                }
                for (int index = 0; index < uniforms.size(); index++) {
                    JsonObject uniform = uniforms.get(index).getAsJsonObject();
                    int declaredCount = uniform.get("count").getAsInt();
                    JsonArray values = uniform.getAsJsonArray("values");
                    assertEquals(
                            declaredCount,
                            values.size(),
                            () -> resource.getFileName()
                                    + " uniform "
                                    + uniform.get("name").getAsString()
                                    + " declares " + declaredCount
                                    + " default values but supplies "
                                    + values.size());
                }
            }
        }
    }

    @Test
    void densityAttachmentIsTheOnlySceneDepthSource() throws IOException {
        String pipeline = javaSource(
                "com/jayemceekay/shadowedhearts/client/render/"
                        + "DensityFboPipeline.java");
        String densityFbo = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String compactPipeline = compact(pipeline);
        String compactDensityFbo = compact(densityFbo);

        assertTrue(compactPipeline.contains(
                "public int getDensityDepthTextureId() {"
                        + " return densityTarget != null"
                        + " ? densityTarget.getDepthTextureId() : 0; }"));
        assertTrue(compactDensityFbo.contains(
                "static int getSceneDepthTextureId() {"
                        + " return PIPELINE.getDensityDepthTextureId(); }"));
        assertEquals(3, countOccurrences(
                        densityFbo, "PIPELINE.getDensityDepthTextureId()"),
                "direct volume, edge resolve, and reduced upsample must use"
                        + " the density attachment's pristine scene depth");
        assertFalse(densityFbo.contains("getProxyFrontDepthTextureId"));
    }

    @Test
    void beginPassDoesNotPreserveOrRefreshProxyFrontDepth()
            throws IOException {
        String densityFbo = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String captureVfx = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java");
        String beginDensityPass = sourceBetween(
                densityFbo,
                "public static boolean beginDensityPass("
                        + "boolean clearTarget, boolean copyMainDepth)",
                "public static void endDensityPass()");

        assertFalse(beginDensityPass.contains("beginProxyDepthPass"),
                "beginning density must not make a redundant proxy-front copy");
        assertFalse(densityFbo.contains(
                "refreshSceneDepthForDirectVolume"));
        assertFalse(captureVfx.contains(
                "refreshSceneDepthForDirectVolume"));
    }

    @Test
    void proxyBackAllocationApiAndRenderPathRemainAbsent()
            throws IOException {
        String pipeline = javaSource(
                "com/jayemceekay/shadowedhearts/client/render/"
                        + "DensityFboPipeline.java");
        String captureVfx = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallCaptureVfx.java");
        String compactPipeline = compact(pipeline);
        String compactCaptureVfx = compact(captureVfx);

        assertTrue(compactPipeline.contains(
                "public boolean beginProxyDepthPass("
                        + "boolean clearTarget, boolean copyMainDepth) {"
                        + " return beginAuxiliaryPass(proxyFrontDepthTarget,"
                        + " clearTarget, copyMainDepth); }"));
        assertEquals(1, countOccurrences(
                pipeline,
                "proxyFrontDepthTarget = createFloatColorTarget("));
        assertTrue(compactCaptureVfx.contains(
                "if (frontRendered) {"
                        + " compositeProxyDepthAvailable = 1.0f; }"));
        assertFalse(captureVfx.contains("backRendered"));
        assertFalse(captureVfx.contains("GL11.GL_FRONT"));

        for (Path source : mainJavaSources()) {
            String lowerSource = Files.readString(
                    source, StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            assertFalse(lowerSource.contains("proxyback"),
                    "proxy-back production reference remains in " + source);
        }
    }

    @Test
    void lowQualityCompositeResolvesOffscreenAndUpsamplesWithDepth()
            throws IOException {
        String densityFbo = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String pipeline = javaSource(
                "com/jayemceekay/shadowedhearts/client/render/"
                        + "DensityFboPipeline.java");
        String shader = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.fsh");
        String shaderJson = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_volume_composite.json");
        String upsampleShader = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_reduced_upsample.fsh");
        String upsampleJson = resourceSource(
                "assets/shadowedhearts/shaders/core/darkball/"
                        + "dark_ball_reduced_upsample.json");
        String compactDensityFbo = compact(densityFbo);
        String compactPipeline = compact(pipeline);
        String compactUpsample = compact(upsampleShader);

        assertTrue(compactDensityFbo.contains(
                "reducedCompositeRequested ="
                        + " ENABLE_REDUCED_COMPOSITE"
                        + " && (quality == DarkBallVfxQuality.LOW"
                        + " || mediumLargeScreenEffect)"
                        + " && ModShaders.DARK_BALL_REDUCED_UPSAMPLE != null"),
                "LOW and conservatively large MEDIUM effects should"
                        + " request the reduced composite");
        assertTrue(compactDensityFbo.contains(
                "DarkBallDeformationSettings.debugMode() == 0;"),
                "diagnostic modes should not allocate the styled target");
        assertTrue(densityFbo.contains(
                "PIPELINE.setReducedCompositeEnabled("));
        assertTrue(compactDensityFbo.contains(
                "reducedCompositeRequested"
                        + " && debugMode == 0"
                        + " && useEdgeTongueMaterial"),
                "raw deformation diagnostics must retain their direct path");
        assertTrue(densityFbo.contains(
                "PIPELINE.processProcessedToReducedComposite("));
        assertTrue(densityFbo.contains(
                "PIPELINE.compositeReduced("));
        assertTrue(densityFbo.contains(
                "ModShaders.DARK_BALL_REDUCED_UPSAMPLE"));
        int fallback = densityFbo.indexOf(
                "if (!compositeRendered)");
        int directComposite = densityFbo.indexOf(
                "compositeRendered = PIPELINE.composite(",
                Math.max(fallback, 0));
        assertTrue(fallback >= 0 && directComposite > fallback,
                "an unavailable reduced target must fall back in-frame");

        assertTrue(pipeline.contains(
                "private RenderTarget reducedCompositeTarget;"));
        assertTrue(compactPipeline.contains(
                "reducedCompositeTarget = reducedCompositeEnabled"
                        + " ? createValidatedCompactColorTarget("),
                "full-resolution quality modes must not retain"
                        + " an unused compact attachment");
        assertTrue(compactPipeline.contains(
                "GL11.GL_RGBA8,"
                        + " targetWidth,"
                        + " targetHeight,"),
                "the styled intermediate should not consume RGBA16F bandwidth");
        assertTrue(compactPipeline.contains(
                "RenderSystem.setShaderTexture("
                        + " 0, blurTempTarget.getColorTextureId());"),
                "the exact/depth-resolved processed material remains"
                        + " the reduced resolve source");
        assertTrue(compactPipeline.contains(
                "(float) Math.max(savedState.viewportWidth(), 1),"
                        + " (float) Math.max("
                        + "savedState.viewportHeight(), 1)"),
                "pixel-sized rim constants must remain expressed in"
                        + " final-output pixels");

        assertFalse(shaderJson.contains("\"CompositePassMode\""));
        assertFalse(shader.contains("depthAwareReducedComposite"),
                "the expensive material shader must not carry the"
                        + " reduced upsample branch");
        assertTrue(upsampleJson.contains("\"SceneDepthSampler\""));
        assertTrue(upsampleJson.contains("\"InvProjMat\""));
        assertTrue(compactUpsample.contains(
                "vec4 styled = texture(Sampler0, texCoord0);"),
                "the upsample should use one hardware-bilinear color read");
        assertTrue(upsampleShader.contains(
                "texelFetch(Sampler0, coordinates[nearestIndex], 0)"),
                "a single nearest styled sample should classify halos");
        assertTrue(upsampleShader.contains(
                "return texelFetch(SceneDepthSampler, sceneCoord, 0).r"),
                "the compact pass should use scene depth rather than"
                        + " four additional material/color reads");
        assertEquals(2, countOccurrences(
                        upsampleShader, "sceneDepthAt("),
                "the upsample must have one four-sample depth loop, without"
                        + " redundantly fetching the nearest depth twice");
        assertTrue(compactUpsample.contains(
                "if (styled.a < 0.001 || nearestStyled.a < 0.001)"),
                "an empty nearest styled texel must not gain bilinear alpha");
        assertTrue(compactUpsample.contains(
                "float conservativeAlpha = min(styled.a,"
                        + " nearestStyled.a);"),
                "upsampled alpha must never exceed nearest source coverage");
        assertTrue(compactUpsample.contains(
                "fragColor = vec4(styled.rgb, conservativeAlpha);"),
                "RGB should remain hardware-bilinear while alpha stays"
                        + " conservative");
        assertTrue(compactDensityFbo.contains(
                "quality == DarkBallVfxQuality.MEDIUM"
                        + " && projectedBodyAreaFraction"
                        + " >= (mediumReducedCompositeLatched"
                        + " ? mediumReleaseThreshold"
                        + " : MEDIUM_REDUCED_COMPOSITE_AREA_THRESHOLD)"));
        assertTrue(densityFbo.contains(
                "MEDIUM_REDUCED_COMPOSITE_AREA_THRESHOLD * 0.75f"),
                "the automatic policy needs hysteresis to avoid"
                        + " reallocating near the threshold");
        assertTrue(densityFbo.contains(
                "shadowedhearts.darkBallReducedCompositeMediumArea"));
    }

    @Test
    void formationCompositeUsesConservativeExactBodyBounds()
            throws IOException {
        String densityFbo = javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java");
        String compactDensityFbo = compact(densityFbo);

        assertTrue(compactDensityFbo.contains(
                "directVolumeRendered"
                        + " ? directVolumeGeometryBounds"
                        + " : exactBodyGeometryBounds()"
                        + " .expandByPixels("
                        + " DarkBallProjectedEffectBounds"
                        + " .DIRECT_PADDING_PIXELS,"),
                "formation must use the exact projected body instead"
                        + " of a full-screen quad");
        assertTrue(compactDensityFbo.contains(
                "return DarkBallProjectedEffectBounds.UvBounds.conservative("
                        + " bodyBounds.x, bodyBounds.y,"
                        + " bodyBounds.z, bodyBounds.w);"),
                "invalid exact bounds must retain conservative"
                        + " full-screen fallback");
    }

    @Test
    void finalCompositeRestoresCapturedStateEvenWhenDrawingFails()
            throws IOException {
        String pipeline = javaSource(
                "com/jayemceekay/shadowedhearts/client/render/"
                        + "DensityFboPipeline.java");
        String composite = sourceBetween(
                pipeline,
                "private boolean compositeFromTarget(",
                "/**\n     * Releases all framebuffer resources");
        int capture = composite.indexOf(
                "RenderTransactionState savedState ="
                        + "\n                RenderTransactionState.capture();");
        int guardedDraw = composite.indexOf("try {");
        int shaderClear = composite.indexOf("shader.clear();");
        int restore = composite.indexOf("savedState.restore();");

        assertTrue(capture >= 0);
        assertTrue(guardedDraw > capture);
        assertTrue(shaderClear > guardedDraw);
        assertTrue(restore > shaderClear);
        assertTrue(composite.contains("finally {"));
        assertTrue(composite.contains(
                "RenderSystem.getModelViewMatrix().set(savedMV);"));
        assertFalse(composite.contains("RenderSystem.defaultBlendFunc()"),
                "the pass must restore incoming state, not force defaults");
    }

    @Test
    void compactTargetIsValidatedAndFailureDisablesRetries()
            throws IOException {
        String pipeline = javaSource(
                "com/jayemceekay/shadowedhearts/client/render/"
                        + "DensityFboPipeline.java");
        String compactPipeline = compact(pipeline);

        assertTrue(pipeline.contains(
                "GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)"));
        assertTrue(pipeline.contains(
                "GL30.GL_FRAMEBUFFER_COMPLETE"));
        assertTrue(compactPipeline.contains(
                "if (reducedCompositeEnabled"
                        + " && reducedCompositeTarget == null) {"
                        + " reducedCompositeSupported = false;"
                        + " reducedCompositeEnabled = false; }"));
        assertTrue(pipeline.contains(
                "public void disableReducedCompositeAfterFailure()"));
        assertTrue(pipeline.contains(
                "public boolean isReducedCompositeReady()"));
        assertTrue(compact(javaSource(
                "com/jayemceekay/shadowedhearts/client/ball/"
                        + "DarkBallDensityFBO.java")).contains(
                "if (reducedCompositeRequested"
                        + " && !PIPELINE.isReducedCompositeReady()) {"
                        + " reducedCompositeRequested = false;"
                        + " reducedCompositePolicy = \"direct-fallback\"; }"));
    }

    @Test
    void reducedUpsampleIsRegisteredOnBothPlatforms()
            throws IOException {
        String modShaders = javaSource(
                "com/jayemceekay/shadowedhearts/client/ModShaders.java");
        String fabric = rootSource(
                "fabric/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/fabric/ModShadersPlatformImpl.java");
        String neoforge = rootSource(
                "neoforge/src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/neoforge/ModShadersPlatformImpl.java");

        assertTrue(modShaders.contains(
                "ShaderInstance DARK_BALL_REDUCED_UPSAMPLE"));
        assertTrue(fabric.contains(
                "darkball/dark_ball_reduced_upsample"));
        assertTrue(fabric.contains(
                "ModShaders.DARK_BALL_REDUCED_UPSAMPLE = program"));
        assertTrue(neoforge.contains(
                "darkball/dark_ball_reduced_upsample"));
        assertTrue(neoforge.contains(
                "ModShaders.DARK_BALL_REDUCED_UPSAMPLE = shader"));
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

    private static List<Path> mainJavaSources() throws IOException {
        try (Stream<Path> sources = Files.walk(
                Path.of("src/main/java"))) {
            return sources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static String sourceBetween(
            String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        assertTrue(start >= 0, "missing source token " + startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(end > start, "missing source token " + endToken);
        return source.substring(start, end);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
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
}
