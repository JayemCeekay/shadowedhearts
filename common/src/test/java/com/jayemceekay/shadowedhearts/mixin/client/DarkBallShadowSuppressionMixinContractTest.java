package com.jayemceekay.shadowedhearts.mixin.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallShadowSuppressionMixinContractTest {

    @Test
    void shadowSuppressionUsesCaptureStartVisibilityAuthority()
            throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "mixin/client/MixinEntityRenderDispatcher.java"),
                StandardCharsets.UTF_8);
        String captureSource = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallCaptureVfx.java"),
                StandardCharsets.UTF_8);
        String mixinConfig = Files.readString(
                Path.of("src/main/resources/shadowedhearts.mixins.json"),
                StandardCharsets.UTF_8);

        assertTrue(mixinConfig.contains(
                "\"client.MixinEntityRenderDispatcher\""));
        assertTrue(source.contains("@Mixin(EntityRenderDispatcher.class)"));
        assertTrue(source.contains(
                "method = \"renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;\""));
        assertTrue(source.contains(
                "entity instanceof PokemonEntity pokemon"));
        assertTrue(source.contains(
                "DarkBallCaptureVfx.shouldHideEntityShadow(pokemon)"));
        assertTrue(source.contains("ci.cancel()"));
        assertTrue(captureSource.contains(
                "public static boolean shouldHideEntityShadow("
                        + "PokemonEntity entity)"));
        assertTrue(captureSource.contains(
                "for (DarkBallCaptureVfx vfx : ACTIVE.values())"));
        assertTrue(captureSource.contains(
                "(vfx.snapshotRequested || vfx.snapshotApplied)"),
                "any active capture for the Pokemon must suppress its vanilla"
                        + " shadow from capture request onward");
    }

    @Test
    void modelSuppressionStillWaitsForCapturedReplacement()
            throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/jayemceekay/shadowedhearts/"
                        + "client/ball/DarkBallCaptureVfx.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains(
                "public static boolean shouldHideOriginalModel("
                        + "PokemonEntity entity)"));
        assertTrue(source.contains(
                "for (DarkBallCaptureVfx vfx : ACTIVE.values())"));
        assertTrue(source.contains(
                "vfx.pokemonId == entity.getId() && vfx.snapshotApplied"),
                "any applied replacement for the Pokemon must keep the live"
                        + " model suppressed");
    }
}
