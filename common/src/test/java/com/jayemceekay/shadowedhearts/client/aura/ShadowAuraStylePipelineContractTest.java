package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowAuraStylePipelineContractTest {

    @Test
    void clientConfigDefinesAValidatedSignatureDefaultAndParsesTheEnum()
            throws IOException {
        String interfaceConfig = source("config/IClientConfig.java");
        String clientConfig = source("config/ClientConfig.java");

        assertTrue(interfaceConfig.contains(
                "default ShadowAuraStyle shadowAuraStyle()"));
        assertTrue(interfaceConfig.contains("return ShadowAuraStyle.DEFAULT"));
        assertTrue(clientConfig.contains(
                "ModConfigSpec.ConfigValue<String> shadowAuraStyle"));
        assertTrue(compact(clientConfig).contains(
                ".define(\"shadowAuraStyle\","
                        + "ShadowAuraStyle.DEFAULT.serializedName(),"
                        + "ShadowAuraStyle::isValidSerializedName)"));
        assertTrue(clientConfig.contains(
                "return ShadowAuraStyle.fromSerializedName("
                        + "DATA.shadowAuraStyle.get())"));
    }

    @Test
    void worldAndPreviewResolveTheSamePerPokemonStyleBeforeEmission()
            throws IOException {
        String system = source(
                "client/aura/ShadowPokemonAuraSystem.java");
        String gui = source(
                "client/aura/ShadowPokemonAuraGuiRenderer.java");

        assertTrue(countOccurrences(
                system, "ShadowAuraStyleResolver.resolve(") >= 3);
        assertTrue(compact(system).contains(
                "ShadowAuraStyleResolver.resolve(entity,configuredAuraStyle())"));
        assertTrue(compact(system).contains(
                "ShadowAuraStyleResolver.resolve(pokemon,configuredAuraStyle())"));
        assertTrue(system.contains(
                "ShadowAuraStyleProfiles.forStyle(state.style)"));
        assertTrue(system.contains(
                "ShadowAuraStyleProfiles.forStyle(safeStyle).render()"));
        assertTrue(system.contains(
                ".serializedName()"),
                "preview identity must include its resolved style");

        assertTrue(gui.contains("ShadowAuraStyle style = owner.style()"));
        assertTrue(gui.contains(
                "ShadowPokemonAuraFBO.guiDensityShader(style)"));
        assertTrue(compact(gui).contains(
                "ShadowPokemonAuraFBO.beginDensityPass(style,true,true)"));
        assertTrue(gui.contains("ShadowPokemonAuraFBO.endDensityPass(style)"));
        assertTrue(gui.contains("ShadowPokemonAuraFBO.blur(style)"));
        assertTrue(compact(gui).contains(
                "ShadowPokemonAuraFBO.composite(style,minimumU,minimumV,"
                        + "maximumU,maximumV,animationTicks)"));

        String fallback = between(
                gui,
                "private static void renderDensitySplats(List<GuiAnchor>",
                "private static void renderCoveragePuffs");
        assertTrue(fallback.contains("renderTuning.broadChannelScale()"));
        assertTrue(fallback.contains("renderTuning.heatChannelScale()"));
        assertTrue(fallback.contains("renderTuning.wispChannelScale()"));
        assertTrue(fallback.contains("renderTuning.coverageScale()"));
        assertTrue(fallback.contains("renderTuning.opacityScale()"));
    }

    @Test
    void puffsCaptureSpawnStyleAndAreNotRetaggedOrPurgedOnDefaultChanges()
            throws IOException {
        String system = source(
                "client/aura/ShadowPokemonAuraSystem.java");
        String puff = between(
                system,
                "private static final class Puff",
                "\n    }\n}");
        String update = between(
                system,
                "private static void updateSourceStyle",
                "private static long previewSeed");

        assertTrue(puff.contains("final ShadowAuraStyle style"));
        assertTrue(compact(puff).contains(
                "this.style=source==null||source.style==null"
                        + "?ShadowAuraStyle.DEFAULT:source.style"));
        assertTrue(update.contains("state.style = safeStyle"));
        assertFalse(update.contains("removeIf"),
                "existing style-tagged puffs must drain under their spawn material");
        assertFalse(update.contains("particles.clear"));
        assertFalse(update.contains("ACTIVE.clear"));
        assertTrue(system.contains("puffStyles.add(puff.style)"));
        assertTrue(system.contains("if (puff.style != safeStyle)"));
    }

    @Test
    void mixedWorldStylesUseSequentialIsolatedDensityAndCompositePasses()
            throws IOException {
        String system = source(
                "client/aura/ShadowPokemonAuraSystem.java");
        String fbo = source(
                "client/aura/ShadowPokemonAuraFBO.java");
        String render = between(
                system,
                "private static void renderDensityPipelineInternal",
                "private static void updateWorldPixelLodsForFrame");
        String mask = between(
                system,
                "public static void renderModelMask",
                "public static boolean isIrisShaderPackActive");

        assertTrue(render.contains(
                "EnumSet<ShadowAuraStyle> puffStyles"));
        assertTrue(render.contains("puffStyles.add(puff.style)"));
        assertTrue(render.contains(
                "EnumSet<ShadowAuraStyle> requestedStyles"));
        assertTrue(render.contains("requestedStyles.addAll(puffStyles)"));
        assertTrue(render.contains("requestedStyles.addAll(maskStyles)"));
        assertTrue(render.contains(
                "for (ShadowAuraStyle style : requestedStyles)"));
        assertTrue(compact(render).contains(
                "ShadowPokemonAuraFBO.beginDensityPass(style,!styleHasMask,true)"));
        assertTrue(compact(render).contains(
                "renderDensitySplats(camera,partialTicks,style)"));
        assertTrue(render.contains(
                "new EnumMap<>(ShadowAuraStyle.class)"));
        assertTrue(compact(render).contains(
                "visiblePixelContributors.put(style,renderDensitySplats("));
        assertTrue(compact(render).contains(
                "updateWorldPixelLodsForFrame("
                        + "visiblePixelContributors,puffStyles)"));
        assertTrue(render.contains("ShadowPokemonAuraFBO.endDensityPass(style)"));
        assertTrue(render.contains("ShadowPokemonAuraFBO.blur(style)"));
        assertTrue(compact(render).contains(
                "ShadowPokemonAuraFBO.composite("
                        + "style,worldPixelLodForStyle(style))"));

        assertTrue(compact(mask).contains(
                "ShadowAuraStyleResolver.resolve(entity,configuredAuraStyle())"));
        assertTrue(mask.contains(
                "MASK_CAPTURED_STYLES.contains(style)"));
        assertTrue(compact(mask).contains(
                "ShadowPokemonAuraFBO.beginDensityPass(style,"
                        + "clearForFirstMask,clearForFirstMask)"));
        assertTrue(mask.contains("MASK_CAPTURED_STYLES.add(style)"));
        assertTrue(mask.contains("ShadowPokemonAuraFBO.endDensityPass(style)"));

        assertTrue(fbo.contains(
                "Map<ShadowAuraStyle, DensityFboPipeline> STYLE_PIPELINES"));
        assertTrue(fbo.contains("new EnumMap<>(ShadowAuraStyle.class)"));
        assertTrue(fbo.contains("STYLE_PIPELINES.computeIfAbsent"));
        assertTrue(compact(fbo).contains(
                "ignored->newDensityFboPipeline(2,0.82f*"
                        + "ShadowAuraStyleProfiles.forStyle(safeStyle)"
                        + ".render().blurRadiusScale())"));
        assertTrue(countOccurrences(fbo, ".pixelSizeScale()") >= 2,
                "world fractional mip and GUI pixel size must both honor style tuning");
        assertTrue(fbo.contains(
                "public static ShaderInstance worldDensityShader("));
        assertTrue(fbo.contains(
                "public static ShaderInstance guiDensityShader("));
        assertTrue(fbo.contains(
                "case COLOSSEUM -> ModShaders."
                        + "SHADOW_POKEMON_AURA_COLOSSEUM_DENSITY"));
        assertTrue(fbo.contains(
                "case XD_FAITHFUL -> ModShaders."
                        + "SHADOW_POKEMON_AURA_XD_FAITHFUL_DENSITY"));
    }

    @Test
    void xdFilamentPuffsBindTheirDedicatedWorldAndGuiMaterials()
            throws IOException {
        String system = source(
                "client/aura/ShadowPokemonAuraSystem.java");
        String gui = source(
                "client/aura/ShadowPokemonAuraGuiRenderer.java");
        String fbo = source(
                "client/aura/ShadowPokemonAuraFBO.java");

        assertTrue(system.contains("PuffType.FILAMENT"));
        assertTrue(system.contains("case FILAMENT"));
        assertTrue(system.contains(
                "puff.type == PuffType.FILAMENT"));
        assertTrue(system.contains("burst.targetVisibleMin()"));
        assertTrue(system.contains("burst.targetVisibleMax()"));
        assertTrue(system.contains("filament.segmentCountMin()"));
        assertTrue(system.contains("filament.segmentCountMax()"));
        assertTrue(system.contains("filament.branchCountMin()"));
        assertTrue(system.contains("filament.branchCountMax()"));
        assertTrue(system.contains("burst.sizeMinModelScale()"));
        assertTrue(system.contains("burst.sizeMaxModelScale()"));
        assertTrue(system.contains("burst.lifetimeMinTicks()"));
        assertTrue(system.contains("burst.lifetimeMaxTicks()"));
        assertTrue(system.contains("filament.coreWidthModelScale()"));
        assertTrue(system.contains("filament.haloWidthScale()"));
        assertTrue(system.contains(
                "XD_FILAMENT_CORE_UV_HALF_WIDTH = 0.028f"));
        assertTrue(system.contains(
                "XD_FILAMENT_HALO_UV_HALF_WIDTH = 0.105f"));
        assertTrue(compact(system).contains(
                "coreBillboardHalfSize=coreWorldHalfWidth/"
                        + "XD_FILAMENT_CORE_UV_HALF_WIDTH"));
        assertTrue(compact(system).contains(
                "haloBillboardHalfSize=haloWorldHalfWidth/"
                        + "XD_FILAMENT_HALO_UV_HALF_WIDTH"));
        assertTrue(compact(system).contains(
                "caseFILAMENT->ShadowAuraStyleProfiles.IDENTITY_PUFF"),
                "filament dimensions and lifetime must not inherit wisp tuning");
        assertTrue(system.contains("filament.lifetimeScale()"));
        assertTrue(system.contains(".filament().driftFollow()"));
        assertEquals(1,
                countOccurrences(system, "filament.intensityScale()"),
                "filament intensity must be applied exactly once");
        assertTrue(fbo.contains(
                "public static ShaderInstance filamentDensityShader("
                        + "boolean gui)"));
        assertTrue(fbo.contains(
                "SHADOW_POKEMON_AURA_XD_FAITHFUL_FILAMENT_DENSITY"));
        assertTrue(fbo.contains(
                "SHADOW_POKEMON_AURA_XD_FAITHFUL_GUI_FILAMENT_DENSITY"));
        assertTrue(system.contains(
                "ShadowPokemonAuraFBO.filamentDensityShader(false)"),
                "world filament mesh must bind the dedicated local-UV material");
        assertTrue(gui.contains(
                "ShadowPokemonAuraFBO.filamentDensityShader(true)"),
                "preview filament mesh must bind the GUI-owned material");
        assertTrue(system.contains("\"FilamentSeed\""));
        assertTrue(system.contains("\"FilamentPhase\""));
        assertTrue(compact(system).contains(
                "floatseedOffset=fract(puff.rotation/Mth.TWO_PI)"));
        assertTrue(system.contains("float phaseOffset = puff.lodRoll"));
        assertTrue(compact(gui).contains(
                "renderedBroad=fract(rotation/TAU)"));
        assertTrue(gui.contains("renderedSpark = fract("));
        assertTrue(gui.contains(
                "ShadowPokemonAuraSystem.setupFilamentDensityShader("));
        assertTrue(compact(gui).contains(
                "ShadowPokemonAuraSystem.setupFilamentDensityShader("
                        + "filamentShader,animationTicks,true)"));
    }

    @Test
    void xdBurstSchedulerConsumesSpatialOccupancyAndClusterLocalTiming()
            throws IOException {
        String system = source(
                "client/aura/ShadowPokemonAuraSystem.java");
        String scheduler = between(
                system,
                "private static void emitXdFaithfulAura(",
                "static int selectXdBurstAnchorIndex(");
        String alphaSampling = between(
                system,
                "static float xdBurstEnvelope(",
                "private static float wrapAngle(");

        assertTrue(scheduler.contains("occupiedClusterPositions"));
        assertTrue(scheduler.contains("puff.type == PuffType.BROAD_HAZE"),
                "only one root broad puff should reserve each active cluster region");
        assertTrue(scheduler.contains("selectXdBurstAnchorIndex("));
        assertTrue(scheduler.contains("state.nextXdBurstTick"));
        assertTrue(scheduler.contains("xdBurstSpawnDelay("));
        assertTrue(scheduler.contains("clusterLifetime"));
        assertTrue(countOccurrences(
                        compact(scheduler),
                        "particles,clusterLifetime)") >= 4,
                "root, core, mote, and filament children must share one cluster lifetime");
        assertTrue(compact(alphaSampling).contains(
                "alpha*=xdBurstEnvelope("
                        + "puff.age+partialTicks,puff.lifetime)"),
                "every XD child must use its own cluster-local age and lifetime envelope");
    }

    private static String source(String suffix) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + suffix),
                StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0, "missing start marker " + start);
        assertTrue(endIndex > startIndex, "missing end marker " + end);
        return source.substring(startIndex, endIndex);
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

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
