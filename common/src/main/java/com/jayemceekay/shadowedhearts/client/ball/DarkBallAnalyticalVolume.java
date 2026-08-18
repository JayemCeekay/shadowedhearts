package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;

/**
 * Immutable analytical body data plus a lightweight advected SDF surface band.
 *
 * <p>The body is never transported. Its signed distance, thickness, and release
 * order are uploaded once and evaluated analytically by the volume shader. Only
 * the narrow surface shell owns time-varying density and velocity state.
 */
final class DarkBallAnalyticalVolume {
    // Full source-resolution evaluation profile. Keep these matched to
    // DarkBallVolumeGrid while comparing silhouette fidelity; the preferred
    // GPU transport derives its body atlas dimensions from this class.
    static final int X_SIZE = 96;
    static final int Y_SIZE = 64;
    static final int Z_SIZE = 64;
    static final int CELL_COUNT = X_SIZE * Y_SIZE * Z_SIZE;
    static final int ATLAS_WIDTH = X_SIZE * Z_SIZE;
    static final int ATLAS_HEIGHT = Y_SIZE;

    private static final int[] NEIGHBOR_X = {-1, 1, 0, 0, 0, 0};
    private static final int[] NEIGHBOR_Y = {0, 0, -1, 1, 0, 0};
    private static final int[] NEIGHBOR_Z = {0, 0, 0, 0, -1, 1};
    private static final float INF = Float.POSITIVE_INFINITY;
    private static final float INLET_HEIGHT_FLOOR_PERCENTILE = 0.70f;
    private static final float INLET_TARGET_HEIGHT_PERCENTILE = 0.84f;
    private static final float INLET_MINIMUM_FACING = 0.05f;

    private final DarkBallVolumeBuildResult volume;
    private final DarkBallVfxQuality quality;
    private final float[] signedDistance = new float[CELL_COUNT];
    private final float[] localThickness = new float[CELL_COUNT];
    private final float[] normalX = new float[CELL_COUNT];
    private final float[] normalY = new float[CELL_COUNT];
    private final float[] normalZ = new float[CELL_COUNT];
    private final float[] releaseOrder = new float[CELL_COUNT];
    private final float[] transportOrder = new float[CELL_COUNT];
    private final float[] shellBase = new float[CELL_COUNT];
    private final float[] shellDensity = new float[CELL_COUNT];
    private final float[] nextShellDensity = new float[CELL_COUNT];
    private final float[] velocityX = new float[CELL_COUNT];
    private final float[] velocityY = new float[CELL_COUNT];
    private final float[] velocityZ = new float[CELL_COUNT];
    private final float[] nextVelocityX = new float[CELL_COUNT];
    private final float[] nextVelocityY = new float[CELL_COUNT];
    private final float[] nextVelocityZ = new float[CELL_COUNT];
    private final int[] componentId = new int[CELL_COUNT];
    private final FloatBuffer shapeUpload = BufferUtils.createFloatBuffer(CELL_COUNT * 4);
    // R carries one globally consistent inlet-distance potential used only by
    // visual spike transport. GBA carry the precomputed, normalized source-SDF
    // direction. Depletion deliberately remains on the independent geodesic B
    // channel of the main shape atlas.
    private final FloatBuffer surfaceAttributeUpload = BufferUtils.createFloatBuffer(CELL_COUNT * 4);
    private final FloatBuffer shellUpload = BufferUtils.createFloatBuffer(CELL_COUNT * 4);
    private final Vector3f outletLocal = new Vector3f();
    private final Vector3f ballLocal = new Vector3f();

    private int componentCount;
    private int primaryComponent = -1;
    private int primaryVoxelCount;
    private int outletIndex = -1;
    private float occupiedBodyMaxX;
    private int shapeTextureId;
    private int surfaceAttributeTextureId;
    private int shellTextureId;
    private boolean shellTextureDirty = true;
    private float accumulator;

    private DarkBallAnalyticalVolume(DarkBallVolumeBuildResult volume, DarkBallVfxQuality quality) {
        this.volume = volume;
        this.quality = quality;
    }

    static DarkBallAnalyticalVolume build(DarkBallVolumeBuildResult volume,
                                          Vector3f initialBallLocal,
                                          DarkBallVfxQuality quality) {
        if (volume == null || volume.signedDistanceField() == null
                || volume.sdfGradientX() == null || volume.sdfGradientY() == null
                || volume.sdfGradientZ() == null || volume.localThicknessField() == null) {
            return null;
        }

        DarkBallAnalyticalVolume result = new DarkBallAnalyticalVolume(
                volume,
                quality == null ? DarkBallVfxQuality.MEDIUM : quality
        );
        result.updateBallLocal(initialBallLocal);
        result.sampleSourceFields();
        result.labelComponents();
        result.selectOutlet();
        if (result.outletIndex < 0) {
            return null;
        }
        result.outletLocal.set(result.cellCenterLocal(result.outletIndex));
        result.computeOccupiedBodyMaxX();
        result.computeReleaseOrder();
        result.computeTransportOrder();
        result.initializeShell();
        return result;
    }

    DarkBallVfxQuality quality() {
        return quality;
    }

    Vector3f outletLocal() {
        return new Vector3f(outletLocal);
    }

    DarkBallSurfaceMesh buildSurfaceMesh(
            BooleanSupplier cancellationRequested) {
        return DarkBallSurfaceMeshExtractor.extract(
                new DarkBallSurfaceMeshExtractor.GridInput(
                        X_SIZE,
                        Y_SIZE,
                        Z_SIZE,
                        volume.captureLength(),
                        volume.radius(),
                        signedDistance,
                        normalX,
                        normalY,
                        normalZ,
                        localThickness,
                        releaseOrder,
                        transportOrder),
                cancellationRequested);
    }

    /**
     * Chooses an inlet center that is guaranteed to lie inside the primary
     * connected component. A fixed metric inset from the surface can cross a
     * thin horn, tail, or limb and leave the transfer slab with no density.
     */
    Vector3f interiorSiphonRootLocal(Vector3f towardBall, float requestedInset) {
        Vector3f outward = new Vector3f(towardBall).sub(outletLocal);
        normalizeOrDefault(outward, 1.0f, 0.0f, 0.0f);

        float voxel = shellVoxelSize();
        float inset = Math.max(requestedInset, voxel * 1.5f);
        Vector3f preferred = new Vector3f(outletLocal).fma(-inset, outward);
        float searchRadius = Math.max(inset * 2.4f, voxel * 4.0f);
        float searchRadiusSquared = searchRadius * searchRadius;
        float minimumInteriorDepth = voxel * 0.20f;
        Vector3f worldUpLocal = worldUpLocal();
        float outletHeight = outletLocal.dot(worldUpLocal);
        float minimumRootHeight = outletHeight
                - Math.max(inset * 1.35f, voxel * 3.0f);
        int bestIndex = -1;
        float bestScore = Float.POSITIVE_INFINITY;

        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent
                    || signedDistance[i] > -minimumInteriorDepth) {
                continue;
            }
            Vector3f point = cellCenterLocal(i);
            float outletDistanceSquared = point.distanceSquared(outletLocal);
            float pointHeight = point.dot(worldUpLocal);
            if (outletDistanceSquared > searchRadiusSquared
                    || pointHeight < minimumRootHeight) {
                continue;
            }
            float depthReward = Math.min(-signedDistance[i], voxel * 2.0f);
            float heightDrop = Math.max(0.0f, outletHeight - pointHeight);
            float score = point.distanceSquared(preferred)
                    + outletDistanceSquared * 0.08f
                    + heightDrop * heightDrop * 0.16f
                    - depthReward * depthReward * 0.20f;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        if (bestIndex >= 0) {
            return cellCenterLocal(bestIndex);
        }

        // Degenerate/thin captures may not contain a cell as deep as the
        // preferred threshold. Relax depth first while retaining the upper-body
        // height constraint, so a horn or thin crest cannot drag the buried
        // transfer root down into unrelated lower geometry.
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent
                    || signedDistance[i] > 0.0f) {
                continue;
            }
            Vector3f point = cellCenterLocal(i);
            if (point.dot(worldUpLocal) < minimumRootHeight) {
                continue;
            }
            float score = point.distanceSquared(preferred);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0) {
            return cellCenterLocal(bestIndex);
        }

        // Preserve a truly interior P0 even if doing so requires relaxing the
        // height constraint on an exceptionally thin upper surface.
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent
                    || signedDistance[i] > 0.0f) {
                continue;
            }
            float score = cellCenterLocal(i).distanceSquared(preferred);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0) {
            return cellCenterLocal(bestIndex);
        }

        // Pathological sub-voxel captures can contain only the conservative
        // +0.45-voxel component shell. Retain component ownership as the final
        // fallback rather than returning an unrelated point.
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent) {
                continue;
            }
            float score = cellCenterLocal(i).distanceSquared(preferred);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex >= 0 ? cellCenterLocal(bestIndex)
                : new Vector3f(outletLocal);
    }

    float signedDistanceLocal(Vector3f local) {
        float gx = local.x / Math.max(volume.captureLength(), 0.001f) * X_SIZE - 0.5f;
        float gy = (local.y / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f)
                * Y_SIZE - 0.5f;
        float gz = (local.z / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f)
                * Z_SIZE - 0.5f;
        if (gx < -1.0f || gx > X_SIZE || gy < -1.0f || gy > Y_SIZE
                || gz < -1.0f || gz > Z_SIZE) {
            return volume.radius() * 4.0f;
        }
        return sampleGrid(signedDistance, gx, gy, gz);
    }

    float occupiedBodyMaxX() {
        return occupiedBodyMaxX;
    }

    void updateBallLocal(Vector3f value) {
        if (value != null) {
            ballLocal.set(value);
            ballLocal.x = Math.max(ballLocal.x, volume.bodyRadius() * 0.25f);
        }
    }

    void advance(float siphonProgress, float destabilization, float dt) {
        if (dt <= 0.0f) {
            return;
        }

        accumulator += Math.min(dt, 0.12f);
        float step = quality.shellStepSeconds();
        int maxSteps = quality == DarkBallVfxQuality.HIGH ? 3 : 2;
        int steps = 0;
        while (accumulator >= step && steps < maxSteps) {
            simulateShellStep(Mth.clamp(siphonProgress, 0.0f, 1.20f),
                    Mth.clamp(destabilization, 0.0f, 1.0f), step);
            accumulator -= step;
            steps++;
        }
        if (accumulator >= step) {
            accumulator = step * 0.95f;
        }
    }

    int uploadShapeTexture() {
        if (shapeTextureId != 0) {
            return shapeTextureId;
        }

        shapeTextureId = createAtlasTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shapeTextureId);
        shapeUpload.clear();
        for (int y = 0; y < Y_SIZE; y++) {
            for (int z = 0; z < Z_SIZE; z++) {
                for (int x = 0; x < X_SIZE; x++) {
                    int index = index(x, y, z);
                    shapeUpload.put(signedDistance[index]);
                    shapeUpload.put(localThickness[index]);
                    shapeUpload.put(releaseOrder[index]);
                    // Only the outlet-connected primary component may seed the
                    // mobile field. Other disconnected pieces remain visible in
                    // the anchored silhouette, but cannot strand transported
                    // density against an isolated containment boundary.
                    shapeUpload.put(componentId[index] == primaryComponent ? 1.0f : 0.0f);
                }
            }
        }
        shapeUpload.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_FLOAT, shapeUpload);
        return shapeTextureId;
    }

    int uploadSurfaceAttributeTexture() {
        if (surfaceAttributeTextureId != 0) {
            return surfaceAttributeTextureId;
        }

        surfaceAttributeTextureId = createAtlasTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, surfaceAttributeTextureId);
        surfaceAttributeUpload.clear();
        for (int y = 0; y < Y_SIZE; y++) {
            for (int z = 0; z < Z_SIZE; z++) {
                for (int x = 0; x < X_SIZE; x++) {
                    int index = index(x, y, z);
                    surfaceAttributeUpload.put(transportOrder[index]);
                    surfaceAttributeUpload.put(normalX[index]);
                    surfaceAttributeUpload.put(normalY[index]);
                    surfaceAttributeUpload.put(normalZ[index]);
                }
            }
        }
        surfaceAttributeUpload.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                surfaceAttributeUpload);
        return surfaceAttributeTextureId;
    }

    int uploadShellTexture() {
        if (shellTextureId == 0) {
            shellTextureId = createAtlasTexture();
            shellTextureDirty = true;
        }
        if (!shellTextureDirty) {
            return shellTextureId;
        }

        shellUpload.clear();
        float velocityScale = 1.0f / Math.max(volume.bodyRadius(), 0.001f);
        for (int y = 0; y < Y_SIZE; y++) {
            for (int z = 0; z < Z_SIZE; z++) {
                for (int x = 0; x < X_SIZE; x++) {
                    int index = index(x, y, z);
                    shellUpload.put(Mth.clamp(shellDensity[index], 0.0f, 1.5f));
                    shellUpload.put(Mth.clamp(velocityX[index] * velocityScale, -2.0f, 2.0f));
                    shellUpload.put(Mth.clamp(velocityY[index] * velocityScale, -2.0f, 2.0f));
                    shellUpload.put(Mth.clamp(velocityZ[index] * velocityScale, -2.0f, 2.0f));
                }
            }
        }
        shellUpload.flip();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shellTextureId);
        if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) == 0) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F,
                    ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_FLOAT, shellUpload);
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                    ATLAS_WIDTH, ATLAS_HEIGHT, GL11.GL_RGBA, GL11.GL_FLOAT, shellUpload);
        }
        shellTextureDirty = false;
        return shellTextureId;
    }

    void destroy() {
        if (shapeTextureId != 0) {
            GL11.glDeleteTextures(shapeTextureId);
            shapeTextureId = 0;
        }
        if (surfaceAttributeTextureId != 0) {
            GL11.glDeleteTextures(surfaceAttributeTextureId);
            surfaceAttributeTextureId = 0;
        }
        if (shellTextureId != 0) {
            GL11.glDeleteTextures(shellTextureId);
            shellTextureId = 0;
        }
    }

    String summary() {
        return "grid=" + X_SIZE + "x" + Y_SIZE + "x" + Z_SIZE
                + ", shellWidth=" + quality.shellWidthVoxels()
                + ", components=" + componentCount
                + ", primaryVoxels=" + primaryVoxelCount
                + ", outlet=" + outletIndex;
    }

    private void sampleSourceFields() {
        float[] sourceSdf = volume.signedDistanceField();
        float[] sourceThickness = volume.localThicknessField();
        float[] sourceGx = volume.sdfGradientX();
        float[] sourceGy = volume.sdfGradientY();
        float[] sourceGz = volume.sdfGradientZ();

        for (int y = 0; y < Y_SIZE; y++) {
            for (int z = 0; z < Z_SIZE; z++) {
                for (int x = 0; x < X_SIZE; x++) {
                    int index = index(x, y, z);
                    Vector3f local = cellCenterLocal(index);
                    float gx = local.x / Math.max(volume.captureLength(), 0.001f)
                            * (DarkBallVolumeGrid.X_SLICES - 1);
                    float gy = (local.y / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f)
                            * (DarkBallVolumeGrid.SLICE_SIZE - 1);
                    float gz = (local.z / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f)
                            * (DarkBallVolumeGrid.SLICE_SIZE - 1);

                    signedDistance[index] = sampleSource(sourceSdf, gx, gy, gz);
                    localThickness[index] = Math.max(0.0f, sampleSource(sourceThickness, gx, gy, gz));
                    normalX[index] = sampleSource(sourceGx, gx, gy, gz);
                    normalY[index] = sampleSource(sourceGy, gx, gy, gz);
                    normalZ[index] = sampleSource(sourceGz, gx, gy, gz);
                    normalizeNormal(index);
                }
            }
        }
    }

    private void labelComponents() {
        Arrays.fill(componentId, -1);
        int[] queue = new int[CELL_COUNT];
        int nextId = 0;
        int bestCount = 0;

        for (int start = 0; start < CELL_COUNT; start++) {
            if (componentId[start] >= 0 || !isMaterial(start)) {
                continue;
            }

            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            componentId[start] = nextId;
            int count = 0;
            while (head < tail) {
                int current = queue[head++];
                count++;
                int x = xOf(current);
                int y = yOf(current);
                int z = zOf(current);
                for (int n = 0; n < 6; n++) {
                    int nx = x + NEIGHBOR_X[n];
                    int ny = y + NEIGHBOR_Y[n];
                    int nz = z + NEIGHBOR_Z[n];
                    if (!inBounds(nx, ny, nz)) {
                        continue;
                    }
                    int neighbor = index(nx, ny, nz);
                    if (componentId[neighbor] < 0 && isMaterial(neighbor)) {
                        componentId[neighbor] = nextId;
                        queue[tail++] = neighbor;
                    }
                }
            }

            if (count > bestCount) {
                bestCount = count;
                primaryComponent = nextId;
            }
            nextId++;
        }

        componentCount = nextId;
        primaryVoxelCount = bestCount;
    }

    private void selectOutlet() {
        float shellThreshold = shellVoxelSize() * 1.6f;
        float[] candidateHeights = new float[CELL_COUNT];
        int candidateCount = 0;
        Vector3f worldUpLocal = worldUpLocal();
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent
                    || Math.abs(signedDistance[i]) > shellThreshold) {
                continue;
            }
            candidateHeights[candidateCount++] = cellCenterLocal(i)
                    .dot(worldUpLocal);
        }

        if (candidateCount > 0) {
            Arrays.sort(candidateHeights, 0, candidateCount);
            float heightFloor = sortedPercentile(candidateHeights, candidateCount,
                    INLET_HEIGHT_FLOOR_PERCENTILE);
            float targetHeight = sortedPercentile(candidateHeights, candidateCount,
                    INLET_TARGET_HEIGHT_PERCENTILE);
            float lowHeight = sortedPercentile(candidateHeights, candidateCount, 0.10f);
            float highHeight = sortedPercentile(candidateHeights, candidateCount, 0.95f);
            float heightSpan = Math.max(highHeight - lowHeight, shellVoxelSize() * 4.0f);

            outletIndex = selectUpperOutletCandidate(shellThreshold, worldUpLocal,
                    heightFloor, targetHeight, heightSpan, true);
            if (outletIndex < 0) {
                outletIndex = selectUpperOutletCandidate(shellThreshold, worldUpLocal,
                        heightFloor, targetHeight, heightSpan, false);
            }
        }

        if (outletIndex >= 0) {
            return;
        }
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] == primaryComponent) {
                outletIndex = i;
                return;
            }
        }
    }

    private int selectUpperOutletCandidate(float shellThreshold,
                                           Vector3f worldUpLocal,
                                           float heightFloor,
                                           float targetHeight,
                                           float heightSpan,
                                           boolean requireBallFacing) {
        float bestScore = Float.POSITIVE_INFINITY;
        int bestIndex = -1;
        float extent = Math.max(volume.captureLength(), volume.radius() * 2.0f);
        float extentSquared = Math.max(extent * extent, 0.0001f);
        float thicknessScale = Math.max(shellVoxelSize() * 3.0f, 0.0001f);

        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent
                    || Math.abs(signedDistance[i]) > shellThreshold) {
                continue;
            }
            Vector3f point = cellCenterLocal(i);
            float height = point.dot(worldUpLocal);
            if (height < heightFloor) {
                continue;
            }

            Vector3f toBall = new Vector3f(ballLocal).sub(point);
            float ballDistanceSquared = toBall.lengthSquared();
            normalizeOrDefault(toBall, 1.0f, 0.0f, 0.0f);
            float facing = normalX[i] * toBall.x
                    + normalY[i] * toBall.y
                    + normalZ[i] * toBall.z;
            if (requireBallFacing && facing < INLET_MINIMUM_FACING) {
                continue;
            }

            float normalizedHeightError = (height - targetHeight) / heightSpan;
            float normalizedThickness = Mth.clamp(
                    localThickness[i] / thicknessScale, 0.0f, 1.0f);
            float facingPenalty = 1.0f - Mth.clamp(facing, 0.0f, 1.0f);
            float score = normalizedHeightError * normalizedHeightError * 1.60f
                    + ballDistanceSquared / extentSquared * 0.30f
                    + (1.0f - normalizedThickness) * 0.25f
                    + facingPenalty * 0.20f;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private Vector3f worldUpLocal() {
        Vector3f result = new Vector3f(
                (float) volume.axis().y,
                (float) volume.side().y,
                (float) volume.up().y
        );
        normalizeOrDefault(result, 0.0f, 0.0f, 1.0f);
        return result;
    }

    static float sortedPercentile(float[] sortedValues, int count, float percentile) {
        if (sortedValues == null || sortedValues.length == 0 || count <= 0) {
            return 0.0f;
        }
        int boundedCount = Math.min(count, sortedValues.length);
        float position = Mth.clamp(percentile, 0.0f, 1.0f)
                * (boundedCount - 1);
        int lower = Mth.floor(position);
        int upper = Math.min(lower + 1, boundedCount - 1);
        return Mth.lerp(position - lower, sortedValues[lower], sortedValues[upper]);
    }

    private void computeReleaseOrder() {
        Arrays.fill(releaseOrder, INF);
        PriorityQueue<Node> queue = new PriorityQueue<>();
        releaseOrder[outletIndex] = 0.0f;
        queue.add(new Node(outletIndex, 0.0f));
        float maxDistance = 0.0f;
        float stepX = volume.captureLength() / X_SIZE;
        float stepY = volume.radius() * 2.0f / Y_SIZE;
        float stepZ = volume.radius() * 2.0f / Z_SIZE;

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node.distance > releaseOrder[node.index] + 0.00001f) {
                continue;
            }
            maxDistance = Math.max(maxDistance, node.distance);
            int x = xOf(node.index);
            int y = yOf(node.index);
            int z = zOf(node.index);
            // Six-neighbor propagation produces Manhattan-distance terraces
            // that become visible as parallel bands once the screen composite
            // outlines each remaining island. A metric 26-neighbor stencil
            // keeps the same geodesic far-to-inlet order with a much rounder,
            // continuous front.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int nx = x + dx;
                        int ny = y + dy;
                        int nz = z + dz;
                        if (!inBounds(nx, ny, nz)) {
                            continue;
                        }
                        int neighbor = index(nx, ny, nz);
                        if (componentId[neighbor] != primaryComponent) {
                            continue;
                        }
                        float metricStep = (float) Math.sqrt(
                                dx * dx * stepX * stepX
                                        + dy * dy * stepY * stepY
                                        + dz * dz * stepZ * stepZ);
                        float boundaryDepth = (Math.abs(signedDistance[node.index])
                                + Math.abs(signedDistance[neighbor])) * 0.5f;
                        float boundaryBias = 1.0f + Mth.clamp(boundaryDepth
                                / Math.max(shellVoxelSize() * 2.0f, 0.0001f),
                                0.0f, 0.32f);
                        float candidate = node.distance + metricStep * boundaryBias;
                        if (candidate < releaseOrder[neighbor]) {
                            releaseOrder[neighbor] = candidate;
                            queue.add(new Node(neighbor, candidate));
                        }
                    }
                }
            }
        }

        float normalizer = Math.max(maxDistance, shellVoxelSize());
        Vector3f outlet = cellCenterLocal(outletIndex);
        float fallbackMax = (float) Math.sqrt(volume.captureLength() * volume.captureLength()
                + volume.radius() * volume.radius() * 8.0f);
        for (int i = 0; i < CELL_COUNT; i++) {
            if (Float.isFinite(releaseOrder[i])) {
                releaseOrder[i] = farToOutletReleaseOrder(
                        releaseOrder[i] / normalizer);
            } else {
                Vector3f point = cellCenterLocal(i);
                float distance = point.distance(outlet);
                releaseOrder[i] = farToOutletReleaseOrder(
                        distance / Math.max(fallbackMax, 0.001f));
            }
        }
    }

    /**
     * The depletion equation removes low release-order values first. Store the
     * farthest material near zero and the inlet near one so the body collapses
     * toward the siphon instead of eroding outward from it.
     */
    static float farToOutletReleaseOrder(float normalizedOutletDistance) {
        return 1.0f - Mth.clamp(normalizedOutletDistance, 0.0f, 1.0f);
    }

    /**
     * Produces the visual-only transport potential. Unlike depletion release
     * order, this uses one metric in material, exterior, and disconnected
     * regions, so trilinear sampling cannot cross a geodesic/Euclidean seam.
     */
    private void computeTransportOrder() {
        Vector3f outlet = cellCenterLocal(outletIndex);
        float maximumMaterialDistance = 0.0f;
        for (int i = 0; i < CELL_COUNT; i++) {
            float outletDistance = cellCenterLocal(i).distance(outlet);
            transportOrder[i] = outletDistance;
            if (componentId[i] >= 0) {
                maximumMaterialDistance = Math.max(maximumMaterialDistance,
                        outletDistance);
            }
        }
        maximumMaterialDistance = Math.max(maximumMaterialDistance,
                shellVoxelSize());
        for (int i = 0; i < CELL_COUNT; i++) {
            transportOrder[i] = euclideanTransportOrder(
                    transportOrder[i],
                    maximumMaterialDistance);
        }
    }

    static float euclideanTransportOrder(float outletDistance,
                                         float maximumMaterialDistance) {
        float normalizedDistance = Math.max(outletDistance, 0.0f)
                / Math.max(maximumMaterialDistance, 0.0001f);
        return 1.0f - Mth.clamp(normalizedDistance, 0.0f, 1.0f);
    }

    private void computeOccupiedBodyMaxX() {
        float maximum = 0.0f;
        float cellWidth = volume.captureLength() / X_SIZE;
        for (int index = 0; index < CELL_COUNT; index++) {
            if (componentId[index] < 0) {
                continue;
            }
            // Use the occupied cell's outer face rather than its center so a
            // extracted surface never clips the last body voxel.
            float cellMaximum = (xOf(index) + 1.0f) * cellWidth;
            maximum = Math.max(maximum, cellMaximum);
        }
        occupiedBodyMaxX = Mth.clamp(maximum, cellWidth, volume.captureLength());
    }

    private void initializeShell() {
        float width = shellVoxelSize() * quality.shellWidthVoxels();
        for (int i = 0; i < CELL_COUNT; i++) {
            float band = 1.0f - smoothstep(width * 0.45f, width, Math.abs(signedDistance[i]));
            float component = componentId[i] >= 0 ? 1.0f : 0.72f;
            shellBase[i] = band * component;
            shellDensity[i] = shellBase[i];
        }
        shellTextureDirty = true;
    }

    private void simulateShellStep(float siphonProgress, float destabilization, float step) {
        float releaseFront = Mth.clamp(siphonProgress * 1.16f - 0.045f, 0.0f, 1.30f);
        float releaseWidth = 0.110f + (1.0f - Math.min(siphonProgress, 1.0f)) * 0.040f;
        Vector3f curveTangent = DarkBallCaptureMath.siphonCurveTangent(
                outletLocal, ballLocal, volume.bodyRadius(), 0.0f);
        normalizeOrDefault(curveTangent, 1.0f, 0.0f, 0.0f);

        for (int i = 0; i < CELL_COUNT; i++) {
            if (shellBase[i] <= 0.0001f) {
                nextShellDensity[i] = 0.0f;
                nextVelocityX[i] = 0.0f;
                nextVelocityY[i] = 0.0f;
                nextVelocityZ[i] = 0.0f;
                continue;
            }

            Vector3f point = cellCenterLocal(i);
            Vector3f normal = new Vector3f(normalX[i], normalY[i], normalZ[i]);
            Vector3f toOutlet = new Vector3f(outletLocal).sub(point);
            float outletDistance = Math.max(toOutlet.length(), 0.0001f);
            toOutlet.div(outletDistance);

            float phase = deterministicNoise(i, 17) * 6.2831855f + destabilization * 5.7f;
            Vector3f noiseAxis = new Vector3f(
                    (float) Math.sin(phase * 1.13f),
                    (float) Math.cos(phase * 0.91f),
                    (float) Math.sin(phase * 0.73f + 1.7f)
            );
            Vector3f tangent = normal.cross(noiseAxis, new Vector3f());
            normalizeOrDefault(tangent, 0.0f, 1.0f, 0.0f);
            Vector3f curl = normal.cross(tangent, new Vector3f());
            normalizeOrDefault(curl, 0.0f, 0.0f, 1.0f);

            float nearOutlet = 1.0f - smoothstep(volume.bodyRadius() * 0.25f,
                    volume.bodyRadius() * 1.45f, outletDistance);
            float pull = siphonProgress * (0.24f + nearOutlet * 1.35f);
            float boil = (0.10f + destabilization * 0.34f)
                    * (0.72f + deterministicNoise(i, 31) * 0.28f);

            Vector3f velocity = new Vector3f(velocityX[i], velocityY[i], velocityZ[i]);
            velocity.mul((float) Math.exp(-3.2f * step));
            velocity.fma(step * pull, toOutlet);
            velocity.fma(step * boil, tangent);
            velocity.fma(step * boil * 0.36f, curl);
            velocity.fma(step * nearOutlet * siphonProgress * 0.92f, curveTangent);

            float normalVelocity = velocity.dot(normal);
            velocity.fma(-normalVelocity * 0.82f, normal);
            velocity.fma(-signedDistance[i] * step * 4.8f, normal);
            clampLength(velocity, Math.max(volume.bodyRadius() * 1.25f, 0.15f));

            Vector3f backtrace = new Vector3f(point).fma(-step, velocity);
            float advected = sampleShell(shellDensity, backtrace);
            float laggedFront = Math.max(0.0f, releaseFront - 0.075f);
            float shellRemaining = smoothstep(laggedFront - releaseWidth,
                    laggedFront + releaseWidth, releaseOrder[i]);
            if (siphonProgress <= 0.001f) {
                shellRemaining = 1.0f;
            }

            float fluctuation = 0.84f + 0.16f * (float) Math.sin(
                    phase + siphonProgress * 13.0f + releaseOrder[i] * 8.0f);
            float target = shellBase[i] * shellRemaining * fluctuation;
            nextShellDensity[i] = Mth.lerp(0.34f, advected * shellRemaining, target);
            nextVelocityX[i] = velocity.x;
            nextVelocityY[i] = velocity.y;
            nextVelocityZ[i] = velocity.z;
        }

        System.arraycopy(nextShellDensity, 0, shellDensity, 0, CELL_COUNT);
        System.arraycopy(nextVelocityX, 0, velocityX, 0, CELL_COUNT);
        System.arraycopy(nextVelocityY, 0, velocityY, 0, CELL_COUNT);
        System.arraycopy(nextVelocityZ, 0, velocityZ, 0, CELL_COUNT);
        shellTextureDirty = true;
    }

    private float sampleShell(float[] field, Vector3f local) {
        float gx = local.x / Math.max(volume.captureLength(), 0.001f) * X_SIZE - 0.5f;
        float gy = (local.y / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f) * Y_SIZE - 0.5f;
        float gz = (local.z / Math.max(volume.radius(), 0.001f) * 0.5f + 0.5f) * Z_SIZE - 0.5f;
        if (gx < -1.0f || gx > X_SIZE || gy < -1.0f || gy > Y_SIZE || gz < -1.0f || gz > Z_SIZE) {
            return 0.0f;
        }
        return sampleGrid(field, gx, gy, gz);
    }

    private static float sampleSource(float[] field, float gx, float gy, float gz) {
        float x = Mth.clamp(gx, 0.0f, DarkBallVolumeGrid.X_SLICES - 1.0f);
        float y = Mth.clamp(gy, 0.0f, DarkBallVolumeGrid.SLICE_SIZE - 1.0f);
        float z = Mth.clamp(gz, 0.0f, DarkBallVolumeGrid.SLICE_SIZE - 1.0f);
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        int z0 = Mth.floor(z);
        int x1 = Math.min(x0 + 1, DarkBallVolumeGrid.X_SLICES - 1);
        int y1 = Math.min(y0 + 1, DarkBallVolumeGrid.SLICE_SIZE - 1);
        int z1 = Math.min(z0 + 1, DarkBallVolumeGrid.SLICE_SIZE - 1);
        float tx = x - x0;
        float ty = y - y0;
        float tz = z - z0;

        float c000 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y0, z0)];
        float c100 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y0, z0)];
        float c010 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y1, z0)];
        float c110 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y1, z0)];
        float c001 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y0, z1)];
        float c101 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y0, z1)];
        float c011 = field[DarkBallVolumeVoxelizer.voxelIndex(x0, y1, z1)];
        float c111 = field[DarkBallVolumeVoxelizer.voxelIndex(x1, y1, z1)];
        return trilinear(c000, c100, c010, c110, c001, c101, c011, c111, tx, ty, tz);
    }

    private static float sampleGrid(float[] field, float gx, float gy, float gz) {
        float x = Mth.clamp(gx, 0.0f, X_SIZE - 1.0f);
        float y = Mth.clamp(gy, 0.0f, Y_SIZE - 1.0f);
        float z = Mth.clamp(gz, 0.0f, Z_SIZE - 1.0f);
        int x0 = Mth.floor(x);
        int y0 = Mth.floor(y);
        int z0 = Mth.floor(z);
        int x1 = Math.min(x0 + 1, X_SIZE - 1);
        int y1 = Math.min(y0 + 1, Y_SIZE - 1);
        int z1 = Math.min(z0 + 1, Z_SIZE - 1);
        float tx = x - x0;
        float ty = y - y0;
        float tz = z - z0;
        return trilinear(
                field[index(x0, y0, z0)], field[index(x1, y0, z0)],
                field[index(x0, y1, z0)], field[index(x1, y1, z0)],
                field[index(x0, y0, z1)], field[index(x1, y0, z1)],
                field[index(x0, y1, z1)], field[index(x1, y1, z1)],
                tx, ty, tz
        );
    }

    private static float trilinear(float c000, float c100, float c010, float c110,
                                   float c001, float c101, float c011, float c111,
                                   float tx, float ty, float tz) {
        float c00 = Mth.lerp(tx, c000, c100);
        float c10 = Mth.lerp(tx, c010, c110);
        float c01 = Mth.lerp(tx, c001, c101);
        float c11 = Mth.lerp(tx, c011, c111);
        return Mth.lerp(tz, Mth.lerp(ty, c00, c10), Mth.lerp(ty, c01, c11));
    }

    private void normalizeNormal(int index) {
        float x = normalX[index];
        float y = normalY[index];
        float z = normalZ[index];
        float length = Mth.sqrt(x * x + y * y + z * z);
        if (length <= 0.00001f) {
            normalX[index] = 1.0f;
            normalY[index] = 0.0f;
            normalZ[index] = 0.0f;
        } else {
            normalX[index] = x / length;
            normalY[index] = y / length;
            normalZ[index] = z / length;
        }
    }

    private boolean isMaterial(int index) {
        return signedDistance[index] <= shellVoxelSize() * 0.45f;
    }

    private float shellVoxelSize() {
        return Math.min(volume.captureLength() / X_SIZE, volume.radius() * 2.0f / Y_SIZE);
    }

    private Vector3f cellCenterLocal(int index) {
        int x = xOf(index);
        int y = yOf(index);
        int z = zOf(index);
        return new Vector3f(
                (x + 0.5f) / X_SIZE * volume.captureLength(),
                ((y + 0.5f) / Y_SIZE * 2.0f - 1.0f) * volume.radius(),
                ((z + 0.5f) / Z_SIZE * 2.0f - 1.0f) * volume.radius()
        );
    }

    private static int createAtlasTexture() {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static void clampLength(Vector3f value, float maximum) {
        float lengthSquared = value.lengthSquared();
        if (lengthSquared > maximum * maximum) {
            value.mul(maximum / Mth.sqrt(lengthSquared));
        }
    }

    private static void normalizeOrDefault(Vector3f value, float x, float y, float z) {
        if (value.lengthSquared() < 0.0000001f) {
            value.set(x, y, z);
        } else {
            value.normalize();
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0f : 1.0f;
        }
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float deterministicNoise(int index, int salt) {
        int value = index * 0x1f123bb5 ^ salt * 0x6c8e9cf5;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        return (value & 0x00ffffff) / (float) 0x01000000;
    }

    private static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < X_SIZE && y >= 0 && y < Y_SIZE && z >= 0 && z < Z_SIZE;
    }

    private static int index(int x, int y, int z) {
        return (y * Z_SIZE + z) * X_SIZE + x;
    }

    private static int xOf(int index) {
        return index % X_SIZE;
    }

    private static int zOf(int index) {
        return (index / X_SIZE) % Z_SIZE;
    }

    private static int yOf(int index) {
        return index / (X_SIZE * Z_SIZE);
    }

    private record Node(int index, float distance) implements Comparable<Node> {
        @Override
        public int compareTo(Node other) {
            return Float.compare(distance, other.distance);
        }
    }
}
