package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowAuraGuiRenderContractTest {

    @Test
    void allGuiEntrypointsUseTheDensityAuraInsteadOfLegacyCylinders()
            throws IOException {
        String emitters = source("client/aura/ShadowAuraEmitters.java");
        String summary = between(
                emitters,
                "public static void renderInSummaryGUI",
                "public static void renderInPcGUI");
        String pc = between(
                emitters,
                "public static void renderInPcGUI",
                "public static void renderInPurificationGUI");
        String purification = between(
                emitters,
                "public static void renderInPurificationGUI",
                "private static void renderInModelWidgetGUI");
        String sharedWidgetDelegate = between(
                emitters,
                "private static void renderInModelWidgetGUI",
                "public static void onRender");

        assertTrue(summary.contains("renderInModelWidgetGUI("));
        assertTrue(pc.contains("renderInModelWidgetGUI("));
        assertTrue(purification.contains("ShadowPokemonAuraGuiRenderer.render("));
        assertTrue(sharedWidgetDelegate.contains(
                "ShadowPokemonAuraGuiRenderer.render("));
        assertDensityOnly(summary);
        assertDensityOnly(pc);
        assertDensityOnly(purification);
        assertDensityOnly(sharedWidgetDelegate);
    }

    @Test
    void guiAuraCapturesTheActualPosedModelAndRunsAnIsolatedFboTransaction()
            throws IOException {
        String renderer = source(
                "client/aura/ShadowPokemonAuraGuiRenderer.java");
        String modelWidgetMixin = source("mixin/MixinModelWidgetAura.java");
        String posableMixin = source(
                "mixin/client/MixinPosableModelAuraMask.java");
        String partMixin = source(
                "mixin/client/MixinModelPartShadowAuraCapture.java");
        String purification = kotlinSource(
                "client/gui/PurificationStorageWidget.kt");
        String system = source("client/aura/ShadowPokemonAuraSystem.java");

        assertTrue(modelWidgetMixin.contains(
                "shadowedhearts$previewAura()"));
        assertTrue(modelWidgetMixin.contains(
                "ShadowPokemonAuraGuiRenderer.beginCapture("));
        assertTrue(modelWidgetMixin.contains(
                "method = \"setPokemon\""));
        assertTrue(posableMixin.contains(
                "ShadowPokemonAuraGuiRenderer.beginRenderedModelCapture(context, stack, rootPart)"));
        assertTrue(posableMixin.contains(
                "ShadowPokemonAuraGuiRenderer.endRenderedModelCapture()"));
        assertTrue(partMixin.contains(
                "ShadowPokemonAuraGuiRenderer.captureRenderedModelPart"));
        assertTrue(partMixin.contains(
                "ShadowPokemonAuraGuiRenderer.endRenderedModelPart"));
        assertTrue(purification.contains(
                "centerPokemon.uuid.toString()"));
        assertTrue(purification.indexOf("ShadowPokemonAuraGuiRenderer.beginCapture(")
                < purification.indexOf("drawProfilePokemon("));
        assertTrue(purification.contains("centerAuraPreview"));

        assertTrue(renderer.contains(
                "textures/particle/shadow_pokemon_aura.png"));
        assertTrue(renderer.contains(
                "ShadowPokemonAuraFBO.guiDensityShader(style)"));
        assertTrue(renderer.contains(
                "ShadowAuraStyle style = owner.style()"));
        assertTrue(compact(renderer).contains(
                "ShadowPokemonAuraFBO.beginDensityPass(style,true,true)"));
        assertTrue(renderer.contains("finally"));
        assertTrue(renderer.contains("ShadowPokemonAuraFBO.endDensityPass(style)"));
        assertTrue(renderer.contains("ShadowPokemonAuraFBO.blur(style)"));
        assertTrue(compact(renderer).contains(
                "ShadowPokemonAuraFBO.composite(style,minimumU,minimumV,"));
        assertTrue(renderer.contains("AuraNoiseScale"));
        assertTrue(renderer.contains("owner.advance(System.nanoTime(), strength)"));
        assertTrue(renderer.contains("renderPersistentDensitySplats(owner"));
        assertTrue(renderer.contains("owner.forEachPuff("));
        assertTrue(renderer.contains("simulationToGui.transformPosition("));
        assertTrue(renderer.contains("simulationToGui.transformDirection("));
        assertTrue(renderer.contains("RenderContext.Companion.getSPECIES()"));
        assertTrue(renderer.contains("RenderContext.Companion.getASPECTS()"));
        assertTrue(renderer.contains("RenderContext.Companion.getPOSABLE_STATE()"));
        assertTrue(renderer.contains("ticket.expectedState != renderedState"));
        assertTrue(renderer.contains(
                "private record CaptureTicket(ShadowPokemonAuraSystem.PreviewInstance owner"));
        assertTrue(renderer.contains("int generation,"));
        assertTrue(renderer.contains("PosableState expectedState"));
        assertTrue(renderer.contains("completed.generation == owner.generation()"));
        assertTrue(renderer.contains("boolean useProfileFallback = hasUnconsumedCapture(owner)"));
        assertTrue(renderer.contains("owner.useFallbackPose("));
        assertTrue(renderer.contains(
                "boolean persistentAura = owner.hasPose()\n                && owner.advance(System.nanoTime(), strength)"));
        assertTrue(renderer.contains(
                "(System.nanoTime() / 50_000_000.0) % 1200.0"));
        assertFalse(renderer.contains(
                "System.nanoTime() / 50_000_000.0 + partialTicks"));
        assertTrue(system.contains("public static final class PreviewInstance"));
        assertTrue(system.contains("private static final int PREWARM_TICKS = 96"));
        assertTrue(system.contains("private static final int MAX_PREVIEW_PUFFS = MAX_PUFFS"));
        assertTrue(system.contains("private static final long STEP_NANOS = 50_000_000L"));
        assertTrue(system.contains(
                "emitModelAura(frame, sourceState, latestAnchors, strength, 1.0f, particles)"));
        assertTrue(system.contains(
                "new ParticleRuntime(random, new ArrayList<>(), () -> MAX_PREVIEW_PUFFS)"));
        assertTrue(system.contains("rawToAura"));
        assertTrue(system.contains("inverseRootPose"));
        assertFalse(renderer.contains("ShadowPokemonAuraSystem.observe("));
        assertFalse(renderer.contains("renderDensityPipeline("));
    }

    @Test
    void worldAndGuiMaterialsUseIsolatedProgramsWithSharedMath()
            throws IOException {
        String guiRenderer = source(
                "client/aura/ShadowPokemonAuraGuiRenderer.java");
        String worldRenderer = source("client/aura/ShadowPokemonAuraSystem.java");
        String fbo = source("client/aura/ShadowPokemonAuraFBO.java");
        String pipeline = source("client/render/DensityFboPipeline.java");
        String shaders = source("client/ModShaders.java");
        String fabricShaders = platformSource(
                "fabric",
                "client/fabric/ModShadersPlatformImpl.java");
        String neoForgeShaders = platformSource(
                "neoforge",
                "client/neoforge/ModShadersPlatformImpl.java");

        String worldDensityMaterial = resource(
                "shaders/core/aura/shadow_pokemon_aura_density.json");
        String guiDensityMaterial = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_density.json");
        String worldDensityFragment = resource(
                "shaders/core/aura/shadow_pokemon_aura_density.fsh");
        String guiDensityFragment = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_density.fsh");
        String guiDensityVertex = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_density.vsh");
        String densityMath = resource(
                "shaders/include/shadow_pokemon_aura_density.glsl");

        String worldCompositeMaterial = resource(
                "shaders/core/aura/shadow_pokemon_aura_composite.json");
        String guiCompositeMaterial = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_composite.json");
        String worldCompositeFragment = resource(
                "shaders/core/aura/shadow_pokemon_aura_composite.fsh");
        String guiCompositeFragment = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_composite.fsh");
        String guiCompositeVertex = resource(
                "shaders/core/aura/shadow_pokemon_aura_gui_composite.vsh");
        String compositeMath = resource(
                "shaders/include/shadow_pokemon_aura_composite.glsl");

        assertTrue(worldRenderer.contains("uNoiseScale.set(1.0f)"));
        assertTrue(worldRenderer.contains("uMaskFalloff.set(4.2f)"));
        assertTrue(worldRenderer.contains(
                "ShadowPokemonAuraFBO.worldDensityShader(safeStyle)"));
        assertFalse(worldRenderer.contains(
                "ModShaders.SHADOW_POKEMON_AURA_GUI_DENSITY"));
        assertTrue(guiRenderer.contains(
                "ShadowPokemonAuraFBO.guiDensityShader(safeStyle)"));
        assertTrue(fbo.contains(
                "case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_DENSITY"));
        assertTrue(fbo.contains(
                "case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_GUI_DENSITY"));
        assertTrue(fbo.contains(
                "case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_COMPOSITE"));
        assertTrue(fbo.contains(
                "case SIGNATURE -> ModShaders.SHADOW_POKEMON_AURA_GUI_COMPOSITE"));
        assertFalse(guiRenderer.contains(
                "ModShaders.SHADOW_POKEMON_AURA_DENSITY"));
        assertFalse(guiRenderer.contains(
                "ModShaders.SHADOW_POKEMON_AURA_COMPOSITE"));
        assertTrue(guiRenderer.contains("shader.getUniform(\"AuraTime\")"));

        assertTrue(worldDensityMaterial.matches(
                "(?s).*\\\"name\\\"\\s*:\\s*\\\"AuraNoiseScale\\\""
                        + ".*?\\\"values\\\"\\s*:\\s*\\[\\s*1\\.0\\s*].*"));
        assertTrue(worldDensityMaterial.matches(
                "(?s).*\\\"name\\\"\\s*:\\s*\\\"AuraMaskFalloff\\\""
                        + ".*?\\\"values\\\"\\s*:\\s*\\[\\s*4\\.2\\s*].*"));
        assertTrue(guiDensityMaterial.matches(
                "(?s).*\\\"name\\\"\\s*:\\s*\\\"AuraNoiseScale\\\""
                        + ".*?\\\"values\\\"\\s*:\\s*\\[\\s*1\\.0\\s*].*"));
        assertTrue(guiDensityMaterial.matches(
                "(?s).*\\\"name\\\"\\s*:\\s*\\\"AuraMaskFalloff\\\""
                        + ".*?\\\"values\\\"\\s*:\\s*\\[\\s*4\\.2\\s*].*"));
        assertTrue(guiDensityMaterial.contains("\"name\": \"AuraTime\""));
        assertFalse(guiDensityMaterial.contains("\"name\": \"GameTime\""));
        assertTrue(worldDensityMaterial.contains(
                "shadowedhearts:aura/shadow_pokemon_aura_density"));
        assertTrue(guiDensityMaterial.contains(
                "shadowedhearts:aura/shadow_pokemon_aura_gui_density"));
        assertTrue(worldDensityFragment.contains(
                "#moj_import <shadowedhearts:shadow_pokemon_aura_density.glsl>"));
        assertTrue(guiDensityFragment.contains(
                "#moj_import <shadowedhearts:shadow_pokemon_aura_density.glsl>"));
        assertTrue(worldDensityFragment.contains("uniform float AuraMaskFalloff;"));
        assertTrue(worldDensityFragment.contains("uniform float GameTime;"));
        assertFalse(worldDensityFragment.contains("uniform float AuraTime;"));
        assertTrue(guiDensityFragment.contains("uniform float AuraMaskFalloff;"));
        assertTrue(guiDensityFragment.contains("uniform float AuraTime;"));
        assertFalse(guiDensityFragment.contains("uniform float GameTime;"));
        assertTrue(densityMath.contains("vec4 shadowPokemonAuraDensity("));
        assertTrue(densityMath.contains(
                "exp(-maskFalloff * (1.0 - mask) * (1.0 - mask))"));
        assertTrue(guiDensityVertex.contains("in ivec2 UV2;"));

        assertTrue(worldCompositeMaterial.contains(
                "shadowedhearts:aura/shadow_pokemon_aura_composite"));
        assertTrue(guiCompositeMaterial.contains(
                "shadowedhearts:aura/shadow_pokemon_aura_gui_composite"));
        assertTrue(worldCompositeFragment.contains(
                "#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>"));
        assertTrue(worldCompositeFragment.contains("uniform float GameTime;"));
        assertTrue(worldCompositeFragment.contains("uniform vec2 ScreenSize;"));
        assertFalse(worldCompositeFragment.contains("uniform float AuraTime;"));
        assertFalse(worldCompositeFragment.contains("uniform vec2 AuraScreenSize;"));
        assertTrue(guiCompositeFragment.contains(
                "#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>"));
        assertTrue(guiCompositeMaterial.contains("\"name\": \"AuraTime\""));
        assertTrue(guiCompositeMaterial.contains("\"name\": \"AuraScreenSize\""));
        assertFalse(guiCompositeMaterial.contains("\"name\": \"GameTime\""));
        assertFalse(guiCompositeMaterial.contains("\"name\": \"ScreenSize\""));
        assertTrue(guiCompositeFragment.contains("uniform float AuraTime;"));
        assertTrue(guiCompositeFragment.contains("uniform vec2 AuraScreenSize;"));
        assertTrue(fbo.contains("shader.getUniform(\"AuraTime\")"));
        assertTrue(fbo.contains("setupCompositeUniforms(shader, animationTicks)"));
        assertTrue(pipeline.contains(
                "setVec2Uniform(shader, \"AuraScreenSize\""));
        assertTrue(pipeline.contains(
                "setVec2Uniform(shader, \"AuraRenderSize\""));
        assertTrue(compositeMath.contains("bool shadowPokemonAuraComposite("));
        assertTrue(compositeMath.contains("compositeColor = vec4(color, alpha)"));
        assertTrue(guiCompositeVertex.contains("in vec2 UV0;"));

        assertTrue(shaders.contains(
                "ShaderInstance SHADOW_POKEMON_AURA_GUI_DENSITY"));
        assertTrue(shaders.contains(
                "ShaderInstance SHADOW_POKEMON_AURA_GUI_COMPOSITE"));
        assertDedicatedGuiRegistrations(fabricShaders);
        assertDedicatedGuiRegistrations(neoForgeShaders);

        String worldComposite = between(
                fbo,
                "public static boolean composite()",
                "/**");
        String guiComposite = between(
                fbo,
                "public static boolean composite(float minimumU",
                "static int guiPixelSize()");
        assertTrue(worldComposite.contains(
                "worldPixelCompositeShader(safeStyle)"));
        assertTrue(worldComposite.contains(
                "worldCompositeShader(safeStyle)"));
        assertFalse(worldComposite.contains(
                "guiCompositeShader"));
        assertTrue(guiComposite.contains(
                "guiPixelCompositeShader(safeStyle)"));
        assertTrue(guiComposite.contains(
                "guiCompositeShader(safeStyle)"));
        assertFalse(guiComposite.contains(
                "worldCompositeShader"));
    }

    private static void assertDedicatedGuiRegistrations(String registration) {
        assertTrue(registration.matches(
                "(?s).*shadowedhearts:aura/shadow_pokemon_aura_gui_density"
                        + ".*?DefaultVertexFormat\\.PARTICLE"
                        + ".*?ModShaders\\.SHADOW_POKEMON_AURA_GUI_DENSITY.*"));
        assertTrue(registration.matches(
                "(?s).*shadowedhearts:aura/shadow_pokemon_aura_gui_composite"
                        + ".*?DefaultVertexFormat\\.POSITION_TEX"
                        + ".*?ModShaders\\.SHADOW_POKEMON_AURA_GUI_COMPOSITE.*"));
    }

    private static void assertDensityOnly(String methodBody) {
        assertFalse(methodBody.contains("SHADOW_AURA_FOG_CYLINDER"));
        assertFalse(methodBody.contains("SHADOW_AURA_XD_CYLINDER"));
        assertFalse(methodBody.contains("AuraRenderTypes.shadow_fog()"));
        assertFalse(methodBody.contains("AuraRenderTypes.shadow_xd()"));
        assertFalse(methodBody.contains("CylinderBuffers"));
        assertFalse(methodBody.contains("buffersSummary"));
        assertFalse(methodBody.contains("buffersPC"));
        assertFalse(methodBody.contains("buffersPurification"));
    }

    private static String source(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/" + suffix),
                StandardCharsets.UTF_8);
    }

    private static String kotlinSource(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/kotlin/com/jayemceekay/shadowedhearts/" + suffix),
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
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, "missing start marker: " + start);
        assertTrue(endIndex > startIndex, "missing end marker: " + end);
        return source.substring(startIndex, endIndex);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
