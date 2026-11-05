package com.jayemceekay.shadowedhearts.client.ball;

/** CPU-side alpha-clipped source triangles, independent of render-thread resources. */
final class DarkBallSurfaceMesh {
    private final float[] positions;
    private final float[] normals;
    private final int[] indices;

    DarkBallSurfaceMesh(float[] positions, float[] normals, int[] indices) {
        if (positions == null || positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions must contain complete xyz triples");
        }
        if (normals == null || normals.length != positions.length) {
            throw new IllegalArgumentException("normals must match the position triple count");
        }
        if (indices == null || indices.length % 3 != 0) {
            throw new IllegalArgumentException("indices must contain complete triangles");
        }
        this.positions = positions;
        this.normals = normals;
        this.indices = indices;
    }

    int vertexCount() {
        return positions.length / 3;
    }

    int triangleCount() {
        return indices.length / 3;
    }

    /** Open sheets and alpha-cutout boundaries are valid captured source geometry. */
    boolean hasFiniteGeometry() {
        if (vertexCount() < 3 || triangleCount() == 0) return false;
        for (int i = 0; i < positions.length; i++) {
            if (!Float.isFinite(positions[i]) || !Float.isFinite(normals[i])) return false;
        }
        for (int index : indices) {
            if (index < 0 || index >= vertexCount()) return false;
        }
        return true;
    }

    float[] positions() {
        return positions;
    }

    float[] normals() {
        return normals;
    }

    int[] indices() {
        return indices;
    }
}
