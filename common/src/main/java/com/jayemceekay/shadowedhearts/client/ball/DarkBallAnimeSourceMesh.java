package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.CapturedTriangle;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Immutable alpha-clipped source geometry for the anime energy body. */
final class DarkBallAnimeSourceMesh {
    static final int MAX_CLIPPED_TRIANGLES = 131_072;
    private static final double MIN_TRIANGLE_AREA = 1.0e-12;

    private DarkBallAnimeSourceMesh() {
    }

    static MeshResult prepare(List<CapturedTriangle> triangles, DarkBallSnapshotTextures textures,
                              DarkBallVolumeBuildResult frame, BooleanSupplier cancellationRequested) {
        return prepare(triangles, textures, frame, cancellationRequested, MAX_CLIPPED_TRIANGLES);
    }

    static MeshResult prepare(List<CapturedTriangle> triangles, DarkBallSnapshotTextures textures,
                              DarkBallVolumeBuildResult frame, BooleanSupplier cancellationRequested,
                              int triangleLimit) {
        return DarkBallPreparationCancellation.run(cancellationRequested, () -> {
            if (triangles == null || textures == null || !validFrame(frame)) {
                return new MeshResult(null, new int[0], Stats.unavailable(
                        triangles == null ? 0 : triangles.size(), "invalid direct surface input"));
            }
            MeshBuilder builder = new MeshBuilder(triangles.size(), frame,
                    Math.max(1, Math.min(triangleLimit, MAX_CLIPPED_TRIANGLES)));
            Map<SourceKey, SourceGroup> groups = new LinkedHashMap<>();
            for (CapturedTriangle triangle : triangles) {
                DarkBallPreparationCancellation.check();
                LocalTriangle local = localTriangle(triangle, frame, textures);
                if (local == null) {
                    builder.degenerateTriangles++;
                    continue;
                }
                SourceKey key = SourceKey.of(local);
                SourceGroup group = groups.computeIfAbsent(key, ignored -> new SourceGroup());
                group.sources.add(local);
                if (group.opaqueSource == null && fullyVisible(local)) {
                    group.opaqueSource = local;
                }
            }
            try {
                for (SourceGroup group : groups.values()) {
                    DarkBallPreparationCancellation.check();
                    if (group.opaqueSource != null) {
                        // An opaque base already covers every partial overlay on
                        // exactly this oriented triangle. Count its area once.
                        builder.duplicateSourceTriangles += group.sources.size() - 1;
                        builder.clip(group.opaqueSource);
                    } else {
                        for (LocalTriangle source : group.sources) {
                            builder.clip(source);
                        }
                    }
                }
                return builder.finish();
            } catch (SurfaceLimitException failure) {
                return new MeshResult(null, new int[0], builder.stats(0, failure.getMessage()));
            }
        });
    }

    private static LocalTriangle localTriangle(CapturedTriangle triangle,
                                                 DarkBallVolumeBuildResult frame,
                                                 DarkBallSnapshotTextures textures) {
        if (triangle == null || !finite(triangle.a()) || !finite(triangle.b())
                || !finite(triangle.c()) || !Float.isFinite(triangle.au())
                || !Float.isFinite(triangle.av()) || !Float.isFinite(triangle.bu())
                || !Float.isFinite(triangle.bv()) || !Float.isFinite(triangle.cu())
                || !Float.isFinite(triangle.cv())) {
            return null;
        }
        Point a = local(triangle.a(), frame);
        Point b = local(triangle.b(), frame);
        Point c = local(triangle.c(), frame);
        Point ab = b.subtract(a);
        Point ac = c.subtract(a);
        Point normal = ab.cross(ac);
        double twiceArea = normal.length();
        if (!Double.isFinite(twiceArea) || twiceArea <= MIN_TRIANGLE_AREA * 2.0) {
            return null;
        }
        return new LocalTriangle(triangle, a, b, c, normal.scale(1.0 / twiceArea),
                textures.alphaFor(triangle));
    }

    private static boolean fullyVisible(LocalTriangle local) {
        CapturedTriangle source = local.source();
        float minimumVertexAlpha = Math.min(source.alphaA(),
                Math.min(source.alphaB(), source.alphaC())) / 255.0f;
        if (local.alpha() == null) {
            return minimumVertexAlpha >= DarkBallTextureAlphaClipper.ALPHA_CUTOFF;
        }
        var range = local.alpha().range(Math.min(source.au(), Math.min(source.bu(), source.cu())),
                Math.min(source.av(), Math.min(source.bv(), source.cv())),
                Math.max(source.au(), Math.max(source.bu(), source.cu())),
                Math.max(source.av(), Math.max(source.bv(), source.cv())));
        return range.minimum() * minimumVertexAlpha >= DarkBallTextureAlphaClipper.ALPHA_CUTOFF;
    }

    private static Point local(Vec3 world, DarkBallVolumeBuildResult frame) {
        Vec3 offset = world.subtract(frame.root());
        return new Point(offset.dot(frame.axis()), offset.dot(frame.side()), offset.dot(frame.up()));
    }

    private static boolean validFrame(DarkBallVolumeBuildResult frame) {
        return frame != null && finite(frame.root()) && finite(frame.axis())
                && finite(frame.side()) && finite(frame.up())
                && Float.isFinite(frame.captureLength()) && frame.captureLength() > 0.0f
                && Float.isFinite(frame.radius()) && frame.radius() > 0.0f;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    record MeshResult(DarkBallSurfaceMesh mesh, int[] triangleComponents, Stats stats) {
    }

    record Stats(int sourceTriangles, int clippedTriangles, int retainedTriangles,
                 int duplicateSourceTriangles, int duplicateClippedTriangles,
                 int degenerateTriangles, int invisibleTriangles, int connectedComponents,
                 String failureReason) {
        static Stats unavailable(int sources, String reason) {
            return new Stats(sources, 0, 0, 0, 0, 0, 0, 0, reason);
        }

        String summary() {
            return "sourceTriangles=" + sourceTriangles + ", clippedTriangles=" + clippedTriangles
                    + ", retainedTriangles=" + retainedTriangles
                    + ", duplicateSources=" + duplicateSourceTriangles
                    + ", duplicateClipped=" + duplicateClippedTriangles
                    + ", rejectedDegenerate=" + degenerateTriangles
                    + ", invisibleSources=" + invisibleTriangles
                    + ", sourceComponents=" + connectedComponents
                    + (failureReason.isEmpty() ? "" : ", unavailable=" + failureReason);
        }
    }

    private static final class SourceGroup {
        private final List<LocalTriangle> sources = new ArrayList<>();
        private LocalTriangle opaqueSource;
    }

    private record LocalTriangle(CapturedTriangle source, Point a, Point b, Point c, Point normal,
                                 DarkBallTextureAlphaClipper.AlphaField alpha) {
        Point interpolate(DarkBallTextureAlphaClipper.ParamVertex vertex) {
            // Interpolate in the local frame. Multiplying absolute world
            // coordinates by rounded barycentric weights drifts at large X/Z.
            return a.add(b.subtract(a).scale(vertex.weightB()))
                    .add(c.subtract(a).scale(vertex.weightC()));
        }
    }

    private record Point(double x, double y, double z) {
        Point add(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
        Point subtract(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }
        Point scale(double scale) { return new Point(x * scale, y * scale, z * scale); }
        Point cross(Point other) {
            return new Point(y * other.z - z * other.y, z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }
        double length() { return Math.sqrt(x * x + y * y + z * z); }
        PositionKey key() { return new PositionKey(bits(x), bits(y), bits(z)); }
        private static int bits(double value) {
            float rounded = (float) value;
            return Float.floatToIntBits(rounded == 0.0f ? 0.0f : rounded);
        }
    }

    private record PositionKey(int x, int y, int z) implements Comparable<PositionKey> {
        @Override
        public int compareTo(PositionKey other) {
            int order = Integer.compare(x, other.x);
            if (order == 0) order = Integer.compare(y, other.y);
            if (order == 0) order = Integer.compare(z, other.z);
            return order;
        }
    }

    private record SourceKey(PositionKey a, PositionKey b, PositionKey c, PositionKey normal) {
        static SourceKey of(LocalTriangle triangle) {
            PositionKey[] points = {triangle.a().key(), triangle.b().key(), triangle.c().key()};
            Arrays.sort(points);
            return new SourceKey(points[0], points[1], points[2], triangle.normal().key());
        }
    }

    private record VertexKey(PositionKey position, PositionKey normal) {
    }

    private record TopologyKey(long x, long y, long z) {
    }

    private record TriangleKey(int a, int b, int c) {
        static TriangleKey of(int a, int b, int c) {
            int low = Math.min(a, Math.min(b, c));
            int high = Math.max(a, Math.max(b, c));
            return new TriangleKey(low, a + b + c - low - high, high);
        }
    }

    private static final class MeshBuilder {
        private final int sourceTriangles;
        private final int triangleLimit;
        private final double topologyStep;
        private final Map<VertexKey, Integer> vertexByKey = new HashMap<>();
        private final Map<TopologyKey, Integer> topologyOwners = new HashMap<>();
        private final Map<TriangleKey, Boolean> retained = new HashMap<>();
        private float[] positions = new float[768];
        private float[] normals = new float[768];
        private int[] parent = new int[256];
        private int[] indices = new int[768];
        private int vertexCount;
        private int indexCount;
        private int clippedTriangles;
        private int duplicateSourceTriangles;
        private int duplicateClippedTriangles;
        private int degenerateTriangles;
        private int invisibleTriangles;

        MeshBuilder(int sourceTriangles, DarkBallVolumeBuildResult frame,
                    int triangleLimit) {
            this.sourceTriangles = sourceTriangles;
            this.triangleLimit = triangleLimit;
            // Connectivity only: never snap/move source positions or normals.
            // A few local float ULPs reconnect texel clips along shared edges.
            topologyStep = Math.max(1.0e-7,
                    Math.ulp(Math.max(frame.captureLength(), frame.radius() * 2.0f)) * 8.0);
        }

        void clip(LocalTriangle source) {
            DarkBallPreparationCancellation.check();
            int before = clippedTriangles;
            DarkBallTextureAlphaClipper.forEachVisibleTriangleCoalesced(source.source(), source.alpha(),
                    clipped -> addTriangle(source, clipped));
            if (clippedTriangles == before) invisibleTriangles++;
        }

        void addTriangle(LocalTriangle source, DarkBallTextureAlphaClipper.ParamTriangle triangle) {
            DarkBallPreparationCancellation.checkEvery(clippedTriangles);
            if (++clippedTriangles > triangleLimit) {
                throw new SurfaceLimitException("alpha-clipped triangle budget exceeded (" + triangleLimit + ")");
            }
            Point a = source.interpolate(triangle.a());
            Point b = source.interpolate(triangle.b());
            Point c = source.interpolate(triangle.c());
            double area = b.subtract(a).cross(c.subtract(a)).length() * 0.5;
            if (!Double.isFinite(area) || area <= MIN_TRIANGLE_AREA) {
                degenerateTriangles++;
                return;
            }
            // Clipped doubles can have positive area while the three distinct
            // float positions submitted to the GPU are collinear. Validate
            // that actual representation before publishing vertices or asking
            // downstream core refinement to consume a zero-area triangle.
            a = packedPosition(a);
            b = packedPosition(b);
            c = packedPosition(c);
            Point packedNormal = b.subtract(a).cross(c.subtract(a));
            if (packedNormal.x() == 0.0 && packedNormal.y() == 0.0
                    && packedNormal.z() == 0.0) {
                degenerateTriangles++;
                return;
            }
            int first = vertex(a, source.normal());
            int second = vertex(b, source.normal());
            int third = vertex(c, source.normal());
            if (first == second || first == third || second == third) {
                degenerateTriangles++;
                return;
            }
            if (retained.putIfAbsent(TriangleKey.of(first, second, third), Boolean.TRUE) != null) {
                duplicateClippedTriangles++;
                return;
            }
            if (indexCount + 3 > indices.length) {
                indices = Arrays.copyOf(indices, Math.min(triangleLimit * 3, indices.length * 2));
            }
            indices[indexCount++] = first;
            indices[indexCount++] = second;
            indices[indexCount++] = third;
            union(first, second);
            union(first, third);
        }

        private static Point packedPosition(Point point) {
            float x = (float) point.x();
            float y = (float) point.y();
            float z = (float) point.z();
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new SurfaceLimitException("non-finite packed direct surface position");
            }
            // Keep the cross product in double precision after rounding: only
            // exactly zero packed area is rejected, not a small visible face.
            return new Point(x, y, z);
        }

        private int vertex(Point point, Point normal) {
            VertexKey key = new VertexKey(point.key(), normal.key());
            Integer existing = vertexByKey.get(key);
            if (existing != null) return existing;
            float x = (float) point.x();
            float y = (float) point.y();
            float z = (float) point.z();
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new SurfaceLimitException("non-finite direct surface position");
            }
            if (vertexCount == positions.length / 3) {
                int size = Math.min(triangleLimit * 3, positions.length / 3 * 2);
                positions = Arrays.copyOf(positions, size * 3);
                normals = Arrays.copyOf(normals, size * 3);
                parent = Arrays.copyOf(parent, size);
            }
            int vertex = vertexCount++;
            int triple = vertex * 3;
            positions[triple] = x;
            positions[triple + 1] = y;
            positions[triple + 2] = z;
            normals[triple] = (float) normal.x();
            normals[triple + 1] = (float) normal.y();
            normals[triple + 2] = (float) normal.z();
            parent[vertex] = vertex;
            vertexByKey.put(key, vertex);
            TopologyKey topology = new TopologyKey(Math.round(x / topologyStep),
                    Math.round(y / topologyStep), Math.round(z / topologyStep));
            Integer adjacent = topologyOwners.putIfAbsent(topology, vertex);
            if (adjacent != null) union(vertex, adjacent);
            return vertex;
        }

        private int root(int vertex) {
            while (parent[vertex] != vertex) {
                parent[vertex] = parent[parent[vertex]];
                vertex = parent[vertex];
            }
            return vertex;
        }

        private void union(int first, int second) {
            int a = root(first);
            int b = root(second);
            if (a != b) parent[Math.max(a, b)] = Math.min(a, b);
        }

        MeshResult finish() {
            if (indexCount == 0) {
                return new MeshResult(null, new int[0], stats(0, "no visible source triangles"));
            }
            int count = indexCount / 3;
            int[] components = new int[count];
            Map<Integer, Integer> labels = new LinkedHashMap<>();
            for (int triangle = 0; triangle < count; triangle++) {
                DarkBallPreparationCancellation.checkEvery(triangle);
                int base = triangle * 3;
                int first = indices[base];
                components[triangle] = labels.computeIfAbsent(root(first), ignored -> labels.size());
            }
            DarkBallSurfaceMesh mesh = new DarkBallSurfaceMesh(
                    Arrays.copyOf(positions, vertexCount * 3),
                    Arrays.copyOf(normals, vertexCount * 3),
                    Arrays.copyOf(indices, indexCount));
            return new MeshResult(mesh, components, stats(labels.size(), ""));
        }

        Stats stats(int components, String failure) {
            return new Stats(sourceTriangles, clippedTriangles, indexCount / 3,
                    duplicateSourceTriangles, duplicateClippedTriangles, degenerateTriangles,
                    invisibleTriangles, components, failure);
        }
    }

    private static final class SurfaceLimitException extends RuntimeException {
        SurfaceLimitException(String message) { super(message); }
    }
}
