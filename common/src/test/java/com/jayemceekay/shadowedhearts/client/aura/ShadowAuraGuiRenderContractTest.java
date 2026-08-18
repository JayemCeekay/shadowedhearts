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

        assertTrue(modelWidgetMixin.contains(
                "ShadowPokemonAuraGuiRenderer.beginCapture(this.pokemon)"));
        assertTrue(posableMixin.contains(
                "ShadowPokemonAuraGuiRenderer.beginRenderedModelCapture()"));
        assertTrue(posableMixin.contains(
                "ShadowPokemonAuraGuiRenderer.endRenderedModelCapture()"));
        assertTrue(partMixin.contains(
                "ShadowPokemonAuraGuiRenderer.captureRenderedModelPart"));
        assertTrue(purification.indexOf(
                "ShadowPokemonAuraGuiRenderer.beginCapture(centerRenderablePokemon)")
                < purification.indexOf("drawProfilePokemon("));

        assertTrue(renderer.contains(
                "textures/particle/penumbra_trail_16.png"));
        assertTrue(renderer.contains(
                "ModShaders.SHADOW_POKEMON_AURA_DENSITY"));
        assertTrue(renderer.contains(
                "ShadowPokemonAuraFBO.beginDensityPass(true, true)"));
        assertTrue(renderer.contains("finally"));
        assertTrue(renderer.contains("ShadowPokemonAuraFBO.endDensityPass()"));
        assertTrue(renderer.contains("ShadowPokemonAuraFBO.composite("));
        assertTrue(renderer.contains("seenAnchorCount"));
        assertTrue(renderer.contains("capture.anchors.set(slot, anchor)"));
        assertTrue(renderer.contains("AuraNoiseScale"));
        assertTrue(renderer.contains("frontmostCoverageAnchors("));
        assertTrue(renderer.contains("renderCoveragePuffs("));
        assertTrue(renderer.contains("projection.depthBias("));
        assertTrue(renderer.contains(
                "outward.z * outwardPush + cameraBias.z"));
        assertTrue(renderer.contains("anchor.z + awayBias.z"));
        assertTrue(renderer.contains("anchor.outwardZ"));
        assertTrue(renderer.contains("GL11.GL_DEPTH_RANGE"));
        assertFalse(renderer.contains("ShadowPokemonAuraSystem.observe("));
        assertFalse(renderer.contains("renderDensityPipeline("));
    }

    @Test
    void guiMaterialOverridesCannotLeakIntoTheWorldAuraMaterial()
            throws IOException {
        String guiRenderer = source(
                "client/aura/ShadowPokemonAuraGuiRenderer.java");
        String worldRenderer = source("client/aura/ShadowPokemonAuraSystem.java");
        String densityMaterial = resource(
                "shaders/core/aura/shadow_pokemon_aura_density.json");
        String densityFragment = resource(
                "shaders/core/aura/shadow_pokemon_aura_density.fsh");

        assertTrue(worldRenderer.contains("uNoiseScale.set(1.0f)"));
        assertTrue(worldRenderer.contains("uMaskFalloff.set(4.2f)"));
        assertTrue(densityMaterial.matches(
                "(?s).*\\\"name\\\"\\s*:\\s*\\\"AuraNoiseScale\\\""
                        + ".*?\\\"values\\\"\\s*:\\s*\\[\\s*1\\.0\\s*].*"));
        assertTrue(densityMaterial.matches(
                "(?s).*\\\"name\\\"\\s*:\\s*\\\"AuraMaskFalloff\\\""
                        + ".*?\\\"values\\\"\\s*:\\s*\\[\\s*4\\.2\\s*].*"));
        assertTrue(densityFragment.contains("uniform float AuraMaskFalloff;"));
        assertTrue(densityFragment.contains(
                "exp(-AuraMaskFalloff * (1.0 - mask) * (1.0 - mask))"));

        int guiOverride = guiRenderer.indexOf(
                "maskFalloff.set(GUI_MASK_FALLOFF)");
        int draw = guiRenderer.indexOf("BufferUploader.drawWithShader(mesh)");
        int restore = guiRenderer.indexOf(
                "maskFalloff.set(WORLD_MASK_FALLOFF)");
        assertTrue(guiRenderer.contains("GUI_MASK_FALLOFF = 1.8f"));
        assertTrue(guiRenderer.contains("WORLD_MASK_FALLOFF = 4.2f"));
        assertTrue(guiOverride >= 0 && guiOverride < draw);
        assertTrue(draw < restore);
        assertTrue(guiRenderer.substring(draw, restore).contains("finally"));
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
}
