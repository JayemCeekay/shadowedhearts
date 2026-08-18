package com.jayemceekay.shadowedhearts.client.ball;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rest-mesh topology and orientation gates for shapes more representative than
 * a single convex smoke-test sphere.
 */
class DarkBallSurfaceMeshTopologyAcceptanceTest {
    private static final int SIZE = 24;
    private static final float CAPTURE_LENGTH = 2.0f;
    private static final float RADIUS = 1.0f;

    @Test
    void sphereTrianglesAreWoundTowardIncreasingSignedDistance() {
        SyntheticFields fields = sphereFields(
                1.0f, 0.0f, 0.0f, 0.70f);
        DarkBallSurfaceMesh mesh =
                DarkBallSurfaceMeshExtractor.extract(fields.input());

        assertTrue(mesh.isRenderable(), mesh.summary());
        int[] indices = mesh.indices();
        float[] positions = mesh.positions();
        float[] normals = mesh.normals();
        for (int offset = 0; offset < indices.length; offset += 3) {
            int a = indices[offset];
            int b = indices[offset + 1];
            int c = indices[offset + 2];
            float abx = coordinate(positions, b, 0)
                    - coordinate(positions, a, 0);
            float aby = coordinate(positions, b, 1)
                    - coordinate(positions, a, 1);
            float abz = coordinate(positions, b, 2)
                    - coordinate(positions, a, 2);
            float acx = coordinate(positions, c, 0)
                    - coordinate(positions, a, 0);
            float acy = coordinate(positions, c, 1)
                    - coordinate(positions, a, 1);
            float acz = coordinate(positions, c, 2)
                    - coordinate(positions, a, 2);
            float crossX = aby * acz - abz * acy;
            float crossY = abz * acx - abx * acz;
            float crossZ = abx * acy - aby * acx;
            float outwardX = coordinate(normals, a, 0)
                    + coordinate(normals, b, 0)
                    + coordinate(normals, c, 0);
            float outwardY = coordinate(normals, a, 1)
                    + coordinate(normals, b, 1)
                    + coordinate(normals, c, 1);
            float outwardZ = coordinate(normals, a, 2)
                    + coordinate(normals, b, 2)
                    + coordinate(normals, c, 2);
            float alignment = crossX * outwardX
                    + crossY * outwardY
                    + crossZ * outwardZ;
            assertTrue(alignment > 0.0f,
                    "triangle " + offset / 3
                            + " is not outward-wound");
        }
    }

    @Test
    void concaveTorusRemainsOneClosedGenusOneSurface() {
        SyntheticFields fields = torusFields(0.473f, 0.183f);
        DarkBallSurfaceMesh mesh =
                DarkBallSurfaceMeshExtractor.extract(fields.input());

        assertTrue(mesh.isRenderable(), mesh.summary());
        assertEquals(1, mesh.validation().connectedComponents());
        assertEquals(0, mesh.validation().boundaryEdges());
        assertEquals(0, mesh.validation().nonManifoldEdges());
        assertEquals(0, mesh.validation().windingConflicts());

        Set<Integer> usedVertices = new HashSet<>();
        Set<Long> edges = new HashSet<>();
        int[] indices = mesh.indices();
        for (int offset = 0; offset < indices.length; offset += 3) {
            int a = indices[offset];
            int b = indices[offset + 1];
            int c = indices[offset + 2];
            usedVertices.add(a);
            usedVertices.add(b);
            usedVertices.add(c);
            addEdge(edges, a, b);
            addEdge(edges, b, c);
            addEdge(edges, c, a);
        }

        int eulerCharacteristic = usedVertices.size()
                - edges.size()
                + mesh.triangleCount();
        assertEquals(0, eulerCharacteristic,
                "a closed single torus must retain genus one");
    }

    private static SyntheticFields sphereFields(
            float centerX,
            float centerY,
            float centerZ,
            float sphereRadius) {
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
                    int index = index(x, y, z);
                    float localX = localX(x);
                    float localY = localY(y);
                    float localZ = localZ(z);
                    float dx = localX - centerX;
                    float dy = localY - centerY;
                    float dz = localZ - centerZ;
                    float length = (float) Math.sqrt(
                            dx * dx + dy * dy + dz * dz);
                    float inverseLength = length > 0.00001f
                            ? 1.0f / length
                            : 1.0f;
                    signedDistance[index] = length - sphereRadius;
                    normalX[index] = dx * inverseLength;
                    normalY[index] = dy * inverseLength;
                    normalZ[index] = dz * inverseLength;
                    thickness[index] = Math.max(
                            -signedDistance[index], 0.0f) * 2.0f;
                    release[index] = clamp01(
                            1.0f - length / sphereRadius);
                    transport[index] = clamp01(
                            localX / CAPTURE_LENGTH);
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

    private static SyntheticFields torusFields(float majorRadius,
                                                float minorRadius) {
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
                    int index = index(x, y, z);
                    float localX = localX(x);
                    float localY = localY(y);
                    float localZ = localZ(z);
                    // Deliberately offset the fixture from the lattice so the
                    // test exercises concavity rather than the special case of
                    // an isosurface passing exactly through a grid node.
                    float axial = localX - 0.973f;
                    float centeredY = localY - 0.019f;
                    float centeredZ = localZ + 0.013f;
                    float radialLength = (float) Math.sqrt(
                            centeredY * centeredY
                                    + centeredZ * centeredZ);
                    float radialOffset = radialLength - majorRadius;
                    float tubeLength = (float) Math.sqrt(
                            axial * axial
                                    + radialOffset * radialOffset);
                    float analyticDistance = tubeLength - minorRadius;
                    // Match the captured voxelizer's boundary behavior: its
                    // class-distance SDF gives material and exterior samples a
                    // nonzero half-voxel sign instead of placing exact zeros on
                    // lattice nodes.
                    signedDistance[index] =
                            analyticDistance < 0.0f ? -0.5f : 0.5f;

                    float inverseTube = tubeLength > 0.00001f
                            ? 1.0f / tubeLength
                            : 1.0f;
                    float inverseRadial = radialLength > 0.00001f
                            ? 1.0f / radialLength
                            : 1.0f;
                    normalX[index] = axial * inverseTube;
                    float radialNormal = radialOffset * inverseTube;
                    normalY[index] =
                            radialNormal * centeredY * inverseRadial;
                    normalZ[index] =
                            radialNormal * centeredZ * inverseRadial;
                    thickness[index] = Math.max(
                            -analyticDistance, 0.0f) * 2.0f;
                    release[index] = clamp01(
                            (localY + RADIUS) / (RADIUS * 2.0f));
                    transport[index] = clamp01(
                            localX / CAPTURE_LENGTH);
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

    private static float coordinate(float[] triples,
                                    int vertex,
                                    int component) {
        return triples[vertex * 3 + component];
    }

    private static void addEdge(Set<Long> edges, int a, int b) {
        int minimum = Math.min(a, b);
        int maximum = Math.max(a, b);
        edges.add((Integer.toUnsignedLong(minimum) << 32)
                | Integer.toUnsignedLong(maximum));
    }

    private static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    private static float localX(int x) {
        return (x + 0.5f) / SIZE * CAPTURE_LENGTH;
    }

    private static float localY(int y) {
        return ((y + 0.5f) / SIZE * 2.0f - 1.0f) * RADIUS;
    }

    private static float localZ(int z) {
        return ((z + 0.5f) / SIZE * 2.0f - 1.0f) * RADIUS;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
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
                    SIZE,
                    SIZE,
                    SIZE,
                    CAPTURE_LENGTH,
                    RADIUS,
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
