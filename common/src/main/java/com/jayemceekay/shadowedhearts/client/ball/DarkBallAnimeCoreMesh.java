package com.jayemceekay.shadowedhearts.client.ball;

import java.util.Arrays;

/** Finite captured source triangles used during conversion into the energy body. */
final class DarkBallAnimeCoreMesh {
    static final int MAX_TRIANGLES = 131_072;
    static final int MAX_VERTICES = MAX_TRIANGLES * 3;
    private final float[] positions, normals;
    private final int[] indices;
    private final double maximumEdge;

    private DarkBallAnimeCoreMesh(float[] positions, float[] normals, int[] indices, double maximumEdge) {
        this.positions = positions;
        this.normals = normals;
        this.indices = indices;
        this.maximumEdge = maximumEdge;
    }

    static DarkBallAnimeCoreMesh capture(DarkBallSurfaceMesh source, double radius) {
        DarkBallPreparationCancellation.check();
        if (source == null || !source.hasFiniteGeometry() || source.triangleCount() == 0
                || source.triangleCount() > MAX_TRIANGLES || source.vertexCount() > MAX_VERTICES
                || !Double.isFinite(radius) || radius <= 0)
            throw new IllegalArgumentException("Invalid or over-budget anime source mesh");
        Vertices vertices = new Vertices(source.positions(), source.normals());
        int[] triangles = source.indices().clone();
        for (int i = 0; i < triangles.length; i++) {
            DarkBallPreparationCancellation.checkEvery(i);
            if (triangles[i] < 0 || triangles[i] >= vertices.count)
                throw new IllegalArgumentException("Invalid anime source triangle index");
        }
        triangles = withoutZeroAreaTriangles(vertices, triangles);
        if (triangles.length == 0)
            throw new IllegalArgumentException("Anime source has no nondegenerate triangles");
        double maximumEdgeSquared = 0;
        for (int i = 0; i < triangles.length; i += 3) {
            DarkBallPreparationCancellation.checkEvery(i / 3);
            int a = triangles[i], b = triangles[i + 1], c = triangles[i + 2];
            double longestSquared = Math.max(vertices.distanceSquared(a, b),
                    Math.max(vertices.distanceSquared(b, c), vertices.distanceSquared(c, a)));
            double doubleArea = vertices.doubleArea(a, b, c);
            if (!(doubleArea > 0) || !Double.isFinite(doubleArea) || !Double.isFinite(longestSquared))
                throw new IllegalArgumentException("Degenerate anime source triangle");
            double aspect = longestSquared / doubleArea;
            if (!Double.isFinite(aspect)) throw new IllegalArgumentException("Invalid anime triangle aspect");
            maximumEdgeSquared = Math.max(maximumEdgeSquared, longestSquared);
        }
        double maximumEdge = Math.sqrt(maximumEdgeSquared);
        if (maximumEdge > radius * 2 + Math.max(1e-10, radius * 1e-6))
            throw new IllegalArgumentException("Anime source edge exceeds enclosing radius");
        DarkBallPreparationCancellation.check();
        return new DarkBallAnimeCoreMesh(vertices.finishPositions(), vertices.finishNormals(), triangles,
                maximumEdge);
    }

    float[] positions() { return positions; }
    float[] normals() { return normals; }
    int[] indices() { return indices; }
    int vertexCount() { return positions.length / 3; }
    int triangleCount() { return indices.length / 3; }
    double maximumEdge() { return maximumEdge; }

    private static int[] withoutZeroAreaTriangles(Vertices vertices, int[] triangles) {
        int retained = 0;
        for (int i = 0; i < triangles.length; i += 3) {
            DarkBallPreparationCancellation.checkEvery(i / 3);
            double area = vertices.doubleArea(triangles[i], triangles[i + 1], triangles[i + 2]);
            if (!Double.isFinite(area)) throw new IllegalArgumentException("Nonfinite anime source triangle");
            if (area == 0) continue;
            triangles[retained++] = triangles[i];
            triangles[retained++] = triangles[i + 1];
            triangles[retained++] = triangles[i + 2];
        }
        return retained == triangles.length ? triangles : Arrays.copyOf(triangles, retained);
    }


    private static final class Vertices {
        private float[] p, n;
        private int count;
        Vertices(float[] positions, float[] normals) {
            p = positions.clone(); n = normals.clone(); count = p.length / 3;
        }
        double distanceSquared(int a, int b) {
            double x = (double) p[a * 3] - p[b * 3];
            double y = (double) p[a * 3 + 1] - p[b * 3 + 1];
            double z = (double) p[a * 3 + 2] - p[b * 3 + 2];
            return x * x + y * y + z * z;
        }
        double doubleArea(int a, int b, int c) {
            double ux = (double) p[b * 3] - p[a * 3], uy = (double) p[b * 3 + 1] - p[a * 3 + 1];
            double uz = (double) p[b * 3 + 2] - p[a * 3 + 2];
            double vx = (double) p[c * 3] - p[a * 3], vy = (double) p[c * 3 + 1] - p[a * 3 + 1];
            double vz = (double) p[c * 3 + 2] - p[a * 3 + 2];
            double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            return Math.sqrt(x * x + y * y + z * z);
        }
        float[] finishPositions() { return p; }
        float[] finishNormals() { return n; }
    }

}
