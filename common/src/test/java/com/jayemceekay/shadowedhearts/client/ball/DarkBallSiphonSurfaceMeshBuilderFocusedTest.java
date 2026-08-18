package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkBallSiphonSurfaceMeshBuilderFocusedTest {
    private static final float EPSILON = 0.00001f;

    @Test
    void buildsClosedSingleComponentEightSidedTubeWithSharedCaps() {
        Vector3f[] nodes = gentlyCurvedNodes();
        DarkBallSiphonSurfaceMesh mesh =
                DarkBallSiphonSurfaceMesh.build(nodes, 0.18f, 0.11f);

        assertEquals(DarkBallSiphonBoltPath.NODE_COUNT,
                DarkBallSiphonSurfaceMesh.RING_COUNT);
        assertEquals(8, DarkBallSiphonSurfaceMesh.SIDES);
        assertEquals(14 * 8 + 2, mesh.vertexCount());
        assertEquals(13 * 8 * 2 + 8 * 2, mesh.triangleCount());

        Map<Long, EdgeUse> edges = new HashMap<>();
        boolean[] referenced = new boolean[mesh.vertexCount()];
        int[] indices = mesh.indices();
        for (int offset = 0; offset < indices.length; offset += 3) {
            int a = indices[offset];
            int b = indices[offset + 1];
            int c = indices[offset + 2];
            assertTrue(a != b && b != c && c != a);
            referenced[a] = true;
            referenced[b] = true;
            referenced[c] = true;
            includeEdge(edges, a, b);
            includeEdge(edges, b, c);
            includeEdge(edges, c, a);
            assertTrue(triangleAreaSquared(
                    mesh.positions(), a, b, c) > 1.0e-12f,
                    "regular tube triangles must not collapse");
        }
        for (boolean vertexReferenced : referenced) {
            assertTrue(vertexReferenced);
        }
        for (EdgeUse edge : edges.values()) {
            assertEquals(2, edge.count,
                    "every edge must have exactly two incident faces");
            assertEquals(0, edge.orientationBalance,
                    "incident faces must use opposite edge winding");
        }
        assertEquals(2,
                mesh.vertexCount() - edges.size() + mesh.triangleCount(),
                "closed connected tube must have sphere Euler characteristic");
    }

    @Test
    void exportsFiniteUnitNormalsAndCompositePayloads() {
        Vector3f[] nodes = gentlyCurvedNodes();
        float rootRadius = 0.22f;
        float endRadius = 0.09f;
        DarkBallSiphonSurfaceMesh mesh =
                DarkBallSiphonSurfaceMesh.build(
                        nodes, rootRadius, endRadius);

        assertEquals(mesh.vertexCount() * 3, mesh.positions().length);
        assertEquals(mesh.vertexCount() * 3, mesh.normals().length);
        assertEquals(mesh.vertexCount(), mesh.curveT().length);
        assertEquals(mesh.vertexCount(), mesh.localRadius().length);
        assertEquals(mesh.vertexCount(), mesh.materialWindow().length);

        assertFinite(mesh.positions());
        assertFinite(mesh.normals());
        assertFinite(mesh.curveT());
        assertFinite(mesh.localRadius());
        assertFinite(mesh.materialWindow());
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            assertEquals(1.0f, vectorLength(mesh.normals(), vertex), EPSILON);
            assertTrue(mesh.curveT()[vertex] >= 0.0f
                    && mesh.curveT()[vertex] <= 1.0f);
            assertTrue(mesh.localRadius()[vertex] > 0.0f);
            assertEquals(1.0f, mesh.materialWindow()[vertex], EPSILON);
        }

        for (int ring = 0;
             ring < DarkBallSiphonSurfaceMesh.RING_COUNT;
             ring++) {
            float expectedT = ring
                    / (DarkBallSiphonSurfaceMesh.RING_COUNT - 1.0f);
            for (int side = 0;
                 side < DarkBallSiphonSurfaceMesh.SIDES;
                 side++) {
                int vertex = ring * DarkBallSiphonSurfaceMesh.SIDES + side;
                assertEquals(expectedT, mesh.curveT()[vertex], EPSILON);
                assertEquals(mesh.localRadius()[vertex],
                        vertex(mesh.positions(), vertex)
                                .distance(nodes[ring]),
                        EPSILON);
            }
        }
        assertEquals(rootRadius, mesh.localRadius()[0], EPSILON);
        assertEquals(endRadius,
                mesh.localRadius()[
                        (DarkBallSiphonSurfaceMesh.RING_COUNT - 1)
                                * DarkBallSiphonSurfaceMesh.SIDES],
                EPSILON);

        int rootCap = DarkBallSiphonSurfaceMesh.RING_COUNT
                * DarkBallSiphonSurfaceMesh.SIDES;
        int endCap = rootCap + 1;
        assertVectorEquals(nodes[0], vertex(mesh.positions(), rootCap));
        assertVectorEquals(nodes[nodes.length - 1],
                vertex(mesh.positions(), endCap));
        assertEquals(0.0f, mesh.curveT()[rootCap], EPSILON);
        assertEquals(1.0f, mesh.curveT()[endCap], EPSILON);
        Vector3f firstDirection =
                new Vector3f(nodes[1]).sub(nodes[0]).normalize();
        Vector3f lastDirection =
                new Vector3f(nodes[nodes.length - 1])
                        .sub(nodes[nodes.length - 2]).normalize();
        assertTrue(vertex(mesh.normals(), rootCap)
                .dot(firstDirection) < -0.90f);
        assertTrue(vertex(mesh.normals(), endCap)
                .dot(lastDirection) > 0.90f);
    }

    @Test
    void revealAndTrailingGatesDoNotChangeClosedGeometry() {
        Vector3f[] nodes = gentlyCurvedNodes();
        float[] trailing = new float[nodes.length];
        float[] open = new float[nodes.length];
        for (int node = 0; node < nodes.length; node++) {
            trailing[node] = node / (nodes.length - 1.0f);
            open[node] = 1.0f;
        }
        float progress = 0.23f;

        DarkBallSiphonSurfaceMesh reference =
                DarkBallSiphonSurfaceMesh.build(
                        nodes, 0.20f, 0.10f, progress, open);
        DarkBallSiphonSurfaceMesh gated =
                DarkBallSiphonSurfaceMesh.build(
                        nodes, 0.20f, 0.10f, progress, trailing);

        assertArrayEquals(reference.positions(), gated.positions());
        assertArrayEquals(reference.normals(), gated.normals());
        assertArrayEquals(reference.curveT(), gated.curveT());
        assertArrayEquals(reference.localRadius(), gated.localRadius());
        assertArrayEquals(reference.indices(), gated.indices());
        for (int ring = 0; ring < nodes.length; ring++) {
            float expected = reference.materialWindow()[
                    ring * DarkBallSiphonSurfaceMesh.SIDES]
                    * trailing[ring];
            for (int side = 0;
                 side < DarkBallSiphonSurfaceMesh.SIDES;
                 side++) {
                int vertex = ring * DarkBallSiphonSurfaceMesh.SIDES + side;
                assertEquals(expected,
                        gated.materialWindow()[vertex], EPSILON);
            }
        }
        assertTrue(gated.materialWindow()[0]
                        < reference.materialWindow()[0],
                "trailing gate must be represented independently of geometry");
    }

    @Test
    void explicitContractedShoulderMatchesTheShaderRadiusProfile() {
        Vector3f[] nodes = gentlyCurvedNodes();
        float bodyRadius = 4.0f;
        float voxelSize = 0.01f;
        float endSourceRadius = 0.12f;
        float inletScale =
                DarkBallCaptureVfx.SIPHON_CONTRACTED_INLET_RADIUS_SCALE;
        float rootRadius = Math.max(
                bodyRadius * 0.055f * inletScale,
                voxelSize * 0.90f);
        float shoulderRadius = Math.max(
                bodyRadius * 0.070f * inletScale,
                voxelSize * 1.10f);
        float endRadius = Math.max(
                endSourceRadius * 0.72f,
                voxelSize * 0.90f);
        DarkBallSiphonSurfaceMesh mesh =
                DarkBallSiphonSurfaceMesh.build(
                        nodes, rootRadius, shoulderRadius, endRadius,
                        1.0f, null);

        for (int ring = 0; ring < nodes.length; ring++) {
            float curveT = ring / (nodes.length - 1.0f);
            float expected =
                    DarkBallProjectedEffectBounds.siphonRadius(
                            curveT,
                            bodyRadius,
                            voxelSize,
                            endSourceRadius,
                            inletScale);
            for (int side = 0;
                 side < DarkBallSiphonSurfaceMesh.SIDES;
                 side++) {
                int vertex =
                        ring * DarkBallSiphonSurfaceMesh.SIDES + side;
                assertEquals(expected,
                        mesh.localRadius()[vertex], EPSILON);
                assertEquals(expected,
                        vertex(mesh.positions(), vertex)
                                .distance(nodes[ring]),
                        EPSILON);
            }
        }
    }

    @Test
    void parallelTransportDoesNotFlipOnNearCollinearOrSharpPaths() {
        assertFrameContinuity(nearCollinearNodes(), 0.999f);
        assertFrameContinuity(sharpPlanarNodes(), 0.90f);
    }

    @Test
    void repeatedAndFullyDegenerateNodesRemainFiniteAndDeterministic() {
        Vector3f[] repeated = nearCollinearNodes();
        repeated[4].set(repeated[3]);
        repeated[5].set(repeated[3]);
        DarkBallSiphonSurfaceMesh first =
                DarkBallSiphonSurfaceMesh.build(repeated, 0.16f, 0.08f);
        DarkBallSiphonSurfaceMesh second =
                DarkBallSiphonSurfaceMesh.build(repeated, 0.16f, 0.08f);
        assertFinite(first.positions());
        assertFinite(first.normals());
        assertArrayEquals(first.positions(), second.positions());
        assertArrayEquals(first.normals(), second.normals());
        assertArrayEquals(first.indices(), second.indices());

        Vector3f[] collapsed =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        for (int node = 0; node < collapsed.length; node++) {
            collapsed[node] = new Vector3f(2.0f, -1.0f, 0.5f);
        }
        DarkBallSiphonSurfaceMesh degenerate =
                DarkBallSiphonSurfaceMesh.build(
                        collapsed, 0.16f, 0.08f);
        assertFinite(degenerate.positions());
        assertFinite(degenerate.normals());
        for (int vertex = 0; vertex < degenerate.vertexCount(); vertex++) {
            assertEquals(1.0f,
                    vectorLength(degenerate.normals(), vertex), EPSILON);
        }
    }

    @Test
    void rejectsInvalidShapeInputsWithoutMutatingNodesOrGates() {
        Vector3f[] nodes = gentlyCurvedNodes();
        Vector3f[] originals = copy(nodes);
        float[] trailing = new float[nodes.length];
        for (int node = 0; node < trailing.length; node++) {
            trailing[node] = 0.25f + node * 0.02f;
        }
        float[] originalTrailing = trailing.clone();

        DarkBallSiphonSurfaceMesh.build(
                nodes, 0.18f, 0.09f, Float.NaN, trailing);
        for (int node = 0; node < nodes.length; node++) {
            assertVectorEquals(originals[node], nodes[node]);
        }
        assertArrayEquals(originalTrailing, trailing);

        assertThrows(IllegalArgumentException.class,
                () -> DarkBallSiphonSurfaceMesh.build(
                        new Vector3f[13], 0.18f, 0.09f));
        assertThrows(IllegalArgumentException.class,
                () -> DarkBallSiphonSurfaceMesh.build(
                        nodes, 0.0f, 0.09f));
        assertThrows(IllegalArgumentException.class,
                () -> DarkBallSiphonSurfaceMesh.build(
                        nodes, 0.18f, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> DarkBallSiphonSurfaceMesh.build(
                        nodes, 0.18f, 0.09f, 0.5f, new float[13]));

        Vector3f[] nonfinite = copy(nodes);
        nonfinite[6].x = Float.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class,
                () -> DarkBallSiphonSurfaceMesh.build(
                        nonfinite, 0.18f, 0.09f));
    }

    private static void assertFrameContinuity(Vector3f[] nodes,
                                              float minimumSideDot) {
        DarkBallSiphonSurfaceMesh mesh =
                DarkBallSiphonSurfaceMesh.build(nodes, 0.15f, 0.08f);
        Vector3f previousSide = null;
        for (int ring = 0; ring < nodes.length; ring++) {
            int ringStart = ring * DarkBallSiphonSurfaceMesh.SIDES;
            Vector3f side = vertex(mesh.normals(), ringStart);
            Vector3f up = vertex(mesh.normals(),
                    ringStart + DarkBallSiphonSurfaceMesh.SIDES / 4);
            Vector3f tangent = new Vector3f(side).cross(up);
            assertEquals(1.0f, tangent.length(), EPSILON);
            if (previousSide != null) {
                assertTrue(previousSide.dot(side) >= minimumSideDot,
                        "parallel-transport side flipped at ring " + ring);
            }
            previousSide = side;
        }
    }

    private static Vector3f[] gentlyCurvedNodes() {
        Vector3f[] nodes =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        for (int node = 0; node < nodes.length; node++) {
            float t = node / (nodes.length - 1.0f);
            nodes[node] = new Vector3f(
                    t * 5.0f,
                    (float) Math.sin(t * Math.PI * 1.4) * 0.35f,
                    (float) Math.sin(t * Math.PI * 0.8) * 0.22f);
        }
        return nodes;
    }

    private static Vector3f[] nearCollinearNodes() {
        Vector3f[] nodes =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        for (int node = 0; node < nodes.length; node++) {
            nodes[node] = new Vector3f(
                    node * 0.45f,
                    (node % 2 == 0 ? 1.0f : -1.0f) * 1.0e-7f,
                    node * 2.0e-8f);
        }
        return nodes;
    }

    private static Vector3f[] sharpPlanarNodes() {
        float[] turnDegrees = {
                0.0f, 0.0f, 72.0f, 72.0f, -38.0f, -38.0f,
                96.0f, 96.0f, 18.0f, 18.0f, 126.0f, 126.0f, 52.0f
        };
        Vector3f[] nodes =
                new Vector3f[DarkBallSiphonBoltPath.NODE_COUNT];
        nodes[0] = new Vector3f();
        for (int node = 1; node < nodes.length; node++) {
            float angle =
                    (float) Math.toRadians(turnDegrees[node - 1]);
            nodes[node] = new Vector3f(nodes[node - 1]).add(
                    (float) Math.cos(angle) * 0.55f,
                    (float) Math.sin(angle) * 0.55f,
                    0.0f);
        }
        return nodes;
    }

    private static void includeEdge(Map<Long, EdgeUse> edges,
                                    int from,
                                    int to) {
        int lower = Math.min(from, to);
        int upper = Math.max(from, to);
        long key = (Integer.toUnsignedLong(lower) << 32)
                | Integer.toUnsignedLong(upper);
        int direction = from == lower ? 1 : -1;
        EdgeUse current = edges.get(key);
        if (current == null) {
            edges.put(key, new EdgeUse(1, direction));
        } else {
            edges.put(key, new EdgeUse(
                    current.count + 1,
                    current.orientationBalance + direction));
        }
    }

    private static float triangleAreaSquared(float[] positions,
                                             int a,
                                             int b,
                                             int c) {
        Vector3f ab = vertex(positions, b).sub(vertex(positions, a));
        Vector3f ac = vertex(positions, c).sub(vertex(positions, a));
        return ab.cross(ac).lengthSquared();
    }

    private static float vectorLength(float[] vectors, int vertex) {
        return vertex(vectors, vertex).length();
    }

    private static Vector3f vertex(float[] values, int vertex) {
        int offset = vertex * 3;
        return new Vector3f(
                values[offset], values[offset + 1], values[offset + 2]);
    }

    private static Vector3f[] copy(Vector3f[] source) {
        Vector3f[] copy = new Vector3f[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = new Vector3f(source[index]);
        }
        return copy;
    }

    private static void assertFinite(float[] values) {
        for (float value : values) {
            assertTrue(Float.isFinite(value));
        }
    }

    private static void assertVectorEquals(Vector3f expected,
                                           Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    private record EdgeUse(int count, int orientationBalance) {
    }
}
