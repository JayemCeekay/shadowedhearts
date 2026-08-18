package com.jayemceekay.shadowedhearts.client.aura;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShadowPokemonAuraGuiTuningTest {

    @Test
    void keepsEnoughImmediatePuffsForAReadableGuiEnvelope() {
        assertEquals(84, ShadowPokemonAuraGuiRenderer.renderedPuffCount(42));
        assertEquals(192, ShadowPokemonAuraGuiRenderer.renderedPuffCount(96));
    }

    @Test
    void detailSamplingSpansTheWholeOrderedCoverageGrid() {
        assertEquals(0, ShadowPokemonAuraGuiRenderer.distributedIndex(0, 48, 96));
        assertEquals(48, ShadowPokemonAuraGuiRenderer.distributedIndex(24, 48, 96));
        assertEquals(94, ShadowPokemonAuraGuiRenderer.distributedIndex(47, 48, 96));
    }

    @Test
    void sizesCoverageAndDetailFromTheVisibleModelExtent() {
        assertEquals(13.5f, ShadowPokemonAuraGuiRenderer.basePuffSize(150.0f), 0.0001f);
        assertEquals(3.25f, ShadowPokemonAuraGuiRenderer.basePuffSize(10.0f), 0.0001f);
        assertEquals(21.0f, ShadowPokemonAuraGuiRenderer.basePuffSize(400.0f), 0.0001f);

        assertEquals(18.0f, ShadowPokemonAuraGuiRenderer.coveragePuffSize(150.0f), 0.0001f);
        assertEquals(4.5f, ShadowPokemonAuraGuiRenderer.coveragePuffSize(10.0f), 0.0001f);
        assertEquals(28.0f, ShadowPokemonAuraGuiRenderer.coveragePuffSize(400.0f), 0.0001f);
    }

    @Test
    void coverageOpacityAndOutwardPushStayWithinTheTunedEnvelope() {
        assertEquals(0.279f, ShadowPokemonAuraGuiRenderer.coverageAlpha(1.0f, 0.0f), 0.0001f);
        assertEquals(0.341f, ShadowPokemonAuraGuiRenderer.coverageAlpha(1.0f, 1.0f), 0.0001f);
        assertEquals(0.1705f, ShadowPokemonAuraGuiRenderer.coverageAlpha(0.5f, 1.0f), 0.0001f);

        assertEquals(3.24f, ShadowPokemonAuraGuiRenderer.coverageOutwardPush(18.0f, 0.0f), 0.0001f);
        assertEquals(4.14f, ShadowPokemonAuraGuiRenderer.coverageOutwardPush(18.0f, 0.5f), 0.0001f);
        assertEquals(5.04f, ShadowPokemonAuraGuiRenderer.coverageOutwardPush(18.0f, 1.0f), 0.0001f);
    }

    @Test
    void animatedDetailFadesOutWhileCoverageRemainsStationary() {
        assertEquals(0.0f, ShadowPokemonAuraGuiRenderer.lifeEnvelope(0.0f), 0.0001f);
        assertEquals(1.0f, ShadowPokemonAuraGuiRenderer.lifeEnvelope(0.5f), 0.0001f);
        assertEquals(0.0f, ShadowPokemonAuraGuiRenderer.lifeEnvelope(1.0f), 0.0001f);
    }

    @Test
    void outwardPlacementMovesAwayFromTheProjectedModelCenter() {
        Vector3f outward = ShadowPokemonAuraGuiRenderer.normalizedScreenOutward(
                3.0f, 4.0f,
                0.0f, 0.0f
        );

        assertEquals(0.6f, outward.x, 0.0001f);
        assertEquals(0.8f, outward.y, 0.0001f);
        assertEquals(0.0f, outward.z, 0.0001f);
    }

    @Test
    void capturedSurfaceDirectionPreservesTrueThreeDimensionalOutwardMotion() {
        Vector3f outward = ShadowPokemonAuraGuiRenderer.normalizedOutward(
                3.0f,
                4.0f,
                12.0f
        );

        assertEquals(3.0f / 13.0f, outward.x, 0.0001f);
        assertEquals(4.0f / 13.0f, outward.y, 0.0001f);
        assertEquals(12.0f / 13.0f, outward.z, 0.0001f);
    }

    @Test
    void gridKeepsOneStableFrontmostSamplePerOccupiedCell() {
        float[] x = {0.0f, 0.0f, 1.0f, 1.0f, Float.NaN};
        float[] y = {0.0f, 0.0f, 1.0f, 1.0f, 0.5f};
        float[] depth = {0.80f, 0.20f, 0.50f, 0.50f, 0.0f};

        int[] normalDepth = ShadowPokemonAuraGuiRenderer.selectFrontmostCells(
                x, y, depth, 2, 2, false, true);
        int[] reversedDepth = ShadowPokemonAuraGuiRenderer.selectFrontmostCells(
                x, y, depth, 2, 2, true, true);

        assertEquals(1, normalDepth[0]);
        assertEquals(2, normalDepth[3], "equal-depth ties keep the first sample");
        assertEquals(0, reversedDepth[0]);
        assertEquals(2, reversedDepth[3]);
        assertEquals(2, Arrays.stream(normalDepth).filter(index -> index >= 0).count());
    }

    @Test
    void camerawardDepthStepHonorsNormalAndReversedDepthFunctions() {
        assertEquals(0.40f, ShadowPokemonAuraGuiRenderer.shiftedWindowDepth(
                0.50f, 0.0f, 1.0f, false, true, 0.10f), 0.0001f);
        assertEquals(0.60f, ShadowPokemonAuraGuiRenderer.shiftedWindowDepth(
                0.50f, 0.0f, 1.0f, true, true, 0.10f), 0.0001f);
        assertEquals(0.60f, ShadowPokemonAuraGuiRenderer.shiftedWindowDepth(
                0.50f, 0.0f, 1.0f, false, false, 0.10f), 0.0001f);
        assertEquals(0.40f, ShadowPokemonAuraGuiRenderer.shiftedWindowDepth(
                0.50f, 0.0f, 1.0f, true, false, 0.10f), 0.0001f);
        assertEquals(0.0f, ShadowPokemonAuraGuiRenderer.shiftedWindowDepth(
                0.02f, 1.0f, 0.0f, false, true, 0.10f), 0.0001f);
        assertEquals(1.0f, ShadowPokemonAuraGuiRenderer.shiftedWindowDepth(
                0.98f, 1.0f, 0.0f, true, true, 0.10f), 0.0001f);
    }
}
