package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.CapturedTriangle;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallTextureVoxelizerTest {
    private static final int VOXEL_COUNT = DarkBallVolumeGrid.X_SLICES
            * DarkBallVolumeGrid.SLICE_SIZE
            * DarkBallVolumeGrid.SLICE_SIZE;

    @Test
    void parallelOpaqueSurfacesLeaveOneCellGap() {
        float[] coverage = new float[VOXEL_COUNT];
        boolean[] shell = new boolean[VOXEL_COUNT];
        CapturedTriangle source = capturedTriangle(
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                255, 255, 255);
        DarkBallTextureAlphaClipper.AlphaField opaque =
                new DarkBallTextureAlphaClipper.AlphaField(
                        1, 1, new float[]{1.0f});

        rasterizePlane(30.0f, coverage, shell, source, opaque);
        rasterizePlane(32.0f, coverage, shell, source, opaque);

        assertTrue(shell[index(30, 20, 20)]);
        assertTrue(shell[index(32, 20, 20)]);
        assertFalse(shell[index(31, 20, 20)],
                "surface coverage must not expand across the one-cell gap");
        assertEquals(0.0f, coverage[index(31, 20, 20)], 0.0f);
    }

    @Test
    void halfCellPhasedParallelSurfacesLeaveOneCellGap() {
        float[] coverage = new float[VOXEL_COUNT];
        boolean[] shell = new boolean[VOXEL_COUNT];
        CapturedTriangle source = capturedTriangle(
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                255, 255, 255);
        DarkBallTextureAlphaClipper.AlphaField opaque =
                new DarkBallTextureAlphaClipper.AlphaField(
                        1, 1, new float[]{1.0f});

        rasterizePlane(30.5f, coverage, shell, source, opaque);
        rasterizePlane(32.5f, coverage, shell, source, opaque);

        assertTrue(shell[index(30, 20, 20)],
                "the lower voxel owns a surface exactly on its upper face");
        assertTrue(shell[index(32, 20, 20)]);
        assertFalse(shell[index(31, 20, 20)],
                "half-cell surface phasing must not merge the intervening gap");
        assertEquals(0.0f, coverage[index(31, 20, 20)], 0.0f);
        assertFalse(shell[index(33, 20, 20)],
                "half-open ownership must not duplicate a face into both cells");
    }

    @Test
    void transparentTexelStripeStaysOpenInVisibleShell() {
        float[] coverage = new float[VOXEL_COUNT];
        boolean[] shell = new boolean[VOXEL_COUNT];
        CapturedTriangle source = capturedTriangle(
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                255, 255, 255);
        DarkBallTextureAlphaClipper.AlphaField striped =
                new DarkBallTextureAlphaClipper.AlphaField(
                        4, 1, new float[]{1.0f, 0.0f, 1.0f, 1.0f});

        rasterizeLargePlane(30.0f, coverage, shell, source, striped);

        assertTrue(shell[index(30, 16, 12)],
                "opaque texels before the stripe should remain visible");
        assertFalse(shell[index(30, 26, 12)],
                "the interior of the transparent stripe must stay open");
        assertEquals(0.0f, coverage[index(30, 26, 12)], 0.0f);
        assertTrue(shell[index(30, 38, 12)],
                "opaque texels after the stripe should remain visible");
    }

    @Test
    void isolatedOpaqueTexelEmitsCoverage() {
        float[] alpha = new float[4 * 4];
        alpha[1 * 4 + 1] = 1.0f;
        DarkBallTextureAlphaClipper.AlphaField isolated =
                new DarkBallTextureAlphaClipper.AlphaField(4, 4, alpha);
        CapturedTriangle source = capturedTriangle(
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                255, 255, 255);
        float[] coverage = new float[VOXEL_COUNT];
        boolean[] shell = new boolean[VOXEL_COUNT];

        rasterizeLargePlane(30.0f, coverage, shell, source, isolated);

        assertTrue(shell[index(30, 26, 26)],
                "an opaque texel that projects above voxel scale must survive clipping");
        assertTrue(coverage[index(30, 26, 26)] > 0.0f);
        assertFalse(shell[index(30, 14, 14)],
                "neighboring transparent texels must remain absent");
    }

    @Test
    void transparentTextureAndZeroVertexAlphaEmitNothing() {
        Vector3f a = new Vector3f(30.0f, 8.0f, 8.0f);
        Vector3f b = new Vector3f(30.0f, 56.0f, 8.0f);
        Vector3f c = new Vector3f(30.0f, 8.0f, 56.0f);
        DarkBallTextureAlphaClipper.AlphaField transparent =
                new DarkBallTextureAlphaClipper.AlphaField(
                        1, 1, new float[]{0.0f});
        DarkBallTextureAlphaClipper.AlphaField opaque =
                new DarkBallTextureAlphaClipper.AlphaField(
                        1, 1, new float[]{1.0f});

        float[] transparentCoverage = new float[VOXEL_COUNT];
        boolean[] transparentShell = new boolean[VOXEL_COUNT];
        DarkBallVolumeVoxelizer.rasterizeTexturedTriangle(
                transparentCoverage, transparentShell, a, b, c,
                capturedTriangle(
                        0.0f, 0.0f,
                        1.0f, 0.0f,
                        0.0f, 1.0f,
                        255, 255, 255),
                transparent);

        float[] vertexCoverage = new float[VOXEL_COUNT];
        boolean[] vertexShell = new boolean[VOXEL_COUNT];
        DarkBallVolumeVoxelizer.rasterizeTexturedTriangle(
                vertexCoverage, vertexShell, a, b, c,
                capturedTriangle(
                        0.0f, 0.0f,
                        1.0f, 0.0f,
                        0.0f, 1.0f,
                        0, 0, 0),
                opaque);

        assertFalse(hasAny(transparentShell));
        assertFalse(hasAny(vertexShell));
        assertEquals(0.0f, maximum(transparentCoverage), 0.0f);
        assertEquals(0.0f, maximum(vertexCoverage), 0.0f);
    }

    @Test
    void degenerateUvKeepsOrDiscardsDeterministically() {
        DarkBallTextureAlphaClipper.AlphaField texture =
                new DarkBallTextureAlphaClipper.AlphaField(
                        2, 2,
                        new float[]{
                                0.0f, 0.0f,
                                0.0f, 1.0f
                        });
        CapturedTriangle visible = capturedTriangle(
                0.75f, 0.75f,
                0.75f, 0.75f,
                0.75f, 0.75f,
                255, 255, 255);
        CapturedTriangle hidden = capturedTriangle(
                0.25f, 0.25f,
                0.25f, 0.25f,
                0.25f, 0.25f,
                255, 255, 255);

        List<DarkBallTextureAlphaClipper.ParamTriangle> first =
                clippedTriangles(visible, texture);
        List<DarkBallTextureAlphaClipper.ParamTriangle> second =
                clippedTriangles(visible, texture);
        List<DarkBallTextureAlphaClipper.ParamTriangle> discarded =
                clippedTriangles(hidden, texture);

        assertEquals(1, first.size());
        assertEquals(first, second,
                "degenerate UV clipping must remain deterministic");
        assertTrue(discarded.isEmpty());
    }

    @Test
    void signedDistanceSeedsBoundaryAtHalfCellSpacing() {
        boolean[] material = new boolean[VOXEL_COUNT];
        material[index(48, 32, 32)] = true;

        float[] sdf = DarkBallVolumeVoxelizer.computeSignedDistanceField(
                material, 2.0f, 1.0f);

        assertEquals(-0.5f, sdf[index(48, 32, 32)], 0.000001f,
                "the solid cell center is half a lateral cell inside the boundary");
        assertEquals(1.0f, sdf[index(49, 32, 32)], 0.000001f,
                "the axial neighbor is half an axial cell outside the boundary");
        assertEquals(0.5f, sdf[index(48, 33, 32)], 0.000001f,
                "the lateral neighbor is half a lateral cell outside the boundary");
    }

    private static void rasterizePlane(
            float x,
            float[] coverage,
            boolean[] shell,
            CapturedTriangle source,
            DarkBallTextureAlphaClipper.AlphaField texture) {
        DarkBallVolumeVoxelizer.rasterizeTexturedTriangle(
                coverage, shell,
                new Vector3f(x, 8.0f, 8.0f),
                new Vector3f(x, 48.0f, 8.0f),
                new Vector3f(x, 8.0f, 48.0f),
                source, texture);
    }

    private static void rasterizeLargePlane(
            float x,
            float[] coverage,
            boolean[] shell,
            CapturedTriangle source,
            DarkBallTextureAlphaClipper.AlphaField texture) {
        DarkBallVolumeVoxelizer.rasterizeTexturedTriangle(
                coverage, shell,
                new Vector3f(x, 8.0f, 8.0f),
                new Vector3f(x, 56.0f, 8.0f),
                new Vector3f(x, 8.0f, 56.0f),
                source, texture);
    }

    private static CapturedTriangle capturedTriangle(
            float au, float av,
            float bu, float bv,
            float cu, float cv,
            int alphaA, int alphaB, int alphaC) {
        return new CapturedTriangle(
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                au, av, bu, bv, cu, cv,
                alphaA, alphaB, alphaC);
    }

    private static List<DarkBallTextureAlphaClipper.ParamTriangle>
    clippedTriangles(
            CapturedTriangle source,
            DarkBallTextureAlphaClipper.AlphaField texture) {
        List<DarkBallTextureAlphaClipper.ParamTriangle> triangles =
                new ArrayList<>();
        DarkBallTextureAlphaClipper.forEachVisibleTriangle(
                source, texture, triangles::add);
        return triangles;
    }

    private static int index(int x, int y, int z) {
        return DarkBallVolumeVoxelizer.voxelIndex(x, y, z);
    }

    private static boolean hasAny(boolean[] values) {
        for (boolean value : values) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    private static float maximum(float[] values) {
        float maximum = 0.0f;
        for (float value : values) {
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }
}
