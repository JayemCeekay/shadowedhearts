package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Vector3f;

/**
 * Small CPU-side indexed tube generated from the Dark Ball siphon's local
 * plasma-bolt nodes.
 *
 * <p>The mesh deliberately keeps a closed, fixed topology while the path
 * moves. Leading and trailing reveal are carried in {@link #materialWindow()}
 * instead of collapsing rings to zero radius. A later raster upload can
 * therefore reject or fade hidden fragments without introducing degenerate
 * triangles or changing the index buffer.</p>
 */
final class DarkBallSiphonSurfaceMesh {
    static final int SIDES = 8;
    static final int RING_COUNT = DarkBallSiphonBoltPath.NODE_COUNT;

    private static final float SHOULDER_RADIUS_SCALE = 0.070f / 0.055f;
    private static final float LENGTH_EPSILON_SQUARED = 1.0e-12f;
    private static final float FRAME_EPSILON_SQUARED = 1.0e-10f;

    private final float[] positions;
    private final float[] normals;
    private final float[] curveT;
    private final float[] localRadius;
    private final float[] materialWindow;
    private final int[] indices;

    private DarkBallSiphonSurfaceMesh(float[] positions,
                                      float[] normals,
                                      float[] curveT,
                                      float[] localRadius,
                                      float[] materialWindow,
                                      int[] indices) {
        this.positions = positions;
        this.normals = normals;
        this.curveT = curveT;
        this.localRadius = localRadius;
        this.materialWindow = materialWindow;
        this.indices = indices;
    }

    /**
     * Builds the complete tube without a leading or trailing material clip.
     */
    static DarkBallSiphonSurfaceMesh build(Vector3f[] localBoltNodes,
                                           float rootRadius,
                                           float endRadius) {
        return build(
                localBoltNodes,
                rootRadius,
                rootRadius * SHOULDER_RADIUS_SCALE,
                endRadius,
                1.0f,
                null);
    }

    /**
     * Builds the complete tube and evaluates its current material window.
     *
     * @param localBoltNodes exactly the existing fourteen local siphon nodes
     * @param rootRadius positive tube radius at the body-side inlet
     * @param endRadius positive tube radius at the Dark Ball endpoint
     * @param siphonProgress current normalized leading-fill progress
     * @param trailingGateByNode optional normalized trailing gate sampled at
     *                           each bolt node; {@code null} keeps every node
     *                           behind the leading edge
     */
    static DarkBallSiphonSurfaceMesh build(Vector3f[] localBoltNodes,
                                           float rootRadius,
                                           float endRadius,
                                           float siphonProgress,
                                           float[] trailingGateByNode) {
        return build(
                localBoltNodes,
                rootRadius,
                rootRadius * SHOULDER_RADIUS_SCALE,
                endRadius,
                siphonProgress,
                trailingGateByNode);
    }

    /**
     * Builds the complete tube with independently floored inlet, shoulder, and
     * endpoint radii. Keeping the shoulder explicit makes the indexed tube
     * match the procedural presentation and projected bounds even when voxel
     * floors dominate a small or strongly contracted inlet.
     */
    static DarkBallSiphonSurfaceMesh build(Vector3f[] localBoltNodes,
                                           float rootRadius,
                                           float shoulderRadius,
                                           float endRadius,
                                           float siphonProgress,
                                           float[] trailingGateByNode) {
        validateNodes(localBoltNodes);
        requirePositiveFinite(rootRadius, "rootRadius");
        requirePositiveFinite(shoulderRadius, "shoulderRadius");
        requirePositiveFinite(endRadius, "endRadius");
        if (trailingGateByNode != null
                && trailingGateByNode.length != RING_COUNT) {
            throw new IllegalArgumentException(
                    "trailingGateByNode must contain one value per bolt node");
        }

        Vector3f[] tangents = buildTangents(localBoltNodes);
        Vector3f[] sides = new Vector3f[RING_COUNT];
        Vector3f[] ups = new Vector3f[RING_COUNT];
        buildRotationMinimizingFrames(tangents, sides, ups);

        int sideVertexCount = RING_COUNT * SIDES;
        int vertexCount = sideVertexCount + 2;
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] curveT = new float[vertexCount];
        float[] localRadius = new float[vertexCount];
        float[] materialWindow = new float[vertexCount];

        float safeProgress = clampUnitFinite(siphonProgress);
        for (int ring = 0; ring < RING_COUNT; ring++) {
            float ringT = ring / (RING_COUNT - 1.0f);
            float radius = radiusAt(
                    ringT, rootRadius, shoulderRadius, endRadius);
            float trailingGate = trailingGateByNode == null
                    ? 1.0f
                    : clampUnitFinite(trailingGateByNode[ring]);
            float window = leadingGate(safeProgress, ringT) * trailingGate;
            for (int side = 0; side < SIDES; side++) {
                float angle = (float) (side * Math.PI * 2.0 / SIDES);
                float cosine = (float) Math.cos(angle);
                float sine = (float) Math.sin(angle);
                Vector3f radial = new Vector3f(sides[ring]).mul(cosine)
                        .fma(sine, ups[ring]);
                int vertex = ring * SIDES + side;
                writeVector(positions, vertex,
                        new Vector3f(localBoltNodes[ring])
                                .fma(radius, radial));
                writeVector(normals, vertex, radial);
                curveT[vertex] = ringT;
                localRadius[vertex] = radius;
                materialWindow[vertex] = window;
            }
        }

        int rootCapVertex = sideVertexCount;
        int endCapVertex = rootCapVertex + 1;
        writeCapVertex(rootCapVertex, localBoltNodes[0],
                new Vector3f(tangents[0]).negate(),
                0.0f, rootRadius,
                leadingGate(safeProgress, 0.0f)
                        * trailingGateAt(trailingGateByNode, 0),
                positions, normals, curveT, localRadius, materialWindow);
        writeCapVertex(endCapVertex, localBoltNodes[RING_COUNT - 1],
                tangents[RING_COUNT - 1],
                1.0f, endRadius,
                leadingGate(safeProgress, 1.0f)
                        * trailingGateAt(
                        trailingGateByNode, RING_COUNT - 1),
                positions, normals, curveT, localRadius, materialWindow);

        int sideTriangleCount = (RING_COUNT - 1) * SIDES * 2;
        int capTriangleCount = SIDES * 2;
        int[] indices =
                new int[(sideTriangleCount + capTriangleCount) * 3];
        int cursor = 0;
        for (int ring = 0; ring < RING_COUNT - 1; ring++) {
            int currentRing = ring * SIDES;
            int nextRing = currentRing + SIDES;
            for (int side = 0; side < SIDES; side++) {
                int nextSide = (side + 1) % SIDES;
                int current = currentRing + side;
                int currentNext = currentRing + nextSide;
                int next = nextRing + side;
                int nextNext = nextRing + nextSide;

                indices[cursor++] = current;
                indices[cursor++] = currentNext;
                indices[cursor++] = nextNext;
                indices[cursor++] = current;
                indices[cursor++] = nextNext;
                indices[cursor++] = next;
            }
        }

        int endRing = (RING_COUNT - 1) * SIDES;
        for (int side = 0; side < SIDES; side++) {
            int nextSide = (side + 1) % SIDES;
            indices[cursor++] = rootCapVertex;
            indices[cursor++] = nextSide;
            indices[cursor++] = side;

            indices[cursor++] = endCapVertex;
            indices[cursor++] = endRing + side;
            indices[cursor++] = endRing + nextSide;
        }

        return new DarkBallSiphonSurfaceMesh(
                positions,
                normals,
                curveT,
                localRadius,
                materialWindow,
                indices);
    }

    int vertexCount() {
        return positions.length / 3;
    }

    int triangleCount() {
        return indices.length / 3;
    }

    float[] positions() {
        return positions;
    }

    float[] normals() {
        return normals;
    }

    float[] curveT() {
        return curveT;
    }

    float[] localRadius() {
        return localRadius;
    }

    float[] materialWindow() {
        return materialWindow;
    }

    int[] indices() {
        return indices;
    }

    private static Vector3f[] buildTangents(Vector3f[] nodes) {
        Vector3f fallback = firstUsableDirection(nodes);
        Vector3f[] tangents = new Vector3f[RING_COUNT];
        for (int ring = 0; ring < RING_COUNT; ring++) {
            Vector3f incoming = directionToPreviousDistinct(nodes, ring);
            Vector3f outgoing = directionToNextDistinct(nodes, ring);
            Vector3f tangent;
            if (incoming != null && outgoing != null) {
                tangent = new Vector3f(incoming).add(outgoing);
                if (tangent.lengthSquared() < FRAME_EPSILON_SQUARED) {
                    tangent.set(outgoing);
                }
            } else if (outgoing != null) {
                tangent = outgoing;
            } else if (incoming != null) {
                tangent = incoming;
            } else {
                tangent = new Vector3f(fallback);
            }
            tangents[ring] = normalizeOr(tangent, fallback);
        }
        return tangents;
    }

    private static void buildRotationMinimizingFrames(
            Vector3f[] tangents,
            Vector3f[] sides,
            Vector3f[] ups) {
        sides[0] = initialSide(tangents[0]);
        ups[0] = normalizedCross(tangents[0], sides[0],
                initialUp(tangents[0], sides[0]));

        for (int ring = 1; ring < RING_COUNT; ring++) {
            Vector3f previousTangent = tangents[ring - 1];
            Vector3f tangent = tangents[ring];
            float cosine = clampSignedUnit(previousTangent.dot(tangent));
            Vector3f rotationAxis =
                    new Vector3f(previousTangent).cross(tangent);
            float sineSquared = rotationAxis.lengthSquared();

            Vector3f transported = new Vector3f(sides[ring - 1]);
            if (sineSquared > FRAME_EPSILON_SQUARED) {
                float sine = (float) Math.sqrt(sineSquared);
                rotationAxis.mul(1.0f / sine);
                Vector3f axisCrossSide =
                        new Vector3f(rotationAxis).cross(transported);
                float axisProjection = rotationAxis.dot(transported);
                transported.mul(cosine)
                        .fma(sine, axisCrossSide)
                        .fma(axisProjection * (1.0f - cosine),
                                rotationAxis);
            }

            // Projection is also the stable antiparallel fallback. A side
            // perpendicular to T remains perpendicular to -T, avoiding an
            // arbitrary 180-degree phase choice at a hairpin.
            transported.fma(-transported.dot(tangent), tangent);
            if (transported.lengthSquared() < FRAME_EPSILON_SQUARED) {
                transported.set(initialSide(tangent));
            } else {
                transported.normalize();
            }
            sides[ring] = transported;
            ups[ring] = normalizedCross(tangent, transported,
                    initialUp(tangent, transported));
        }
    }

    private static Vector3f firstUsableDirection(Vector3f[] nodes) {
        for (int index = 0; index < nodes.length - 1; index++) {
            Vector3f direction =
                    new Vector3f(nodes[index + 1]).sub(nodes[index]);
            if (direction.lengthSquared() >= LENGTH_EPSILON_SQUARED) {
                return direction.normalize();
            }
        }
        return new Vector3f(1.0f, 0.0f, 0.0f);
    }

    private static Vector3f directionToPreviousDistinct(
            Vector3f[] nodes,
            int ring) {
        for (int previous = ring - 1; previous >= 0; previous--) {
            Vector3f direction =
                    new Vector3f(nodes[ring]).sub(nodes[previous]);
            if (direction.lengthSquared() >= LENGTH_EPSILON_SQUARED) {
                return direction.normalize();
            }
        }
        return null;
    }

    private static Vector3f directionToNextDistinct(Vector3f[] nodes,
                                                     int ring) {
        for (int next = ring + 1; next < nodes.length; next++) {
            Vector3f direction =
                    new Vector3f(nodes[next]).sub(nodes[ring]);
            if (direction.lengthSquared() >= LENGTH_EPSILON_SQUARED) {
                return direction.normalize();
            }
        }
        return null;
    }

    /**
     * Frisvad's branch-light orthonormal basis gives a deterministic initial
     * phase without choosing between nearly tied world-reference axes.
     */
    private static Vector3f initialSide(Vector3f tangent) {
        if (tangent.z < -0.999999f) {
            return new Vector3f(0.0f, -1.0f, 0.0f);
        }
        float inverse = 1.0f / (1.0f + tangent.z);
        return new Vector3f(
                1.0f - tangent.x * tangent.x * inverse,
                -tangent.x * tangent.y * inverse,
                -tangent.x).normalize();
    }

    private static Vector3f initialUp(Vector3f tangent, Vector3f side) {
        Vector3f up = new Vector3f(tangent).cross(side);
        if (up.lengthSquared() < FRAME_EPSILON_SQUARED) {
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return up.normalize();
    }

    private static Vector3f normalizedCross(Vector3f first,
                                             Vector3f second,
                                             Vector3f fallback) {
        Vector3f result = new Vector3f(first).cross(second);
        return normalizeOr(result, fallback);
    }

    private static Vector3f normalizeOr(Vector3f value,
                                         Vector3f fallback) {
        if (value.lengthSquared() < FRAME_EPSILON_SQUARED) {
            return new Vector3f(fallback);
        }
        return value.normalize();
    }

    private static float radiusAt(float curveT,
                                  float rootRadius,
                                  float shoulderRadius,
                                  float endRadius) {
        float throat = smoothstep(0.0f, 0.12f, curveT);
        float downstream = smoothstep(0.10f, 1.0f, curveT);
        return lerp(lerp(rootRadius, shoulderRadius, throat),
                endRadius, downstream);
    }

    private static float leadingGate(float siphonProgress, float curveT) {
        float leadingProgress =
                smoothstep(
                        DarkBallCaptureVfx.SIPHON_LEADING_EASE_START,
                        DarkBallCaptureVfx.SIPHON_LEADING_EASE_END,
                        siphonProgress);
        float leadingFront = lerp(-0.06f, 1.08f, leadingProgress);
        return 1.0f - smoothstep(
                leadingFront - 0.045f,
                leadingFront + 0.045f,
                curveT);
    }

    private static float trailingGateAt(float[] trailingGateByNode,
                                         int node) {
        return trailingGateByNode == null
                ? 1.0f
                : clampUnitFinite(trailingGateByNode[node]);
    }

    private static float smoothstep(float lower, float upper, float value) {
        float amount = clampUnitFinite((value - lower) / (upper - lower));
        return amount * amount * (3.0f - 2.0f * amount);
    }

    private static float lerp(float lower, float upper, float amount) {
        return lower + (upper - lower) * amount;
    }

    private static float clampSignedUnit(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static float clampUnitFinite(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void writeCapVertex(
            int vertex,
            Vector3f position,
            Vector3f normal,
            float vertexCurveT,
            float radius,
            float window,
            float[] positions,
            float[] normals,
            float[] curveT,
            float[] localRadius,
            float[] materialWindow) {
        writeVector(positions, vertex, position);
        writeVector(normals, vertex, normal);
        curveT[vertex] = vertexCurveT;
        localRadius[vertex] = radius;
        materialWindow[vertex] = window;
    }

    private static void writeVector(float[] destination,
                                    int vertex,
                                    Vector3f value) {
        int offset = vertex * 3;
        destination[offset] = value.x;
        destination[offset + 1] = value.y;
        destination[offset + 2] = value.z;
    }

    private static void validateNodes(Vector3f[] nodes) {
        if (nodes == null || nodes.length != RING_COUNT) {
            throw new IllegalArgumentException(
                    "localBoltNodes must contain exactly "
                            + RING_COUNT + " nodes");
        }
        for (int node = 0; node < nodes.length; node++) {
            Vector3f value = nodes[node];
            if (value == null
                    || !Float.isFinite(value.x)
                    || !Float.isFinite(value.y)
                    || !Float.isFinite(value.z)) {
                throw new IllegalArgumentException(
                        "localBoltNodes[" + node + "] must be finite");
            }
        }
    }

    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(
                    name + " must be positive and finite");
        }
    }
}
