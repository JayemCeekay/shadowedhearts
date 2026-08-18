package com.jayemceekay.shadowedhearts.client.ball;

/**
 * CPU-side, indexed zero-isosurface extracted from the captured Dark Ball SDF.
 *
 * <p>The arrays deliberately remain independent from Minecraft render classes.
 * Extraction runs as a background sidecar after the authoritative analytical
 * volume installs; a later render-thread handoff can upload these immutable
 * arrays into a VBO without making the mesh generator depend on OpenGL.</p>
 */
final class DarkBallSurfaceMesh {
    private final float[] positions;
    private final float[] normals;
    private final float[] localThickness;
    private final float[] releaseOrder;
    private final float[] presentationReleaseOrder;
    private final float[] transportOrder;
    private final int[] indices;
    private final Validation validation;

    DarkBallSurfaceMesh(float[] positions,
                        float[] normals,
                        float[] localThickness,
                        float[] releaseOrder,
                        float[] transportOrder,
                        int[] indices,
                        Validation validation) {
        this(
                positions,
                normals,
                localThickness,
                releaseOrder,
                releaseOrder,
                transportOrder,
                indices,
                validation);
    }

    private DarkBallSurfaceMesh(float[] positions,
                                float[] normals,
                                float[] localThickness,
                                float[] releaseOrder,
                                float[] presentationReleaseOrder,
                                float[] transportOrder,
                                int[] indices,
                                Validation validation) {
        this.positions = requireVertexTriples("positions", positions);
        this.normals = requireMatchingTriples("normals", normals,
                this.positions.length);
        int vertexCount = this.positions.length / 3;
        this.localThickness = requireMatchingScalars(
                "localThickness", localThickness, vertexCount);
        this.releaseOrder = requireMatchingScalars(
                "releaseOrder", releaseOrder, vertexCount);
        this.presentationReleaseOrder = requireMatchingScalars(
                "presentationReleaseOrder",
                presentationReleaseOrder,
                vertexCount);
        this.transportOrder = requireMatchingScalars(
                "transportOrder", transportOrder, vertexCount);
        if (indices == null || indices.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "indices must contain complete triangles");
        }
        this.indices = indices;
        this.validation = validation == null
                ? Validation.empty()
                : validation;
    }

    int vertexCount() {
        return positions.length / 3;
    }

    int triangleCount() {
        return indices.length / 3;
    }

    boolean isRenderable() {
        return vertexCount() >= 3
                && triangleCount() >= 1
                && validation.watertight();
    }

    /**
     * Surface splats consume independent samples and therefore do not require
     * watertight, manifold, or consistently wound triangle connectivity.
     */
    boolean hasFiniteSplatSamples() {
        if (vertexCount() < 1) {
            return false;
        }
        for (int vertex = 0; vertex < vertexCount(); vertex++) {
            int triple = vertex * 3;
            if (!finite(positions[triple])
                    || !finite(positions[triple + 1])
                    || !finite(positions[triple + 2])
                    || !finite(normals[triple])
                    || !finite(normals[triple + 1])
                    || !finite(normals[triple + 2])
                    || !finite(localThickness[vertex])
                    || !finite(releaseOrder[vertex])
                    || !finite(presentationReleaseOrder[vertex])
                    || !finite(transportOrder[vertex])) {
                return false;
            }
        }
        return true;
    }

    float[] positions() {
        return positions;
    }

    float[] normals() {
        return normals;
    }

    float[] localThickness() {
        return localThickness;
    }

    float[] releaseOrder() {
        return releaseOrder;
    }

    float[] presentationReleaseOrder() {
        return presentationReleaseOrder;
    }

    float[] transportOrder() {
        return transportOrder;
    }

    int[] indices() {
        return indices;
    }

    Validation validation() {
        return validation;
    }

    DarkBallSurfaceMesh withReleaseOrder(float[] geometricReleaseOrder) {
        return new DarkBallSurfaceMesh(
                positions,
                normals,
                localThickness,
                geometricReleaseOrder,
                presentationReleaseOrder,
                transportOrder,
                indices,
                validation);
    }

    String summary() {
        return "vertices=" + vertexCount()
                + ", triangles=" + triangleCount()
                + ", components=" + validation.connectedComponents()
                + ", boundaryEdges=" + validation.boundaryEdges()
                + ", nonManifoldEdges=" + validation.nonManifoldEdges()
                + ", windingConflicts=" + validation.windingConflicts()
                + ", rejectedDegenerates="
                + validation.rejectedDegenerateTriangles()
                + ", zeroAdjusted=" + validation.zeroAdjustedSamples()
                + ", collapsedPolygons=" + validation.collapsedPolygons()
                + ", duplicateRejects="
                + validation.rejectedDuplicateIndexTriangles()
                + ", lowAreaRejects="
                + validation.rejectedLowAreaTriangles();
    }

    private static float[] requireVertexTriples(String name, float[] values) {
        if (values == null || values.length % 3 != 0) {
            throw new IllegalArgumentException(
                    name + " must contain complete xyz triples");
        }
        return values;
    }

    private static float[] requireMatchingTriples(String name, float[] values,
                                                   int expectedLength) {
        if (values == null || values.length != expectedLength) {
            throw new IllegalArgumentException(
                    name + " must match the position triple count");
        }
        return values;
    }

    private static float[] requireMatchingScalars(String name, float[] values,
                                                   int expectedLength) {
        if (values == null || values.length != expectedLength) {
            throw new IllegalArgumentException(
                    name + " must contain one value per vertex");
        }
        return values;
    }

    private static boolean finite(float value) {
        return Float.isFinite(value);
    }

    /**
     * Topology diagnostics calculated immediately after extraction.
     *
     * <p>A fixed indexed mesh cannot open a topological hole during later
     * vertex deformation. That guarantee is only useful when the rest mesh
     * begins closed, manifold, and consistently wound, so the renderer must
     * refuse meshes for which {@link #watertight()} is false.</p>
     */
    record Validation(
            int connectedComponents,
            int boundaryEdges,
            int nonManifoldEdges,
            int windingConflicts,
            int rejectedDegenerateTriangles,
            int zeroAdjustedSamples,
            int collapsedPolygons,
            int rejectedDuplicateIndexTriangles,
            int rejectedLowAreaTriangles
    ) {
        Validation(int connectedComponents,
                   int boundaryEdges,
                   int nonManifoldEdges,
                   int windingConflicts,
                   int rejectedDegenerateTriangles) {
            this(
                    connectedComponents,
                    boundaryEdges,
                    nonManifoldEdges,
                    windingConflicts,
                    rejectedDegenerateTriangles,
                    0,
                    0,
                    0,
                    0);
        }

        Validation {
            if (connectedComponents < 0
                    || boundaryEdges < 0
                    || nonManifoldEdges < 0
                    || windingConflicts < 0
                    || rejectedDegenerateTriangles < 0
                    || zeroAdjustedSamples < 0
                    || collapsedPolygons < 0
                    || rejectedDuplicateIndexTriangles < 0
                    || rejectedLowAreaTriangles < 0) {
                throw new IllegalArgumentException(
                        "mesh validation counts cannot be negative");
            }
            long detailedRejects =
                    (long) rejectedDuplicateIndexTriangles
                            + rejectedLowAreaTriangles;
            if (detailedRejects > rejectedDegenerateTriangles) {
                throw new IllegalArgumentException(
                        "detailed triangle rejects cannot exceed the total");
            }
        }

        boolean watertight() {
            return boundaryEdges == 0
                    && nonManifoldEdges == 0
                    && windingConflicts == 0;
        }

        static Validation empty() {
            return new Validation(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
