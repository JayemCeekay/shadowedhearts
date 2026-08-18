package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSurfaceMeshExtractorTest {
    private static final int SIZE = 14;
    private static final float CAPTURE_LENGTH = 2.0f;
    private static final float RADIUS = 1.0f;

    @Test
    void extractsWeldedWatertightSphereWithFiniteVertexAttributes() {
        SyntheticFields fields = sphereFields(1.0f, 0.0f, 0.0f, 0.68f);

        DarkBallSurfaceMesh mesh = DarkBallSurfaceMeshExtractor.extract(
                fields.input());

        assertTrue(mesh.isRenderable(), mesh.summary());
        assertTrue(mesh.vertexCount() > 100);
        assertTrue(mesh.triangleCount() > 200);
        assertEquals(0, mesh.validation().boundaryEdges());
        assertEquals(0, mesh.validation().nonManifoldEdges());
        assertEquals(0, mesh.validation().windingConflicts());
        assertEquals(mesh.vertexCount() * 3, mesh.positions().length);
        assertEquals(mesh.vertexCount() * 3, mesh.normals().length);
        assertEquals(mesh.vertexCount(), mesh.localThickness().length);
        assertEquals(mesh.vertexCount(), mesh.releaseOrder().length);
        assertEquals(mesh.vertexCount(), mesh.transportOrder().length);

        for (float value : mesh.positions()) {
            assertTrue(Float.isFinite(value));
        }
        for (float value : mesh.normals()) {
            assertTrue(Float.isFinite(value));
        }
        for (float value : mesh.localThickness()) {
            assertTrue(Float.isFinite(value));
            assertTrue(value >= 0.0f);
        }
        for (float value : mesh.releaseOrder()) {
            assertTrue(value >= 0.0f && value <= 1.0f);
        }
        for (float value : mesh.transportOrder()) {
            assertTrue(value >= 0.0f && value <= 1.0f);
        }
    }

    @Test
    void positiveGhostBorderClosesMaterialTouchingTheSourceBoundary() {
        // This sphere crosses the local X=0 atlas boundary. Without the ghost
        // layer its extracted surface would have an open boundary loop.
        SyntheticFields fields = sphereFields(0.08f, 0.0f, 0.0f, 0.62f);

        DarkBallSurfaceMesh mesh = DarkBallSurfaceMeshExtractor.extract(
                fields.input());

        assertTrue(mesh.isRenderable(), mesh.summary());
        assertEquals(0, mesh.validation().boundaryEdges());
        assertEquals(0, mesh.validation().nonManifoldEdges());
        assertEquals(0, mesh.validation().windingConflicts());
    }

    @Test
    void nearGridNodeCrossingsRemainWatertight() {
        int nodeX = 10;
        int nodeY = 6;
        int nodeZ = 6;
        float localX = (nodeX + 0.5f) / SIZE * CAPTURE_LENGTH;
        float localY = ((nodeY + 0.5f) / SIZE * 2.0f - 1.0f)
                * RADIUS;
        float localZ = ((nodeZ + 0.5f) / SIZE * 2.0f - 1.0f)
                * RADIUS;
        float dx = localX - 1.0f;
        float sphereRadius = (float) Math.sqrt(
                dx * dx + localY * localY + localZ * localZ)
                + CAPTURE_LENGTH * 0.000002f;
        SyntheticFields fields = sphereFields(
                1.0f, 0.0f, 0.0f, sphereRadius);

        DarkBallSurfaceMesh mesh = DarkBallSurfaceMeshExtractor.extract(
                fields.input());

        assertTrue(mesh.isRenderable(), mesh.summary());
        assertEquals(0, mesh.validation().boundaryEdges());
        assertEquals(0, mesh.validation().nonManifoldEdges());
        assertEquals(0, mesh.validation().windingConflicts());
        assertTrue(mesh.validation().zeroAdjustedSamples() > 0);
        assertEquals(0, mesh.validation().rejectedDegenerateTriangles());
        assertEquals(0, mesh.validation().collapsedPolygons());
        assertEquals(0, mesh.validation().rejectedDuplicateIndexTriangles());
        assertEquals(0, mesh.validation().rejectedLowAreaTriangles());
    }

    @Test
    void emptyFieldProducesNoRenderableGeometryWithoutTopologyErrors() {
        int count = SIZE * SIZE * SIZE;
        float[] positive = new float[count];
        Arrays.fill(positive, 1.0f);
        float[] normalX = new float[count];
        Arrays.fill(normalX, 1.0f);
        float[] zeroes = new float[count];

        DarkBallSurfaceMesh mesh = DarkBallSurfaceMeshExtractor.extract(
                new DarkBallSurfaceMeshExtractor.GridInput(
                        SIZE, SIZE, SIZE,
                        CAPTURE_LENGTH, RADIUS,
                        positive,
                        normalX,
                        zeroes,
                        zeroes,
                        zeroes,
                        zeroes,
                        zeroes));

        assertFalse(mesh.isRenderable());
        assertEquals(0, mesh.vertexCount());
        assertEquals(0, mesh.triangleCount());
        assertTrue(mesh.validation().watertight());
    }

    @Test
    void extractionIsDeterministicForTheSameCapturedField() {
        SyntheticFields fields = sphereFields(1.0f, 0.0f, 0.0f, 0.68f);

        DarkBallSurfaceMesh first = DarkBallSurfaceMeshExtractor.extract(
                fields.input());
        DarkBallSurfaceMesh second = DarkBallSurfaceMeshExtractor.extract(
                fields.input());

        assertArrayEquals(first.positions(), second.positions());
        assertArrayEquals(first.normals(), second.normals());
        assertArrayEquals(first.indices(), second.indices());
        assertEquals(first.validation(), second.validation());
    }

    @Test
    void preservesDisconnectedComponentsAsSeparatelyClosedSurfaces() {
        SyntheticFields fields = twoSphereFields();

        DarkBallSurfaceMesh mesh = DarkBallSurfaceMeshExtractor.extract(
                fields.input());

        assertTrue(mesh.isRenderable(), mesh.summary());
        assertEquals(2, mesh.validation().connectedComponents());
        assertEquals(0, mesh.validation().boundaryEdges());
        assertEquals(0, mesh.validation().nonManifoldEdges());
    }

    @Test
    void nonFiniteThicknessSamplesFallBackToZero() {
        SyntheticFields fields = sphereFields(
                1.0f, 0.0f, 0.0f, 0.68f);
        Arrays.fill(fields.thickness(), Float.NaN);

        DarkBallSurfaceMesh mesh = DarkBallSurfaceMeshExtractor.extract(
                fields.input());

        assertTrue(mesh.isRenderable(), mesh.summary());
        for (float value : mesh.localThickness()) {
            assertEquals(0.0f, value);
        }
    }

    @Test
    void cooperativeCancellationStopsExtraction() {
        SyntheticFields fields = sphereFields(
                1.0f, 0.0f, 0.0f, 0.68f);

        assertThrows(CancellationException.class,
                () -> DarkBallSurfaceMeshExtractor.extract(
                        fields.input(), () -> true));
    }

    private static SyntheticFields sphereFields(
            float centerX, float centerY, float centerZ, float sphereRadius) {
        int count = SIZE * SIZE * SIZE;
        float[] signedDistance = new float[count];
        float[] normalX = new float[count];
        float[] normalY = new float[count];
        float[] normalZ = new float[count];
        float[] thickness = new float[count];
        float[] release = new float[count];
        float[] transport = new float[count];

        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = (y * SIZE + z) * SIZE + x;
                    float localX = (x + 0.5f) / SIZE * CAPTURE_LENGTH;
                    float localY = ((y + 0.5f) / SIZE * 2.0f - 1.0f)
                            * RADIUS;
                    float localZ = ((z + 0.5f) / SIZE * 2.0f - 1.0f)
                            * RADIUS;
                    float dx = localX - centerX;
                    float dy = localY - centerY;
                    float dz = localZ - centerZ;
                    float distance = (float) Math.sqrt(
                            dx * dx + dy * dy + dz * dz);
                    signedDistance[index] = distance - sphereRadius;
                    float inverseLength = distance > 0.00001f
                            ? 1.0f / distance
                            : 1.0f;
                    normalX[index] = dx * inverseLength;
                    normalY[index] = dy * inverseLength;
                    normalZ[index] = dz * inverseLength;
                    thickness[index] = Math.max(
                            sphereRadius - distance, 0.0f) * 2.0f;
                    release[index] = clamp01(
                            1.0f - distance / Math.max(sphereRadius, 0.0001f));
                    transport[index] = clamp01(
                            (localX - (centerX - sphereRadius))
                                    / (sphereRadius * 2.0f));
                }
            }
        }

        return new SyntheticFields(
                signedDistance,
                normalX,
                normalY,
                normalZ,
                thickness,
                release,
                transport);
    }

    private static SyntheticFields twoSphereFields() {
        int count = SIZE * SIZE * SIZE;
        float[] signedDistance = new float[count];
        float[] normalX = new float[count];
        float[] normalY = new float[count];
        float[] normalZ = new float[count];
        float[] thickness = new float[count];
        float[] release = new float[count];
        float[] transport = new float[count];
        float[] centers = {0.55f, 1.45f};
        float sphereRadius = 0.28f;

        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = (y * SIZE + z) * SIZE + x;
                    float localX = (x + 0.5f) / SIZE * CAPTURE_LENGTH;
                    float localY = ((y + 0.5f) / SIZE * 2.0f - 1.0f)
                            * RADIUS;
                    float localZ = ((z + 0.5f) / SIZE * 2.0f - 1.0f)
                            * RADIUS;
                    float bestDistance = Float.POSITIVE_INFINITY;
                    float bestDx = 1.0f;
                    float bestDy = 0.0f;
                    float bestDz = 0.0f;
                    float bestLength = 1.0f;
                    for (float centerX : centers) {
                        float dx = localX - centerX;
                        float dy = localY;
                        float dz = localZ;
                        float length = (float) Math.sqrt(
                                dx * dx + dy * dy + dz * dz);
                        float distance = length - sphereRadius;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestDx = dx;
                            bestDy = dy;
                            bestDz = dz;
                            bestLength = length;
                        }
                    }
                    signedDistance[index] = bestDistance;
                    float inverseLength = bestLength > 0.00001f
                            ? 1.0f / bestLength
                            : 1.0f;
                    normalX[index] = bestDx * inverseLength;
                    normalY[index] = bestDy * inverseLength;
                    normalZ[index] = bestDz * inverseLength;
                    thickness[index] = Math.max(-bestDistance, 0.0f) * 2.0f;
                    release[index] = clamp01(localX / CAPTURE_LENGTH);
                    transport[index] = release[index];
                }
            }
        }
        return new SyntheticFields(
                signedDistance,
                normalX,
                normalY,
                normalZ,
                thickness,
                release,
                transport);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(value, 1.0f));
    }

    private record SyntheticFields(
            float[] signedDistance,
            float[] normalX,
            float[] normalY,
            float[] normalZ,
            float[] thickness,
            float[] release,
            float[] transport
    ) {
        DarkBallSurfaceMeshExtractor.GridInput input() {
            return new DarkBallSurfaceMeshExtractor.GridInput(
                    SIZE, SIZE, SIZE,
                    CAPTURE_LENGTH, RADIUS,
                    signedDistance,
                    normalX,
                    normalY,
                    normalZ,
                    thickness,
                    release,
                    transport);
        }
    }
}
