package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowPokemonAuraDistancePixelScaleTest {

    @Test
    void desiredLodPlateausAtTheMaximumInsideFiveBlocks() {
        assertEquals(3.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, 0.0),
                0.0001f);
        assertEquals(3.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, 4.9999),
                0.0001f);
        assertEquals(3.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, 5.0),
                0.0001f,
                "eight-pixel blocks must stop growing at five camera blocks");
        assertEquals(2.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, 10.0),
                0.0001f);
        assertEquals(1.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, 20.0),
                0.0001f);
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, 40.0),
                0.0001f);
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, 400.0),
                0.0001f,
                "the aura must remain at one output pixel beyond forty blocks");

        assertEquals(2.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(4, 2.0),
                0.0001f,
                "the configured maximum caps the nearest LOD");
        assertEquals(1.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(4, 10.0),
                0.0001f);
        assertEquals(2.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(6, 5.0),
                0.0001f,
                "non-power-of-two maxima must canonicalize down to a valid mip level");
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(0, 2.0),
                0.0001f,
                "invalid maxima degrade to a one-pixel presentation");
    }

    @Test
    void geometricDistanceMidpointsProduceHalfMipLevels() {
        assertEquals(2.5f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(
                        8, Math.sqrt(5.0 * 10.0)),
                0.0001f);
        assertEquals(1.5f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(
                        8, Math.sqrt(10.0 * 20.0)),
                0.0001f);
        assertEquals(0.5f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(
                        8, Math.sqrt(20.0 * 40.0)),
                0.0001f,
                "fractional LOD must interpolate geometrically between pixel sizes");
    }

    @Test
    void desiredLodNeverIncreasesAsCameraDistanceGrows() {
        float previous = Float.POSITIVE_INFINITY;
        for (int sample = 0; sample <= 400; sample++) {
            double cameraDistance = sample * 0.25;
            float pixelLod = ShadowPokemonAuraSystem.desiredWorldPixelLod(
                    8, cameraDistance);
            assertTrue(pixelLod <= previous,
                    "pixel blocks must not grow as the Pokemon recedes");
            assertTrue(pixelLod >= 0.0f && pixelLod <= 3.0f);
            previous = pixelLod;
        }
    }

    @Test
    void invalidDistancesHaveDeterministicSafeBehavior() {
        assertEquals(3.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, Double.NaN),
                0.0001f);
        assertEquals(3.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(8, -0.01),
                0.0001f);
        assertEquals(3.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(
                        8, Double.NEGATIVE_INFINITY),
                0.0001f);
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.desiredWorldPixelLod(
                        8, Double.POSITIVE_INFINITY),
                0.0001f,
                "an infinitely distant source resolves to one output pixel");
    }

    @Test
    void stabilizationPreservesFractionalLodInsteadOfSnappingToTiers() {
        assertEquals(2.5f,
                ShadowPokemonAuraSystem.stabilizedWorldPixelLod(
                        0.25f, 8, Math.sqrt(5.0 * 10.0)),
                0.0001f);
        assertEquals(1.5f,
                ShadowPokemonAuraSystem.stabilizedWorldPixelLod(
                        3.0f, 8, Math.sqrt(10.0 * 20.0)),
                0.0001f,
                "continuous mip blending replaces discrete hysteresis bands");
    }

    @Test
    void invalidDistancePreservesTheClampedCurrentLod() {
        assertEquals(1.25f,
                ShadowPokemonAuraSystem.stabilizedWorldPixelLod(
                        1.25f, 8, Double.NaN),
                0.0001f);
        assertEquals(1.25f,
                ShadowPokemonAuraSystem.stabilizedWorldPixelLod(
                        1.25f, 8, -1.0),
                0.0001f);
        assertEquals(2.0f,
                ShadowPokemonAuraSystem.stabilizedWorldPixelLod(
                        3.0f, 4, Double.NaN),
                0.0001f,
                "the retained LOD must first respect a lowered configured maximum");
        assertEquals(0.0f,
                ShadowPokemonAuraSystem.stabilizedWorldPixelLod(
                        1.25f, 8, Double.POSITIVE_INFINITY),
                0.0001f);
    }

    @Test
    void sharedGridHonorsTheFinestFractionalLodAcrossVisibleSources() {
        float nearSource = ShadowPokemonAuraSystem
                .stabilizedWorldPixelLod(0.0f, 8, Math.sqrt(5.0 * 10.0));
        float farSource = ShadowPokemonAuraSystem
                .stabilizedWorldPixelLod(3.0f, 8, Math.sqrt(20.0 * 40.0));

        assertEquals(2.5f, nearSource, 0.0001f);
        assertEquals(0.5f, farSource, 0.0001f);
        assertEquals(0.5f, Math.min(nearSource, farSource), 0.0001f,
                "one shared FBO must satisfy the finest visible source, including a recall tail");
    }

    @Test
    void projectedPuffCoverageRejectsOffscreenAndBehindCameraCenters() {
        assertTrue(projectedQuad(-0.1f, -0.1f, 0.1f, 0.1f, 0.0f, 1.0f));
        assertTrue(projectedQuad(0.9f, -0.1f, 1.2f, 0.1f, 0.0f, 1.0f),
                "a puff whose quad crosses the right edge still contributes");
        assertFalse(projectedQuad(1.01f, -0.1f, 1.2f, 0.1f, 0.0f, 1.0f));
        assertFalse(projectedQuad(-0.1f, -1.2f, 0.1f, -1.01f, 0.0f, 1.0f));
        assertFalse(projectedQuad(-0.1f, -0.1f, 0.1f, 0.1f, 0.0f, -1.0f));
        assertFalse(projectedQuad(-0.1f, -0.1f, 0.1f, 0.1f, 1.01f, 1.0f),
                "far-clipped quads cannot influence the presentation tier");
        assertFalse(ShadowPokemonAuraSystem.projectedPuffOverlapsViewport(
                new Vector4f(Float.NaN, 0.0f, 0.0f, 1.0f),
                new Vector4f(),
                new Vector4f(),
                new Vector4f()));
    }

    @Test
    void worldFrameUsesFinestVisibleLodPerStyleForDirectAndIrisComposite()
            throws IOException {
        String system = javaSource("client/aura/ShadowPokemonAuraSystem.java");
        String update = between(
                system,
                "private static void updateWorldPixelLodsForFrame(",
                "static float desiredWorldPixelLod(");
        String render = between(
                system,
                "private static void renderDensityPipelineInternal(",
                "private static void updateWorldPixelLodsForFrame(");
        String splats = between(
                system,
                "private static List<WorldPixelContributor> renderDensitySplats(",
                "private static float puffRenderQuality(");
        String iris = between(
                system,
                "public static void compositeIris()",
                "public static void renderDensityPipeline(Camera camera, float partialTicks, Matrix4f projectionMatrix)");
        String clear = between(
                system,
                "public static void clear()",
                "/**\n     * Preview-owned version");
        String compactUpdate = compact(update);

        assertFalse(update.contains("SOURCES"),
                "stale and offscreen source records must not select the frame grid");
        assertTrue(compactUpdate.contains(
                "source.stableWorldPixelLod=stabilizedWorldPixelLod("
                        + "source.stableWorldPixelLod,"
                        + "maximumPixelSize,"
                        + "contributor.cameraDistance)"),
                "each source owns its continuous LOD state across contributor changes");
        assertTrue(compactUpdate.contains(
                "sharedStyleLod=Math.min("
                        + "sharedStyleLod,source.stableWorldPixelLod)"),
                "each style target must use the finest fractional LOD required by its visible sources");
        assertTrue(compactUpdate.contains(
                "WORLD_PIXEL_LODS_THIS_FRAME.put(style,sharedStyleLod)"));
        assertTrue(compactUpdate.contains(
                "contributors=contributorsByStyle==null?null:"
                        + "contributorsByStyle.get(style)"),
                "contributors from one style must never select another style's pixel grid");
        assertTrue(compactUpdate.contains(
                "elseif(activePuffStyles==null||!activePuffStyles.contains(style))"),
                "each style's recall tail must retain only that style's last stable LOD");

        assertTrue(compact(render).contains(
                "visiblePixelContributors.put(style,renderDensitySplats("
                        + "camera,partialTicks,style))"));
        assertTrue(compact(render).contains(
                "updateWorldPixelLodsForFrame("
                        + "visiblePixelContributors,puffStyles)"));
        assertTrue(splats.contains("projectedPuffOverlapsViewport("),
                "only quads that can reach the viewport may influence the shared grid");
        assertTrue(compact(splats).contains(
                "puffClipTransform.set(viewProjection).mul(pose)"),
                "CPU visibility must use the same projection, view, pose, rotation, and scale as the shader");
        assertTrue(occurrences(splats, "puffClipTransform.transform(") == 4,
                "all four rotated billboard corners are required for conservative clip-plane rejection");
        assertTrue(system.contains("case 4 -> point.z + point.w < 0.0f"));
        assertTrue(system.contains("case 5 -> point.w - point.z < 0.0f"));
        assertTrue(compact(splats).contains(
                "pixelDistance=sourceCameraDistance("
                        + "puff.source,camPos,distance)"),
                "the five-block plateau must be measured from the Pokemon source, not an outlying puff");
        assertTrue(compact(splats).contains(
                "includeWorldPixelContributor("
                        + "visiblePixelContributors,puff.source,pixelDistance)"),
                "world LOD must use absolute camera distance rather than Pokemon size");
        assertFalse(splats.contains("normalizedPixelDistance"));
        assertTrue(compact(splats).contains(
                "if(contributor.source==source)"));
        assertTrue(compact(render).contains(
                "ShadowPokemonAuraFBO.composite(style,worldPixelLodForStyle(style))"),
                "the direct path must consume the fractional LOD selected for this frame");
        assertTrue(compact(render).contains("irisCompositePending=true"));
        assertTrue(compact(iris).contains(
                "ShadowPokemonAuraFBO.composite(style,worldPixelLodForStyle(style))"),
                "the delayed Iris path must consume the same cached density-frame LOD");
        assertFalse(iris.contains("updateWorldPixelLodsForFrame"),
                "Iris presentation must not recompute from later camera state");
        assertFalse(iris.contains("getMainCamera"));
        assertTrue(clear.contains("resetWorldPixelLods()"));
        assertTrue(compact(update).contains(
                "WORLD_PIXEL_LODS_THIS_FRAME.getOrDefault("
                        + "style==null?ShadowAuraStyle.DEFAULT:style,"));
    }

    @Test
    void spawnedPuffsRetainTheirSourceAfterSourceRemoval()
            throws IOException {
        String system = javaSource("client/aura/ShadowPokemonAuraSystem.java");
        assertTrue(system.contains("final SourceState source;"),
                "recall puffs must retain their owner and its continuous LOD state");
        String sourceDistance = between(
                system,
                "private static double sourceCameraDistance(",
                "private static void includeWorldPixelContributor(");
        assertTrue(sourceDistance.contains(
                "double dx = source.pixelCenterX - cameraPosition.x"));
        assertTrue(sourceDistance.contains(
                "double dy = source.pixelCenterY - cameraPosition.y"));
        assertTrue(sourceDistance.contains(
                "double dz = source.pixelCenterZ - cameraPosition.z"));
        assertTrue(sourceDistance.contains(
                "Math.sqrt(dx * dx + dy * dy + dz * dz)"));

        String despawn = between(
                system,
                "public static void onPokemonDespawn(int entityId)",
                "public static void clear()");
        assertTrue(despawn.contains("SOURCES.remove(entityId)"));
        assertFalse(despawn.contains("ACTIVE"),
                "recall tails must remain alive with their captured source state");
    }

    @Test
    void adaptiveWorldSizingDoesNotDependOnDeviceDepthConvention()
            throws IOException {
        String system = javaSource("client/aura/ShadowPokemonAuraSystem.java");
        String adaptive = between(
                system,
                "private static List<WorldPixelContributor> renderDensitySplats(",
                "private static float puffRenderQuality(");

        assertTrue(adaptive.contains("camera.getPosition()"));
        assertTrue(adaptive.contains("Math.sqrt(rx * rx + ry * ry + rz * rz)"),
                "camera-relative Euclidean distance works for forward and reversed-Z projections");
        assertTrue(adaptive.contains("projectedPuffOverlapsViewport("));
        assertFalse(adaptive.contains("gl_FragCoord"));
        assertFalse(adaptive.contains("GL_GREATER"));
        assertFalse(adaptive.contains("GL_LESS"));
        assertFalse(adaptive.contains("InvProj"));
        assertFalse(adaptive.contains("Sampler1"));
        assertFalse(adaptive.contains("getDensityDepthTextureId"));
    }

    @Test
    void guiCompositeRemainsIndependentFromWorldDistanceState()
            throws IOException {
        String fbo = javaSource("client/aura/ShadowPokemonAuraFBO.java");
        String gui = between(
                fbo,
                "public static boolean composite(float minimumU",
                "static int guiPixelSize()");
        String guiRenderer = javaSource(
                "client/aura/ShadowPokemonAuraGuiRenderer.java");

        assertTrue(compact(gui).contains(
                "intguiPixelSize=Math.max(1,Math.round(guiPixelSize()*"
                        + "ShadowAuraStyleProfiles.forStyle(safeStyle)"
                        + ".render().pixelSizeScale()))"));
        assertTrue(gui.contains("guiPixelCompositeShader(safeStyle)"));
        assertTrue(gui.contains("guiCompositeShader(safeStyle)"));
        assertTrue(gui.contains("SHADOW_POKEMON_AURA_GUI_PRESENT"));
        assertFalse(gui.contains("worldPixelSize"));
        assertFalse(gui.contains("maximumWorldPixelSize"));
        assertFalse(gui.contains("worldPixelLod"));
        assertFalse(gui.contains("maximumWorldPixelLod"));
        assertFalse(gui.contains("distance"));
        assertFalse(guiRenderer.contains("desiredWorldPixelLod"));
        assertFalse(guiRenderer.contains("stabilizedWorldPixelLod"));
        assertFalse(guiRenderer.contains("worldPixelLodThisFrame"));
        assertFalse(guiRenderer.contains("WORLD_PIXEL_LODS_THIS_FRAME"));
        assertFalse(guiRenderer.contains("worldPixelLodForStyle"));
        assertFalse(guiRenderer.contains("updateWorldPixelLodsForFrame"));
    }

    @Test
    void lodZeroAndFractionalMipFailureUseDirectWorldCompositeInTheSameFrame()
            throws IOException {
        String fbo = javaSource("client/aura/ShadowPokemonAuraFBO.java");
        String world = between(
                fbo,
                "public static boolean composite(float worldPixelLod)",
                "/**");

        assertTrue(world.contains("effectivePixelLod > 0.001f"),
                "LOD zero must skip a pointless full-resolution mip target");
        int pixelAttempt = world.indexOf("pipeline.compositeFractionalMip(");
        int directFallback = world.indexOf(
                "return pipeline.composite(",
                pixelAttempt);
        assertTrue(pixelAttempt >= 0 && directFallback > pixelAttempt,
                "allocation, shader, and presentation failures must fall through immediately");
        assertTrue(world.contains("worldPixelCompositeShader(safeStyle)"));
        assertTrue(world.contains("worldCompositeShader(safeStyle)"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static boolean projectedQuad(
            float minimumX,
            float minimumY,
            float maximumX,
            float maximumY,
            float z,
            float w) {
        return ShadowPokemonAuraSystem.projectedPuffOverlapsViewport(
                new Vector4f(minimumX, minimumY, z, w),
                new Vector4f(maximumX, minimumY, z, w),
                new Vector4f(maximumX, maximumY, z, w),
                new Vector4f(minimumX, maximumY, z, w)
        );
    }

    private static String javaSource(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/" + suffix),
                StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0, "missing start marker: " + start);
        assertTrue(endIndex > startIndex, "missing end marker: " + end);
        return source.substring(startIndex, endIndex);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
