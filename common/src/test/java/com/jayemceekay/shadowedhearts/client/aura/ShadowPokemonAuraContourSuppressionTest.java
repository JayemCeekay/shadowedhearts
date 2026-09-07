package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowPokemonAuraContourSuppressionTest {

    @Test
    void broadOnlyCompositeOpacityNeverFallsAsOverlapIncreases() {
        float previous = broadOnlyAlpha(0.0f);
        for (int sample = 1; sample <= 2_000; sample++) {
            float broad = sample / 1_000.0f;
            float alpha = broadOnlyAlpha(broad);
            assertTrue(alpha + 0.000001f >= previous,
                    "broad overlap created an opacity annulus at " + broad);
            previous = alpha;
        }
        assertTrue(broadOnlyAlpha(1.12f) >= broadOnlyAlpha(0.54f));
    }

    @Test
    void edgeLightingIsReservedForDetailDominantPuffs() {
        assertEquals(0.0f, detailGate(1.00f, 0.05f, 0.00f), 0.0001f);
        assertEquals(0.0f, detailGate(0.72f, 0.25f, 0.06f), 0.0001f);
        assertTrue(detailGate(0.10f, 1.00f, 0.18f) > 0.99f);
        assertTrue(detailGate(0.00f, 0.25f, 1.00f) > 0.99f);
    }

    @Test
    void visibleBroadAndCoreCohortsHoldOneSizeUntilTheyHaveFaded()
            throws IOException {
        assertEquals(0.86f, broadSize(0.22f), 0.0001f);
        assertEquals(0.86f, broadSize(0.60f), 0.0001f);
        assertEquals(0.86f, broadSize(0.84f), 0.0001f);
        assertEquals(0.99f, coreSize(0.18f), 0.0001f);
        assertEquals(0.99f, coreSize(0.64f), 0.0001f);
        assertEquals(0.99f, coreSize(0.84f), 0.0001f);
        assertEquals(0.0f, broadAlpha(0.0f), 0.0001f);
        assertEquals(0.0f, broadAlpha(1.0f), 0.0001f);
        assertEquals(0.0f, coreAlpha(0.0f), 0.0001f);
        assertEquals(0.0f, coreAlpha(1.0f), 0.0001f);
        assertTrue(broadAlpha(0.84f) < 0.40f);
        assertTrue(coreAlpha(0.84f) < 0.40f);

        String source = read("src/main/java/com/jayemceekay/shadowedhearts/"
                + "client/aura/ShadowPokemonAuraSystem.java");
        assertTrue(source.contains("Mth.lerp(grow, 0.28f, 0.86f)"));
        assertTrue(source.contains("Mth.lerp(grow, 0.24f, 0.99f)"));
        assertTrue(source.contains("smoothstep(0.60f, 0.98f, t)"));
        assertTrue(source.contains("smoothstep(0.64f, 0.98f, t)"));
    }

    @Test
    void sharedMaterialAndParticleMaskDoNotReintroduceBroadContours()
            throws IOException {
        String composite = read("src/main/resources/assets/shadowedhearts/"
                + "shaders/include/shadow_pokemon_aura_composite.glsl");
        String density = read("src/main/resources/assets/shadowedhearts/"
                + "shaders/include/shadow_pokemon_aura_density.glsl");
        String textureMetadata = read("src/main/resources/assets/shadowedhearts/"
                + "textures/particle/shadow_pokemon_aura.png.mcmeta");
        String system = read("src/main/java/com/jayemceekay/shadowedhearts/"
                + "client/aura/ShadowPokemonAuraSystem.java");
        String gui = read("src/main/java/com/jayemceekay/shadowedhearts/"
                + "client/aura/ShadowPokemonAuraGuiRenderer.java");
        String penumbraTrail = read("src/main/java/com/jayemceekay/shadowedhearts/"
                + "client/particle/PenumbraTrailSystem.java");
        String snagTrail = read("src/main/java/com/jayemceekay/shadowedhearts/"
                + "client/ball/SnagTrailDensitySystem.java");

        assertFalse(composite.contains("interiorMask"));
        assertFalse(composite.contains("edgeColor"));
        assertFalse(composite.contains("float rim = 1.0 -"));
        assertTrue(composite.contains("float detailShare = clamp("));
        assertTrue(composite.contains("float detailRim = rimStrength"));
        assertTrue(composite.contains("vec3 color = broadColor;"));
        assertTrue(
                composite.indexOf("float dx = dFdx(combinedDensity);")
                        < composite.indexOf("if (broad < 0.001"),
                "screen derivatives must be evaluated before non-uniform culls");
        assertTrue(density.contains("float maskGate = smoothstep(0.01, 0.10, mask);"));
        assertTrue(density.contains("* maskGate;"));
        assertTrue(textureMetadata.contains("\"blur\": true"));
        assertTrue(textureMetadata.contains("\"clamp\": true"));
        assertTrue(system.contains("textures/particle/shadow_pokemon_aura.png"));
        assertTrue(gui.contains("textures/particle/shadow_pokemon_aura.png"));
        assertTrue(penumbraTrail.contains("textures/particle/penumbra_trail.png"));
        assertTrue(snagTrail.contains("textures/particle/penumbra_trail.png"));
        assertFalse(penumbraTrail.contains("shadow_pokemon_aura.png"));
        assertFalse(snagTrail.contains("shadow_pokemon_aura.png"));
        assertTrue(system.contains("private static PuffStretch samplePuffStretch("));
        assertTrue(system.contains("Math.min(1.0f + safeSpeed * 3.0f, 1.12f)"));
        assertTrue(system.contains("Math.min(1.0f + safeSpeed * 5.0f, 1.20f)"));
        assertTrue(gui.contains("size, alpha, rotation, majorScale, minorScale"));
        assertTrue(gui.contains("guiSize * majorScale"));
        assertTrue(gui.contains("guiSize * minorScale"));
    }

    private static float broadOnlyAlpha(float broad) {
        float broadFog = (float) Math.pow(smoothstep(0.016f, 0.54f, broad), 0.86f);
        float coverageFog = smoothstep(0.02f, 0.58f, broad * 0.76f);
        float darkenFactor = broadFog * 0.22f;
        return Math.min(
                broadFog * 0.54f
                        + coverageFog * 0.12f
                        + darkenFactor * 0.34f,
                0.74f
        );
    }

    private static float detailGate(float broad, float wisp, float heat) {
        float share = clamp((wisp + heat) / Math.max(broad + wisp + heat, 0.001f));
        return smoothstep(0.32f, 0.68f, share);
    }

    private static float broadSize(float t) {
        float grow = smoothstep(0.0f, 0.22f, t);
        float lateShrink = 1.0f - 0.12f * smoothstep(0.84f, 1.0f, t);
        return lerp(grow, 0.28f, 0.86f) * lateShrink;
    }

    private static float coreSize(float t) {
        float grow = smoothstep(0.0f, 0.18f, t);
        float lateShrink = 1.0f - 0.10f * smoothstep(0.84f, 1.0f, t);
        return lerp(grow, 0.24f, 0.99f) * lateShrink;
    }

    private static float broadAlpha(float t) {
        return smoothstep(0.0f, 0.18f, t)
                * (1.0f - smoothstep(0.60f, 0.98f, t));
    }

    private static float coreAlpha(float t) {
        return smoothstep(0.0f, 0.14f, t)
                * (1.0f - smoothstep(0.64f, 0.98f, t));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
