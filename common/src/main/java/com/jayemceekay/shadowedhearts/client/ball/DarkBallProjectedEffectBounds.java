package com.jayemceekay.shadowedhearts.client.ball;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Conservative screen-space bounds for the direct Dark Ball volume.
 *
 * <p>The volume shader renders an oriented body box and sixteen square siphon
 * prisms. This helper mirrors those coarse geometric envelopes on the CPU and
 * projects them before the expensive fragment shader is submitted. It never
 * attempts a tight bound through the near plane: an ambiguous projection
 * deliberately falls back to the full viewport so the optimization cannot
 * clip the effect.
 */
final class DarkBallProjectedEffectBounds {
    static final int PRESENTATION_PRISM_COUNT = 16;
    static final int DIRECT_PADDING_PIXELS = 1;
    static final int EDGE_PADDING_PIXELS = 2;
    static final int COMPOSITE_PADDING_PIXELS = 4;

    private static final float CLIP_W_EPSILON = 1.0e-5f;
    private static final float SEGMENT_EPSILON = 1.0e-6f;
    private static final float NORMALIZE_LENGTH_SQUARED_EPSILON =
            1.0e-7f;
    private static final float SQRT_TWO = 1.41421356f;

    private DarkBallProjectedEffectBounds() {
    }

    static Projection begin(Matrix4f projection,
                            Quaternionf cameraRotation,
                            Vector3d cameraPosition) {
        return new Projection(projection, cameraRotation, cameraPosition);
    }

    /**
     * Immutable OpenGL-style normalized viewport bounds. V grows from the
     * bottom of the viewport, matching both POSITION_TEX quads and the current
     * Dark Ball shaders.
     */
    record UvBounds(float minU, float minV, float maxU, float maxV) {
        private static final UvBounds FULL_SCREEN =
                new UvBounds(0.0f, 0.0f, 1.0f, 1.0f);

        static UvBounds fullScreen() {
            return FULL_SCREEN;
        }

        static UvBounds conservative(float minimumU, float minimumV,
                                     float maximumU, float maximumV) {
            if (!Float.isFinite(minimumU) || !Float.isFinite(minimumV)
                    || !Float.isFinite(maximumU)
                    || !Float.isFinite(maximumV)
                    || maximumU <= minimumU
                    || maximumV <= minimumV) {
                return FULL_SCREEN;
            }
            UvBounds clamped = new UvBounds(
                    clamp01(minimumU), clamp01(minimumV),
                    clamp01(maximumU), clamp01(maximumV));
            if (clamped.maxU <= clamped.minU
                    || clamped.maxV <= clamped.minV) {
                return FULL_SCREEN;
            }
            return clamped;
        }

        UvBounds union(UvBounds other) {
            if (other == null || isFullScreen() || other.isFullScreen()) {
                return FULL_SCREEN;
            }
            return new UvBounds(
                    Math.min(minU, other.minU),
                    Math.min(minV, other.minV),
                    Math.max(maxU, other.maxU),
                    Math.max(maxV, other.maxV));
        }

        UvBounds expandByPixels(int pixels, int viewportWidth,
                                int viewportHeight) {
            if (isFullScreen() || pixels <= 0) {
                return this;
            }
            float paddingU = pixels / (float) Math.max(viewportWidth, 1);
            float paddingV = pixels / (float) Math.max(viewportHeight, 1);
            return new UvBounds(
                    clamp01(minU - paddingU),
                    clamp01(minV - paddingV),
                    clamp01(maxU + paddingU),
                    clamp01(maxV + paddingV));
        }

        float inscribedRadiusPixels(int viewportWidth, int viewportHeight) {
            float widthPixels = (maxU - minU)
                    * Math.max(viewportWidth, 0);
            float heightPixels = (maxV - minV)
                    * Math.max(viewportHeight, 0);
            return Math.max(Math.min(widthPixels, heightPixels) * 0.5f,
                    0.0f);
        }

        private boolean isFullScreen() {
            return minU <= 0.0f && minV <= 0.0f
                    && maxU >= 1.0f && maxV >= 1.0f;
        }
    }

    static final class Projection {
        private final Matrix4f projection;
        private final Quaternionf worldToView;
        private final Vector3d cameraPosition;
        private final Vector3f shaderProjectionCorrectionView;
        private final float[] clipCornerComponents = new float[8 * 4];
        private final Vector3f viewScratch = new Vector3f();
        private final Vector4f clipScratch = new Vector4f();

        private float minimumU = 1.0f;
        private float minimumV = 1.0f;
        private float maximumU = 0.0f;
        private float maximumV = 0.0f;
        private boolean hasVisibleBounds;
        private boolean forceFullScreen;

        private Projection(Matrix4f projection,
                           Quaternionf cameraRotation,
                           Vector3d cameraPosition) {
            this.projection = projection == null
                    ? new Matrix4f()
                    : new Matrix4f(projection);
            Quaternionf cameraToWorld = cameraRotation == null
                    ? new Quaternionf()
                    : new Quaternionf(cameraRotation);
            this.worldToView =
                    new Quaternionf(cameraToWorld).conjugate();
            this.cameraPosition = cameraPosition == null
                    ? new Vector3d()
                    : new Vector3d(cameraPosition);
            this.shaderProjectionCorrectionView =
                    shaderProjectionCorrectionView(
                            this.projection,
                            cameraToWorld,
                            this.worldToView,
                            this.cameraPosition);
            if (!isFinite(this.cameraPosition)
                    || !isFinite(this.worldToView)
                    || !isFinite(this.shaderProjectionCorrectionView)) {
                forceFullScreen = true;
            }
        }

        /**
         * Includes an axis-aligned local box transformed by the volume's
         * orthonormal world basis.
         */
        void includeOrientedBox(Vector3d worldRoot,
                                Vector3d worldAxis,
                                Vector3d worldSide,
                                Vector3d worldUp,
                                float minimumX,
                                float minimumY,
                                float minimumZ,
                                float maximumX,
                                float maximumY,
                                float maximumZ) {
            if (forceFullScreen) {
                return;
            }
            if (!isFinite(worldRoot) || !isFinite(worldAxis)
                    || !isFinite(worldSide) || !isFinite(worldUp)
                    || !Float.isFinite(minimumX)
                    || !Float.isFinite(minimumY)
                    || !Float.isFinite(minimumZ)
                    || !Float.isFinite(maximumX)
                    || !Float.isFinite(maximumY)
                    || !Float.isFinite(maximumZ)
                    || maximumX < minimumX
                    || maximumY < minimumY
                    || maximumZ < minimumZ) {
                forceFullScreen = true;
                return;
            }

            // The captured model is positioned from double-precision
            // camera-relative coordinates, while the direct volume shader
            // receives absolute world root/camera values as float uniforms.
            // At large Minecraft coordinates those two projections can differ
            // by multiple pixels (or blocks). Bound both representations so
            // neither the exact mask nor the shader's quantized ray volume can
            // escape the submitted quad.
            includeOrientedBoxProjection(
                    worldRoot, worldAxis, worldSide, worldUp,
                    minimumX, minimumY, minimumZ,
                    maximumX, maximumY, maximumZ,
                    false);
            if (!forceFullScreen) {
                includeOrientedBoxProjection(
                        worldRoot, worldAxis, worldSide, worldUp,
                        minimumX, minimumY, minimumZ,
                        maximumX, maximumY, maximumZ,
                        true);
            }
        }

        private void includeOrientedBoxProjection(
                Vector3d worldRoot,
                Vector3d worldAxis,
                Vector3d worldSide,
                Vector3d worldUp,
                float minimumX,
                float minimumY,
                float minimumZ,
                float maximumX,
                float maximumY,
                float maximumZ,
                boolean quantizeLikeShaderUniforms) {
            int nearPlaneInsideCount = 0;
            for (int corner = 0; corner < 8; corner++) {
                float localX = (corner & 1) == 0 ? minimumX : maximumX;
                float localY = (corner & 2) == 0 ? minimumY : maximumY;
                float localZ = (corner & 4) == 0 ? minimumZ : maximumZ;
                if (quantizeLikeShaderUniforms) {
                    float relativeRootX = (float) worldRoot.x
                            - (float) cameraPosition.x;
                    float relativeRootY = (float) worldRoot.y
                            - (float) cameraPosition.y;
                    float relativeRootZ = (float) worldRoot.z
                            - (float) cameraPosition.z;
                    // The shader subtracts its absolute float uniforms first
                    // and performs the rest of the raymarch in local space.
                    // Add local offsets only after that quantized subtraction;
                    // adding them to a 30M absolute coordinate would lose the
                    // sub-block extent a second time.
                    viewScratch.set(
                            relativeRootX
                                    + (float) worldAxis.x * localX
                            + (float) worldSide.x * localY
                            + (float) worldUp.x * localZ,
                            relativeRootY
                                    + (float) worldAxis.y * localX
                            + (float) worldSide.y * localY
                            + (float) worldUp.y * localZ,
                            relativeRootZ
                                    + (float) worldAxis.z * localX
                            + (float) worldSide.z * localY
                            + (float) worldUp.z * localZ);
                } else {
                    double worldX = worldRoot.x
                            + worldAxis.x * localX
                            + worldSide.x * localY
                            + worldUp.x * localZ;
                    double worldY = worldRoot.y
                            + worldAxis.y * localX
                            + worldSide.y * localY
                            + worldUp.y * localZ;
                    double worldZ = worldRoot.z
                            + worldAxis.z * localX
                            + worldSide.z * localY
                            + worldUp.z * localZ;
                    // Subtract in double precision before converting to the
                    // camera-relative float coordinates used by model draws.
                    viewScratch.set(
                            (float) (worldX - cameraPosition.x),
                            (float) (worldY - cameraPosition.y),
                            (float) (worldZ - cameraPosition.z));
                }
                viewScratch.rotate(worldToView);
                if (quantizeLikeShaderUniforms) {
                    viewScratch.add(shaderProjectionCorrectionView);
                }
                clipScratch.set(
                        viewScratch.x, viewScratch.y, viewScratch.z, 1.0f);
                projection.transform(clipScratch);
                if (!isFinite(clipScratch)) {
                    forceFullScreen = true;
                    return;
                }
                int component = corner * 4;
                clipCornerComponents[component] = clipScratch.x;
                clipCornerComponents[component + 1] = clipScratch.y;
                clipCornerComponents[component + 2] = clipScratch.z;
                clipCornerComponents[component + 3] = clipScratch.w;
                if (clipScratch.z + clipScratch.w >= 0.0f) {
                    nearPlaneInsideCount++;
                }
            }

            // A convex box entirely behind the near plane contributes no
            // pixels. A crossing box can cover an arbitrarily large portion of
            // the viewport, so prefer a safe full-screen fallback.
            if (nearPlaneInsideCount == 0) {
                return;
            }
            if (nearPlaneInsideCount != 8) {
                forceFullScreen = true;
                return;
            }

            float primitiveMinimumU = Float.POSITIVE_INFINITY;
            float primitiveMinimumV = Float.POSITIVE_INFINITY;
            float primitiveMaximumU = Float.NEGATIVE_INFINITY;
            float primitiveMaximumV = Float.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                int component = corner * 4;
                float clipX = clipCornerComponents[component];
                float clipY = clipCornerComponents[component + 1];
                float clipW = clipCornerComponents[component + 3];
                if (clipW <= CLIP_W_EPSILON) {
                    forceFullScreen = true;
                    return;
                }
                float u = clipX / clipW * 0.5f + 0.5f;
                float v = clipY / clipW * 0.5f + 0.5f;
                if (!Float.isFinite(u) || !Float.isFinite(v)) {
                    forceFullScreen = true;
                    return;
                }
                primitiveMinimumU = Math.min(primitiveMinimumU, u);
                primitiveMinimumV = Math.min(primitiveMinimumV, v);
                primitiveMaximumU = Math.max(primitiveMaximumU, u);
                primitiveMaximumV = Math.max(primitiveMaximumV, v);
            }

            if (primitiveMaximumU <= 0.0f || primitiveMinimumU >= 1.0f
                    || primitiveMaximumV <= 0.0f
                    || primitiveMinimumV >= 1.0f) {
                return;
            }

            primitiveMinimumU = clamp01(primitiveMinimumU);
            primitiveMinimumV = clamp01(primitiveMinimumV);
            primitiveMaximumU = clamp01(primitiveMaximumU);
            primitiveMaximumV = clamp01(primitiveMaximumV);
            if (primitiveMaximumU <= primitiveMinimumU
                    || primitiveMaximumV <= primitiveMinimumV) {
                forceFullScreen = true;
                return;
            }

            minimumU = Math.min(minimumU, primitiveMinimumU);
            minimumV = Math.min(minimumV, primitiveMinimumV);
            maximumU = Math.max(maximumU, primitiveMaximumU);
            maximumV = Math.max(maximumV, primitiveMaximumV);
            hasVisibleBounds = true;
        }

        /**
         * The shader recovers an affine projection's pinhole in view space,
         * rotates it to world space, then adds it to a float CameraPos. At very
         * large coordinates that addition may discard some or all of the eye
         * offset. This correction projects the resulting shader ray volume,
         * not an idealized double-precision pinhole.
         */
        private static Vector3f shaderProjectionCorrectionView(
                Matrix4f projection,
                Quaternionf cameraToWorld,
                Quaternionf worldToView,
                Vector3d cameraPosition) {
            Matrix4f inverseProjection = new Matrix4f(projection).invert();
            Vector4f homogeneousEye = inverseProjection.transform(
                    new Vector4f(0.0f, 0.0f, 1.0f, 0.0f));
            Vector3f eyeView = Math.abs(homogeneousEye.w) > 1.0e-6f
                    ? new Vector3f(
                    homogeneousEye.x / homogeneousEye.w,
                    homogeneousEye.y / homogeneousEye.w,
                    homogeneousEye.z / homogeneousEye.w)
                    : new Vector3f();
            if (!isFinite(eyeView)) {
                return new Vector3f(
                        Float.NaN, Float.NaN, Float.NaN);
            }

            Vector3f eyeWorld = new Vector3f(eyeView)
                    .rotate(cameraToWorld);
            float cameraX = (float) cameraPosition.x;
            float cameraY = (float) cameraPosition.y;
            float cameraZ = (float) cameraPosition.z;
            Vector3f retainedEyeWorld = new Vector3f(
                    (cameraX + eyeWorld.x) - cameraX,
                    (cameraY + eyeWorld.y) - cameraY,
                    (cameraZ + eyeWorld.z) - cameraZ);
            retainedEyeWorld.rotate(worldToView);
            return eyeView.sub(retainedEyeWorld);
        }

        /**
         * Includes the shader's sixteen node-aware Hermite presentation
         * prisms. Each square cross-section is enclosed by a radius
         * {@code sqrt(2) * halfWidth}, so this remains conservative without
         * reproducing the shader's transported side/up frame.
         */
        void includeSiphonPrisms(Vector3d worldRoot,
                                 Vector3d worldAxis,
                                 Vector3d worldSide,
                                 Vector3d worldUp,
                                 Vector3f[] nodes,
                                 float bodyRadius,
                                 float voxelSize,
                                 float siphonEndRadius) {
            if (forceFullScreen) {
                return;
            }
            if (!validNodes(nodes)
                    || !Float.isFinite(bodyRadius)
                    || !Float.isFinite(voxelSize)
                    || !Float.isFinite(siphonEndRadius)) {
                forceFullScreen = true;
                return;
            }

            Vector3f pathFallback = new Vector3f(
                    nodes[nodes.length - 1]).sub(nodes[0]);
            Vector3f previousAxis = normalizeOr(
                    siphonPathNodeTangent(nodes, 0), pathFallback);
            float previousLength = 0.0f;
            float previousHalfWidth = siphonRadius(
                    0.0f, bodyRadius, voxelSize, siphonEndRadius);
            boolean hasPreviousPrism = false;
            Vector3f prismStart = siphonPathPoint(nodes, 0.0f);

            for (int prismIndex = 0;
                 prismIndex < PRESENTATION_PRISM_COUNT;
                 prismIndex++) {
                float startT = siphonPresentationCurveT(prismIndex);
                float endT = siphonPresentationCurveT(prismIndex + 1);
                Vector3f prismEnd = siphonPathPoint(nodes, endT);
                Vector3f segment = new Vector3f(prismEnd).sub(prismStart);
                float prismLength = segment.length();
                Vector3f prismAxis = normalizeOr(segment, previousAxis);
                float prismHalfWidth = Math.max(
                        siphonRadius(startT, bodyRadius, voxelSize,
                                siphonEndRadius),
                        siphonRadius(endT, bodyRadius, voxelSize,
                                siphonEndRadius));

                float startExtension = 0.0f;
                if (hasPreviousPrism && prismLength > SEGMENT_EPSILON) {
                    float turnCosine = clamp(
                            previousAxis.dot(prismAxis), -0.995f, 1.0f);
                    float turnTangent = (float) Math.sqrt(Math.max(
                            (1.0f - turnCosine)
                                    / (1.0f + turnCosine),
                            0.0f));
                    float requestedMiter = voxelSize * 0.12f
                            + SQRT_TWO
                            * Math.max(previousHalfWidth, prismHalfWidth)
                            * turnTangent;
                    float overlapCap = 0.45f
                            * Math.min(previousLength, prismLength);
                    startExtension = Math.min(
                            requestedMiter, Math.max(overlapCap, 0.0f));
                }

                if (prismLength > SEGMENT_EPSILON) {
                    Vector3f extendedStart = new Vector3f(prismStart)
                            .fma(-startExtension, prismAxis);
                    float envelope = SQRT_TWO
                            * Math.max(prismHalfWidth, 0.0f);
                    includeOrientedBox(
                            worldRoot, worldAxis, worldSide, worldUp,
                            Math.min(extendedStart.x, prismEnd.x) - envelope,
                            Math.min(extendedStart.y, prismEnd.y) - envelope,
                            Math.min(extendedStart.z, prismEnd.z) - envelope,
                            Math.max(extendedStart.x, prismEnd.x) + envelope,
                            Math.max(extendedStart.y, prismEnd.y) + envelope,
                            Math.max(extendedStart.z, prismEnd.z) + envelope);

                    hasPreviousPrism = true;
                    previousAxis = prismAxis;
                    previousLength = prismLength;
                    previousHalfWidth = prismHalfWidth;
                }
                prismStart = prismEnd;
            }
        }

        UvBounds finish(int viewportWidth, int viewportHeight,
                        int paddingPixels) {
            if (forceFullScreen || !hasVisibleBounds) {
                return UvBounds.fullScreen();
            }
            return new UvBounds(
                    minimumU, minimumV, maximumU, maximumV)
                    .expandByPixels(
                            paddingPixels, viewportWidth, viewportHeight);
        }
    }

    static float siphonPresentationCurveT(int boundaryIndex) {
        int segmentCount = DarkBallSiphonBoltPath.NODE_COUNT - 1;
        if (boundaryIndex <= 0) {
            return 0.0f;
        }
        if (boundaryIndex >= PRESENTATION_PRISM_COUNT) {
            return 1.0f;
        }
        if (boundaryIndex <= 3) {
            return boundaryIndex / (float) segmentCount;
        }
        int offsetIndex = boundaryIndex - 4;
        int block = offsetIndex / 4;
        int remainder = offsetIndex - block * 4;
        float pathUnits = 3 + block * 3
                + (remainder == 0 ? 0.5f : remainder);
        return pathUnits / segmentCount;
    }

    static Vector3f siphonPathPoint(Vector3f[] nodes, float curveT) {
        int segmentCount = DarkBallSiphonBoltPath.NODE_COUNT - 1;
        float scaled = clamp01(curveT) * segmentCount;
        int segmentIndex = Math.max(0, Math.min(
                (int) Math.floor(scaled), segmentCount - 1));
        float segmentT = clamp01(scaled - segmentIndex);
        Vector3f start = nodes[segmentIndex];
        Vector3f end = nodes[segmentIndex + 1];
        Vector3f startTangent =
                siphonPathNodeTangent(nodes, segmentIndex);
        Vector3f endTangent =
                siphonPathNodeTangent(nodes, segmentIndex + 1);

        float t2 = segmentT * segmentT;
        float t3 = t2 * segmentT;
        return new Vector3f(start).mul(
                        2.0f * t3 - 3.0f * t2 + 1.0f)
                .fma(t3 - 2.0f * t2 + segmentT, startTangent)
                .fma(-2.0f * t3 + 3.0f * t2, end)
                .fma(t3 - t2, endTangent);
    }

    static Vector3f siphonPathNodeTangent(Vector3f[] nodes, int index) {
        int lastIndex = DarkBallSiphonBoltPath.NODE_COUNT - 1;
        if (index <= 0) {
            return new Vector3f(nodes[1]).sub(nodes[0]).mul(0.68f);
        }
        if (index >= lastIndex) {
            return new Vector3f(nodes[lastIndex])
                    .sub(nodes[lastIndex - 1]).mul(0.68f);
        }

        Vector3f previousLeg =
                new Vector3f(nodes[index]).sub(nodes[index - 1]);
        Vector3f nextLeg =
                new Vector3f(nodes[index + 1]).sub(nodes[index]);
        float previousLength = previousLeg.length();
        float nextLength = nextLeg.length();
        Vector3f previousDirection = normalizeOr(previousLeg, nextLeg);
        Vector3f nextDirection = normalizeOr(nextLeg, previousLeg);
        Vector3f tangentDirection = normalizeOr(
                new Vector3f(previousDirection).add(nextDirection),
                nextDirection);
        float alignment = clamp(
                previousDirection.dot(nextDirection), -1.0f, 1.0f);
        float cornerRetention = mix(
                0.16f, 0.58f, smoothstep(0.94f, 0.995f, alignment));
        return tangentDirection.mul(
                Math.min(previousLength, nextLength) * cornerRetention);
    }

    static float siphonRadius(float curveT,
                              float bodyRadius,
                              float voxelSize,
                              float siphonEndRadius) {
        float u = clamp01(curveT);
        float rootRadius = Math.max(bodyRadius * 0.055f,
                voxelSize * 0.90f);
        float shoulderRadius = Math.max(bodyRadius * 0.070f,
                voxelSize * 1.10f);
        float endRadius = Math.max(siphonEndRadius * 0.72f,
                voxelSize * 0.90f);
        float throat = smoothstep(0.0f, 0.12f, u);
        float downstream = smoothstep(0.10f, 1.0f, u);
        return mix(mix(rootRadius, shoulderRadius, throat),
                endRadius, downstream);
    }

    private static Vector3f normalizeOr(Vector3f source,
                                        Vector3f fallback) {
        Vector3f result = new Vector3f(source);
        if (result.lengthSquared()
                <= NORMALIZE_LENGTH_SQUARED_EPSILON) {
            result.set(fallback);
        }
        if (result.lengthSquared()
                <= NORMALIZE_LENGTH_SQUARED_EPSILON) {
            return result.set(1.0f, 0.0f, 0.0f);
        }
        return result.normalize();
    }

    private static boolean validNodes(Vector3f[] nodes) {
        if (nodes == null
                || nodes.length < DarkBallSiphonBoltPath.NODE_COUNT) {
            return false;
        }
        for (int index = 0;
             index < DarkBallSiphonBoltPath.NODE_COUNT;
             index++) {
            if (!isFinite(nodes[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFinite(Vector3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static boolean isFinite(Vector3f vector) {
        return vector != null
                && Float.isFinite(vector.x)
                && Float.isFinite(vector.y)
                && Float.isFinite(vector.z);
    }

    private static boolean isFinite(Vector4f vector) {
        return Float.isFinite(vector.x)
                && Float.isFinite(vector.y)
                && Float.isFinite(vector.z)
                && Float.isFinite(vector.w);
    }

    private static boolean isFinite(Quaternionf quaternion) {
        return Float.isFinite(quaternion.x)
                && Float.isFinite(quaternion.y)
                && Float.isFinite(quaternion.z)
                && Float.isFinite(quaternion.w);
    }

    private static float smoothstep(float edge0, float edge1,
                                    float value) {
        float t = clamp01((value - edge0)
                / Math.max(edge1 - edge0, 1.0e-6f));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float mix(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
