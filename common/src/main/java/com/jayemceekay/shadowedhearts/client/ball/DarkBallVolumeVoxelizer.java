package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.CapturedTriangle;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

final class DarkBallVolumeVoxelizer {
    static final float PHYSICAL_MATERIAL_THRESHOLD = 0.018f;

    /** Three source voxels is about 1.5 voxels in the rendered analytical atlas. */
    private static final int COHESIVE_CLOSE_RADIUS_VOXELS = 3;
    private static final float NARROW_ENVELOPE_VOXELS = 1.25f;
    private static final float MINIMUM_SURFACE_COVERAGE = 0.08f;
    private static final float HALF_VOXEL_DIAGONAL = 0.8660254f;
    private static final float HALF_OPEN_VOXEL_BIAS = 0.0001f;

    private DarkBallVolumeVoxelizer() {
    }

    static DarkBallVolumeBuildResult build(Vec3 ballPos, Vec3 pokemonCenter, AABB pokemonBounds,
                                           List<Vec3> capturedModelVertices,
                                           List<CapturedTriangle> capturedModelTriangles,
                                           NativeImage textureImage) {
        Vec3 captureDir = ballPos.subtract(pokemonCenter);
        if (captureDir.lengthSqr() < 1e-6) captureDir = new Vec3(0, 1, 0);
        captureDir = captureDir.normalize();
        Vec3 side = DarkBallCaptureMath.stableSide(captureDir);
        Vec3 up = captureDir.cross(side).normalize();
        DarkBallTextureAlphaClipper.AlphaField textureAlpha =
                captureTextureAlpha(textureImage);

        double maxExtent = Math.max(pokemonBounds.getXsize(),
                Math.max(pokemonBounds.getYsize(), pokemonBounds.getZsize()));
        MeshCaptureBounds meshBounds = capturedMeshBounds(
                capturedModelVertices,
                capturedModelTriangles,
                textureAlpha,
                pokemonCenter,
                captureDir,
                side,
                up,
                maxExtent
        );
        float bodyRadius = Math.max(0.24f, Math.max(meshBounds.maxSideAbs(), meshBounds.maxUpAbs()));

        float axisMargin = Math.max(0.08f, (float) maxExtent * 0.07f);
        float lateralMargin = Math.max(0.06f, (float) maxExtent * 0.045f);
        float rootOffset = meshBounds.minAxis() - axisMargin;
        Vec3 root = pokemonCenter.add(captureDir.scale(rootOffset));
        float ballAxis = (float) ballPos.subtract(pokemonCenter).dot(captureDir);
        float maxAxis = Math.max(meshBounds.maxAxis() + axisMargin, ballAxis + axisMargin);
        float captureLength = Math.max(axisMargin * 2.0f, maxAxis - rootOffset);
        float lateralScale = Math.max(0.001f,
                Math.max(meshBounds.maxSideAbs(), meshBounds.maxUpAbs()) + lateralMargin);

        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        int voxelCount = DarkBallVolumeGrid.X_SLICES * sliceSize * sliceSize;
        float[] visibleSurfaceCoverage = new float[voxelCount];
        boolean[] visibleShell = new boolean[voxelCount];
        boolean[] topologyShell = new boolean[voxelCount];
        float[] topologySurfaceAlpha = new float[voxelCount];
        float[] topologySurfaceDistance = new float[voxelCount];
        Arrays.fill(topologySurfaceDistance, Float.POSITIVE_INFINITY);
        float voxelStepX = captureLength / Math.max(
                1, DarkBallVolumeGrid.X_SLICES - 1);
        float voxelStepYz = lateralScale * 2.0f / Math.max(
                1, sliceSize - 1);

        if (!capturedModelTriangles.isEmpty()) {
            for (CapturedTriangle triangle : capturedModelTriangles) {
                Vector3f a = toVolumeGridPosition(triangle.a(), root, captureDir, side, up, captureLength, lateralScale);
                Vector3f b = toVolumeGridPosition(triangle.b(), root, captureDir, side, up, captureLength, lateralScale);
                Vector3f c = toVolumeGridPosition(triangle.c(), root, captureDir, side, up, captureLength, lateralScale);
                rasterizeTopologyTriangle(topologyShell, topologySurfaceAlpha,
                        topologySurfaceDistance, a, b, c, triangle, textureAlpha,
                        voxelStepX, voxelStepYz);
                rasterizeTexturedTriangle(visibleSurfaceCoverage, visibleShell,
                        a, b, c, triangle, textureAlpha);
            }
        }

        boolean[] physicalMaterial;
        if (hasAnyVoxel(topologyShell)) {
            physicalMaterial = buildTextureExtrudedMaterial(
                    topologyShell,
                    topologySurfaceAlpha,
                    topologySurfaceDistance,
                    visibleShell,
                    voxelStepX,
                    voxelStepYz
            );
        } else {
            // Geometry-only emergency fallback for a malformed or incomplete
            // capture. It is deliberately unreachable for normal textured
            // triangle captures.
            float[] fallbackCore = new float[voxelCount];
            float[] fallbackEnvelope = new float[voxelCount];
            for (Vec3 vertex : capturedModelVertices) {
                Vector3f local = toVolumeLocalPosition(vertex, root, captureDir, side, up, captureLength, lateralScale);
                splatSoftVolume(fallbackCore, fallbackEnvelope, local.x, local.y, local.z);
            }
            physicalMaterial = buildPhysicalMaterialMask(fallbackCore);
        }

        float[] signedDistanceField = computeSignedDistanceField(physicalMaterial, voxelStepX, voxelStepYz);
        float[] coreField = new float[voxelCount];
        float[] envelopeField = new float[voxelCount];
        populateMaterialFields(coreField, envelopeField, visibleSurfaceCoverage,
                physicalMaterial, signedDistanceField, voxelStepX, voxelStepYz);
        boolean[] cohesiveMaterial = closeVoxels(physicalMaterial,
                COHESIVE_CLOSE_RADIUS_VOXELS);
        float[] cohesiveSignedDistanceField = computeSignedDistanceField(
                cohesiveMaterial, voxelStepX, voxelStepYz);
        float[][] sdfGradient = computeSdfGradientField(signedDistanceField, voxelStepX, voxelStepYz);
        float[] localThicknessField = computeLocalThicknessField(physicalMaterial, voxelStepX, voxelStepYz);
        float[] surfaceDepthField = computeSurfaceDepthField(visibleShell,
                physicalMaterial);

        return new DarkBallVolumeBuildResult(
                root,
                captureDir,
                side,
                up,
                root.subtract(pokemonCenter),
                captureLength,
                bodyRadius,
                lateralScale,
                coreField,
                envelopeField,
                surfaceDepthField,
                signedDistanceField,
                cohesiveSignedDistanceField,
                sdfGradient[0],
                sdfGradient[1],
                sdfGradient[2],
                localThicknessField,
                voxelStepX,
                voxelStepYz
        );
    }

    static int voxelIndex(int x, int y, int z) {
        return (x * DarkBallVolumeGrid.SLICE_SIZE + z) * DarkBallVolumeGrid.SLICE_SIZE + y;
    }

    private static MeshCaptureBounds capturedMeshBounds(List<Vec3> capturedModelVertices,
                                                         List<CapturedTriangle> capturedModelTriangles,
                                                         DarkBallTextureAlphaClipper.AlphaField textureAlpha,
                                                         Vec3 center, Vec3 axis, Vec3 side,
                                                         Vec3 up, double fallbackExtent) {
        MutableMeshCaptureBounds bounds = new MutableMeshCaptureBounds();
        if (textureAlpha != null) {
            for (CapturedTriangle source : capturedModelTriangles) {
                DarkBallTextureAlphaClipper.forEachVisibleTriangle(
                        source,
                        textureAlpha,
                        clipped -> {
                            bounds.include(worldPoint(source, clipped.a()),
                                    center, axis, side, up);
                            bounds.include(worldPoint(source, clipped.b()),
                                    center, axis, side, up);
                            bounds.include(worldPoint(source, clipped.c()),
                                    center, axis, side, up);
                        }
                );
            }
        }

        if (!bounds.hasSamples()) {
            for (Vec3 vertex : capturedModelVertices) {
                bounds.include(vertex, center, axis, side, up);
            }
        }

        if (!bounds.isFinite() || bounds.maxAxis <= bounds.minAxis) {
            float half = Math.max(0.24f, (float) fallbackExtent * 0.5f);
            return new MeshCaptureBounds(-half, half, half, half);
        }
        float fallbackRadius = Math.max(0.18f, (float) fallbackExtent * 0.18f);
        return new MeshCaptureBounds(
                bounds.minAxis,
                bounds.maxAxis,
                Math.max(bounds.maxSideAbs, fallbackRadius),
                Math.max(bounds.maxUpAbs, fallbackRadius)
        );
    }

    private static Vector3f toVolumeLocalPosition(Vec3 vertex, Vec3 root, Vec3 axis, Vec3 side, Vec3 up,
                                                  float captureLength, float lateralScale) {
        Vec3 rel = vertex.subtract(root);
        return new Vector3f(
                (float) (rel.dot(axis) / captureLength),
                (float) (rel.dot(side) / lateralScale * 0.5 + 0.5),
                (float) (rel.dot(up) / lateralScale * 0.5 + 0.5)
        );
    }

    private static Vector3f toVolumeGridPosition(Vec3 vertex, Vec3 root, Vec3 axis, Vec3 side, Vec3 up,
                                                 float captureLength, float lateralScale) {
        Vector3f local = toVolumeLocalPosition(vertex, root, axis, side, up, captureLength, lateralScale);
        return new Vector3f(
                local.x * (DarkBallVolumeGrid.X_SLICES - 1),
                local.y * (DarkBallVolumeGrid.SLICE_SIZE - 1),
                local.z * (DarkBallVolumeGrid.SLICE_SIZE - 1)
        );
    }

    private static DarkBallTextureAlphaClipper.AlphaField captureTextureAlpha(
            NativeImage textureImage) {
        if (textureImage == null || textureImage.getWidth() <= 0
                || textureImage.getHeight() <= 0) {
            return null;
        }
        int width = textureImage.getWidth();
        int height = textureImage.getHeight();
        float[] alpha = new float[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                alpha[y * width + x] = FastColor.ABGR32.alpha(
                        textureImage.getPixelRGBA(x, y)) / 255.0f;
            }
        }
        return new DarkBallTextureAlphaClipper.AlphaField(width, height, alpha);
    }

    private static Vec3 worldPoint(CapturedTriangle source,
                                   DarkBallTextureAlphaClipper.ParamVertex point) {
        return source.a().scale(point.weightA())
                .add(source.b().scale(point.weightB()))
                .add(source.c().scale(point.weightC()));
    }

    private static Vector3f gridPoint(Vector3f a, Vector3f b, Vector3f c,
                                      DarkBallTextureAlphaClipper.ParamVertex point) {
        return new Vector3f(
                a.x * point.weightA() + b.x * point.weightB()
                        + c.x * point.weightC(),
                a.y * point.weightA() + b.y * point.weightB()
                        + c.y * point.weightC(),
                a.z * point.weightA() + b.z * point.weightB()
                        + c.z * point.weightC()
        );
    }

    static void rasterizeTexturedTriangle(float[] surfaceCoverage,
                                          boolean[] visibleShell,
                                          Vector3f a, Vector3f b, Vector3f c,
                                          CapturedTriangle source,
                                          DarkBallTextureAlphaClipper.AlphaField textureAlpha) {
        DarkBallTextureAlphaClipper.forEachVisibleTriangle(
                source,
                textureAlpha,
                clipped -> rasterizeSurfaceTriangle(
                        surfaceCoverage,
                        visibleShell,
                        gridPoint(a, b, c, clipped.a()),
                        gridPoint(a, b, c, clipped.b()),
                        gridPoint(a, b, c, clipped.c())
                )
        );
    }

    private static void rasterizeTopologyTriangle(boolean[] topologyShell,
                                                  float[] surfaceAlpha,
                                                  float[] surfaceDistance,
                                                  Vector3f a, Vector3f b, Vector3f c,
                                                  CapturedTriangle source,
                                                  DarkBallTextureAlphaClipper.AlphaField textureAlpha,
                                                  float voxelStepX,
                                                  float voxelStepYz) {
        if (isDegenerateTriangle(a, b, c)) {
            return;
        }
        float halfExtent = 0.5001f;
        int minX = Mth.clamp((int) Math.floor(
                        Math.min(a.x, Math.min(b.x, c.x)) - halfExtent),
                0, DarkBallVolumeGrid.X_SLICES - 1);
        int maxX = Mth.clamp((int) Math.ceil(
                        Math.max(a.x, Math.max(b.x, c.x)) + halfExtent),
                0, DarkBallVolumeGrid.X_SLICES - 1);
        int minY = Mth.clamp((int) Math.floor(
                        Math.min(a.y, Math.min(b.y, c.y)) - halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int maxY = Mth.clamp((int) Math.ceil(
                        Math.max(a.y, Math.max(b.y, c.y)) + halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int minZ = Mth.clamp((int) Math.floor(
                        Math.min(a.z, Math.min(b.z, c.z)) - halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int maxZ = Mth.clamp((int) Math.ceil(
                        Math.max(a.z, Math.max(b.z, c.z)) + halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);

        Vector3f physicalA = new Vector3f(
                a.x * voxelStepX, a.y * voxelStepYz, a.z * voxelStepYz);
        Vector3f physicalB = new Vector3f(
                b.x * voxelStepX, b.y * voxelStepYz, b.z * voxelStepYz);
        Vector3f physicalC = new Vector3f(
                c.x * voxelStepX, c.y * voxelStepYz, c.z * voxelStepYz);
        float[] barycentric = new float[3];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!triangleIntersectsUnitVoxel(a, b, c, x, y, z)) {
                        continue;
                    }
                    float distanceSquared = pointTriangleDistanceSqr(
                            x * voxelStepX,
                            y * voxelStepYz,
                            z * voxelStepYz,
                            physicalA, physicalB, physicalC, barycentric);
                    int index = voxelIndex(x, y, z);
                    topologyShell[index] = true;
                    float alpha = sourceAlphaAt(source, textureAlpha,
                            barycentric[0], barycentric[1], barycentric[2]);
                    if (distanceSquared + 1e-6f < surfaceDistance[index]) {
                        surfaceDistance[index] = distanceSquared;
                        surfaceAlpha[index] = alpha;
                    } else if (Math.abs(distanceSquared - surfaceDistance[index])
                            <= 1e-6f) {
                        surfaceAlpha[index] = Math.min(surfaceAlpha[index], alpha);
                    }
                }
            }
        }
    }

    static void rasterizeSurfaceTriangle(float[] surfaceCoverage,
                                         boolean[] visibleShell,
                                         Vector3f a, Vector3f b, Vector3f c) {
        if (isDegenerateTriangle(a, b, c)) {
            return;
        }
        float halfExtent = 0.5001f;
        int minX = Mth.clamp((int) Math.floor(
                        Math.min(a.x, Math.min(b.x, c.x)) - halfExtent),
                0, DarkBallVolumeGrid.X_SLICES - 1);
        int maxX = Mth.clamp((int) Math.ceil(
                        Math.max(a.x, Math.max(b.x, c.x)) + halfExtent),
                0, DarkBallVolumeGrid.X_SLICES - 1);
        int minY = Mth.clamp((int) Math.floor(
                        Math.min(a.y, Math.min(b.y, c.y)) - halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int maxY = Mth.clamp((int) Math.ceil(
                        Math.max(a.y, Math.max(b.y, c.y)) + halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int minZ = Mth.clamp((int) Math.floor(
                        Math.min(a.z, Math.min(b.z, c.z)) - halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int maxZ = Mth.clamp((int) Math.ceil(
                        Math.max(a.z, Math.max(b.z, c.z)) + halfExtent),
                0, DarkBallVolumeGrid.SLICE_SIZE - 1);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!triangleIntersectsUnitVoxel(a, b, c, x, y, z)) {
                        continue;
                    }
                    float distance = Mth.sqrt(pointTriangleDistanceSqr(
                            x, y, z, a, b, c, null));
                    float coverage = Math.max(MINIMUM_SURFACE_COVERAGE,
                            1.0f - distance / HALF_VOXEL_DIAGONAL);
                    int index = voxelIndex(x, y, z);
                    visibleShell[index] = true;
                    surfaceCoverage[index] = Math.max(
                            surfaceCoverage[index], coverage);
                }
            }
        }
    }

    private static boolean isDegenerateTriangle(Vector3f a, Vector3f b,
                                                Vector3f c) {
        float abx = b.x - a.x;
        float aby = b.y - a.y;
        float abz = b.z - a.z;
        float acx = c.x - a.x;
        float acy = c.y - a.y;
        float acz = c.z - a.z;
        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        return nx * nx + ny * ny + nz * nz < 1e-8f;
    }

    private static boolean triangleIntersectsUnitVoxel(Vector3f a, Vector3f b,
                                                       Vector3f c,
                                                       float centerX,
                                                       float centerY,
                                                       float centerZ) {
        float biasedCenterX = centerX + HALF_OPEN_VOXEL_BIAS;
        float biasedCenterY = centerY + HALF_OPEN_VOXEL_BIAS;
        float biasedCenterZ = centerZ + HALF_OPEN_VOXEL_BIAS;
        float ax = a.x - biasedCenterX;
        float ay = a.y - biasedCenterY;
        float az = a.z - biasedCenterZ;
        float bx = b.x - biasedCenterX;
        float by = b.y - biasedCenterY;
        float bz = b.z - biasedCenterZ;
        float cx = c.x - biasedCenterX;
        float cy = c.y - biasedCenterY;
        float cz = c.z - biasedCenterZ;
        float abx = bx - ax;
        float aby = by - ay;
        float abz = bz - az;
        float bcx = cx - bx;
        float bcy = cy - by;
        float bcz = cz - bz;
        float cax = ax - cx;
        float cay = ay - cy;
        float caz = az - cz;

        if (separatedOnAxis(1.0f, 0.0f, 0.0f,
                ax, ay, az, bx, by, bz, cx, cy, cz)
                || separatedOnAxis(0.0f, 1.0f, 0.0f,
                ax, ay, az, bx, by, bz, cx, cy, cz)
                || separatedOnAxis(0.0f, 0.0f, 1.0f,
                ax, ay, az, bx, by, bz, cx, cy, cz)) {
            return false;
        }

        float normalX = aby * (cz - az) - abz * (cy - ay);
        float normalY = abz * (cx - ax) - abx * (cz - az);
        float normalZ = abx * (cy - ay) - aby * (cx - ax);
        if (separatedOnAxis(normalX, normalY, normalZ,
                ax, ay, az, bx, by, bz, cx, cy, cz)) {
            return false;
        }

        return !separatedOnEdgeAxes(abx, aby, abz,
                ax, ay, az, bx, by, bz, cx, cy, cz)
                && !separatedOnEdgeAxes(bcx, bcy, bcz,
                ax, ay, az, bx, by, bz, cx, cy, cz)
                && !separatedOnEdgeAxes(cax, cay, caz,
                ax, ay, az, bx, by, bz, cx, cy, cz);
    }

    private static boolean separatedOnEdgeAxes(float edgeX, float edgeY,
                                               float edgeZ,
                                               float ax, float ay, float az,
                                               float bx, float by, float bz,
                                               float cx, float cy, float cz) {
        return separatedOnAxis(0.0f, edgeZ, -edgeY,
                ax, ay, az, bx, by, bz, cx, cy, cz)
                || separatedOnAxis(-edgeZ, 0.0f, edgeX,
                ax, ay, az, bx, by, bz, cx, cy, cz)
                || separatedOnAxis(edgeY, -edgeX, 0.0f,
                ax, ay, az, bx, by, bz, cx, cy, cz);
    }

    private static boolean separatedOnAxis(float axisX, float axisY, float axisZ,
                                           float ax, float ay, float az,
                                           float bx, float by, float bz,
                                           float cx, float cy, float cz) {
        float lengthSquared = axisX * axisX + axisY * axisY
                + axisZ * axisZ;
        if (lengthSquared < 1e-12f) {
            return false;
        }
        float pa = axisX * ax + axisY * ay + axisZ * az;
        float pb = axisX * bx + axisY * by + axisZ * bz;
        float pc = axisX * cx + axisY * cy + axisZ * cz;
        float minimum = Math.min(pa, Math.min(pb, pc));
        float maximum = Math.max(pa, Math.max(pb, pc));
        float radius = 0.5f * (Math.abs(axisX)
                + Math.abs(axisY) + Math.abs(axisZ));
        return minimum > radius || maximum < -radius;
    }

    private static float sourceAlphaAt(CapturedTriangle source,
                                       DarkBallTextureAlphaClipper.AlphaField textureAlpha,
                                       float weightA, float weightB, float weightC) {
        float vertexAlpha = (source.alphaA() * weightA
                + source.alphaB() * weightB
                + source.alphaC() * weightC) / 255.0f;
        if (vertexAlpha <= 0.0f) {
            return 0.0f;
        }
        if (textureAlpha == null) {
            return Mth.clamp(vertexAlpha, 0.0f, 1.0f);
        }
        float u = source.au() * weightA + source.bu() * weightB
                + source.cu() * weightC;
        float v = source.av() * weightA + source.bv() * weightB
                + source.cv() * weightC;
        return textureAlpha.sample(u, v)
                * Mth.clamp(vertexAlpha, 0.0f, 1.0f);
    }

    /**
     * Extrudes the texture-alpha ownership of the nearest exterior model
     * surface through the raw closed geometry. This gives texture cutouts a
     * well-defined 3D interior without letting an intentional alpha opening
     * drain the whole flood-filled body.
     */
    static boolean[] buildTextureExtrudedMaterial(boolean[] topologyShell,
                                                  float[] topologySurfaceAlpha,
                                                  float[] topologySurfaceDistance,
                                                  boolean[] visibleShell,
                                                  float voxelStepX,
                                                  float voxelStepYz) {
        boolean[] exterior = floodExteriorEmpty(topologyShell);
        boolean[] rawSolid = new boolean[topologyShell.length];
        float[] ownershipDistance = new float[topologyShell.length];
        float[] ownershipAlpha = new float[topologyShell.length];
        Arrays.fill(ownershipDistance, Float.POSITIVE_INFINITY);
        int seeds = 0;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;

        for (int x = 0; x < DarkBallVolumeGrid.X_SLICES; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    int index = voxelIndex(x, y, z);
                    rawSolid[index] = topologyShell[index] || !exterior[index];
                    if (topologyShell[index]
                            && Float.isFinite(topologySurfaceDistance[index])
                            && touchesExterior(exterior, x, y, z)) {
                        ownershipDistance[index] = Mth.sqrt(
                                topologySurfaceDistance[index]);
                        ownershipAlpha[index] = Math.max(
                                topologySurfaceAlpha[index],
                                visibleShell[index]
                                        ? DarkBallTextureAlphaClipper.ALPHA_CUTOFF
                                        : 0.0f);
                        seeds++;
                    }
                }
            }
        }

        // Open or pathological captures can lack a formally exterior-facing
        // shell cell. Retain deterministic ownership rather than dropping the
        // entire source.
        if (seeds == 0) {
            for (int i = 0; i < topologyShell.length; i++) {
                if (topologyShell[i]
                        && Float.isFinite(topologySurfaceDistance[i])) {
                    ownershipDistance[i] = Mth.sqrt(
                            topologySurfaceDistance[i]);
                    ownershipAlpha[i] = Math.max(
                            topologySurfaceAlpha[i],
                            visibleShell[i]
                                    ? DarkBallTextureAlphaClipper.ALPHA_CUTOFF
                                    : 0.0f);
                }
            }
        }

        propagateNearestAlpha(rawSolid, ownershipDistance, ownershipAlpha,
                voxelStepX, voxelStepYz);
        boolean[] material = new boolean[topologyShell.length];
        for (int i = 0; i < material.length; i++) {
            material[i] = visibleShell[i]
                    || (!topologyShell[i] && !exterior[i]
                    && ownershipAlpha[i]
                    >= DarkBallTextureAlphaClipper.ALPHA_CUTOFF);
        }
        return material;
    }

    private static boolean touchesExterior(boolean[] exterior,
                                           int x, int y, int z) {
        return isExterior(exterior, x - 1, y, z)
                || isExterior(exterior, x + 1, y, z)
                || isExterior(exterior, x, y - 1, z)
                || isExterior(exterior, x, y + 1, z)
                || isExterior(exterior, x, y, z - 1)
                || isExterior(exterior, x, y, z + 1);
    }

    private static boolean isExterior(boolean[] exterior,
                                      int x, int y, int z) {
        if (x < 0 || x >= DarkBallVolumeGrid.X_SLICES
                || y < 0 || y >= DarkBallVolumeGrid.SLICE_SIZE
                || z < 0 || z >= DarkBallVolumeGrid.SLICE_SIZE) {
            return true;
        }
        return exterior[voxelIndex(x, y, z)];
    }

    private static void propagateNearestAlpha(boolean[] rawSolid,
                                              float[] distance,
                                              float[] alpha,
                                              float voxelStepX,
                                              float voxelStepYz) {
        for (int iteration = 0; iteration < 2; iteration++) {
            propagateAlphaPass(rawSolid, distance, alpha,
                    voxelStepX, voxelStepYz, true);
            propagateAlphaPass(rawSolid, distance, alpha,
                    voxelStepX, voxelStepYz, false);
        }
    }

    private static void propagateAlphaPass(boolean[] rawSolid,
                                           float[] distance,
                                           float[] alpha,
                                           float voxelStepX,
                                           float voxelStepYz,
                                           boolean forward) {
        int xStart = forward ? 0 : DarkBallVolumeGrid.X_SLICES - 1;
        int xEnd = forward ? DarkBallVolumeGrid.X_SLICES : -1;
        int xStep = forward ? 1 : -1;
        int zStart = forward ? 0 : DarkBallVolumeGrid.SLICE_SIZE - 1;
        int zEnd = forward ? DarkBallVolumeGrid.SLICE_SIZE : -1;
        int zStep = forward ? 1 : -1;
        int yStart = forward ? 0 : DarkBallVolumeGrid.SLICE_SIZE - 1;
        int yEnd = forward ? DarkBallVolumeGrid.SLICE_SIZE : -1;
        int yStep = forward ? 1 : -1;

        for (int x = xStart; x != xEnd; x += xStep) {
            for (int z = zStart; z != zEnd; z += zStep) {
                for (int y = yStart; y != yEnd; y += yStep) {
                    int index = voxelIndex(x, y, z);
                    if (!rawSolid[index]) {
                        continue;
                    }
                    float bestDistance = distance[index];
                    float bestAlpha = alpha[index];
                    for (int ox = -1; ox <= 1; ox++) {
                        for (int oz = -1; oz <= 1; oz++) {
                            for (int oy = -1; oy <= 1; oy++) {
                                if (ox == 0 && oy == 0 && oz == 0) {
                                    continue;
                                }
                                if (forward && !isPreviousNeighbor(ox, oy, oz)) {
                                    continue;
                                }
                                if (!forward && !isNextNeighbor(ox, oy, oz)) {
                                    continue;
                                }
                                int nx = x + ox;
                                int ny = y + oy;
                                int nz = z + oz;
                                if (nx < 0 || nx >= DarkBallVolumeGrid.X_SLICES
                                        || ny < 0 || ny >= DarkBallVolumeGrid.SLICE_SIZE
                                        || nz < 0 || nz >= DarkBallVolumeGrid.SLICE_SIZE) {
                                    continue;
                                }
                                int neighbor = voxelIndex(nx, ny, nz);
                                if (!rawSolid[neighbor]
                                        || !Float.isFinite(distance[neighbor])) {
                                    continue;
                                }
                                float candidate = distance[neighbor]
                                        + neighborDistance(ox, oy, oz,
                                        voxelStepX, voxelStepYz);
                                if (candidate + 1e-6f < bestDistance
                                        || (Math.abs(candidate - bestDistance)
                                        <= 1e-6f
                                        && alpha[neighbor] < bestAlpha)) {
                                    bestDistance = candidate;
                                    bestAlpha = alpha[neighbor];
                                }
                            }
                        }
                    }
                    distance[index] = bestDistance;
                    alpha[index] = bestAlpha;
                }
            }
        }
    }

    private static void populateMaterialFields(float[] core,
                                               float[] envelope,
                                               float[] surfaceCoverage,
                                               boolean[] material,
                                               float[] signedDistance,
                                               float voxelStepX,
                                               float voxelStepYz) {
        float envelopeWidth = Math.max(voxelStepX, voxelStepYz)
                * NARROW_ENVELOPE_VOXELS;
        for (int i = 0; i < material.length; i++) {
            if (material[i]) {
                core[i] = Math.max(0.96f, surfaceCoverage[i]);
                envelope[i] = 1.0f;
            } else {
                envelope[i] = DarkBallCaptureMath.smoothstep(
                        envelopeWidth, 0.0f,
                        Math.max(signedDistance[i], 0.0f));
            }
        }
    }

    private static boolean hasAnyVoxel(boolean[] voxels) {
        for (boolean voxel : voxels) {
            if (voxel) return true;
        }
        return false;
    }

    private static boolean[] floodExteriorEmpty(boolean[] barrier) {
        int xCount = DarkBallVolumeGrid.X_SLICES;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        boolean[] exterior = new boolean[barrier.length];
        int[] queue = new int[barrier.length];
        int head = 0;
        int tail = 0;

        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                tail = enqueueExteriorBoundary(barrier, exterior, queue, tail, x, 0, z);
                tail = enqueueExteriorBoundary(barrier, exterior, queue, tail, x, sliceSize - 1, z);
            }
            for (int y = 0; y < sliceSize; y++) {
                tail = enqueueExteriorBoundary(barrier, exterior, queue, tail, x, y, 0);
                tail = enqueueExteriorBoundary(barrier, exterior, queue, tail, x, y, sliceSize - 1);
            }
        }
        for (int z = 0; z < sliceSize; z++) {
            for (int y = 0; y < sliceSize; y++) {
                tail = enqueueExteriorBoundary(barrier, exterior, queue, tail, 0, y, z);
                tail = enqueueExteriorBoundary(barrier, exterior, queue, tail, xCount - 1, y, z);
            }
        }

        while (head < tail) {
            int packed = queue[head++];
            int x = packed / (sliceSize * sliceSize);
            int rem = packed - x * sliceSize * sliceSize;
            int z = rem / sliceSize;
            int y = rem - z * sliceSize;

            tail = enqueueExteriorNeighbor(barrier, exterior, queue, tail, x - 1, y, z);
            tail = enqueueExteriorNeighbor(barrier, exterior, queue, tail, x + 1, y, z);
            tail = enqueueExteriorNeighbor(barrier, exterior, queue, tail, x, y - 1, z);
            tail = enqueueExteriorNeighbor(barrier, exterior, queue, tail, x, y + 1, z);
            tail = enqueueExteriorNeighbor(barrier, exterior, queue, tail, x, y, z - 1);
            tail = enqueueExteriorNeighbor(barrier, exterior, queue, tail, x, y, z + 1);
        }

        return exterior;
    }

    private static int enqueueExteriorBoundary(boolean[] barrier, boolean[] exterior, int[] queue, int tail,
                                               int x, int y, int z) {
        int index = voxelIndex(x, y, z);
        if (barrier[index] || exterior[index]) return tail;
        exterior[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private static int enqueueExteriorNeighbor(boolean[] barrier, boolean[] exterior, int[] queue, int tail,
                                               int x, int y, int z) {
        if (x < 0 || x >= DarkBallVolumeGrid.X_SLICES
                || y < 0 || y >= DarkBallVolumeGrid.SLICE_SIZE
                || z < 0 || z >= DarkBallVolumeGrid.SLICE_SIZE) {
            return tail;
        }
        int index = voxelIndex(x, y, z);
        if (barrier[index] || exterior[index]) return tail;
        exterior[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private static float[] computeSurfaceDepthField(boolean[] shell,
                                                    boolean[] material) {
        int[] distances = new int[shell.length];
        java.util.Arrays.fill(distances, -1);

        int[] queue = new int[shell.length];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < shell.length; i++) {
            if (!shell[i]) continue;

            distances[i] = 0;
            queue[tail++] = i;
        }

        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        while (head < tail) {
            int index = queue[head++];
            int x = index / (sliceSize * sliceSize);
            int rem = index - x * sliceSize * sliceSize;
            int z = rem / sliceSize;
            int y = rem - z * sliceSize;
            int nextDistance = distances[index] + 1;

            tail = enqueueSurfaceDepthNeighbor(material, distances, queue, tail, nextDistance, x - 1, y, z);
            tail = enqueueSurfaceDepthNeighbor(material, distances, queue, tail, nextDistance, x + 1, y, z);
            tail = enqueueSurfaceDepthNeighbor(material, distances, queue, tail, nextDistance, x, y - 1, z);
            tail = enqueueSurfaceDepthNeighbor(material, distances, queue, tail, nextDistance, x, y + 1, z);
            tail = enqueueSurfaceDepthNeighbor(material, distances, queue, tail, nextDistance, x, y, z - 1);
            tail = enqueueSurfaceDepthNeighbor(material, distances, queue, tail, nextDistance, x, y, z + 1);
        }

        int maxDistance = 1;
        for (int i = 0; i < distances.length; i++) {
            if (material[i]) {
                maxDistance = Math.max(maxDistance, distances[i]);
            }
        }

        float[] field = new float[shell.length];
        for (int i = 0; i < field.length; i++) {
            if (!material[i] || distances[i] < 0) continue;

            field[i] = Mth.clamp(distances[i] / (float) maxDistance, 0f, 1f);
        }
        return field;
    }

    private static int enqueueSurfaceDepthNeighbor(boolean[] material,
                                                   int[] distances, int[] queue,
                                                   int tail, int nextDistance, int x, int y, int z) {
        if (x < 0 || x >= DarkBallVolumeGrid.X_SLICES
                || y < 0 || y >= DarkBallVolumeGrid.SLICE_SIZE
                || z < 0 || z >= DarkBallVolumeGrid.SLICE_SIZE) {
            return tail;
        }

        int index = voxelIndex(x, y, z);
        if (distances[index] >= 0 || !material[index]) {
            return tail;
        }

        distances[index] = nextDistance;
        queue[tail] = index;
        return tail + 1;
    }

    private static boolean[] buildPhysicalMaterialMask(float[] core) {
        boolean[] material = new boolean[core.length];
        for (int i = 0; i < core.length; i++) {
            material[i] = core[i] > PHYSICAL_MATERIAL_THRESHOLD;
        }
        return material;
    }

    static float[] computeSignedDistanceField(boolean[] material,
                                              float voxelStepX,
                                              float voxelStepYz) {
        float[] insideDistance = computeClassDistanceField(material, true, voxelStepX, voxelStepYz);
        float[] outsideDistance = computeClassDistanceField(material, false, voxelStepX, voxelStepYz);
        float maxDistance = Math.max(
                DarkBallVolumeGrid.X_SLICES * voxelStepX,
                DarkBallVolumeGrid.SLICE_SIZE * voxelStepYz
        );
        float[] sdf = new float[material.length];
        for (int i = 0; i < sdf.length; i++) {
            float d = material[i] ? -insideDistance[i] : outsideDistance[i];
            if (!Float.isFinite(d) || Math.abs(d) > maxDistance) {
                d = material[i] ? -maxDistance : maxDistance;
            }
            sdf[i] = d;
        }
        return sdf;
    }

    private static float[] computeClassDistanceField(boolean[] material, boolean targetMaterial,
                                                     float voxelStepX, float voxelStepYz) {
        int xCount = DarkBallVolumeGrid.X_SLICES;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        float[] distance = new float[material.length];
        java.util.Arrays.fill(distance, Float.POSITIVE_INFINITY);

        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    int index = voxelIndex(x, y, z);
                    if (material[index] == targetMaterial) {
                        distance[index] = classBoundaryDistance(
                                material, targetMaterial, x, y, z,
                                voxelStepX, voxelStepYz);
                    }
                }
            }
        }

        chamferDistancePass(material, targetMaterial, distance, voxelStepX, voxelStepYz, true);
        chamferDistancePass(material, targetMaterial, distance, voxelStepX, voxelStepYz, false);
        chamferDistancePass(material, targetMaterial, distance, voxelStepX, voxelStepYz, true);
        chamferDistancePass(material, targetMaterial, distance, voxelStepX, voxelStepYz, false);
        return distance;
    }

    private static float classBoundaryDistance(boolean[] material,
                                               boolean targetMaterial,
                                               int x, int y, int z,
                                               float voxelStepX,
                                               float voxelStepYz) {
        int xCount = DarkBallVolumeGrid.X_SLICES;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        float best = Float.POSITIVE_INFINITY;
        best = boundaryFaceDistance(material, targetMaterial,
                x - 1, y, z, xCount, sliceSize, voxelStepX, best);
        best = boundaryFaceDistance(material, targetMaterial,
                x + 1, y, z, xCount, sliceSize, voxelStepX, best);
        best = boundaryFaceDistance(material, targetMaterial,
                x, y - 1, z, xCount, sliceSize, voxelStepYz, best);
        best = boundaryFaceDistance(material, targetMaterial,
                x, y + 1, z, xCount, sliceSize, voxelStepYz, best);
        best = boundaryFaceDistance(material, targetMaterial,
                x, y, z - 1, xCount, sliceSize, voxelStepYz, best);
        best = boundaryFaceDistance(material, targetMaterial,
                x, y, z + 1, xCount, sliceSize, voxelStepYz, best);
        return best;
    }

    private static float boundaryFaceDistance(boolean[] material,
                                              boolean targetMaterial,
                                              int x, int y, int z,
                                              int xCount, int sliceSize,
                                              float faceSpacing,
                                              float currentBest) {
        if (x < 0 || x >= xCount || y < 0 || y >= sliceSize
                || z < 0 || z >= sliceSize) {
            return targetMaterial
                    ? Math.min(currentBest, faceSpacing * 0.5f)
                    : currentBest;
        }
        if (material[voxelIndex(x, y, z)] != targetMaterial) {
            return Math.min(currentBest, faceSpacing * 0.5f);
        }
        return currentBest;
    }

    private static void chamferDistancePass(boolean[] material, boolean targetMaterial, float[] distance,
                                            float voxelStepX, float voxelStepYz, boolean forward) {
        int xStart = forward ? 0 : DarkBallVolumeGrid.X_SLICES - 1;
        int xEnd = forward ? DarkBallVolumeGrid.X_SLICES : -1;
        int xStep = forward ? 1 : -1;
        int zStart = forward ? 0 : DarkBallVolumeGrid.SLICE_SIZE - 1;
        int zEnd = forward ? DarkBallVolumeGrid.SLICE_SIZE : -1;
        int zStep = forward ? 1 : -1;
        int yStart = forward ? 0 : DarkBallVolumeGrid.SLICE_SIZE - 1;
        int yEnd = forward ? DarkBallVolumeGrid.SLICE_SIZE : -1;
        int yStep = forward ? 1 : -1;

        for (int x = xStart; x != xEnd; x += xStep) {
            for (int z = zStart; z != zEnd; z += zStep) {
                for (int y = yStart; y != yEnd; y += yStep) {
                    int index = voxelIndex(x, y, z);
                    if (material[index] != targetMaterial) {
                        continue;
                    }

                    float best = distance[index];
                    for (int ox = -1; ox <= 1; ox++) {
                        for (int oz = -1; oz <= 1; oz++) {
                            for (int oy = -1; oy <= 1; oy++) {
                                if (ox == 0 && oy == 0 && oz == 0) {
                                    continue;
                                }
                                if (forward && !isPreviousNeighbor(ox, oy, oz)) {
                                    continue;
                                }
                                if (!forward && !isNextNeighbor(ox, oy, oz)) {
                                    continue;
                                }

                                int nx = x + ox;
                                int ny = y + oy;
                                int nz = z + oz;
                                if (nx < 0 || nx >= DarkBallVolumeGrid.X_SLICES
                                        || ny < 0 || ny >= DarkBallVolumeGrid.SLICE_SIZE
                                        || nz < 0 || nz >= DarkBallVolumeGrid.SLICE_SIZE) {
                                    continue;
                                }
                                int neighbor = voxelIndex(nx, ny, nz);
                                if (material[neighbor] != targetMaterial) {
                                    continue;
                                }
                                float candidate = distance[neighbor] + neighborDistance(ox, oy, oz, voxelStepX, voxelStepYz);
                                if (candidate < best) {
                                    best = candidate;
                                }
                            }
                        }
                    }
                    distance[index] = best;
                }
            }
        }
    }

    private static boolean isPreviousNeighbor(int ox, int oy, int oz) {
        return ox < 0 || (ox == 0 && oz < 0) || (ox == 0 && oz == 0 && oy < 0);
    }

    private static boolean isNextNeighbor(int ox, int oy, int oz) {
        return ox > 0 || (ox == 0 && oz > 0) || (ox == 0 && oz == 0 && oy > 0);
    }

    private static float neighborDistance(int ox, int oy, int oz, float voxelStepX, float voxelStepYz) {
        float dx = ox * voxelStepX;
        float dy = oy * voxelStepYz;
        float dz = oz * voxelStepYz;
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float[][] computeSdfGradientField(float[] sdf, float voxelStepX, float voxelStepYz) {
        float[] gx = new float[sdf.length];
        float[] gy = new float[sdf.length];
        float[] gz = new float[sdf.length];
        int xCount = DarkBallVolumeGrid.X_SLICES;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;

        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    int index = voxelIndex(x, y, z);
                    float dx = sampleSdf(sdf, Mth.clamp(x + 1, 0, xCount - 1), y, z)
                            - sampleSdf(sdf, Mth.clamp(x - 1, 0, xCount - 1), y, z);
                    float dy = sampleSdf(sdf, x, Mth.clamp(y + 1, 0, sliceSize - 1), z)
                            - sampleSdf(sdf, x, Mth.clamp(y - 1, 0, sliceSize - 1), z);
                    float dz = sampleSdf(sdf, x, y, Mth.clamp(z + 1, 0, sliceSize - 1))
                            - sampleSdf(sdf, x, y, Mth.clamp(z - 1, 0, sliceSize - 1));

                    dx /= Math.max(voxelStepX * 2.0f, 0.0001f);
                    dy /= Math.max(voxelStepYz * 2.0f, 0.0001f);
                    dz /= Math.max(voxelStepYz * 2.0f, 0.0001f);
                    float lenSq = dx * dx + dy * dy + dz * dz;
                    if (lenSq < 1e-7f) {
                        gx[index] = 1.0f;
                        gy[index] = 0.0f;
                        gz[index] = 0.0f;
                    } else {
                        float invLen = 1.0f / Mth.sqrt(lenSq);
                        gx[index] = dx * invLen;
                        gy[index] = dy * invLen;
                        gz[index] = dz * invLen;
                    }
                }
            }
        }
        return new float[][]{gx, gy, gz};
    }

    private static float[] computeLocalThicknessField(boolean[] material, float voxelStepX, float voxelStepYz) {
        int xCount = DarkBallVolumeGrid.X_SLICES;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        float[] thickness = new float[material.length];
        float maxThickness = Math.max(xCount * voxelStepX, sliceSize * voxelStepYz);

        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    int index = voxelIndex(x, y, z);
                    if (!material[index]) {
                        continue;
                    }

                    float axisThickness = materialRunLength(material, x, y, z, 1, 0, 0) * voxelStepX;
                    float sideThickness = materialRunLength(material, x, y, z, 0, 1, 0) * voxelStepYz;
                    float upThickness = materialRunLength(material, x, y, z, 0, 0, 1) * voxelStepYz;
                    float localThickness = Math.min(axisThickness, Math.min(sideThickness, upThickness));
                    thickness[index] = Mth.clamp(localThickness, Math.min(voxelStepX, voxelStepYz), maxThickness);
                }
            }
        }
        return thickness;
    }

    private static int materialRunLength(boolean[] material, int x, int y, int z, int dx, int dy, int dz) {
        int count = 1;
        count += countMaterialDirection(material, x, y, z, dx, dy, dz);
        count += countMaterialDirection(material, x, y, z, -dx, -dy, -dz);
        return count;
    }

    private static int countMaterialDirection(boolean[] material, int x, int y, int z, int dx, int dy, int dz) {
        int count = 0;
        int nx = x + dx;
        int ny = y + dy;
        int nz = z + dz;
        while (nx >= 0 && nx < DarkBallVolumeGrid.X_SLICES
                && ny >= 0 && ny < DarkBallVolumeGrid.SLICE_SIZE
                && nz >= 0 && nz < DarkBallVolumeGrid.SLICE_SIZE
                && material[voxelIndex(nx, ny, nz)]) {
            count++;
            nx += dx;
            ny += dy;
            nz += dz;
        }
        return count;
    }

    private static float sampleSdf(float[] sdf, int x, int y, int z) {
        return sdf[voxelIndex(x, y, z)];
    }

    private static float pointTriangleDistanceSqr(float px, float py, float pz,
                                                  Vector3f a, Vector3f b, Vector3f c,
                                                  float[] barycentric) {
        float abx = b.x - a.x;
        float aby = b.y - a.y;
        float abz = b.z - a.z;
        float acx = c.x - a.x;
        float acy = c.y - a.y;
        float acz = c.z - a.z;
        float apx = px - a.x;
        float apy = py - a.y;
        float apz = pz - a.z;
        float d1 = dot(abx, aby, abz, apx, apy, apz);
        float d2 = dot(acx, acy, acz, apx, apy, apz);
        if (d1 <= 0.0f && d2 <= 0.0f) {
            setBarycentric(barycentric, 1.0f, 0.0f, 0.0f);
            return distanceSqr(px, py, pz, a.x, a.y, a.z);
        }

        float bpx = px - b.x;
        float bpy = py - b.y;
        float bpz = pz - b.z;
        float d3 = dot(abx, aby, abz, bpx, bpy, bpz);
        float d4 = dot(acx, acy, acz, bpx, bpy, bpz);
        if (d3 >= 0.0f && d4 <= d3) {
            setBarycentric(barycentric, 0.0f, 1.0f, 0.0f);
            return distanceSqr(px, py, pz, b.x, b.y, b.z);
        }

        float vc = d1 * d4 - d3 * d2;
        if (vc <= 0.0f && d1 >= 0.0f && d3 <= 0.0f) {
            float v = d1 / Math.max(d1 - d3, 0.0001f);
            setBarycentric(barycentric, 1.0f - v, v, 0.0f);
            return distanceSqr(px, py, pz, a.x + abx * v, a.y + aby * v, a.z + abz * v);
        }

        float cpx = px - c.x;
        float cpy = py - c.y;
        float cpz = pz - c.z;
        float d5 = dot(abx, aby, abz, cpx, cpy, cpz);
        float d6 = dot(acx, acy, acz, cpx, cpy, cpz);
        if (d6 >= 0.0f && d5 <= d6) {
            setBarycentric(barycentric, 0.0f, 0.0f, 1.0f);
            return distanceSqr(px, py, pz, c.x, c.y, c.z);
        }

        float vb = d5 * d2 - d1 * d6;
        if (vb <= 0.0f && d2 >= 0.0f && d6 <= 0.0f) {
            float w = d2 / Math.max(d2 - d6, 0.0001f);
            setBarycentric(barycentric, 1.0f - w, 0.0f, w);
            return distanceSqr(px, py, pz, a.x + acx * w, a.y + acy * w, a.z + acz * w);
        }

        float va = d3 * d6 - d5 * d4;
        if (va <= 0.0f && d4 - d3 >= 0.0f && d5 - d6 >= 0.0f) {
            float w = (d4 - d3) / Math.max((d4 - d3) + (d5 - d6), 0.0001f);
            setBarycentric(barycentric, 0.0f, 1.0f - w, w);
            return distanceSqr(px, py, pz,
                    b.x + (c.x - b.x) * w,
                    b.y + (c.y - b.y) * w,
                    b.z + (c.z - b.z) * w);
        }

        float invDenom = 1.0f / Math.max(va + vb + vc, 0.0001f);
        float v = vb * invDenom;
        float w = vc * invDenom;
        setBarycentric(barycentric, 1.0f - v - w, v, w);
        return distanceSqr(px, py, pz,
                a.x + abx * v + acx * w,
                a.y + aby * v + acy * w,
                a.z + abz * v + acz * w);
    }

    private static void setBarycentric(float[] target, float a, float b, float c) {
        if (target == null) {
            return;
        }
        target[0] = a;
        target[1] = b;
        target[2] = c;
    }

    private static float dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }

    private static float distanceSqr(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = ax - bx;
        float dy = ay - by;
        float dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void splatSoftVolume(float[] core, float[] envelope, float lx, float ly, float lz) {
        if (lx < -0.05f || lx > 1.05f || ly < -0.08f || ly > 1.08f || lz < -0.08f || lz > 1.08f) return;

        float cx = lx * (DarkBallVolumeGrid.X_SLICES - 1);
        float cy = ly * (DarkBallVolumeGrid.SLICE_SIZE - 1);
        float cz = lz * (DarkBallVolumeGrid.SLICE_SIZE - 1);
        int radius = 3;
        int minX = Mth.clamp((int) Math.floor(cx - radius), 0, DarkBallVolumeGrid.X_SLICES - 1);
        int maxX = Mth.clamp((int) Math.ceil(cx + radius), 0, DarkBallVolumeGrid.X_SLICES - 1);
        int minY = Mth.clamp((int) Math.floor(cy - radius), 0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int maxY = Mth.clamp((int) Math.ceil(cy + radius), 0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int minZ = Mth.clamp((int) Math.floor(cz - radius), 0, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int maxZ = Mth.clamp((int) Math.ceil(cz + radius), 0, DarkBallVolumeGrid.SLICE_SIZE - 1);

        for (int x = minX; x <= maxX; x++) {
            float dx = (x - cx) / 1.75f;
            for (int z = minZ; z <= maxZ; z++) {
                float dz = (z - cz) / 1.95f;
                for (int y = minY; y <= maxY; y++) {
                    float dy = (y - cy) / 1.95f;
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > 2.75f) continue;

                    float coreWeight = DarkBallCaptureMath.smoothstep(0.95f, 0.04f, d2) * 0.78f;
                    float envelopeWeight = DarkBallCaptureMath.smoothstep(2.75f, 0.12f, d2);
                    int index = voxelIndex(x, y, z);
                    core[index] = Math.max(core[index], coreWeight);
                    envelope[index] = Math.max(envelope[index], envelopeWeight);
                }
            }
        }
    }

    /**
     * Performs a true binary closing (dilation followed by erosion) with a
     * spherical voxel kernel. The operation is deliberately kept separate
     * from the detailed physical mask: it supplies a cohesive visual core only
     * while turbulence is active, so the undeformed Pokemon outline remains
     * unchanged.
     */
    static boolean[] closeVoxels(boolean[] source, int radius) {
        int expectedLength = DarkBallVolumeGrid.X_SLICES
                * DarkBallVolumeGrid.SLICE_SIZE
                * DarkBallVolumeGrid.SLICE_SIZE;
        if (source == null || source.length != expectedLength) {
            throw new IllegalArgumentException("source must match the Dark Ball voxel grid");
        }
        if (radius <= 0) {
            return source.clone();
        }
        int xCount = DarkBallVolumeGrid.X_SLICES;
        int sliceSize = DarkBallVolumeGrid.SLICE_SIZE;
        int paddedXCount = xCount + radius * 2;
        int paddedSliceSize = sliceSize + radius * 2;
        boolean[] paddedSource = new boolean[paddedXCount
                * paddedSliceSize * paddedSliceSize];
        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    paddedSource[morphologyIndex(x + radius, y + radius,
                            z + radius, paddedSliceSize)] = source[voxelIndex(x, y, z)];
                }
            }
        }

        boolean[] dilated = dilateClosingVoxels(paddedSource, radius,
                paddedXCount, paddedSliceSize);
        boolean[] eroded = erodeClosingVoxels(dilated, radius,
                paddedXCount, paddedSliceSize);
        boolean[] result = new boolean[source.length];
        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    result[voxelIndex(x, y, z)] = eroded[morphologyIndex(
                            x + radius, y + radius, z + radius, paddedSliceSize)];
                }
            }
        }
        return result;
    }

    private static boolean[] dilateClosingVoxels(boolean[] source, int radius,
                                                  int xCount, int sliceSize) {
        boolean[] result = new boolean[source.length];
        int radiusSquared = radius * radius;
        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    if (!source[morphologyIndex(x, y, z, sliceSize)]) {
                        continue;
                    }
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                                    continue;
                                }
                                int nx = x + dx;
                                int ny = y + dy;
                                int nz = z + dz;
                                if (nx >= 0 && nx < xCount
                                        && ny >= 0 && ny < sliceSize
                                        && nz >= 0 && nz < sliceSize) {
                                    result[morphologyIndex(nx, ny, nz, sliceSize)] = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean[] erodeClosingVoxels(boolean[] source, int radius,
                                                 int xCount, int sliceSize) {
        boolean[] result = new boolean[source.length];
        int radiusSquared = radius * radius;
        for (int x = 0; x < xCount; x++) {
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    boolean survives = true;
                    for (int dx = -radius; dx <= radius && survives; dx++) {
                        for (int dy = -radius; dy <= radius && survives; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                                    continue;
                                }
                                int nx = x + dx;
                                int ny = y + dy;
                                int nz = z + dz;
                                if (nx < 0 || nx >= xCount
                                        || ny < 0 || ny >= sliceSize
                                        || nz < 0 || nz >= sliceSize
                                        || !source[morphologyIndex(nx, ny, nz, sliceSize)]) {
                                    survives = false;
                                    break;
                                }
                            }
                        }
                    }
                    result[morphologyIndex(x, y, z, sliceSize)] = survives;
                }
            }
        }
        return result;
    }

    private static int morphologyIndex(int x, int y, int z, int sliceSize) {
        return (x * sliceSize + z) * sliceSize + y;
    }

    private static final class MutableMeshCaptureBounds {
        private float minAxis = Float.POSITIVE_INFINITY;
        private float maxAxis = Float.NEGATIVE_INFINITY;
        private float maxSideAbs;
        private float maxUpAbs;
        private int samples;

        private void include(Vec3 point, Vec3 center, Vec3 axis,
                             Vec3 side, Vec3 up) {
            Vec3 relative = point.subtract(center);
            float axisValue = (float) relative.dot(axis);
            float sideValue = (float) relative.dot(side);
            float upValue = (float) relative.dot(up);
            minAxis = Math.min(minAxis, axisValue);
            maxAxis = Math.max(maxAxis, axisValue);
            maxSideAbs = Math.max(maxSideAbs, Math.abs(sideValue));
            maxUpAbs = Math.max(maxUpAbs, Math.abs(upValue));
            samples++;
        }

        private boolean hasSamples() {
            return samples > 0;
        }

        private boolean isFinite() {
            return Float.isFinite(minAxis) && Float.isFinite(maxAxis)
                    && Float.isFinite(maxSideAbs) && Float.isFinite(maxUpAbs);
        }
    }

    private record MeshCaptureBounds(float minAxis, float maxAxis, float maxSideAbs, float maxUpAbs) {
    }
}
