package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowAuraStyleShaderContractTest {

    @Test
    void bothLoadersRegisterDedicatedWorldAndGuiProgramsForEveryStyle()
            throws IOException {
        String fields = source("client/ModShaders.java");
        String fabric = platformSource(
                "fabric", "client/fabric/ModShadersPlatformImpl.java");
        String neoForge = platformSource(
                "neoforge", "client/neoforge/ModShadersPlatformImpl.java");

        assertStylePrograms(fields, fabric, "COLOSSEUM", "colosseum");
        assertStylePrograms(fields, neoForge, "COLOSSEUM", "colosseum");
        assertStylePrograms(fields, fabric, "XD_FAITHFUL", "xd_faithful");
        assertStylePrograms(fields, neoForge, "XD_FAITHFUL", "xd_faithful");

        assertProgram(fields, fabric,
                "SHADOW_POKEMON_AURA_XD_FAITHFUL_FILAMENT_DENSITY",
                "shadow_pokemon_aura_xd_faithful_filament_density",
                "DefaultVertexFormat.PARTICLE");
        assertProgram(fields, fabric,
                "SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_FILAMENT_DENSITY",
                "shadow_pokemon_aura_xd_faithful_gui_filament_density",
                "DefaultVertexFormat.PARTICLE");
        assertProgram(fields, neoForge,
                "SHADOW_POKEMON_AURA_XD_FAITHFUL_FILAMENT_DENSITY",
                "shadow_pokemon_aura_xd_faithful_filament_density",
                "DefaultVertexFormat.PARTICLE");
        assertProgram(fields, neoForge,
                "SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_FILAMENT_DENSITY",
                "shadow_pokemon_aura_xd_faithful_gui_filament_density",
                "DefaultVertexFormat.PARTICLE");
    }

    @Test
    void styleMaterialsKeepWorldAndGuiUniformStateIsolatedWhileSharingMath()
            throws IOException {
        for (String style : new String[]{"colosseum", "xd_faithful"}) {
            String prefix = "shadow_pokemon_aura_" + style;

            String worldDensity = resource(
                    "shaders/core/aura/" + prefix + "_density.fsh");
            String guiDensity = resource(
                    "shaders/core/aura/" + prefix + "_gui_density.fsh");
            String worldDensityJson = resource(
                    "shaders/core/aura/" + prefix + "_density.json");
            String guiDensityJson = resource(
                    "shaders/core/aura/" + prefix + "_gui_density.json");

            assertTrue(worldDensity.contains(
                    "#moj_import <shadowedhearts:shadow_pokemon_aura_density.glsl>"));
            assertTrue(guiDensity.contains(
                    "#moj_import <shadowedhearts:shadow_pokemon_aura_density.glsl>"));
            assertWorldClock(worldDensity, worldDensityJson);
            assertGuiClock(guiDensity, guiDensityJson);
            assertTrue(worldDensityJson.contains(
                    "\"fragment\": \"shadowedhearts:aura/" + prefix
                            + "_density\""));
            assertTrue(guiDensityJson.contains(
                    "\"fragment\": \"shadowedhearts:aura/" + prefix
                            + "_gui_density\""));

            String worldComposite = resource(
                    "shaders/core/aura/" + prefix + "_composite.fsh");
            String guiComposite = resource(
                    "shaders/core/aura/" + prefix + "_gui_composite.fsh");
            String worldCompositeJson = resource(
                    "shaders/core/aura/" + prefix + "_composite.json");
            String guiCompositeJson = resource(
                    "shaders/core/aura/" + prefix + "_gui_composite.json");

            assertTrue(worldComposite.contains(
                    "#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>"));
            assertTrue(guiComposite.contains(
                    "#moj_import <shadowedhearts:shadow_pokemon_aura_composite.glsl>"));
            assertWorldClock(worldComposite, worldCompositeJson);
            assertGuiClock(guiComposite, guiCompositeJson);
            assertTrue(worldComposite.contains("uniform vec2 ScreenSize;"));
            assertFalse(worldComposite.contains("uniform vec2 AuraScreenSize;"));
            assertTrue(guiComposite.contains("uniform vec2 AuraScreenSize;"));
            assertFalse(guiComposite.contains("uniform vec2 ScreenSize;"));
        }
    }

    @Test
    void xdFilamentMaterialBuildsStableLocalUvBranchesLoopsAndHeatHaloChannels()
            throws IOException {
        String filament = resource(
                "shaders/include/shadow_pokemon_aura_xd_filament.glsl");
        String world = resource(
                "shaders/core/aura/"
                        + "shadow_pokemon_aura_xd_faithful_filament_density.fsh");
        String gui = resource(
                "shaders/core/aura/"
                        + "shadow_pokemon_aura_xd_faithful_gui_filament_density.fsh");
        String worldJson = resource(
                "shaders/core/aura/"
                        + "shadow_pokemon_aura_xd_faithful_filament_density.json");
        String guiJson = resource(
                "shaders/core/aura/"
                        + "shadow_pokemon_aura_xd_faithful_gui_filament_density.json");

        assertTrue(filament.contains("shadowAuraFilamentMainX"));
        assertTrue(filament.contains("shadowAuraFilamentBranchDistance"));
        assertTrue(filament.contains("loopDistance"));
        assertTrue(filament.contains(
                "vec2 point = textureCoordinate * 2.0 - 1.0"));
        assertFalse(filament.contains("worldPos"),
                "filament topology must stay attached in local burst UV");
        assertTrue(filament.contains("particleColor.r"));
        assertTrue(filament.contains("particleColor.g"));
        assertTrue(filament.contains("particleColor.b"));
        assertTrue(filament.contains("particleColor.a"));
        assertTrue(compact(filament).contains(
                "smoothstep(0.010,0.028,distanceToFilament)"));
        assertTrue(compact(filament).contains(
                "smoothstep(0.020,0.105,distanceToFilament)"));
        assertTrue(compact(filament).contains(
                "returnvec4(0.0,core*1.18,halo*0.78,halo*0.34)"),
                "filament must leave broad R empty and write heat G, halo B, coverage A");

        assertTrue(world.contains(
                "#moj_import <shadowedhearts:shadow_pokemon_aura_xd_filament.glsl>"));
        assertTrue(gui.contains(
                "#moj_import <shadowedhearts:shadow_pokemon_aura_xd_filament.glsl>"));
        assertTrue(world.contains("uniform float FilamentSeed;"));
        assertTrue(world.contains("uniform float FilamentPhase;"));
        assertTrue(gui.contains("uniform float FilamentSeed;"));
        assertTrue(gui.contains("uniform float FilamentPhase;"));
        assertWorldClock(world, worldJson);
        assertGuiClock(gui, guiJson);
        assertTrue(worldJson.contains("\"name\": \"FilamentSeed\""));
        assertTrue(worldJson.contains("\"name\": \"FilamentPhase\""));
        assertTrue(guiJson.contains("\"name\": \"FilamentSeed\""));
        assertTrue(guiJson.contains("\"name\": \"FilamentPhase\""));
    }

    @Test
    void nonSignatureCompositesApplyTheirOwnPaletteAfterSharedDensityResponse()
            throws IOException {
        assertStyleCompositeWrappers(
                "colosseum", "shadowPokemonAuraColosseumStyle");
        assertStyleCompositeWrappers(
                "xd_faithful", "shadowPokemonAuraXdFaithfulStyle");

        String styleMath = resource(
                "shaders/include/shadow_pokemon_aura_style_composite.glsl");
        assertTrue(styleMath.contains(
                "vec4 shadowPokemonAuraColosseumStyle("));
        assertTrue(styleMath.contains(
                "vec4 shadowPokemonAuraXdFaithfulStyle("));
        assertTrue(compact(styleMath).contains(
                "floatalpha=min(signatureColor.a*0.94,0.74)"));
        assertTrue(compact(styleMath).contains(
                "floatalpha=min(signatureColor.a*0.78+detailLift,0.74)"));
        assertFalse(styleMath.contains("dFdx"));
        assertFalse(styleMath.contains("dFdy"));
    }

    private static void assertStylePrograms(String fields,
                                            String registration,
                                            String fieldStyle,
                                            String resourceStyle) {
        assertProgram(fields, registration,
                "SHADOW_POKEMON_AURA_" + fieldStyle + "_DENSITY",
                "shadow_pokemon_aura_" + resourceStyle + "_density",
                "DefaultVertexFormat.PARTICLE");
        assertProgram(fields, registration,
                "SHADOW_POKEMON_AURA_" + fieldStyle + "_GUI_DENSITY",
                "shadow_pokemon_aura_" + resourceStyle + "_gui_density",
                "DefaultVertexFormat.PARTICLE");
        assertProgram(fields, registration,
                "SHADOW_POKEMON_AURA_" + fieldStyle + "_COMPOSITE",
                "shadow_pokemon_aura_" + resourceStyle + "_composite",
                "DefaultVertexFormat.POSITION_TEX");
        assertProgram(fields, registration,
                "SHADOW_POKEMON_AURA_" + fieldStyle + "_GUI_COMPOSITE",
                "shadow_pokemon_aura_" + resourceStyle + "_gui_composite",
                "DefaultVertexFormat.POSITION_TEX");
        assertProgram(fields, registration,
                "SHADOW_POKEMON_AURA_" + fieldStyle + "_PIXEL_COMPOSITE",
                "shadow_pokemon_aura_" + resourceStyle + "_pixel_composite",
                "DefaultVertexFormat.POSITION_TEX");
        assertProgram(fields, registration,
                "SHADOW_POKEMON_AURA_" + fieldStyle + "_GUI_PIXEL_COMPOSITE",
                "shadow_pokemon_aura_" + resourceStyle
                        + "_gui_pixel_composite",
                "DefaultVertexFormat.POSITION_TEX");
    }

    private static void assertStyleCompositeWrappers(
            String style,
            String function) throws IOException {
        for (String suffix : new String[]{
                "composite",
                "gui_composite",
                "pixel_composite",
                "gui_pixel_composite"}) {
            String shader = resource(
                    "shaders/core/aura/shadow_pokemon_aura_"
                            + style + "_" + suffix + ".fsh");
            String material = resource(
                    "shaders/core/aura/shadow_pokemon_aura_"
                            + style + "_" + suffix + ".json");
            assertTrue(shader.contains(
                    "#moj_import <shadowedhearts:"
                            + "shadow_pokemon_aura_composite.glsl>"));
            assertTrue(shader.contains(
                    "#moj_import <shadowedhearts:"
                            + "shadow_pokemon_aura_style_composite.glsl>"));
            assertTrue(shader.contains(function + "("));
            if (suffix.startsWith("gui_")) {
                assertGuiClock(shader, material);
                assertTrue(shader.contains("uniform vec2 AuraScreenSize;"));
                assertFalse(shader.contains("uniform vec2 ScreenSize;"));
            } else {
                assertWorldClock(shader, material);
                assertTrue(shader.contains("uniform vec2 ScreenSize;"));
                assertFalse(shader.contains("uniform vec2 AuraScreenSize;"));
            }
        }
    }

    private static void assertProgram(String fields,
                                      String registration,
                                      String field,
                                      String id,
                                      String vertexFormat) {
        assertTrue(fields.contains("ShaderInstance " + field));
        int idIndex = registration.indexOf(id);
        assertTrue(idIndex >= 0, "missing shader registration " + id);
        String localRegistration = registration.substring(
                idIndex,
                Math.min(registration.length(), idIndex + 500));
        assertTrue(localRegistration.contains(vertexFormat),
                id + " must use " + vertexFormat);
        assertTrue(localRegistration.contains("ModShaders." + field),
                id + " must assign " + field);
    }

    private static void assertWorldClock(String shader, String material) {
        assertTrue(shader.contains("uniform float GameTime;"));
        assertFalse(shader.contains("uniform float AuraTime;"));
        assertTrue(material.contains("\"name\": \"GameTime\""));
        assertFalse(material.contains("\"name\": \"AuraTime\""));
    }

    private static void assertGuiClock(String shader, String material) {
        assertTrue(shader.contains("uniform float AuraTime;"));
        assertFalse(shader.contains("uniform float GameTime;"));
        assertTrue(material.contains("\"name\": \"AuraTime\""));
        assertFalse(material.contains("\"name\": \"GameTime\""));
    }

    private static String source(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + suffix),
                StandardCharsets.UTF_8);
    }

    private static String platformSource(String module, String suffix)
            throws IOException {
        return Files.readString(
                Path.of("..", module, "src/main/java/"
                        + "com/jayemceekay/shadowedhearts/" + suffix),
                StandardCharsets.UTF_8);
    }

    private static String resource(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/resources/assets/shadowedhearts/" + suffix),
                StandardCharsets.UTF_8);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
