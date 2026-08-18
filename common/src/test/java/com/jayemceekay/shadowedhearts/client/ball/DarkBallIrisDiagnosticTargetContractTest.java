package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the diagnostic final-composite source independently from the Dark
 * Ball scratch attachments. Iris owns a different world-color texture while
 * its pipeline is active, so consulting Minecraft's main target inside the
 * preview builder silently captures a stale vanilla image.
 */
class DarkBallIrisDiagnosticTargetContractTest {

    @Test
    void irisAndVanillaEntriesPublishTheirActualWorldColorTargets()
            throws IOException {
        String emitters = javaSource(
                "client/ball/BallEmitters.java");
        String vanillaEntry = sourceBetween(
                emitters,
                "public static void onRenderFBO(",
                "public static void renderDarkBallIris()");
        String irisEntry = sourceBetween(
                emitters,
                "public static void renderDarkBallIris()",
                "private static DarkBallPreviewResult"
                        + " renderDarkBallComposites(");

        int vanillaPublish = vanillaEntry.indexOf(
                "DarkBallFboDebugPreview.afterCompositeFrame(");
        assertTrue(vanillaPublish >= 0);
        String vanillaPublishCall = sourceThrough(
                vanillaEntry,
                vanillaPublish,
                ");");
        assertTrue(countOccurrences(vanillaPublishCall, ",") == 1,
                "the normal renderer should use the vanilla convenience"
                        + " overload rather than pretend to own an Iris"
                        + " attachment");

        int irisTexture = irisEntry.indexOf(
                "irisSnapshot.diffuseTexture");
        int irisWidth = irisEntry.indexOf(
                "irisSnapshot.renderWidth");
        int irisHeight = irisEntry.indexOf(
                "irisSnapshot.renderHeight");
        int irisPublish = irisEntry.indexOf(
                "DarkBallFboDebugPreview.afterCompositeFrame(");
        assertTrue(irisTexture >= 0,
                "Iris diagnostics must use the bound Iris world-color"
                        + " attachment, not Minecraft's main target");
        assertTrue(irisWidth >= 0 && irisHeight >= 0,
                "the Iris attachment dimensions must travel with its texture");
        assertTrue(irisPublish > irisTexture
                        && irisPublish > irisWidth
                        && irisPublish > irisHeight,
                "Iris texture ownership must be established before the"
                        + " diagnostic snapshot is published");
        assertFalse(irisEntry.contains("getMainRenderTarget()"),
                "the Iris branch must never substitute the vanilla target");
    }

    @Test
    void previewAtlasConsumesAnExplicitTextureAndDimensions()
            throws IOException {
        String preview = javaSource(
                "client/ball/DarkBallFboDebugPreview.java");
        String vanillaOverload = sourceBetween(
                preview,
                "static void afterCompositeFrame(\n"
                        + "            DarkBallCaptureVfx.FboPreviewCapture"
                        + " capture,\n"
                        + "            DarkBallDensityFBO.PreviewFrame"
                        + " frame) {",
                "/**\n"
                        + "     * Iris-aware form");
        assertTrue(vanillaOverload.contains("getMainRenderTarget()"));
        assertTrue(vanillaOverload.contains("target.getColorTextureId()"));
        assertTrue(vanillaOverload.contains("target.width"));
        assertTrue(vanillaOverload.contains("target.height"));

        String buildAtlas = sourceBetween(
                preview,
                "private static boolean buildAtlas(",
                "private static void ensureAtlasTarget()");
        assertTrue(dense(buildAtlas).startsWith(
                        "privatestaticbooleanbuildAtlas("
                                + "DarkBallDensityFBO.PreviewFrameframe,"
                                + "intcompositeTexture)"),
                "the atlas builder must receive the rendered world-color"
                        + " source separately from reusable scratch textures");

        assertFalse(buildAtlas.contains("getMainRenderTarget()"),
                "atlas tiles 0 and 12 must not reach back to a possibly stale"
                        + " Minecraft framebuffer");
        assertTrue(countOccurrences(
                        buildAtlas, "compositeTexture") >= 3,
                "both final-composite atlas tiles must sample the explicit"
                        + " world-color attachment");

        String afterComposite = sourceBetween(
                preview,
                "static void afterCompositeFrame(",
                "private static String fitText(");
        assertTrue(dense(afterComposite).contains(
                        "buildAtlas(frame,compositeSource.texture())"),
                "the exact target published by BallEmitters must reach the"
                        + " atlas builder");
        assertTrue(afterComposite.contains("compositeSource.width()"),
                "diagnostic metadata must use the published target width");
        assertTrue(afterComposite.contains("compositeSource.height()"),
                "diagnostic metadata must use the published target height");
    }

    private static String javaSource(String relativePath)
            throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/")
                        .resolve(relativePath)
                        .normalize(),
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

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String sourceThrough(
            String source, int start, String endToken) {
        int end = source.indexOf(endToken, start);
        assertTrue(end >= start, "missing source token " + endToken);
        return source.substring(start, end + endToken.length());
    }

    private static String dense(String source) {
        return source.replaceAll("\\s+", "");
    }
}
