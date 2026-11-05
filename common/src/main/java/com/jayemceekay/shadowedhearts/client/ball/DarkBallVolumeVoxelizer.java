package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.client.render.DarkBallFieldMaskBufferSource.CapturedTriangle;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

final class DarkBallVolumeVoxelizer {
    private static final DarkBallVolumeVoxelizer FULL = new DarkBallVolumeVoxelizer(
            DarkBallVolumeGrid.X_SLICES, DarkBallVolumeGrid.SLICE_SIZE);
    private final int xSize;
    private final int sideSize;

    static final float PHYSICAL_MATERIAL_THRESHOLD = 0.018f;

    private static final float MINIMUM_SURFACE_COVERAGE = 0.08f;
    private static final float HALF_VOXEL_DIAGONAL = 0.8660254f;
    private static final float HALF_OPEN_VOXEL_BIAS = 0.0001f;

    private DarkBallVolumeVoxelizer(int xSize, int sideSize) {
        this.xSize = xSize;
        this.sideSize = sideSize;
    }

    static DarkBallVolumeBuildResult build(Vec3 ballPos, Vec3 pokemonCenter, AABB pokemonBounds,
                                           List<Vec3> capturedModelVertices,
                                           List<CapturedTriangle> capturedModelTriangles,
                                           NativeImage textureImage) {
        return build(ballPos, pokemonCenter, pokemonBounds, capturedModelVertices,
                capturedModelTriangles, textureImage, null);
    }

    static DarkBallVolumeBuildResult build(Vec3 ballPos, Vec3 pokemonCenter, AABB pokemonBounds,
                                           List<Vec3> capturedModelVertices,
                                           List<CapturedTriangle> capturedModelTriangles,
                                           NativeImage textureImage,
                                           BooleanSupplier cancellationRequested) {
        return DarkBallPreparationCancellation.run(cancellationRequested,
                () -> build(ballPos, pokemonCenter, pokemonBounds,
                        capturedModelVertices, capturedModelTriangles,
                        DarkBallSnapshotTextures.fromBase(textureImage),
                        cancellationRequested));
    }

    static DarkBallVolumeBuildResult build(Vec3 ballPos, Vec3 pokemonCenter, AABB pokemonBounds,
                                           List<Vec3> capturedModelVertices,
                                           List<CapturedTriangle> capturedModelTriangles,
                                           DarkBallSnapshotTextures textures,
                                           BooleanSupplier cancellationRequested) {
        return DarkBallPreparationCancellation.run(cancellationRequested,
                () -> FULL.buildInternal(ballPos, pokemonCenter, pokemonBounds,
                        capturedModelVertices, capturedModelTriangles, textures));
    }

    /** Published fields always use the fixed consumer grid. */
    static int voxelIndex(int x, int y, int z) {
        return (x * DarkBallVolumeGrid.SLICE_SIZE + z) * DarkBallVolumeGrid.SLICE_SIZE + y;
    }

    static void rasterizeTexturedTriangle(float[] surfaceCoverage, boolean[] visibleShell,
                                          Vector3f a, Vector3f b, Vector3f c, CapturedTriangle source,
                                          DarkBallTextureAlphaClipper.AlphaField textureAlpha) {
        FULL.rasterizeTexturedTriangleInternal(surfaceCoverage, visibleShell, a, b, c, source, textureAlpha);
    }

    static void rasterizeSurfaceTriangle(float[] surfaceCoverage, boolean[] visibleShell,
                                         Vector3f a, Vector3f b, Vector3f c) {
        FULL.rasterizeSurfaceTriangleInternal(surfaceCoverage, visibleShell, a, b, c);
    }

    static boolean[] buildTextureExtrudedMaterial(boolean[] topologyShell, float[] topologySurfaceAlpha,
                                                  float[] topologySurfaceDistance, boolean[] visibleShell,
                                                  float voxelStepX, float voxelStepYz) {
        return FULL.buildTextureExtrudedMaterialInternal(topologyShell, topologySurfaceAlpha,
                topologySurfaceDistance, visibleShell, voxelStepX, voxelStepYz);
    }

    static float[] computeSignedDistanceField(boolean[] material, float voxelStepX, float voxelStepYz) {
        return FULL.computeSignedDistanceFieldInternal(material, voxelStepX, voxelStepYz);
    }

    static float[] computeLocalThicknessField(boolean[] material, float voxelStepX, float voxelStepYz) {
        return FULL.computeLocalThicknessFieldInternal(material, voxelStepX, voxelStepYz);
    }

    private DarkBallVolumeBuildResult buildInternal(
            Vec3 ballPos, Vec3 pokemonCenter, AABB pokemonBounds,
            List<Vec3> capturedModelVertices,
            List<CapturedTriangle> capturedModelTriangles,
            DarkBallSnapshotTextures textures) {
        Vec3 captureDir = ballPos.subtract(pokemonCenter);
        if (captureDir.lengthSqr() < 1e-6) captureDir = new Vec3(0, 1, 0);
        captureDir = captureDir.normalize();
        Vec3 side = DarkBallCaptureMath.stableSide(captureDir);
        Vec3 up = captureDir.cross(side).normalize();

        double maxExtent = Math.max(pokemonBounds.getXsize(),
                Math.max(pokemonBounds.getYsize(), pokemonBounds.getZsize()));
        MeshCaptureBounds meshBounds = capturedMeshBounds(
                capturedModelVertices,
                capturedModelTriangles,
                textures,
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

        int sliceSize = sideSize;
        int voxelCount = xSize * sliceSize * sliceSize;
        float[] visibleSurfaceCoverage = new float[voxelCount];
        boolean[] visibleShell = new boolean[voxelCount];
        boolean[] topologyShell = new boolean[voxelCount];
        float[] topologySurfaceAlpha = new float[voxelCount];
        float[] topologySurfaceDistance = new float[voxelCount];
        Arrays.fill(topologySurfaceDistance, Float.POSITIVE_INFINITY);
        float voxelStepX = captureLength / Math.max(
                1, xSize - 1);
        float voxelStepYz = lateralScale * 2.0f / Math.max(
                1, sliceSize - 1);

        if (!capturedModelTriangles.isEmpty()) {
            for (CapturedTriangle triangle : capturedModelTriangles) {
                DarkBallPreparationCancellation.check();
                DarkBallTextureAlphaClipper.AlphaField textureAlpha = textures.alphaFor(triangle);
                Vector3f a = toVolumeGridPosition(triangle.a(), root, captureDir, side, up, captureLength, lateralScale);
                Vector3f b = toVolumeGridPosition(triangle.b(), root, captureDir, side, up, captureLength, lateralScale);
                Vector3f c = toVolumeGridPosition(triangle.c(), root, captureDir, side, up, captureLength, lateralScale);
                rasterizeTopologyTriangle(topologyShell, topologySurfaceAlpha,
                        topologySurfaceDistance, a, b, c, triangle, textureAlpha,
                        voxelStepX, voxelStepYz);
                rasterizeTexturedTriangleInternal(visibleSurfaceCoverage, visibleShell,
                        a, b, c, triangle, textureAlpha);
            }
        }

        boolean[] physicalMaterial;
        if (hasAnyVoxel(topologyShell)) {
            physicalMaterial = buildTextureExtrudedMaterialInternal(
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
            for (Vec3 vertex : capturedModelVertices) {
                DarkBallPreparationCancellation.check();
                Vector3f local = toVolumeLocalPosition(vertex, root, captureDir, side, up, captureLength, lateralScale);
                splatSoftVolume(fallbackCore, local.x, local.y, local.z);
            }
            physicalMaterial = buildPhysicalMaterialMask(fallbackCore);
        }

        float[] signedDistanceField = computeSignedDistanceFieldInternal(physicalMaterial, voxelStepX, voxelStepYz);
        float[] localThicknessField = computeLocalThicknessFieldInternal(physicalMaterial, voxelStepX, voxelStepYz);

        return new DarkBallVolumeBuildResult(root,
                captureDir,
                side,
                up,
                root.subtract(pokemonCenter),
                captureLength,
                bodyRadius,
                lateralScale,
                signedDistanceField,
                localThicknessField,
                voxelStepX,
                voxelStepYz);
    }

    private int voxelIndexInternal(int x, int y, int z) {
        return (x * sideSize + z) * sideSize + y;
    }

    private MeshCaptureBounds capturedMeshBounds(List<Vec3> capturedModelVertices,
                                                         List<CapturedTriangle> capturedModelTriangles,
                                                         DarkBallSnapshotTextures textures,
                                                         Vec3 center, Vec3 axis, Vec3 side,
                                                         Vec3 up, double fallbackExtent) {
        MutableMeshCaptureBounds bounds = new MutableMeshCaptureBounds();
        boolean materialAware = textures.hasAnyAlpha()
                || capturedModelTriangles.stream().anyMatch(triangle -> triangle.material() != null);
        if (materialAware) {
            for (CapturedTriangle source : capturedModelTriangles) {
                DarkBallPreparationCancellation.check();
                DarkBallTextureAlphaClipper.forEachVisibleTriangle(
                        source,
                        textures.alphaFor(source),
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

        if (!bounds.hasSamples() && !materialAware) {
            for (Vec3 vertex : capturedModelVertices) {
                DarkBallPreparationCancellation.check();
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

    private Vector3f toVolumeLocalPosition(Vec3 vertex, Vec3 root, Vec3 axis, Vec3 side, Vec3 up,
                                                  float captureLength, float lateralScale) {
        Vec3 rel = vertex.subtract(root);
        return new Vector3f(
                (float) (rel.dot(axis) / captureLength),
                (float) (rel.dot(side) / lateralScale * 0.5 + 0.5),
                (float) (rel.dot(up) / lateralScale * 0.5 + 0.5)
        );
    }

    private Vector3f toVolumeGridPosition(Vec3 vertex, Vec3 root, Vec3 axis, Vec3 side, Vec3 up,
                                                 float captureLength, float lateralScale) {
        Vector3f local = toVolumeLocalPosition(vertex, root, axis, side, up, captureLength, lateralScale);
        return new Vector3f(
                local.x * (xSize - 1),
                local.y * (sideSize - 1),
                local.z * (sideSize - 1)
        );
    }

    private Vec3 worldPoint(CapturedTriangle source,
                                   DarkBallTextureAlphaClipper.ParamVertex point) {
        return source.a().scale(point.weightA())
                .add(source.b().scale(point.weightB()))
                .add(source.c().scale(point.weightC()));
    }

    private Vector3f gridPoint(Vector3f a, Vector3f b, Vector3f c,
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

    private void rasterizeTexturedTriangleInternal(float[] surfaceCoverage,
                                          boolean[] visibleShell,
                                          Vector3f a, Vector3f b, Vector3f c,
                                          CapturedTriangle source,
                                          DarkBallTextureAlphaClipper.AlphaField textureAlpha) {
        DarkBallTextureAlphaClipper.forEachVisibleTriangle(
                source,
                textureAlpha,
                clipped -> rasterizeSurfaceTriangleInternal(
                        surfaceCoverage,
                        visibleShell,
                        gridPoint(a, b, c, clipped.a()),
                        gridPoint(a, b, c, clipped.b()),
                        gridPoint(a, b, c, clipped.c())
                )
        );
    }

    private void rasterizeTopologyTriangle(boolean[] topologyShell,
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
                0, xSize - 1);
        int maxX = Mth.clamp((int) Math.ceil(
                        Math.max(a.x, Math.max(b.x, c.x)) + halfExtent),
                0, xSize - 1);
        int minY = Mth.clamp((int) Math.floor(
                        Math.min(a.y, Math.min(b.y, c.y)) - halfExtent),
                0, sideSize - 1);
        int maxY = Mth.clamp((int) Math.ceil(
                        Math.max(a.y, Math.max(b.y, c.y)) + halfExtent),
                0, sideSize - 1);
        int minZ = Mth.clamp((int) Math.floor(
                        Math.min(a.z, Math.min(b.z, c.z)) - halfExtent),
                0, sideSize - 1);
        int maxZ = Mth.clamp((int) Math.ceil(
                        Math.max(a.z, Math.max(b.z, c.z)) + halfExtent),
                0, sideSize - 1);

        Vector3f physicalA = new Vector3f(
                a.x * voxelStepX, a.y * voxelStepYz, a.z * voxelStepYz);
        Vector3f physicalB = new Vector3f(
                b.x * voxelStepX, b.y * voxelStepYz, b.z * voxelStepYz);
        Vector3f physicalC = new Vector3f(
                c.x * voxelStepX, c.y * voxelStepYz, c.z * voxelStepYz);
        float[] barycentric = new float[3];
        for (int x = minX; x <= maxX; x++) {
            DarkBallPreparationCancellation.check();
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
                    int index = voxelIndexInternal(x, y, z);
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

    private void rasterizeSurfaceTriangleInternal(float[] surfaceCoverage,
                                         boolean[] visibleShell,
                                         Vector3f a, Vector3f b, Vector3f c) {
        if (isDegenerateTriangle(a, b, c)) {
            return;
        }
        float halfExtent = 0.5001f;
        int minX = Mth.clamp((int) Math.floor(
                        Math.min(a.x, Math.min(b.x, c.x)) - halfExtent),
                0, xSize - 1);
        int maxX = Mth.clamp((int) Math.ceil(
                        Math.max(a.x, Math.max(b.x, c.x)) + halfExtent),
                0, xSize - 1);
        int minY = Mth.clamp((int) Math.floor(
                        Math.min(a.y, Math.min(b.y, c.y)) - halfExtent),
                0, sideSize - 1);
        int maxY = Mth.clamp((int) Math.ceil(
                        Math.max(a.y, Math.max(b.y, c.y)) + halfExtent),
                0, sideSize - 1);
        int minZ = Mth.clamp((int) Math.floor(
                        Math.min(a.z, Math.min(b.z, c.z)) - halfExtent),
                0, sideSize - 1);
        int maxZ = Mth.clamp((int) Math.ceil(
                        Math.max(a.z, Math.max(b.z, c.z)) + halfExtent),
                0, sideSize - 1);

        for (int x = minX; x <= maxX; x++) {
            DarkBallPreparationCancellation.check();
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!triangleIntersectsUnitVoxel(a, b, c, x, y, z)) {
                        continue;
                    }
                    float distance = Mth.sqrt(pointTriangleDistanceSqr(
                            x, y, z, a, b, c, null));
                    float coverage = Math.max(MINIMUM_SURFACE_COVERAGE,
                            1.0f - distance / HALF_VOXEL_DIAGONAL);
                    int index = voxelIndexInternal(x, y, z);
                    visibleShell[index] = true;
                    surfaceCoverage[index] = Math.max(
                            surfaceCoverage[index], coverage);
                }
            }
        }
    }

    private boolean isDegenerateTriangle(Vector3f a, Vector3f b,
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

    private boolean triangleIntersectsUnitVoxel(Vector3f a, Vector3f b,
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

    private boolean separatedOnEdgeAxes(float edgeX, float edgeY,
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

    private boolean separatedOnAxis(float axisX, float axisY, float axisZ,
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

    private float sourceAlphaAt(CapturedTriangle source,
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
    private boolean[] buildTextureExtrudedMaterialInternal(boolean[] topologyShell,
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
        int sliceSize = sideSize;

        for (int x = 0; x < xSize; x++) {
            DarkBallPreparationCancellation.check();
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    int index = voxelIndexInternal(x, y, z);
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
                DarkBallPreparationCancellation.checkEvery(i);
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
            DarkBallPreparationCancellation.checkEvery(i);
            material[i] = visibleShell[i]
                    || (!topologyShell[i] && !exterior[i]
                    && ownershipAlpha[i]
                    >= DarkBallTextureAlphaClipper.ALPHA_CUTOFF);
        }
        return material;
    }

    private boolean touchesExterior(boolean[] exterior,
                                           int x, int y, int z) {
        return isExterior(exterior, x - 1, y, z)
                || isExterior(exterior, x + 1, y, z)
                || isExterior(exterior, x, y - 1, z)
                || isExterior(exterior, x, y + 1, z)
                || isExterior(exterior, x, y, z - 1)
                || isExterior(exterior, x, y, z + 1);
    }

    private boolean isExterior(boolean[] exterior,
                                      int x, int y, int z) {
        if (x < 0 || x >= xSize
                || y < 0 || y >= sideSize
                || z < 0 || z >= sideSize) {
            return true;
        }
        return exterior[voxelIndexInternal(x, y, z)];
    }

    private void propagateNearestAlpha(boolean[] rawSolid,
                                              float[] distance,
                                              float[] alpha,
                                              float voxelStepX,
                                              float voxelStepYz) {
        NeighborStep[] previous = neighborSteps(voxelStepX, voxelStepYz, true);
        NeighborStep[] next = neighborSteps(voxelStepX, voxelStepYz, false);
        for (int iteration = 0; iteration < 2; iteration++) {
            propagateAlphaPass(rawSolid, distance, alpha, previous, true);
            propagateAlphaPass(rawSolid, distance, alpha, next, false);
        }
    }

    private void propagateAlphaPass(boolean[] rawSolid,
                                           float[] distance,
                                           float[] alpha,
                                           NeighborStep[] neighbors,
                                           boolean forward) {
        int xStart = forward ? 0 : xSize - 1;
        int xEnd = forward ? xSize : -1;
        int xStep = forward ? 1 : -1;
        int zStart = forward ? 0 : sideSize - 1;
        int zEnd = forward ? sideSize : -1;
        int zStep = forward ? 1 : -1;
        int yStart = forward ? 0 : sideSize - 1;
        int yEnd = forward ? sideSize : -1;
        int yStep = forward ? 1 : -1;

        for (int x = xStart; x != xEnd; x += xStep) {
            DarkBallPreparationCancellation.check();
            for (int z = zStart; z != zEnd; z += zStep) {
                for (int y = yStart; y != yEnd; y += yStep) {
                    int index = voxelIndexInternal(x, y, z);
                    if (!rawSolid[index]) {
                        continue;
                    }
                    float bestDistance = distance[index];
                    float bestAlpha = alpha[index];
                    for (NeighborStep step : neighbors) {
                        int nx = x + step.x;
                        int ny = y + step.y;
                        int nz = z + step.z;
                        if (nx < 0 || nx >= xSize
                                || ny < 0 || ny >= sideSize
                                || nz < 0 || nz >= sideSize) {
                            continue;
                        }
                        int neighbor = index + step.indexOffset;
                        if (!rawSolid[neighbor]
                                || !Float.isFinite(distance[neighbor])) {
                            continue;
                        }
                        float candidate = distance[neighbor] + step.distance;
                        if (candidate + 1e-6f < bestDistance
                                || (Math.abs(candidate - bestDistance) <= 1e-6f
                                && alpha[neighbor] < bestAlpha)) {
                            bestDistance = candidate;
                            bestAlpha = alpha[neighbor];
                        }
                    }
                    distance[index] = bestDistance;
                    alpha[index] = bestAlpha;
                }
            }
        }
    }

    private boolean hasAnyVoxel(boolean[] voxels) {
        for (boolean voxel : voxels) {
            if (voxel) return true;
        }
        return false;
    }

    private boolean[] floodExteriorEmpty(boolean[] barrier) {
        int xCount = xSize;
        int sliceSize = sideSize;
        boolean[] exterior = new boolean[barrier.length];
        int[] queue = new int[barrier.length];
        int head = 0;
        int tail = 0;

        for (int x = 0; x < xCount; x++) {
            DarkBallPreparationCancellation.check();
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
            DarkBallPreparationCancellation.checkEvery(head);
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

    private int enqueueExteriorBoundary(boolean[] barrier, boolean[] exterior, int[] queue, int tail,
                                               int x, int y, int z) {
        int index = voxelIndexInternal(x, y, z);
        if (barrier[index] || exterior[index]) return tail;
        exterior[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private int enqueueExteriorNeighbor(boolean[] barrier, boolean[] exterior, int[] queue, int tail,
                                               int x, int y, int z) {
        if (x < 0 || x >= xSize
                || y < 0 || y >= sideSize
                || z < 0 || z >= sideSize) {
            return tail;
        }
        int index = voxelIndexInternal(x, y, z);
        if (barrier[index] || exterior[index]) return tail;
        exterior[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    private boolean[] buildPhysicalMaterialMask(float[] core) {
        boolean[] material = new boolean[core.length];
        for (int i = 0; i < core.length; i++) {
            DarkBallPreparationCancellation.checkEvery(i);
            material[i] = core[i] > PHYSICAL_MATERIAL_THRESHOLD;
        }
        return material;
    }

    private float[] computeSignedDistanceFieldInternal(boolean[] material,
                                              float voxelStepX,
                                              float voxelStepYz) {
        float[] insideDistance = computeClassDistanceField(material, true, voxelStepX, voxelStepYz);
        float[] outsideDistance = computeClassDistanceField(material, false, voxelStepX, voxelStepYz);
        float maxDistance = Math.max(
                xSize * voxelStepX,
                sideSize * voxelStepYz
        );
        float[] sdf = new float[material.length];
        for (int i = 0; i < sdf.length; i++) {
            DarkBallPreparationCancellation.checkEvery(i);
            float d = material[i] ? -insideDistance[i] : outsideDistance[i];
            if (!Float.isFinite(d) || Math.abs(d) > maxDistance) {
                d = material[i] ? -maxDistance : maxDistance;
            }
            sdf[i] = d;
        }
        return sdf;
    }

    private float[] computeClassDistanceField(boolean[] material, boolean targetMaterial,
                                                     float voxelStepX, float voxelStepYz) {
        int xCount = xSize;
        int sliceSize = sideSize;
        float[] distance = new float[material.length];
        java.util.Arrays.fill(distance, Float.POSITIVE_INFINITY);

        for (int x = 0; x < xCount; x++) {
            DarkBallPreparationCancellation.check();
            for (int z = 0; z < sliceSize; z++) {
                for (int y = 0; y < sliceSize; y++) {
                    int index = voxelIndexInternal(x, y, z);
                    if (material[index] == targetMaterial) {
                        distance[index] = classBoundaryDistance(
                                material, targetMaterial, x, y, z,
                                voxelStepX, voxelStepYz);
                    }
                }
            }
        }

        NeighborStep[] previous = neighborSteps(voxelStepX, voxelStepYz, true);
        NeighborStep[] next = neighborSteps(voxelStepX, voxelStepYz, false);
        chamferDistancePass(material, targetMaterial, distance, previous, true);
        chamferDistancePass(material, targetMaterial, distance, next, false);
        chamferDistancePass(material, targetMaterial, distance, previous, true);
        chamferDistancePass(material, targetMaterial, distance, next, false);
        return distance;
    }

    private float classBoundaryDistance(boolean[] material,
                                               boolean targetMaterial,
                                               int x, int y, int z,
                                               float voxelStepX,
                                               float voxelStepYz) {
        int xCount = xSize;
        int sliceSize = sideSize;
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

    private float boundaryFaceDistance(boolean[] material,
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
        if (material[voxelIndexInternal(x, y, z)] != targetMaterial) {
            return Math.min(currentBest, faceSpacing * 0.5f);
        }
        return currentBest;
    }

    private void chamferDistancePass(boolean[] material, boolean targetMaterial, float[] distance,
                                            NeighborStep[] neighbors, boolean forward) {
        int xStart = forward ? 0 : xSize - 1;
        int xEnd = forward ? xSize : -1;
        int xStep = forward ? 1 : -1;
        int zStart = forward ? 0 : sideSize - 1;
        int zEnd = forward ? sideSize : -1;
        int zStep = forward ? 1 : -1;
        int yStart = forward ? 0 : sideSize - 1;
        int yEnd = forward ? sideSize : -1;
        int yStep = forward ? 1 : -1;

        for (int x = xStart; x != xEnd; x += xStep) {
            DarkBallPreparationCancellation.check();
            for (int z = zStart; z != zEnd; z += zStep) {
                for (int y = yStart; y != yEnd; y += yStep) {
                    int index = voxelIndexInternal(x, y, z);
                    if (material[index] != targetMaterial) {
                        continue;
                    }

                    float best = distance[index];
                    for (NeighborStep step : neighbors) {
                        int nx = x + step.x;
                        int ny = y + step.y;
                        int nz = z + step.z;
                        if (nx < 0 || nx >= xSize
                                || ny < 0 || ny >= sideSize
                                || nz < 0 || nz >= sideSize) {
                            continue;
                        }
                        int neighbor = index + step.indexOffset;
                        if (material[neighbor] != targetMaterial) {
                            continue;
                        }
                        float candidate = distance[neighbor] + step.distance;
                        if (candidate < best) {
                            best = candidate;
                        }
                    }
                    distance[index] = best;
                }
            }
        }
    }

    /**
     * The same thirteen steps are visited at every voxel in one sweep. Build
     * their physical lengths and flat offsets once, retaining the original
     * x/z/y order because nearest-alpha ties depend on that order.
     */
    private NeighborStep[] neighborSteps(float voxelStepX, float voxelStepYz, boolean forward) {
        NeighborStep[] neighbors = new NeighborStep[13];
        int index = 0;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int oy = -1; oy <= 1; oy++) {
                    if (forward ? isPreviousNeighbor(ox, oy, oz) : isNextNeighbor(ox, oy, oz)) {
                        neighbors[index++] = new NeighborStep(ox, oy, oz,
                                (ox * sideSize + oz) * sideSize + oy,
                                neighborDistance(ox, oy, oz, voxelStepX, voxelStepYz));
                    }
                }
            }
        }
        return neighbors;
    }

    private record NeighborStep(int x, int y, int z, int indexOffset, float distance) {
    }

    private boolean isPreviousNeighbor(int ox, int oy, int oz) {
        return ox < 0 || (ox == 0 && oz < 0) || (ox == 0 && oz == 0 && oy < 0);
    }

    private boolean isNextNeighbor(int ox, int oy, int oz) {
        return ox > 0 || (ox == 0 && oz > 0) || (ox == 0 && oz == 0 && oy > 0);
    }

    private float neighborDistance(int ox, int oy, int oz, float voxelStepX, float voxelStepYz) {
        float dx = ox * voxelStepX;
        float dy = oy * voxelStepYz;
        float dz = oz * voxelStepYz;
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private float[] computeLocalThicknessFieldInternal(boolean[] material, float voxelStepX, float voxelStepYz) {
        int xCount = xSize;
        int sliceSize = sideSize;
        float[] thickness = new float[material.length];
        Arrays.fill(thickness, Float.POSITIVE_INFINITY);
        float maxThickness = Math.max(xCount * voxelStepX, sliceSize * voxelStepYz);
        // Every occupied cell in one contiguous axis run has the same run
        // length. Measure that run once, preserving the former six-direction
        // definition exactly without walking the same run from every cell.
        for (int z = 0; z < sliceSize; z++) {
            DarkBallPreparationCancellation.check();
            for (int y = 0; y < sliceSize; y++) {
                applyThicknessRuns(material, thickness, voxelIndexInternal(0, y, z),
                        sliceSize * sliceSize, xCount, voxelStepX);
            }
        }
        for (int x = 0; x < xCount; x++) {
            DarkBallPreparationCancellation.check();
            for (int line = 0; line < sliceSize; line++) {
                applyThicknessRuns(material, thickness, voxelIndexInternal(x, 0, line),
                        1, sliceSize, voxelStepYz);
                applyThicknessRuns(material, thickness, voxelIndexInternal(x, line, 0),
                        sliceSize, sliceSize, voxelStepYz);
            }
        }
        for (int index = 0; index < material.length; index++) {
            DarkBallPreparationCancellation.checkEvery(index);
            thickness[index] = material[index]
                    ? Mth.clamp(thickness[index], Math.min(voxelStepX, voxelStepYz), maxThickness)
                    : 0.0f;
        }
        return thickness;
    }

    private void applyThicknessRuns(boolean[] material, float[] thickness,
                                           int start, int stride, int length, float voxelStep) {
        for (int cell = 0; cell < length;) {
            DarkBallPreparationCancellation.checkEvery(cell);
            if (!material[start + cell * stride]) {
                cell++;
                continue;
            }
            int first = cell++;
            while (cell < length && material[start + cell * stride]) {
                cell++;
            }
            float runThickness = (cell - first) * voxelStep;
            for (int occupied = first; occupied < cell; occupied++) {
                int index = start + occupied * stride;
                thickness[index] = Math.min(thickness[index], runThickness);
            }
        }
    }

    private float pointTriangleDistanceSqr(float px, float py, float pz,
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

    private void setBarycentric(float[] target, float a, float b, float c) {
        if (target == null) {
            return;
        }
        target[0] = a;
        target[1] = b;
        target[2] = c;
    }

    private float dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }

    private float distanceSqr(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = ax - bx;
        float dy = ay - by;
        float dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private void splatSoftVolume(float[] core, float lx, float ly, float lz) {
        if (lx < -0.05f || lx > 1.05f || ly < -0.08f || ly > 1.08f || lz < -0.08f || lz > 1.08f) return;

        float cx = lx * (xSize - 1);
        float cy = ly * (sideSize - 1);
        float cz = lz * (sideSize - 1);
        int radius = 3;
        int minX = Mth.clamp((int) Math.floor(cx - radius), 0, xSize - 1);
        int maxX = Mth.clamp((int) Math.ceil(cx + radius), 0, xSize - 1);
        int minY = Mth.clamp((int) Math.floor(cy - radius), 0, sideSize - 1);
        int maxY = Mth.clamp((int) Math.ceil(cy + radius), 0, sideSize - 1);
        int minZ = Mth.clamp((int) Math.floor(cz - radius), 0, sideSize - 1);
        int maxZ = Mth.clamp((int) Math.ceil(cz + radius), 0, sideSize - 1);

        for (int x = minX; x <= maxX; x++) {
            DarkBallPreparationCancellation.check();
            float dx = (x - cx) / 1.75f;
            for (int z = minZ; z <= maxZ; z++) {
                float dz = (z - cz) / 1.95f;
                for (int y = minY; y <= maxY; y++) {
                    float dy = (y - cy) / 1.95f;
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > 2.75f) continue;

                    float coreWeight = DarkBallCaptureMath.smoothstep(0.95f, 0.04f, d2) * 0.78f;
                    int index = voxelIndexInternal(x, y, z);
                    core[index] = Math.max(core[index], coreWeight);
                }
            }
        }
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
