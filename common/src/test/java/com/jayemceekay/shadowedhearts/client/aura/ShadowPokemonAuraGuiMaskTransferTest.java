package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowPokemonAuraGuiMaskTransferTest {

    private static final float COVERAGE_WEIGHT = 0.803f;
    private static final float CONSERVATIVE_BROAD_WEIGHT = 0.48f * 0.56f;

    @Test
    void softenedGuiFalloffKeepsAUsefulFractionOfTheActualSplatTexture()
            throws IOException {
        BufferedImage texture = ImageIO.read(Path.of(
                "src/main/resources/assets/shadowedhearts/textures/particle/"
                        + "penumbra_trail_16.png").toFile());
        assertNotNull(texture);
        assertEquals(16, texture.getWidth());
        assertEquals(16, texture.getHeight());
        assertEquals(1.8f, ShadowPokemonAuraGuiRenderer.guiMaskFalloff(), 0.0001f);

        float minimumCoverageAlpha = ShadowPokemonAuraGuiRenderer.coverageAlpha(
                1.0f,
                0.0f
        );
        float maximumCoverageAlpha = ShadowPokemonAuraGuiRenderer.coverageAlpha(
                1.0f,
                1.0f
        );
        assertEquals(0.279f, minimumCoverageAlpha, 0.0001f);
        assertEquals(0.341f, maximumCoverageAlpha, 0.0001f);

        Transfer minimumAlpha = transfer(texture, minimumCoverageAlpha);
        Transfer maximumAlpha = transfer(texture, maximumCoverageAlpha);

        assertTrue(minimumAlpha.survivingTexels >= 140);
        assertTrue(minimumAlpha.coverageAt002 >= 140);
        assertTrue(minimumAlpha.coverageAt003 >= 70);
        assertTrue(minimumAlpha.broadAt0016 >= 26);

        assertTrue(maximumAlpha.coverageAt004 >= 55);
        assertTrue(maximumAlpha.coverageAt005 >= 35);
        assertTrue(maximumAlpha.broadAt0016 >= 36);
    }

    private static Transfer transfer(BufferedImage texture, float alpha) {
        int survivingTexels = 0;
        int coverageAt002 = 0;
        int coverageAt003 = 0;
        int coverageAt004 = 0;
        int coverageAt005 = 0;
        int broadAt0016 = 0;

        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                float mask = ((texture.getRGB(x, y) >>> 24) & 0xFF) / 255.0f;
                if (mask < 0.01f) {
                    continue;
                }
                survivingTexels++;
                float inverseMask = 1.0f - mask;
                float falloff = (float) Math.exp(
                        -ShadowPokemonAuraGuiRenderer.guiMaskFalloff()
                                * inverseMask * inverseMask);
                float coverageBase = falloff * alpha;
                float coverage = coverageBase * COVERAGE_WEIGHT;
                float conservativeBroad = coverageBase
                        * CONSERVATIVE_BROAD_WEIGHT;

                if (coverage >= 0.02f) coverageAt002++;
                if (coverage >= 0.03f) coverageAt003++;
                if (coverage >= 0.04f) coverageAt004++;
                if (coverage >= 0.05f) coverageAt005++;
                if (conservativeBroad >= 0.016f) broadAt0016++;
            }
        }
        return new Transfer(
                survivingTexels,
                coverageAt002,
                coverageAt003,
                coverageAt004,
                coverageAt005,
                broadAt0016
        );
    }

    private record Transfer(int survivingTexels,
                            int coverageAt002,
                            int coverageAt003,
                            int coverageAt004,
                            int coverageAt005,
                            int broadAt0016) {}
}
