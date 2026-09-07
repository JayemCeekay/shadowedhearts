package com.jayemceekay.shadowedhearts.client.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowPokemonAuraPixelPresentationContractTest {

    @Test
    void presentationExtentUsesCeilingDivisionForOddViewportEdges() {
        assertEquals(960,
                DensityFboPipeline.pixelPresentationExtent(1920, 2));
        assertEquals(960,
                DensityFboPipeline.pixelPresentationExtent(1919, 2),
                "the final odd column needs a presentation texel");
        assertEquals(540,
                DensityFboPipeline.pixelPresentationExtent(1079, 2),
                "the final odd row needs a presentation texel");
        assertEquals(1,
                DensityFboPipeline.pixelPresentationExtent(1, 2));
        assertEquals(1,
                DensityFboPipeline.pixelPresentationExtent(0, 2));
        assertEquals(17,
                DensityFboPipeline.pixelPresentationExtent(17, 0),
                "invalid pixel sizes must degrade to one output pixel");
    }

    @Test
    void pixelResolveUsesFinishedDensityAndRestoresTheIrisTransaction()
            throws IOException {
        String pipeline = javaSource(
                "client/render/DensityFboPipeline.java");
        String pixelated = between(
                pipeline,
                "public boolean compositePixelated(",
                "private static void setCompositeSizeUniforms(");

        assertTrue(pixelated.contains(
                "RenderSystem.setShaderTexture(0, densityTarget.getColorTextureId())"),
                "blur finishes in densityTarget, so the horizontal temporary"
                        + " must not become the visible source");
        assertFalse(pixelated.contains(
                "blurTempTarget.getColorTextureId()"));
        assertTrue(pixelated.contains(
                "pixelPresentationExtent(\n                    outputWidth, safePixelSize)"));
        assertTrue(pixelated.contains(
                "pixelPresentationExtent(\n                    outputHeight, safePixelSize)"));
        assertTrue(compact(pipeline).contains(
                "createValidatedCompactColorTarget("
                        + "targetWidth,targetHeight,GL11.GL_NEAREST)"),
                "the finished aura target must upscale without filtering");

        int fullColorMask = pixelated.indexOf(
                "RenderSystem.colorMask(true, true, true, true)");
        int clearTarget = pixelated.indexOf(
                "clearTargetUnscissored(slot.target)");
        assertTrue(fullColorMask >= 0 && fullColorMask < clearTarget,
                "glClear obeys Iris' incoming color mask, so all channels"
                        + " must be enabled before clearing the persistent target");

        int replaceBlend = pixelated.indexOf(
                "GlStateManager.SourceFactor.ONE");
        int replaceDestination = pixelated.indexOf(
                "GlStateManager.DestFactor.ZERO", replaceBlend);
        int alphaBlend = pixelated.indexOf(
                "GlStateManager.SourceFactor.SRC_ALPHA", replaceDestination);
        int alphaDestination = pixelated.indexOf(
                "GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA", alphaBlend);
        assertTrue(replaceBlend >= 0
                        && replaceDestination > replaceBlend
                        && alphaBlend > replaceDestination
                        && alphaDestination > alphaBlend,
                "resolve must preserve straight RGBA with ONE/ZERO before"
                        + " the final SRC_ALPHA presentation");

        assertTrue(pixelated.contains(
                "RenderTransactionState savedState = RenderTransactionState.capture()"));
        assertTrue(pixelated.contains(
                "VertexSorting savedVertexSorting = RenderSystem.getVertexSorting()"));
        assertTrue(pixelated.contains("finally {"));
        assertTrue(pixelated.contains("presentationShader.clear()"));
        assertTrue(pixelated.contains("materialShader.clear()"));
        assertTrue(pixelated.contains("savedProj, savedVertexSorting"));
        assertTrue(pixelated.contains("savedState.restore()"));

        assertTrue(pixelated.contains(
                "GL30.GL_DRAW_FRAMEBUFFER, destination.drawFramebuffer()"));
        assertTrue(pixelated.contains(
                "GL30.GL_READ_FRAMEBUFFER, destination.readFramebuffer()"));
        assertTrue(pixelated.contains(
                "destination.viewportX(), destination.viewportY()"));
        assertTrue(pixelated.contains(
                "presentationShader.getUniform(\"GridOrigin\")"));
        assertTrue(pixelated.contains(
                "(float) destination.viewportX()"));
        assertTrue(pixelated.contains(
                "(float) destination.viewportY()"));
        assertTrue(pixelated.contains(
                "presentationShader.getUniform(\"PixelSize\")"));
        assertTrue(compact(pixelated).contains(
                "setVec2Uniform(materialShader,\"PixelSize\","
                        + "safePixelSize,safePixelSize)"),
                "the material resolve must sample the same integer blocks"
                        + " that the presentation shader displays");
    }

    @Test
    void boundedGuiResolveHasAFilterGuardButPresentationKeepsExactClip()
            throws IOException {
        String pipeline = javaSource(
                "client/render/DensityFboPipeline.java");
        String pixelated = between(
                pipeline,
                "public boolean compositePixelated(",
                "private static void setCompositeSizeUniforms(");

        assertTrue(pixelated.contains(
                "float resolveMinimumU = minimumU - 1.0f / targetWidth"));
        assertTrue(pixelated.contains(
                "float resolveMinimumV = minimumV - 1.0f / targetHeight"));
        assertTrue(pixelated.contains(
                "float resolveMaximumU = maximumU + 1.0f / targetWidth"));
        assertTrue(pixelated.contains(
                "float resolveMaximumV = maximumV + 1.0f / targetHeight"));
        assertTrue(pixelated.contains(
                "drawScreenQuad(\n"
                        + "                    resolveMinimumU, resolveMinimumV,\n"
                        + "                    resolveMaximumU, resolveMaximumV)"));
        assertTrue(pixelated.contains(
                "drawScreenQuad(minimumU, minimumV, maximumU, maximumV)"),
                "the guard texel must never escape the ModelWidget clip");
    }

    @Test
    void directCompositeFallbackUsesDestinationFragmentRate()
            throws IOException {
        String pipeline = javaSource(
                "client/render/DensityFboPipeline.java");
        String direct = between(
                pipeline,
                "private boolean compositeFromTarget(",
                "/**\n     * Resolves the finished density material");
        String compact = compact(direct);

        assertTrue(compact.contains(
                "setCompositeSizeUniforms(shader,viewportWidth,viewportHeight,"
                        + "viewportWidth,viewportHeight)"),
                "direct rendering executes at the destination rate, so its"
                        + " derivative scale must remain one");
        assertFalse(compact.contains(
                "setCompositeSizeUniforms(shader,viewportWidth,viewportHeight,"
                        + "lastWidth,lastHeight)"));
    }

    @Test
    void fractionalWorldLodResolvesOnlyItsAdjacentNestedMipLevels()
            throws IOException {
        String pipeline = javaSource(
                "client/render/DensityFboPipeline.java");
        String fractional = between(
                pipeline,
                "public boolean compositeFractionalMip(",
                "private void resolvePixelPresentationLevel(");
        String compact = compact(fractional);

        assertTrue(compact.contains(
                "intlowerLevel=(int)Math.floor(safeLod)"));
        assertTrue(compact.contains(
                "intupperLevel=(int)Math.ceil(safeLod)"));
        assertTrue(compact.contains("intlowerPixelSize=1<<lowerLevel"));
        assertTrue(compact.contains("intupperPixelSize=1<<upperLevel"));
        assertTrue(compact.contains("floatlodBlend=safeLod-lowerLevel"));
        assertTrue(compact.contains(
                "booleanrequiresUpperLevel=upperLevel!=lowerLevel"));
        assertTrue(compact.contains(
                "lowerSlot=worldFractionalMipPresentations[lowerLevel]"));
        assertTrue(compact.contains(
                "upperSlot=worldFractionalMipPresentations[upperLevel]"));

        int lowerResolve = fractional.indexOf(
                "resolvePixelPresentationLevel(");
        int upperGuard = fractional.indexOf(
                "if (requiresUpperLevel) {", lowerResolve);
        int upperResolve = fractional.indexOf(
                "resolvePixelPresentationLevel(", lowerResolve + 1);
        assertTrue(lowerResolve >= 0
                        && upperGuard > lowerResolve
                        && upperResolve > upperGuard,
                "integral LODs must resolve one target while fractional LODs resolve exactly the adjacent pair");
        assertEquals(2, occurrences(
                fractional, "resolvePixelPresentationLevel("));

        assertTrue(fractional.contains(
                "int lowerTexture = lowerSlot.target.getColorTextureId()"));
        assertTrue(fractional.contains(
                "int upperTexture = upperSlot.target.getColorTextureId()"));
        assertTrue(fractional.contains(
                "presentationShader.setSampler(\"Sampler0\", lowerTexture)"));
        assertTrue(fractional.contains(
                "presentationShader.setSampler(\"Sampler1\", upperTexture)"));
        assertTrue(fractional.contains(
                "presentationShader, \"LowerPixelSize\""));
        assertTrue(fractional.contains(
                "presentationShader, \"UpperPixelSize\""));
        assertTrue(fractional.contains(
                "presentationShader.getUniform(\"LodBlend\")"));
        assertTrue(fractional.contains(
                "presentationShader.getUniform(\"FractionalMipEnabled\")"));
    }

    @Test
    void fractionalPresentationNearestFetchesEachLevelAndBlendsAlphaCorrectly()
            throws IOException {
        String presentMath = resource(
                "shaders/include/shadow_pokemon_aura_present.glsl");
        String worldPresent = resource(
                "shaders/core/aura/shadow_pokemon_aura_present.fsh");
        String worldPresentJson = resource(
                "shaders/core/aura/shadow_pokemon_aura_present.json");
        String fractional = between(
                presentMath,
                "vec4 shadowPokemonAuraFractionalMipPresent(",
                "return vec4(blended.rgb / blended.a, blended.a);");

        assertEquals(2, occurrences(
                fractional, "shadowPokemonAuraPresent("),
                "each adjacent level must keep exact nearest-within-level sampling");
        assertTrue(presentMath.contains(
                "texelFetch(source, sourceCoord, 0)"));
        assertFalse(presentMath.contains("texture(source"));
        assertTrue(fractional.contains(
                "lowerColor.rgb * lowerColor.a"));
        assertTrue(fractional.contains(
                "upperColor.rgb * upperColor.a"));
        assertTrue(fractional.contains(
                "mix(\n            lowerPremultiplied,\n            upperPremultiplied"));
        assertTrue(presentMath.contains(
                "blended.rgb / blended.a"),
                "premultiplied mip interpolation must return straight RGBA for the fixed SRC_ALPHA blend");

        assertTrue(worldPresent.contains("uniform sampler2D Sampler0;"));
        assertTrue(worldPresent.contains("uniform sampler2D Sampler1;"));
        assertTrue(worldPresent.contains("uniform vec2 LowerPixelSize;"));
        assertTrue(worldPresent.contains("uniform vec2 UpperPixelSize;"));
        assertTrue(worldPresent.contains("uniform float LodBlend;"));
        assertTrue(worldPresent.contains(
                "shadowPokemonAuraFractionalMipPresent("));
        assertTrue(compact(worldPresentJson).contains(
                "\"samplers\":[{\"name\":\"Sampler0\"},{\"name\":\"Sampler1\"}]"));
        assertAlphaBlend(worldPresentJson);
    }

    @Test
    void worldAndGuiUseIsolatedTargetsShadersAndDirectFallbacks()
            throws IOException {
        String pipeline = javaSource(
                "client/render/DensityFboPipeline.java");
        String fbo = javaSource(
                "client/aura/ShadowPokemonAuraFBO.java");
        String world = between(
                fbo,
                "public static boolean composite()",
                "/**");
        String gui = between(
                fbo,
                "public static boolean composite(float minimumU",
                "static int guiPixelSize()");
        String compactWorld = compact(world);
        String compactGui = compact(gui);

        assertTrue(pipeline.contains(
                "private final PixelPresentationTarget worldPixelPresentation"));
        assertTrue(pipeline.contains(
                "private final PixelPresentationTarget[] worldFractionalMipPresentations"));
        assertTrue(pipeline.contains(
                "private final PixelPresentationTarget guiPixelPresentation"));
        assertTrue(pipeline.contains("worldPixelPresentation.destroy()"));
        assertTrue(compact(pipeline).contains(
                "level<worldFractionalMipPresentations.length;level++"));
        assertTrue(pipeline.contains(
                "worldFractionalMipPresentations[level].destroy()"));
        assertTrue(pipeline.contains("guiPixelPresentation.destroy()"));
        assertTrue(pipeline.contains(
                "PixelPresentationTarget slot = guiTarget\n"
                        + "                ? guiPixelPresentation\n"
                        + "                : worldPixelPresentation"));

        assertTrue(compact(fbo).contains(
                "readPixelSize(\"shadowedhearts.shadowAuraPixelSize\",8,8)"));
        assertTrue(compact(fbo).contains(
                "readPixelSize(\"shadowedhearts.shadowAuraGuiLogicalPixelSize\",4,4)"));
        assertTrue(compactWorld.contains(
                "publicstaticbooleancomposite(){"
                        + "returncomposite((float)(Math.log(maximumWorldPixelSize())"
                        + "/Math.log(2.0)));}"),
                "the compatibility overload must retain the authored maximum");
        assertTrue(compactWorld.contains(
                "publicstaticbooleancomposite(floatworldPixelLod)"),
                "world callers need a continuous LOD overload");
        assertTrue(compactWorld.contains(
                "Math.max(0.0f,Math.min(maximumLod,worldPixelLod))"),
                "a frame-derived LOD must remain between one pixel and the authored maximum");
        assertTrue(compactWorld.contains(
                "PIPELINE.compositeFractionalMip("
                        + "ModShaders.SHADOW_POKEMON_AURA_PIXEL_COMPOSITE,"
                        + "ShadowPokemonAuraFBO::setupCompositeUniforms,"
                        + "ModShaders.SHADOW_POKEMON_AURA_PRESENT,"
                        + "effectivePixelLod,0.0f,0.0f,1.0f,1.0f)"));
        assertFalse(world.contains(
                "SHADOW_POKEMON_AURA_GUI_PIXEL_COMPOSITE"));
        assertFalse(world.contains("SHADOW_POKEMON_AURA_GUI_PRESENT"));

        int worldPixelAttempt = world.indexOf("PIPELINE.compositeFractionalMip(");
        int worldFallback = world.indexOf(
                "PIPELINE.composite(ModShaders.SHADOW_POKEMON_AURA_COMPOSITE",
                worldPixelAttempt);
        assertTrue(worldPixelAttempt >= 0 && worldFallback > worldPixelAttempt,
                "a missing shader, rejected FBO, or failed presentation must"
                        + " fall back during the same world frame");

        assertTrue(compactGui.contains(
                "PIPELINE.compositePixelated("
                        + "ModShaders.SHADOW_POKEMON_AURA_GUI_PIXEL_COMPOSITE,"
                        + "shader->setupCompositeUniforms(shader,animationTicks),"
                        + "ModShaders.SHADOW_POKEMON_AURA_GUI_PRESENT,"
                        + "guiPixelSize,true,minimumU,minimumV,maximumU,maximumV)"));
        assertFalse(gui.contains(
                "ModShaders.SHADOW_POKEMON_AURA_PIXEL_COMPOSITE,"));
        assertFalse(gui.contains(
                "ModShaders.SHADOW_POKEMON_AURA_PRESENT,"));

        int guiPixelAttempt = gui.indexOf("PIPELINE.compositePixelated(");
        int guiFallback = gui.indexOf(
                "ModShaders.SHADOW_POKEMON_AURA_GUI_COMPOSITE",
                guiPixelAttempt);
        assertTrue(guiPixelAttempt >= 0 && guiFallback > guiPixelAttempt,
                "GUI allocation or shader failure must preserve the bounded"
                        + " direct composite");
        assertTrue(gui.substring(guiFallback).contains("minimumU"));
        assertTrue(gui.substring(guiFallback).contains("minimumV"));
        assertTrue(gui.substring(guiFallback).contains("maximumU"));
        assertTrue(gui.substring(guiFallback).contains("maximumV"));

        assertTrue(fbo.contains(
                "(int) Math.round(mc.getWindow().getGuiScale())"));
        assertTrue(fbo.contains(
                "return Math.min(16, guiScale * GUI_LOGICAL_PIXEL_SIZE)"),
                "GUI sizing remains an independently authored logical-pixel"
                        + " multiplier");
    }

    @Test
    void pixelMaterialsAreDedicatedAndRegisteredOnBothPlatforms()
            throws IOException {
        String shaders = javaSource("client/ModShaders.java");
        String fabric = platformSource(
                "fabric",
                "client/fabric/ModShadersPlatformImpl.java");
        String neoForge = platformSource(
                "neoforge",
                "client/neoforge/ModShadersPlatformImpl.java");

        assertTrue(shaders.contains(
                "ShaderInstance SHADOW_POKEMON_AURA_PIXEL_COMPOSITE"));
        assertTrue(shaders.contains(
                "ShaderInstance SHADOW_POKEMON_AURA_GUI_PIXEL_COMPOSITE"));
        assertTrue(shaders.contains(
                "ShaderInstance SHADOW_POKEMON_AURA_PRESENT"));
        assertTrue(shaders.contains(
                "ShaderInstance SHADOW_POKEMON_AURA_GUI_PRESENT"));

        assertPixelRegistrations(fabric, "program");
        assertPixelRegistrations(neoForge, "shader");
    }

    @Test
    void presentationFetchesExactStraightRgbaWithoutAlphaQuantization()
            throws IOException {
        String presentMath = resource(
                "shaders/include/shadow_pokemon_aura_present.glsl");
        String worldPresent = resource(
                "shaders/core/aura/shadow_pokemon_aura_present.fsh");
        String guiPresent = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_present.fsh");
        String worldPresentJson = resource(
                "shaders/core/aura/shadow_pokemon_aura_present.json");
        String guiPresentJson = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_present.json");
        String worldResolveJson = resource(
                "shaders/core/aura/shadow_pokemon_aura_pixel_composite.json");
        String guiResolveJson = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_pixel_composite.json");
        String worldResolve = resource(
                "shaders/core/aura/shadow_pokemon_aura_pixel_composite.fsh");
        String guiResolve = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_pixel_composite.fsh");
        String compositeMath = resource(
                "shaders/include/shadow_pokemon_aura_composite.glsl");
        String worldDirect = resource(
                "shaders/core/aura/shadow_pokemon_aura_composite.fsh");
        String guiDirect = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_composite.fsh");
        String worldDirectJson = resource(
                "shaders/core/aura/shadow_pokemon_aura_composite.json");
        String guiDirectJson = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_composite.json");

        assertTrue(presentMath.contains("texelFetch(source, sourceCoord, 0)"));
        assertTrue(presentMath.contains(
                "floor((framebufferCoordinate - gridOrigin)"));
        assertTrue(presentMath.contains(
                "/ max(pixelSize, vec2(1.0))"));
        assertTrue(presentMath.contains("textureSize(source, 0)"));
        assertTrue(presentMath.contains("clamp("));
        assertFalse(presentMath.contains("texture(source"),
                "hardware linear filtering would erase the pixel grid");
        assertFalse(presentMath.contains("smoothstep"));
        assertFalse(presentMath.contains("discard"));
        assertFalse(presentMath.contains("quantiz"));
        assertFalse(presentMath.contains("threshold"));

        assertTrue(worldPresent.contains("uniform vec2 GridOrigin;"));
        assertTrue(worldPresent.contains("uniform vec2 PixelSize;"));
        assertTrue(guiPresent.contains("uniform vec2 GridOrigin;"));
        assertTrue(guiPresent.contains("uniform vec2 PixelSize;"));
        assertTrue(worldPresentJson.contains("\"name\": \"GridOrigin\""));
        assertTrue(worldPresentJson.contains("\"name\": \"PixelSize\""));
        assertTrue(guiPresentJson.contains("\"name\": \"GridOrigin\""));
        assertTrue(guiPresentJson.contains("\"name\": \"PixelSize\""));
        assertAlphaBlend(worldPresentJson);
        assertAlphaBlend(guiPresentJson);

        assertReplacementBlend(worldResolveJson);
        assertReplacementBlend(guiResolveJson);
        assertBlockCenteredResolve(worldResolve);
        assertBlockCenteredResolve(guiResolve);
        assertTrue(worldDirect.contains("uniform vec2 RenderSize;"));
        assertTrue(worldDirectJson.contains("\"name\": \"RenderSize\""));
        assertTrue(guiDirect.contains("uniform vec2 AuraRenderSize;"));
        assertTrue(guiDirectJson.contains("\"name\": \"AuraRenderSize\""));
        assertTrue(compositeMath.contains(
                "max(renderSize, vec2(1.0))"));
        assertTrue(compositeMath.contains(
                "/ max(screenSize, vec2(1.0))"));
        assertTrue(compositeMath.contains(
                "compositeColor = vec4(color, alpha)"),
                "the low-resolution target stores straight, continuous RGBA");
        assertFalse(compositeMath.contains("floor(alpha"));
    }

    private static void assertReplacementBlend(String material) {
        String compact = compact(material);
        assertTrue(compact.contains("\"srcrgb\":\"one\""));
        assertTrue(compact.contains("\"dstrgb\":\"zero\""));
        assertTrue(compact.contains("\"srcalpha\":\"one\""));
        assertTrue(compact.contains("\"dstalpha\":\"zero\""));
    }

    private static void assertAlphaBlend(String material) {
        String compact = compact(material);
        assertTrue(compact.contains("\"srcrgb\":\"srcalpha\""));
        assertTrue(compact.contains("\"dstrgb\":\"1-srcalpha\""));
        assertTrue(compact.contains("\"srcalpha\":\"srcalpha\""));
        assertTrue(compact.contains("\"dstalpha\":\"1-srcalpha\""));
    }

    private static void assertBlockCenteredResolve(String material) {
        assertTrue(material.contains("uniform vec2 PixelSize;"));
        assertTrue(material.contains("floor(gl_FragCoord.xy)"),
                "odd output dimensions must not redistribute resolve samples");
        assertTrue(material.contains("safePixelSize * 0.5"));
        assertTrue(material.contains("safeScreenSize / safePixelSize"),
                "detail derivatives should be normalized by the exact block size");
        assertTrue(material.contains("texture(Sampler0, sampleUv)"));
    }

    private static void assertPixelRegistrations(
            String registration,
            String assignedVariable) {
        assertRegistration(
                registration,
                "shadow_pokemon_aura_pixel_composite",
                "SHADOW_POKEMON_AURA_PIXEL_COMPOSITE",
                assignedVariable);
        assertRegistration(
                registration,
                "shadow_pokemon_aura_gui_pixel_composite",
                "SHADOW_POKEMON_AURA_GUI_PIXEL_COMPOSITE",
                assignedVariable);
        assertRegistration(
                registration,
                "shadow_pokemon_aura_present",
                "SHADOW_POKEMON_AURA_PRESENT",
                assignedVariable);
        assertRegistration(
                registration,
                "shadow_pokemon_aura_gui_present",
                "SHADOW_POKEMON_AURA_GUI_PRESENT",
                assignedVariable);
    }

    private static void assertRegistration(
            String registration,
            String resourceName,
            String fieldName,
            String assignedVariable) {
        assertTrue(registration.matches(
                "(?s).*shadowedhearts:aura/" + resourceName
                        + ".*?DefaultVertexFormat\\.POSITION_TEX"
                        + ".*?ModShaders\\." + fieldName
                        + "\\s*=\\s*" + assignedVariable + ".*"),
                "missing POSITION_TEX registration for " + resourceName);
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

    private static String javaSource(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/" + suffix),
                StandardCharsets.UTF_8);
    }

    private static String platformSource(String module, String suffix)
            throws IOException {
        return Files.readString(
                Path.of(
                        "..",
                        module,
                        "src/main/java/com/jayemceekay/shadowedhearts/" + suffix),
                StandardCharsets.UTF_8);
    }

    private static String resource(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/resources/assets/shadowedhearts/" + suffix),
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
