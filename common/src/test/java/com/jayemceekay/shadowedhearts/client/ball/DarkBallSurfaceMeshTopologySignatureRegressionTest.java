package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minimal deterministic equivalent of the Pokemon 67 topology signature.
 *
 * <p>The scalar field and all intersection positions remain identical between
 * both tests. Only captured normal directions change. This isolates polygon
 * ordering from scalar-field topology: interpolated presentation normals can
 * become a poor projection basis for a tetrahedron's geometric isopolygon.</p>
 */
class DarkBallSurfaceMeshTopologySignatureRegressionTest {
    private static final float INVERSE_SQRT_THREE =
            1.0f / (float) Math.sqrt(3.0f);

    @Test
    void adversarialCapturedNormalsCannotChangeScalarTopology() {
        DarkBallSurfaceMesh adversarial =
                DarkBallSurfaceMeshExtractor.extract(
                        input(true));
        DarkBallSurfaceMesh control =
                DarkBallSurfaceMeshExtractor.extract(
                        input(false));
        DarkBallSurfaceMesh.Validation validation =
                adversarial.validation();

        assertTrue(adversarial.isRenderable(), adversarial.summary());
        assertEquals(26, adversarial.vertexCount(), adversarial.summary());
        assertEquals(48, adversarial.triangleCount(), adversarial.summary());
        assertEquals(1,
                validation.connectedComponents(), adversarial.summary());
        assertEquals(0, validation.boundaryEdges(), adversarial.summary());
        assertEquals(0, validation.nonManifoldEdges(), adversarial.summary());
        assertEquals(0, validation.windingConflicts(), adversarial.summary());
        assertEquals(0,
                validation.rejectedDegenerateTriangles(),
                adversarial.summary());
        assertEquals(0,
                validation.collapsedPolygons(), adversarial.summary());
        assertEquals(0,
                validation.rejectedDuplicateIndexTriangles(),
                adversarial.summary());
        assertEquals(0,
                validation.rejectedLowAreaTriangles(),
                adversarial.summary());
        assertArrayEquals(
                control.positions(), adversarial.positions(), 0.0f,
                "captured normals must not affect intersection positions");
        assertArrayEquals(
                unorientedTriangleKeys(control.indices()),
                unorientedTriangleKeys(adversarial.indices()),
                "captured normals must not affect triangle connectivity");
    }

    @Test
    void sameScalarFieldIsWatertightWithStableProjectionNormals() {
        DarkBallSurfaceMesh mesh = DarkBallSurfaceMeshExtractor.extract(
                input(false));

        assertTrue(mesh.isRenderable(), mesh.summary());
        assertEquals(0, mesh.validation().boundaryEdges(), mesh.summary());
        assertEquals(0, mesh.validation().nonManifoldEdges(), mesh.summary());
        assertEquals(0, mesh.validation().windingConflicts(), mesh.summary());
    }

    private static long[] unorientedTriangleKeys(int[] indices) {
        long[] keys = new long[indices.length / 3];
        for (int triangle = 0; triangle < keys.length; triangle++) {
            int offset = triangle * 3;
            int a = indices[offset];
            int b = indices[offset + 1];
            int c = indices[offset + 2];
            int minimum = Math.min(a, Math.min(b, c));
            int maximum = Math.max(a, Math.max(b, c));
            int middle = a + b + c - minimum - maximum;
            keys[triangle] = ((long) minimum << 42)
                    | ((long) middle << 21)
                    | maximum;
        }
        Arrays.sort(keys);
        return keys;
    }

    private static DarkBallSurfaceMeshExtractor.GridInput input(
            boolean capturedNormalRegression) {
        int size = 2;
        int count = size * size * size;
        // Flattening is (y * zSize + z) * xSize + x. The two negative
        // samples are opposite corners of the x=1 face.
        float[] signedDistance = {
                1.0f, -1.0f,
                1.0f, 1.0f,
                1.0f, 1.0f,
                1.0f, -1.0f
        };
        float[] normalX = new float[count];
        float[] normalY = new float[count];
        float[] normalZ = new float[count];
        Arrays.fill(normalX, 1.0f);
        if (capturedNormalRegression) {
            putUnitDiagonal(normalX, normalY, normalZ,
                    0, 1.0f, -1.0f, 1.0f);
            putUnitDiagonal(normalX, normalY, normalZ,
                    1, -1.0f, 1.0f, 1.0f);
            putUnitDiagonal(normalX, normalY, normalZ,
                    5, -1.0f, 1.0f, -1.0f);
            putUnitDiagonal(normalX, normalY, normalZ,
                    7, -1.0f, -1.0f, 1.0f);
        }
        float[] scalar = new float[count];
        Arrays.fill(scalar, 0.5f);
        return new DarkBallSurfaceMeshExtractor.GridInput(
                size, size, size,
                2.0f, 1.0f,
                signedDistance,
                normalX,
                normalY,
                normalZ,
                scalar.clone(),
                scalar.clone(),
                scalar.clone());
    }

    private static void putUnitDiagonal(
            float[] normalX,
            float[] normalY,
            float[] normalZ,
            int node,
            float x,
            float y,
            float z) {
        normalX[node] = x * INVERSE_SQRT_THREE;
        normalY[node] = y * INVERSE_SQRT_THREE;
        normalZ[node] = z * INVERSE_SQRT_THREE;
    }
}
