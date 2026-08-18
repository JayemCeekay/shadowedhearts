package com.jayemceekay.shadowedhearts.client.ball;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Extracts a welded zero-isosurface from the immutable analytical body field.
 *
 * <p>The grid is decomposed with the globally consistent six-tetrahedron
 * Freudenthal triangulation. Unlike an ambiguous per-cube marching-cubes table,
 * neighboring cells therefore make the same decision on their shared face.
 * Intersections are cached by global lattice edge, so every shared surface
 * vertex has one index. A positive ghost-cell border closes pathological source
 * material that reaches the analytical atlas boundary.</p>
 */
final class DarkBallSurfaceMeshExtractor {
    private static final int MAX_VERTEX_COUNT = 750_000;
    private static final int MAX_TRIANGLE_COUNT = 1_500_000;
    /**
     * Keeps the mesh-only scalar field in general position around the zero
     * isosurface. The analytical field can contain extremely small values after
     * trilinear resampling of the captured class-distance SDF. Marching those
     * values literally creates sub-pixel sliver faces which are then rejected
     * as degenerate, opening the otherwise closed surface.
     *
     * <p>Three percent of the widest source-cell spacing is large enough to
     * keep intersections clear of lattice endpoints at 96 x 64 x 64, while
     * moving the reconstructed contour by much less than one source voxel.
     * This adjustment is private to one-time mesh extraction; the analytical
     * field used by the rollback renderer is unchanged.</p>
     */
    private static final float ZERO_SAFE_DISTANCE_FRACTION = 1.0f / 32.0f;
    private static final int[][] CUBE_CORNERS = {
            {0, 0, 0},
            {1, 0, 0},
            {0, 1, 0},
            {1, 1, 0},
            {0, 0, 1},
            {1, 0, 1},
            {0, 1, 1},
            {1, 1, 1}
    };

    /**
     * Six Kuhn/Freudenthal tetrahedra around the shared 000-111 cube diagonal.
     * This exact orientation must remain identical for every grid cell.
     */
    private static final int[][] CUBE_TETRAHEDRA = {
            {0, 1, 3, 7},
            {0, 3, 2, 7},
            {0, 2, 6, 7},
            {0, 6, 4, 7},
            {0, 4, 5, 7},
            {0, 5, 1, 7}
    };

    private DarkBallSurfaceMeshExtractor() {
    }

    static DarkBallSurfaceMesh extract(GridInput input) {
        return extract(input, () -> false);
    }

    static DarkBallSurfaceMesh extract(
            GridInput input,
            BooleanSupplier cancellationRequested) {
        if (cancellationRequested == null) {
            throw new IllegalArgumentException(
                    "surface mesh cancellation supplier cannot be null");
        }
        float zeroSafeDistance = input.zeroSafeDistance();
        Builder builder = new Builder(
                input,
                cancellationRequested,
                input.zeroAdjustedSampleCount(zeroSafeDistance));
        int[] cornerX = new int[8];
        int[] cornerY = new int[8];
        int[] cornerZ = new int[8];
        float[] cornerDistance = new float[8];

        // The node coordinates include one extrapolated positive sample on
        // every side. Cubes therefore span [-1, size - 1] in each dimension.
        for (int y = -1; y < input.ySize(); y++) {
            throwIfCancellationRequested(cancellationRequested);
            for (int z = -1; z < input.zSize(); z++) {
                for (int x = -1; x < input.xSize(); x++) {
                    boolean allInside = true;
                    boolean allOutside = true;
                    for (int corner = 0; corner < CUBE_CORNERS.length;
                         corner++) {
                        int[] offset = CUBE_CORNERS[corner];
                        int nodeX = x + offset[0];
                        int nodeY = y + offset[1];
                        int nodeZ = z + offset[2];
                        float distance = input.sampleDistance(
                                nodeX, nodeY, nodeZ, zeroSafeDistance);
                        cornerX[corner] = nodeX;
                        cornerY[corner] = nodeY;
                        cornerZ[corner] = nodeZ;
                        cornerDistance[corner] = distance;
                        boolean inside = distance < 0.0f;
                        allInside &= inside;
                        allOutside &= !inside;
                    }
                    if (allInside || allOutside) {
                        continue;
                    }

                    for (int[] tetrahedron : CUBE_TETRAHEDRA) {
                        builder.addTetrahedron(
                                tetrahedron,
                                cornerX, cornerY, cornerZ,
                                cornerDistance);
                    }
                }
            }
        }
        return builder.finish();
    }

    /**
     * Immutable view of the fields required to construct future mesh vertex
     * attributes. The flattening convention matches DarkBallAnalyticalVolume:
     * {@code (y * zSize + z) * xSize + x}.
     */
    record GridInput(
            int xSize,
            int ySize,
            int zSize,
            float captureLength,
            float radius,
            float[] signedDistance,
            float[] normalX,
            float[] normalY,
            float[] normalZ,
            float[] localThickness,
            float[] releaseOrder,
            float[] transportOrder
    ) {
        GridInput {
            if (xSize < 2 || ySize < 2 || zSize < 2) {
                throw new IllegalArgumentException(
                        "surface mesh grid dimensions must be at least two");
            }
            if (!(captureLength > 0.0f) || !(radius > 0.0f)
                    || !Float.isFinite(captureLength)
                    || !Float.isFinite(radius)) {
                throw new IllegalArgumentException(
                        "surface mesh dimensions must be finite and positive");
            }
            int count = Math.multiplyExact(
                    Math.multiplyExact(xSize, ySize), zSize);
            requireField("signedDistance", signedDistance, count);
            requireField("normalX", normalX, count);
            requireField("normalY", normalY, count);
            requireField("normalZ", normalZ, count);
            requireField("localThickness", localThickness, count);
            requireField("releaseOrder", releaseOrder, count);
            requireField("transportOrder", transportOrder, count);
        }

        private static void requireField(String name, float[] field,
                                         int expectedLength) {
            if (field == null || field.length != expectedLength) {
                throw new IllegalArgumentException(
                        name + " must match the analytical grid");
            }
        }

        int index(int x, int y, int z) {
            return (y * zSize + z) * xSize + x;
        }

        boolean inBounds(int x, int y, int z) {
            return x >= 0 && x < xSize
                    && y >= 0 && y < ySize
                    && z >= 0 && z < zSize;
        }

        int extendedNodeId(int x, int y, int z) {
            int extendedX = xSize + 2;
            int extendedZ = zSize + 2;
            return ((y + 1) * extendedZ + (z + 1))
                    * extendedX + (x + 1);
        }

        float sampleDistance(int x, int y, int z,
                             float minimumMagnitude) {
            if (inBounds(x, y, z)) {
                float value = signedDistance[index(x, y, z)];
                if (!Float.isFinite(value)) {
                    return ghostDistance();
                }
                if (Math.abs(value) < minimumMagnitude) {
                    return value < 0.0f
                            ? -minimumMagnitude
                            : minimumMagnitude;
                }
                return value;
            }
            return ghostDistance();
        }

        float sampleNormalX(int x, int y, int z) {
            return sampleClamped(normalX, x, y, z, 1.0f);
        }

        float sampleNormalY(int x, int y, int z) {
            return sampleClamped(normalY, x, y, z, 0.0f);
        }

        float sampleNormalZ(int x, int y, int z) {
            return sampleClamped(normalZ, x, y, z, 0.0f);
        }

        float sampleThicknessLocal(float localX, float localY, float localZ) {
            float gridX = localX / captureLength * xSize - 0.5f;
            float gridY = (localY / radius * 0.5f + 0.5f)
                    * ySize - 0.5f;
            float gridZ = (localZ / radius * 0.5f + 0.5f)
                    * zSize - 0.5f;
            float sampled = sampleTrilinear(
                    localThickness, gridX, gridY, gridZ);
            return Float.isFinite(sampled)
                    ? Math.max(0.0f, sampled)
                    : 0.0f;
        }

        float sampleReleaseOrder(int x, int y, int z) {
            return Mth.clamp(sampleClamped(
                    releaseOrder, x, y, z, 1.0f), 0.0f, 1.0f);
        }

        float sampleTransportOrder(int x, int y, int z) {
            return Mth.clamp(sampleClamped(
                    transportOrder, x, y, z, 1.0f), 0.0f, 1.0f);
        }

        float positionX(int x) {
            return (x + 0.5f) / xSize * captureLength;
        }

        float positionY(int y) {
            return ((y + 0.5f) / ySize * 2.0f - 1.0f) * radius;
        }

        float positionZ(int z) {
            return ((z + 0.5f) / zSize * 2.0f - 1.0f) * radius;
        }

        float scale() {
            return Math.max(captureLength, radius * 2.0f);
        }

        float voxelSize() {
            return Math.min(
                    captureLength / xSize,
                    radius * 2.0f / Math.max(ySize, zSize));
        }

        float ghostDistance() {
            return Math.max(
                    captureLength / xSize,
                    radius * 2.0f / Math.min(ySize, zSize)) * 2.0f;
        }

        float zeroSafeDistance() {
            return ghostDistance() * 0.5f
                    * ZERO_SAFE_DISTANCE_FRACTION;
        }

        int zeroAdjustedSampleCount(float minimumMagnitude) {
            int count = 0;
            for (float value : signedDistance) {
                if (Float.isFinite(value)
                        && Math.abs(value) < minimumMagnitude) {
                    count++;
                }
            }
            return count;
        }

        private float sampleClamped(float[] field,
                                    int x, int y, int z,
                                    float fallback) {
            int clampedX = Mth.clamp(x, 0, xSize - 1);
            int clampedY = Mth.clamp(y, 0, ySize - 1);
            int clampedZ = Mth.clamp(z, 0, zSize - 1);
            float value = field[index(clampedX, clampedY, clampedZ)];
            return Float.isFinite(value) ? value : fallback;
        }

        private float sampleTrilinear(float[] field,
                                      float gridX,
                                      float gridY,
                                      float gridZ) {
            float boundedX = Mth.clamp(gridX, 0.0f, xSize - 1.0f);
            float boundedY = Mth.clamp(gridY, 0.0f, ySize - 1.0f);
            float boundedZ = Mth.clamp(gridZ, 0.0f, zSize - 1.0f);
            int x0 = Mth.floor(boundedX);
            int y0 = Mth.floor(boundedY);
            int z0 = Mth.floor(boundedZ);
            int x1 = Math.min(x0 + 1, xSize - 1);
            int y1 = Math.min(y0 + 1, ySize - 1);
            int z1 = Math.min(z0 + 1, zSize - 1);
            float tx = boundedX - x0;
            float ty = boundedY - y0;
            float tz = boundedZ - z0;
            float c000 = field[index(x0, y0, z0)];
            float c100 = field[index(x1, y0, z0)];
            float c010 = field[index(x0, y1, z0)];
            float c110 = field[index(x1, y1, z0)];
            float c001 = field[index(x0, y0, z1)];
            float c101 = field[index(x1, y0, z1)];
            float c011 = field[index(x0, y1, z1)];
            float c111 = field[index(x1, y1, z1)];
            float c00 = Mth.lerp(tx, c000, c100);
            float c10 = Mth.lerp(tx, c010, c110);
            float c01 = Mth.lerp(tx, c001, c101);
            float c11 = Mth.lerp(tx, c011, c111);
            return Mth.lerp(tz,
                    Mth.lerp(ty, c00, c10),
                    Mth.lerp(ty, c01, c11));
        }
    }

    private static final class Builder {
        private final GridInput input;
        private final BooleanSupplier cancellationRequested;
        private Long2IntOpenHashMap vertexByEdge =
                new Long2IntOpenHashMap();
        private final FloatList positions = new FloatList();
        private final FloatList normals = new FloatList();
        private final FloatList localThickness = new FloatList();
        private final FloatList releaseOrder = new FloatList();
        private final FloatList transportOrder = new FloatList();
        private final IntList indices = new IntList();
        private final int[] insideCorners = new int[4];
        private final int[] outsideCorners = new int[4];
        private final int zeroAdjustedSamples;
        private int rejectedDegenerateTriangles;
        private int collapsedPolygons;
        private int rejectedDuplicateIndexTriangles;
        private int rejectedLowAreaTriangles;

        private Builder(GridInput input,
                        BooleanSupplier cancellationRequested,
                        int zeroAdjustedSamples) {
            this.input = input;
            this.cancellationRequested = cancellationRequested;
            this.zeroAdjustedSamples = zeroAdjustedSamples;
            vertexByEdge.defaultReturnValue(-1);
        }

        private void addTetrahedron(int[] tetrahedron,
                                    int[] cornerX,
                                     int[] cornerY,
                                     int[] cornerZ,
                                     float[] cornerDistance) {
            int insideCount = 0;
            int outsideCount = 0;
            for (int corner : tetrahedron) {
                if (cornerDistance[corner] < 0.0f) {
                    insideCorners[insideCount++] = corner;
                } else {
                    outsideCorners[outsideCount++] = corner;
                }
            }
            if (insideCount == 0 || insideCount == 4) {
                return;
            }

            /*
             * Connectivity must come exclusively from the tetrahedron's sign
             * case. Captured normals are presentation data: using them to sort
             * a 2/2 intersection quad can project that planar quad edge-on and
             * turn its perimeter into a bow-tie.
             */
            if (insideCount == 1 || outsideCount == 1) {
                int singleton = insideCount == 1
                        ? insideCorners[0]
                        : outsideCorners[0];
                int[] opposite = insideCount == 1
                        ? outsideCorners
                        : insideCorners;
                int oppositeCount = insideCount == 1
                        ? outsideCount
                        : insideCount;
                if (oppositeCount != 3) {
                    collapsedPolygons++;
                    return;
                }
                emitTriangle(
                        vertexForIntersection(
                                singleton, opposite[0],
                                cornerX, cornerY, cornerZ, cornerDistance),
                        vertexForIntersection(
                                singleton, opposite[1],
                                cornerX, cornerY, cornerZ, cornerDistance),
                        vertexForIntersection(
                                singleton, opposite[2],
                                cornerX, cornerY, cornerZ, cornerDistance));
                return;
            }

            // The only remaining case is two inside and two outside corners.
            // Its cyclic perimeter is v00-v01-v11-v10.
            int v00 = vertexForIntersection(
                    insideCorners[0], outsideCorners[0],
                    cornerX, cornerY, cornerZ, cornerDistance);
            int v01 = vertexForIntersection(
                    insideCorners[0], outsideCorners[1],
                    cornerX, cornerY, cornerZ, cornerDistance);
            int v10 = vertexForIntersection(
                    insideCorners[1], outsideCorners[0],
                    cornerX, cornerY, cornerZ, cornerDistance);
            int v11 = vertexForIntersection(
                    insideCorners[1], outsideCorners[1],
                    cornerX, cornerY, cornerZ, cornerDistance);
            if (v00 == v01 || v00 == v10 || v00 == v11
                    || v01 == v10 || v01 == v11 || v10 == v11) {
                collapsedPolygons++;
                return;
            }

            double diagonal00To11Quality = Math.min(
                    triangleAreaSquared(v00, v01, v11),
                    triangleAreaSquared(v00, v11, v10));
            double diagonal01To10Quality = Math.min(
                    triangleAreaSquared(v00, v01, v10),
                    triangleAreaSquared(v01, v11, v10));
            int qualityComparison = Double.compare(
                    diagonal01To10Quality, diagonal00To11Quality);
            boolean useDiagonal01To10 = qualityComparison > 0
                    || (qualityComparison == 0
                    && vertexPairKey(v01, v10)
                    < vertexPairKey(v00, v11));
            if (useDiagonal01To10) {
                emitTriangle(v00, v01, v10);
                emitTriangle(v01, v11, v10);
            } else {
                emitTriangle(v00, v01, v11);
                emitTriangle(v00, v11, v10);
            }
        }

        private int vertexForIntersection(
                int cornerA,
                int cornerB,
                int[] cornerX,
                int[] cornerY,
                int[] cornerZ,
                float[] cornerDistance) {
            return vertexForIntersection(
                    cornerX[cornerA], cornerY[cornerA], cornerZ[cornerA],
                    cornerDistance[cornerA],
                    cornerX[cornerB], cornerY[cornerB], cornerZ[cornerB],
                    cornerDistance[cornerB]);
        }

        private int vertexForIntersection(
                int ax, int ay, int az, float distanceA,
                int bx, int by, int bz, float distanceB) {
            int nodeA = input.extendedNodeId(ax, ay, az);
            int nodeB = input.extendedNodeId(bx, by, bz);
            float denominator = distanceA - distanceB;
            float t = Math.abs(denominator) > 0.0000001f
                    ? Mth.clamp(distanceA / denominator, 0.0f, 1.0f)
                    : 0.5f;
            /*
             * sampleDistance() keeps every finite endpoint strictly away from
             * zero. Preserve the lattice-edge identity even when interpolation
             * lands very near an endpoint; merging several distinct edges onto
             * one node can create a non-manifold critical vertex.
             */
            long key = edgeVertexKey(nodeA, nodeB);

            int existing = vertexByEdge.get(key);
            if (existing >= 0) {
                return existing;
            }
            if (positions.size() / 3 >= MAX_VERTEX_COUNT) {
                throw new IllegalStateException(
                        "surface mesh exceeded " + MAX_VERTEX_COUNT
                                + " welded vertices");
            }

            float positionX = Mth.lerp(
                    t, input.positionX(ax), input.positionX(bx));
            float positionY = Mth.lerp(
                    t, input.positionY(ay), input.positionY(by));
            float positionZ = Mth.lerp(
                    t, input.positionZ(az), input.positionZ(bz));
            float normalX = Mth.lerp(
                    t,
                    input.sampleNormalX(ax, ay, az),
                    input.sampleNormalX(bx, by, bz));
            float normalY = Mth.lerp(
                    t,
                    input.sampleNormalY(ax, ay, az),
                    input.sampleNormalY(bx, by, bz));
            float normalZ = Mth.lerp(
                    t,
                    input.sampleNormalZ(ax, ay, az),
                    input.sampleNormalZ(bx, by, bz));
            float normalLengthSquared = normalX * normalX
                    + normalY * normalY + normalZ * normalZ;
            if (normalLengthSquared <= 0.00000001f) {
                // SDF values increase toward the exterior. The oriented edge
                // is a stable fallback when a captured gradient is degenerate.
                float sign = distanceB >= distanceA ? 1.0f : -1.0f;
                normalX = (input.positionX(bx) - input.positionX(ax)) * sign;
                normalY = (input.positionY(by) - input.positionY(ay)) * sign;
                normalZ = (input.positionZ(bz) - input.positionZ(az)) * sign;
                normalLengthSquared = normalX * normalX
                        + normalY * normalY + normalZ * normalZ;
            }
            float inverseNormalLength = normalLengthSquared > 0.00000001f
                    ? Mth.invSqrt(normalLengthSquared)
                    : 1.0f;
            normalX *= inverseNormalLength;
            normalY *= inverseNormalLength;
            normalZ *= inverseNormalLength;

            int vertex = positions.size() / 3;
            positions.add(positionX);
            positions.add(positionY);
            positions.add(positionZ);
            normals.add(normalX);
            normals.add(normalY);
            normals.add(normalZ);
            float thicknessProbeDistance = input.voxelSize() * 0.80f;
            localThickness.add(input.sampleThicknessLocal(
                    positionX - normalX * thicknessProbeDistance,
                    positionY - normalY * thicknessProbeDistance,
                    positionZ - normalZ * thicknessProbeDistance));
            releaseOrder.add(Mth.clamp(Mth.lerp(
                    t,
                    input.sampleReleaseOrder(ax, ay, az),
                    input.sampleReleaseOrder(bx, by, bz)), 0.0f, 1.0f));
            transportOrder.add(Mth.clamp(Mth.lerp(
                    t,
                    input.sampleTransportOrder(ax, ay, az),
                    input.sampleTransportOrder(bx, by, bz)), 0.0f, 1.0f));
            vertexByEdge.put(key, vertex);
            return vertex;
        }

        private void emitTriangle(int a, int b, int c) {
            if (indices.size() / 3 >= MAX_TRIANGLE_COUNT) {
                throw new IllegalStateException(
                        "surface mesh exceeded " + MAX_TRIANGLE_COUNT
                                + " triangles");
            }
            if (a == b || b == c || c == a) {
                rejectedDegenerateTriangles++;
                rejectedDuplicateIndexTriangles++;
                return;
            }
            double areaSquared = triangleAreaSquared(a, b, c);
            double scaleSquared = (double) input.scale() * input.scale();
            double minimumAreaSquared = scaleSquared * scaleSquared * 1.0e-16;
            if (!(areaSquared > minimumAreaSquared)
                    || !Double.isFinite(areaSquared)) {
                rejectedDegenerateTriangles++;
                rejectedLowAreaTriangles++;
                return;
            }

            indices.add(a);
            indices.add(b);
            indices.add(c);
        }

        private double triangleAreaSquared(int a, int b, int c) {
            double ax = positions.get(a * 3);
            double ay = positions.get(a * 3 + 1);
            double az = positions.get(a * 3 + 2);
            double abx = positions.get(b * 3) - ax;
            double aby = positions.get(b * 3 + 1) - ay;
            double abz = positions.get(b * 3 + 2) - az;
            double acx = positions.get(c * 3) - ax;
            double acy = positions.get(c * 3 + 1) - ay;
            double acz = positions.get(c * 3 + 2) - az;
            double crossX = aby * acz - abz * acy;
            double crossY = abz * acx - abx * acz;
            double crossZ = abx * acy - aby * acx;
            return crossX * crossX + crossY * crossY + crossZ * crossZ;
        }

        private static long vertexPairKey(int vertexA, int vertexB) {
            int minimum = Math.min(vertexA, vertexB);
            int maximum = Math.max(vertexA, vertexB);
            return (Integer.toUnsignedLong(minimum) << 32)
                    | Integer.toUnsignedLong(maximum);
        }

        private DarkBallSurfaceMesh finish() {
            throwIfCancellationRequested(cancellationRequested);
            int[] meshIndices = indices.toArray();
            normalizeComponentWinding(meshIndices);
            // Validation needs its own edge table. Drop the extraction weld
            // table first so both large maps are not retained at peak memory.
            vertexByEdge = null;
            Topology topology = validateTopology(
                    positions.size() / 3,
                    meshIndices,
                    cancellationRequested);
            DarkBallSurfaceMesh.Validation validation =
                    new DarkBallSurfaceMesh.Validation(
                            topology.connectedComponents(),
                            topology.boundaryEdges(),
                            topology.nonManifoldEdges(),
                            topology.windingConflicts(),
                            rejectedDegenerateTriangles,
                            zeroAdjustedSamples,
                            collapsedPolygons,
                            rejectedDuplicateIndexTriangles,
                            rejectedLowAreaTriangles);
            return new DarkBallSurfaceMesh(
                    positions.toArray(),
                    normals.toArray(),
                    localThickness.toArray(),
                    releaseOrder.toArray(),
                    transportOrder.toArray(),
                    meshIndices,
                    validation);
        }

        /**
         * Makes every shared manifold edge oppositely directed before choosing
         * an outward sign for each connected triangle component.
         *
         * <p>Orienting triangles independently from their interpolated normals
         * can make neighboring faces disagree in concave or high-curvature
         * regions. Adjacency establishes a consistent combinatorial winding
         * first. The accumulated SDF-normal alignment then decides whether the
         * complete component, rather than an individual face, needs flipping.</p>
         */
        private void normalizeComponentWinding(int[] meshIndices) {
            int triangleCount = meshIndices.length / 3;
            if (triangleCount == 0) {
                return;
            }

            Long2LongOpenHashMap firstUseByEdge =
                    new Long2LongOpenHashMap();
            byte[] neighborCount = new byte[triangleCount];
            int[] neighbors = new int[triangleCount * 3];
            byte[] neighborFlipParity = new byte[triangleCount * 3];

            for (int triangle = 0; triangle < triangleCount; triangle++) {
                if ((triangle & 1023) == 0) {
                    throwIfCancellationRequested(cancellationRequested);
                }
                int offset = triangle * 3;
                addWindingAdjacency(
                        firstUseByEdge,
                        neighborCount,
                        neighbors,
                        neighborFlipParity,
                        triangle,
                        meshIndices[offset],
                        meshIndices[offset + 1]);
                addWindingAdjacency(
                        firstUseByEdge,
                        neighborCount,
                        neighbors,
                        neighborFlipParity,
                        triangle,
                        meshIndices[offset + 1],
                        meshIndices[offset + 2]);
                addWindingAdjacency(
                        firstUseByEdge,
                        neighborCount,
                        neighbors,
                        neighborFlipParity,
                        triangle,
                        meshIndices[offset + 2],
                        meshIndices[offset]);
            }

            byte[] flipState = new byte[triangleCount];
            Arrays.fill(flipState, (byte) -1);
            int[] queue = new int[triangleCount];
            for (int start = 0; start < triangleCount; start++) {
                if (flipState[start] >= 0) {
                    continue;
                }

                int head = 0;
                int tail = 0;
                queue[tail++] = start;
                flipState[start] = 0;
                while (head < tail) {
                    if ((head & 1023) == 0) {
                        throwIfCancellationRequested(cancellationRequested);
                    }
                    int triangle = queue[head++];
                    int count = Byte.toUnsignedInt(
                            neighborCount[triangle]);
                    for (int neighborIndex = 0;
                         neighborIndex < count;
                         neighborIndex++) {
                        int slot = triangle * 3 + neighborIndex;
                        int neighbor = neighbors[slot];
                        byte requiredState = (byte) (
                                flipState[triangle]
                                        ^ neighborFlipParity[slot]);
                        if (flipState[neighbor] < 0) {
                            flipState[neighbor] = requiredState;
                            queue[tail++] = neighbor;
                        }
                    }
                }

                double outwardAlignment = 0.0;
                for (int componentIndex = 0;
                     componentIndex < tail;
                     componentIndex++) {
                    int triangle = queue[componentIndex];
                    outwardAlignment += triangleNormalAlignment(
                            meshIndices,
                            triangle,
                            flipState[triangle] != 0);
                }
                if (outwardAlignment < 0.0) {
                    for (int componentIndex = 0;
                         componentIndex < tail;
                         componentIndex++) {
                        int triangle = queue[componentIndex];
                        flipState[triangle] ^= 1;
                    }
                }
            }

            for (int triangle = 0; triangle < triangleCount; triangle++) {
                if (flipState[triangle] == 0) {
                    continue;
                }
                int offset = triangle * 3;
                int swap = meshIndices[offset + 1];
                meshIndices[offset + 1] = meshIndices[offset + 2];
                meshIndices[offset + 2] = swap;
            }
        }

        private void addWindingAdjacency(
                Long2LongOpenHashMap firstUseByEdge,
                byte[] neighborCount,
                int[] neighbors,
                byte[] neighborFlipParity,
                int triangle,
                int from,
                int to) {
            int minimum = Math.min(from, to);
            int maximum = Math.max(from, to);
            long key = (Integer.toUnsignedLong(minimum) << 32)
                    | Integer.toUnsignedLong(maximum);
            boolean positiveDirection = from == minimum;
            long previous = firstUseByEdge.get(key);
            if (previous == 0L) {
                firstUseByEdge.put(
                        key,
                        encodeFirstWindingUse(
                                triangle,
                                positiveDirection,
                                1));
                return;
            }

            int useCount = (int) (previous >>> 33);
            if (useCount == 1) {
                int firstTriangle = (int) previous - 1;
                boolean firstPositive =
                        (previous & (1L << 32)) != 0L;
                byte flipParity = (byte) (
                        firstPositive == positiveDirection ? 1 : 0);
                addTriangleNeighbor(
                        neighborCount,
                        neighbors,
                        neighborFlipParity,
                        firstTriangle,
                        triangle,
                        flipParity);
                addTriangleNeighbor(
                        neighborCount,
                        neighbors,
                        neighborFlipParity,
                        triangle,
                        firstTriangle,
                        flipParity);
            }
            firstUseByEdge.put(
                    key,
                    encodeFirstWindingUse(
                            (int) previous - 1,
                            (previous & (1L << 32)) != 0L,
                            Math.min(useCount + 1, 0x3fffffff)));
        }

        private static long encodeFirstWindingUse(
                int triangle,
                boolean positiveDirection,
                int useCount) {
            return Integer.toUnsignedLong(triangle + 1)
                    | (positiveDirection ? 1L << 32 : 0L)
                    | ((long) useCount << 33);
        }

        private static void addTriangleNeighbor(
                byte[] neighborCount,
                int[] neighbors,
                byte[] neighborFlipParity,
                int triangle,
                int neighbor,
                byte flipParity) {
            int count = Byte.toUnsignedInt(neighborCount[triangle]);
            if (count >= 3) {
                return;
            }
            int slot = triangle * 3 + count;
            neighbors[slot] = neighbor;
            neighborFlipParity[slot] = flipParity;
            neighborCount[triangle] = (byte) (count + 1);
        }

        private double triangleNormalAlignment(
                int[] meshIndices,
                int triangle,
                boolean flip) {
            int offset = triangle * 3;
            int a = meshIndices[offset];
            int b = meshIndices[offset + (flip ? 2 : 1)];
            int c = meshIndices[offset + (flip ? 1 : 2)];
            float ax = positions.get(a * 3);
            float ay = positions.get(a * 3 + 1);
            float az = positions.get(a * 3 + 2);
            float abx = positions.get(b * 3) - ax;
            float aby = positions.get(b * 3 + 1) - ay;
            float abz = positions.get(b * 3 + 2) - az;
            float acx = positions.get(c * 3) - ax;
            float acy = positions.get(c * 3 + 1) - ay;
            float acz = positions.get(c * 3 + 2) - az;
            float crossX = aby * acz - abz * acy;
            float crossY = abz * acx - abx * acz;
            float crossZ = abx * acy - aby * acx;
            float outwardX = normals.get(a * 3)
                    + normals.get(b * 3)
                    + normals.get(c * 3);
            float outwardY = normals.get(a * 3 + 1)
                    + normals.get(b * 3 + 1)
                    + normals.get(c * 3 + 1);
            float outwardZ = normals.get(a * 3 + 2)
                    + normals.get(b * 3 + 2)
                    + normals.get(c * 3 + 2);
            return (double) crossX * outwardX
                    + (double) crossY * outwardY
                    + (double) crossZ * outwardZ;
        }

        private static long edgeVertexKey(int nodeA, int nodeB) {
            int minimum = Math.min(nodeA, nodeB);
            int maximum = Math.max(nodeA, nodeB);
            return (Integer.toUnsignedLong(minimum) << 32)
                    | Integer.toUnsignedLong(maximum);
        }
    }

    private static Topology validateTopology(
            int vertexCount,
            int[] indices,
            BooleanSupplier cancellationRequested) {
        Long2LongOpenHashMap edges = new Long2LongOpenHashMap();
        DisjointSet components = new DisjointSet(vertexCount);
        boolean[] usedVertices = new boolean[vertexCount];
        int triangleCount = indices.length / 3;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            if ((triangle & 1023) == 0) {
                throwIfCancellationRequested(cancellationRequested);
            }
            int offset = triangle * 3;
            int a = indices[offset];
            int b = indices[offset + 1];
            int c = indices[offset + 2];
            usedVertices[a] = true;
            usedVertices[b] = true;
            usedVertices[c] = true;
            components.union(a, b);
            components.union(b, c);
            addEdge(edges, a, b);
            addEdge(edges, b, c);
            addEdge(edges, c, a);
        }

        int boundaryEdges = 0;
        int nonManifoldEdges = 0;
        int windingConflicts = 0;
        int inspectedEdges = 0;
        for (Long2LongMap.Entry entry : edges.long2LongEntrySet()) {
            if ((inspectedEdges++ & 4095) == 0) {
                throwIfCancellationRequested(cancellationRequested);
            }
            long use = entry.getLongValue();
            int count = (int) (use >>> 32);
            int orientationBalance = (int) use;
            if (count == 1) {
                boundaryEdges++;
            } else if (count != 2) {
                nonManifoldEdges++;
            } else if (orientationBalance != 0) {
                windingConflicts++;
            }
        }

        int connectedComponents = 0;
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if ((vertex & 4095) == 0) {
                throwIfCancellationRequested(cancellationRequested);
            }
            if (usedVertices[vertex]
                    && components.find(vertex) == vertex) {
                connectedComponents++;
            }
        }
        return new Topology(
                connectedComponents,
                boundaryEdges,
                nonManifoldEdges,
                windingConflicts);
    }

    private static void addEdge(
            Long2LongOpenHashMap edges,
            int from,
            int to) {
        int minimum = Math.min(from, to);
        int maximum = Math.max(from, to);
        long key = (Integer.toUnsignedLong(minimum) << 32)
                | Integer.toUnsignedLong(maximum);
        long use = edges.get(key);
        int count = (int) (use >>> 32);
        int orientationBalance = (int) use;
        count++;
        orientationBalance += from == minimum ? 1 : -1;
        edges.put(key, ((long) count << 32)
                | Integer.toUnsignedLong(orientationBalance));
    }

    private static void throwIfCancellationRequested(
            BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException(
                    "Dark Ball surface mesh extraction canceled");
        }
    }

    private record Topology(
            int connectedComponents,
            int boundaryEdges,
            int nonManifoldEdges,
            int windingConflicts
    ) {
    }

    private static final class DisjointSet {
        private final int[] parent;
        private final byte[] rank;

        private DisjointSet(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            int root = value;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[value] != value) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }

        private void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) {
                return;
            }
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
        }
    }

    private static final class FloatList {
        private float[] values = new float[1024];
        private int size;

        private void add(float value) {
            ensureCapacity(size + 1);
            values[size++] = value;
        }

        private float get(int index) {
            return values[index];
        }

        private int size() {
            return size;
        }

        private float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }

        private void ensureCapacity(int requested) {
            if (requested <= values.length) {
                return;
            }
            int next = Math.max(requested, values.length * 2);
            float[] expanded = new float[next];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }
    }

    private static final class IntList {
        private int[] values = new int[1024];
        private int size;

        private void add(int value) {
            ensureCapacity(size + 1);
            values[size++] = value;
        }

        private int size() {
            return size;
        }

        private int[] toArray() {
            int[] result = new int[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }

        private void ensureCapacity(int requested) {
            if (requested <= values.length) {
                return;
            }
            int next = Math.max(requested, values.length * 2);
            int[] expanded = new int[next];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }
    }
}
