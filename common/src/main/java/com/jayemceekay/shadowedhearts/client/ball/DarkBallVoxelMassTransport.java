package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * CPU prototype for the Dark Ball voxel mass transport design.
 *
 * <p>This is deliberately separate from the current splat simulation so the
 * transport field can become the source of truth incrementally.
 */
final class DarkBallVoxelMassTransport {
    static final int X_SIZE = 48;
    static final int Y_SIZE = 32;
    static final int Z_SIZE = 32;
    static final int SIPHON_BINS = 32;
    static final int MASS_ATLAS_WIDTH = X_SIZE * Z_SIZE;
    static final int MASS_ATLAS_HEIGHT = Y_SIZE;

    private static final int CELL_COUNT = X_SIZE * Y_SIZE * Z_SIZE;
    private static final int MAX_DOWNSTREAM = 6;
    private static final float OCCUPANCY_THRESHOLD = 0.018f;
    private static final float MIN_ACTIVE_MASS = 0.000015f;
    private static final float SIM_STEP = 1.0f / 30.0f;
    private static final int MAX_STEPS = 3;

    private final DarkBallVolumeBuildResult volume;
    private final float[] initialMass = new float[CELL_COUNT];
    private final float[] currentMass = new float[CELL_COUNT];
    private final float[] nextMass = new float[CELL_COUNT];
    private final float[] signedDistance = new float[CELL_COUNT];
    private final float[] localThickness = new float[CELL_COUNT];
    private final float[] normalX = new float[CELL_COUNT];
    private final float[] normalY = new float[CELL_COUNT];
    private final float[] normalZ = new float[CELL_COUNT];
    private final float[] potential = new float[CELL_COUNT];
    private final int[] componentId = new int[CELL_COUNT];
    private final byte[] downstreamCount = new byte[CELL_COUNT];
    private final int[] downstreamIndex = new int[CELL_COUNT * MAX_DOWNSTREAM];
    private final float[] downstreamWeight = new float[CELL_COUNT * MAX_DOWNSTREAM];
    private final float[] siphonMass = new float[SIPHON_BINS];
    private final float[] nextSiphonMass = new float[SIPHON_BINS];
    private final FloatBuffer uploadBuffer = BufferUtils.createFloatBuffer(CELL_COUNT);
    private final FloatBuffer siphonUploadBuffer = BufferUtils.createFloatBuffer(SIPHON_BINS);

    private int componentCount;
    private int primaryComponent = -1;
    private int primaryVoxelCount;
    private int outletIndex = -1;
    private float initialTotalMass;
    private float bodyMass;
    private float queuedMass;
    private float absorbedMass;
    private float accumulator;
    private int massTextureId;
    private int siphonTextureId;
    private boolean textureDirty = true;
    private boolean siphonTextureDirty = true;
    private Vector3f outletLocal = new Vector3f();

    private DarkBallVoxelMassTransport(DarkBallVolumeBuildResult volume) {
        this.volume = volume;
    }

    static DarkBallVoxelMassTransport build(DarkBallVolumeBuildResult volume, Vector3f ballLocal) {
        if (volume == null || volume.coreField() == null || volume.signedDistanceField() == null) {
            return null;
        }
        DarkBallVoxelMassTransport transport = new DarkBallVoxelMassTransport(volume);
        transport.initializeMassFields();
        if (transport.initialTotalMass <= 0.0001f) {
            return null;
        }
        transport.labelComponents();
        if (transport.primaryComponent < 0) {
            return null;
        }
        transport.selectOutlet(ballLocal);
        if (transport.outletIndex < 0) {
            return null;
        }
        transport.outletLocal.set(transport.cellCenterLocal(transport.outletIndex));
        transport.computeOutletPotential();
        transport.computeDownstreamWeights();
        return transport;
    }

    void advance(float globalSiphon, float finalFlush, float dt) {
        if (dt <= 0f) {
            return;
        }

        accumulator += Math.min(dt, 0.12f);
        int steps = 0;
        while (accumulator >= SIM_STEP && steps < MAX_STEPS) {
            simulateBodyStep(globalSiphon, finalFlush, SIM_STEP);
            simulateSiphonStep(globalSiphon, finalFlush, SIM_STEP);
            accumulator -= SIM_STEP;
            steps++;
        }
        if (accumulator >= SIM_STEP) {
            accumulator = SIM_STEP * 0.95f;
        }
    }

    int uploadMassTexture() {
        if (massTextureId == 0) {
            massTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, massTextureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            fillUploadBuffer();
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL30.GL_R16F,
                    MASS_ATLAS_WIDTH,
                    MASS_ATLAS_HEIGHT,
                    0,
                    GL11.GL_RED,
                    GL11.GL_FLOAT,
                    uploadBuffer
            );
            textureDirty = false;
            return massTextureId;
        }

        if (textureDirty) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, massTextureId);
            fillUploadBuffer();
            GL11.glTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    MASS_ATLAS_WIDTH,
                    MASS_ATLAS_HEIGHT,
                    GL11.GL_RED,
                    GL11.GL_FLOAT,
                    uploadBuffer
            );
            textureDirty = false;
        }
        return massTextureId;
    }

    int uploadSiphonTexture() {
        if (siphonTextureId == 0) {
            siphonTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, siphonTextureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            fillSiphonUploadBuffer();
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL30.GL_R16F,
                    SIPHON_BINS,
                    1,
                    0,
                    GL11.GL_RED,
                    GL11.GL_FLOAT,
                    siphonUploadBuffer
            );
            siphonTextureDirty = false;
            return siphonTextureId;
        }

        if (siphonTextureDirty) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, siphonTextureId);
            fillSiphonUploadBuffer();
            GL11.glTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    SIPHON_BINS,
                    1,
                    GL11.GL_RED,
                    GL11.GL_FLOAT,
                    siphonUploadBuffer
            );
            siphonTextureDirty = false;
        }
        return siphonTextureId;
    }

    private void fillUploadBuffer() {
        uploadBuffer.clear();
        for (int y = 0; y < Y_SIZE; y++) {
            for (int z = 0; z < Z_SIZE; z++) {
                for (int x = 0; x < X_SIZE; x++) {
                    uploadBuffer.put(currentMass[index(x, y, z)]);
                }
            }
        }
        uploadBuffer.flip();
    }

    private void fillSiphonUploadBuffer() {
        siphonUploadBuffer.clear();
        float normalizer = Math.max(initialTotalMass * 0.018f, 0.0001f);
        for (float mass : siphonMass) {
            siphonUploadBuffer.put(Mth.clamp(mass / normalizer, 0f, 4.0f));
        }
        siphonUploadBuffer.flip();
    }

    float initialTotalMass() {
        return initialTotalMass;
    }

    Vector3f outletLocal() {
        return new Vector3f(outletLocal);
    }

    void destroy() {
        if (massTextureId != 0) {
            GL11.glDeleteTextures(massTextureId);
            massTextureId = 0;
        }
        if (siphonTextureId != 0) {
            GL11.glDeleteTextures(siphonTextureId);
            siphonTextureId = 0;
        }
    }

    float sampleRelativeMass(Vector3f local) {
        float current = trilinearSample(currentMass, local);
        float initial = trilinearSample(initialMass, local);
        return Mth.clamp(current / Math.max(initial, 0.0001f), 0f, 1.45f);
    }

    String summary() {
        return "grid=" + X_SIZE + "x" + Y_SIZE + "x" + Z_SIZE
                + ", components=" + componentCount
                + ", primaryVoxels=" + primaryVoxelCount
                + ", outlet=" + outletIndex
                + ", initialMass=" + format(initialTotalMass)
                + ", bodyMass=" + format(bodyMass);
    }

    float conservationError() {
        float siphon = queuedMass;
        for (float mass : siphonMass) {
            siphon += mass;
        }
        return bodyMass + siphon + absorbedMass - initialTotalMass;
    }

    private void initializeMassFields() {
        int sourceSlice = DarkBallVolumeGrid.SLICE_SIZE;
        float[] core = volume.coreField();
        float[] envelope = volume.envelopeField();
        float[] sdf = volume.signedDistanceField();
        float[] thickness = volume.localThicknessField();
        float[] gradientX = volume.sdfGradientX();
        float[] gradientY = volume.sdfGradientY();
        float[] gradientZ = volume.sdfGradientZ();
        float thicknessReference = Math.max(volume.bodyRadius() * 0.72f, volume.sdfVoxelYz());
        float maxInteriorDepth = Math.max(volume.bodyRadius() * 0.52f, volume.sdfVoxelYz() * 4.0f);

        initialTotalMass = 0f;
        for (int x = 0; x < X_SIZE; x++) {
            int sx = Mth.clamp((int) (((x + 0.5f) / X_SIZE) * DarkBallVolumeGrid.X_SLICES), 0, DarkBallVolumeGrid.X_SLICES - 1);
            for (int z = 0; z < Z_SIZE; z++) {
                int sz = Mth.clamp((int) (((z + 0.5f) / Z_SIZE) * sourceSlice), 0, sourceSlice - 1);
                for (int y = 0; y < Y_SIZE; y++) {
                    int sy = Mth.clamp((int) (((y + 0.5f) / Y_SIZE) * sourceSlice), 0, sourceSlice - 1);
                    int sourceIndex = DarkBallVolumeVoxelizer.voxelIndex(sx, sy, sz);
                    int index = index(x, y, z);

                    float coreValue = valueAt(core, sourceIndex);
                    float envelopeValue = valueAt(envelope, sourceIndex);
                    float occupancy = Math.max(coreValue, envelopeValue * 0.72f);
                    float distance = valueAt(sdf, sourceIndex);
                    float localThick = valueAt(thickness, sourceIndex);
                    signedDistance[index] = distance;
                    localThickness[index] = localThick;

                    Vector3f normal = new Vector3f(
                            valueAt(gradientX, sourceIndex),
                            valueAt(gradientY, sourceIndex),
                            valueAt(gradientZ, sourceIndex)
                    );
                    normalizeOrDefault(normal, 1f, 0f, 0f);
                    normalX[index] = normal.x;
                    normalY[index] = normal.y;
                    normalZ[index] = normal.z;

                    if (coreValue <= OCCUPANCY_THRESHOLD && occupancy <= 0.052f) {
                        continue;
                    }

                    float depthWeight = smoothstep(0f, maxInteriorDepth, -distance);
                    float thicknessWeight = Mth.clamp(localThick / thicknessReference, 0f, 1f);
                    float mass = occupancy
                            * Mth.lerp(depthWeight, 0.68f, 1.18f)
                            * Mth.lerp(thicknessWeight, 0.56f, 1.0f);
                    if (mass <= MIN_ACTIVE_MASS) {
                        continue;
                    }

                    initialMass[index] = mass;
                    currentMass[index] = mass;
                    initialTotalMass += mass;
                }
            }
        }
        bodyMass = initialTotalMass;
    }

    private void labelComponents() {
        Arrays.fill(componentId, -1);
        int[] queue = new int[CELL_COUNT];
        float[] componentMass = new float[CELL_COUNT];
        int[] componentVoxels = new int[CELL_COUNT];
        componentCount = 0;

        for (int i = 0; i < CELL_COUNT; i++) {
            if (!isOccupied(i) || componentId[i] >= 0) {
                continue;
            }

            int head = 0;
            int tail = 0;
            queue[tail++] = i;
            componentId[i] = componentCount;
            while (head < tail) {
                int cell = queue[head++];
                componentMass[componentCount] += initialMass[cell];
                componentVoxels[componentCount]++;

                int x = xOf(cell);
                int y = yOf(cell);
                int z = zOf(cell);
                tail = enqueueComponentNeighbor(queue, tail, componentCount, x - 1, y, z);
                tail = enqueueComponentNeighbor(queue, tail, componentCount, x + 1, y, z);
                tail = enqueueComponentNeighbor(queue, tail, componentCount, x, y - 1, z);
                tail = enqueueComponentNeighbor(queue, tail, componentCount, x, y + 1, z);
                tail = enqueueComponentNeighbor(queue, tail, componentCount, x, y, z - 1);
                tail = enqueueComponentNeighbor(queue, tail, componentCount, x, y, z + 1);
            }
            componentCount++;
        }

        float bestMass = 0f;
        primaryComponent = -1;
        primaryVoxelCount = 0;
        for (int i = 0; i < componentCount; i++) {
            if (componentMass[i] > bestMass) {
                bestMass = componentMass[i];
                primaryComponent = i;
                primaryVoxelCount = componentVoxels[i];
            }
        }
    }

    private int enqueueComponentNeighbor(int[] queue, int tail, int id, int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return tail;
        }
        int neighbor = index(x, y, z);
        if (!isOccupied(neighbor) || componentId[neighbor] >= 0) {
            return tail;
        }
        componentId[neighbor] = id;
        queue[tail++] = neighbor;
        return tail;
    }

    private void selectOutlet(Vector3f ballLocal) {
        Vector3f centroid = primaryCentroid();
        float bestScore = -Float.MAX_VALUE;
        outletIndex = -1;
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent) {
                continue;
            }

            Vector3f local = cellCenterLocal(i);
            Vector3f toBall = new Vector3f(ballLocal).sub(local);
            float ballDistance = toBall.length();
            normalizeOrDefault(toBall, 1f, 0f, 0f);
            Vector3f normal = new Vector3f(normalX[i], normalY[i], normalZ[i]);
            normalizeOrDefault(normal, 1f, 0f, 0f);

            float facing = Mth.clamp(normal.dot(toBall), 0f, 1f);
            float surface = 1.0f - smoothstep(0f, Math.max(volume.bodyRadius() * 0.34f, volume.sdfVoxelYz() * 2.0f), Math.abs(signedDistance[i]));
            float thickness = Mth.clamp(localThickness[i] / Math.max(volume.bodyRadius() * 0.42f, volume.sdfVoxelYz()), 0f, 1f);
            float thinPenalty = 1.0f - smoothstep(0.12f, 0.48f, thickness);
            float centroidPenalty = local.distance(centroid) / Math.max(volume.bodyRadius() * 2.2f, 0.001f);
            float ballPenalty = ballDistance / Math.max(volume.captureLength() + volume.bodyRadius(), 0.001f);

            float score = facing * 2.0f
                    + surface * 1.2f
                    + thickness * 0.75f
                    - thinPenalty * 0.85f
                    - centroidPenalty * 0.18f
                    - ballPenalty * 0.10f;
            if (score > bestScore) {
                bestScore = score;
                outletIndex = i;
            }
        }
    }

    private Vector3f primaryCentroid() {
        Vector3f centroid = new Vector3f();
        float total = 0f;
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent) {
                continue;
            }
            float mass = initialMass[i];
            centroid.fma(mass, cellCenterLocal(i));
            total += mass;
        }
        if (total > 0.0001f) {
            centroid.mul(1.0f / total);
        }
        return centroid;
    }

    private void computeOutletPotential() {
        Arrays.fill(potential, Float.POSITIVE_INFINITY);
        PriorityQueue<Node> queue = new PriorityQueue<>();
        potential[outletIndex] = 0f;
        queue.add(new Node(outletIndex, 0f));

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node.cost > potential[node.index] + 0.00001f) {
                continue;
            }
            int x = xOf(node.index);
            int y = yOf(node.index);
            int z = zOf(node.index);
            relaxPotentialNeighbor(queue, node.index, x - 1, y, z);
            relaxPotentialNeighbor(queue, node.index, x + 1, y, z);
            relaxPotentialNeighbor(queue, node.index, x, y - 1, z);
            relaxPotentialNeighbor(queue, node.index, x, y + 1, z);
            relaxPotentialNeighbor(queue, node.index, x, y, z - 1);
            relaxPotentialNeighbor(queue, node.index, x, y, z + 1);
        }
    }

    private void relaxPotentialNeighbor(PriorityQueue<Node> queue, int from, int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return;
        }
        int to = index(x, y, z);
        if (componentId[to] != primaryComponent) {
            return;
        }
        float thickness01 = Mth.clamp(localThickness[to] / Math.max(volume.bodyRadius() * 0.58f, volume.sdfVoxelYz()), 0f, 1f);
        float boundaryPenalty = 1.0f - smoothstep(-volume.sdfVoxelYz() * 0.5f, volume.bodyRadius() * 0.20f, -signedDistance[to]);
        float traversalCost = 1.0f
                + (1.0f - thickness01) * 0.42f
                + boundaryPenalty * 0.26f;
        float candidate = potential[from] + traversalCost;
        if (candidate < potential[to]) {
            potential[to] = candidate;
            queue.add(new Node(to, candidate));
        }
    }

    private void computeDownstreamWeights() {
        Arrays.fill(downstreamCount, (byte) 0);
        Arrays.fill(downstreamIndex, -1);
        Arrays.fill(downstreamWeight, 0f);
        for (int i = 0; i < CELL_COUNT; i++) {
            if (componentId[i] != primaryComponent || !Float.isFinite(potential[i]) || i == outletIndex) {
                continue;
            }

            int x = xOf(i);
            int y = yOf(i);
            int z = zOf(i);
            float totalRaw = 0f;
            totalRaw += addDownstreamCandidate(i, 0, x - 1, y, z);
            totalRaw += addDownstreamCandidate(i, 1, x + 1, y, z);
            totalRaw += addDownstreamCandidate(i, 2, x, y - 1, z);
            totalRaw += addDownstreamCandidate(i, 3, x, y + 1, z);
            totalRaw += addDownstreamCandidate(i, 4, x, y, z - 1);
            totalRaw += addDownstreamCandidate(i, 5, x, y, z + 1);
            if (totalRaw <= 0.000001f) {
                continue;
            }

            int base = i * MAX_DOWNSTREAM;
            int count = downstreamCount[i] & 0xFF;
            for (int n = 0; n < count; n++) {
                downstreamWeight[base + n] /= totalRaw;
            }
        }
    }

    private float addDownstreamCandidate(int from, int slotHint, int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return 0f;
        }
        int to = index(x, y, z);
        if (componentId[to] != primaryComponent || !Float.isFinite(potential[to])) {
            return 0f;
        }
        float drop = potential[from] - potential[to];
        if (drop <= 0.00001f) {
            return 0f;
        }
        int count = downstreamCount[from] & 0xFF;
        if (count >= MAX_DOWNSTREAM) {
            return 0f;
        }
        float variation = 1.0f + deterministicNoise(from, slotHint) * 0.055f;
        float raw = drop * variation;
        int offset = from * MAX_DOWNSTREAM + count;
        downstreamIndex[offset] = to;
        downstreamWeight[offset] = raw;
        downstreamCount[from] = (byte) (count + 1);
        return raw;
    }

    private void simulateBodyStep(float globalSiphon, float finalFlush, float step) {
        float active = smoothstep(0.025f, 0.22f, globalSiphon);
        float rate = Mth.lerp(finalFlush, Mth.lerp(globalSiphon, 0.18f, 3.35f), 9.0f) * active;
        if (rate <= 0.0001f) {
            return;
        }

        Arrays.fill(nextMass, 0f);
        float transferFraction = 1.0f - (float) Math.exp(-rate * step);
        float extracted = 0f;
        float nextTotal = 0f;

        for (int i = 0; i < CELL_COUNT; i++) {
            float mass = currentMass[i];
            if (mass <= MIN_ACTIVE_MASS || componentId[i] != primaryComponent) {
                nextMass[i] += mass;
                nextTotal += mass;
                continue;
            }

            float outgoing = mass * transferFraction;
            int count = downstreamCount[i] & 0xFF;
            if (i == outletIndex || count == 0) {
                float outletBoost = i == outletIndex ? 1.0f : finalFlush;
                float toReservoir = outgoing * outletBoost;
                extracted += toReservoir;
                float retained = mass - toReservoir;
                nextMass[i] += retained;
                nextTotal += retained;
                continue;
            }

            float retained = mass - outgoing;
            nextMass[i] += retained;
            nextTotal += retained;
            int base = i * MAX_DOWNSTREAM;
            for (int n = 0; n < count; n++) {
                int to = downstreamIndex[base + n];
                float moved = outgoing * downstreamWeight[base + n];
                nextMass[to] += moved;
                nextTotal += moved;
            }
        }

        System.arraycopy(nextMass, 0, currentMass, 0, CELL_COUNT);
        bodyMass = nextTotal;
        queuedMass += extracted;
        textureDirty = true;
    }

    private void simulateSiphonStep(float globalSiphon, float finalFlush, float step) {
        float active = smoothstep(0.025f, 0.18f, globalSiphon);
        if (active <= 0.0001f && queuedMass <= MIN_ACTIVE_MASS) {
            return;
        }

        Arrays.fill(nextSiphonMass, 0f);
        float injectFraction = 1.0f - (float) Math.exp(-Mth.lerp(finalFlush, 5.8f, 12.0f) * step);
        float injected = queuedMass * injectFraction * active;
        queuedMass -= injected;
        nextSiphonMass[0] += injected;

        for (int i = 0; i < SIPHON_BINS; i++) {
            float mass = siphonMass[i];
            float t = i / (float) Math.max(1, SIPHON_BINS - 1);
            float localRate = Mth.lerp(t, 4.2f, 10.5f) * Mth.lerp(finalFlush, 1.0f, 1.75f);
            float fraction = 1.0f - (float) Math.exp(-localRate * step);
            float outgoing = mass * fraction;
            float retained = mass - outgoing;
            nextSiphonMass[i] += retained;
            if (i + 1 < SIPHON_BINS) {
                nextSiphonMass[i + 1] += outgoing;
            } else {
                absorbedMass += outgoing;
            }
        }

        System.arraycopy(nextSiphonMass, 0, siphonMass, 0, SIPHON_BINS);
        siphonTextureDirty = true;
    }

    private float trilinearSample(float[] field, Vector3f local) {
        float gx = local.x / Math.max(volume.captureLength(), 0.0001f) * X_SIZE - 0.5f;
        float gy = (local.y / Math.max(volume.radius(), 0.0001f) * 0.5f + 0.5f) * Y_SIZE - 0.5f;
        float gz = (local.z / Math.max(volume.radius(), 0.0001f) * 0.5f + 0.5f) * Z_SIZE - 0.5f;
        int x0 = Mth.floor(gx);
        int y0 = Mth.floor(gy);
        int z0 = Mth.floor(gz);
        float tx = gx - x0;
        float ty = gy - y0;
        float tz = gz - z0;

        float sum = 0f;
        for (int dz = 0; dz <= 1; dz++) {
            float wz = dz == 0 ? 1f - tz : tz;
            int z = Mth.clamp(z0 + dz, 0, Z_SIZE - 1);
            for (int dy = 0; dy <= 1; dy++) {
                float wy = dy == 0 ? 1f - ty : ty;
                int y = Mth.clamp(y0 + dy, 0, Y_SIZE - 1);
                for (int dx = 0; dx <= 1; dx++) {
                    float wx = dx == 0 ? 1f - tx : tx;
                    int x = Mth.clamp(x0 + dx, 0, X_SIZE - 1);
                    sum += field[index(x, y, z)] * wx * wy * wz;
                }
            }
        }
        return sum;
    }

    private Vector3f cellCenterLocal(int index) {
        int x = xOf(index);
        int y = yOf(index);
        int z = zOf(index);
        return new Vector3f(
                ((x + 0.5f) / X_SIZE) * volume.captureLength(),
                (((y + 0.5f) / Y_SIZE) * 2.0f - 1.0f) * volume.radius(),
                (((z + 0.5f) / Z_SIZE) * 2.0f - 1.0f) * volume.radius()
        );
    }

    private boolean isOccupied(int index) {
        return initialMass[index] > MIN_ACTIVE_MASS;
    }

    private static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < X_SIZE && y >= 0 && y < Y_SIZE && z >= 0 && z < Z_SIZE;
    }

    private static int index(int x, int y, int z) {
        return (x * Z_SIZE + z) * Y_SIZE + y;
    }

    private static int xOf(int index) {
        return index / (Y_SIZE * Z_SIZE);
    }

    private static int zOf(int index) {
        return (index / Y_SIZE) % Z_SIZE;
    }

    private static int yOf(int index) {
        return index % Y_SIZE;
    }

    private static float valueAt(float[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0f;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / Math.max(edge1 - edge0, 0.000001f), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float deterministicNoise(int index, int salt) {
        int x = index * 1103515245 + salt * 12345 + 0x45d9f3b;
        x ^= x >>> 16;
        x *= 0x45d9f3b;
        x ^= x >>> 16;
        return ((x & 0xFFFF) / 32767.5f) - 1.0f;
    }

    private static void normalizeOrDefault(Vector3f axis, float fallbackX, float fallbackY, float fallbackZ) {
        float lenSq = axis.lengthSquared();
        if (lenSq < 1e-7f) {
            axis.set(fallbackX, fallbackY, fallbackZ);
            return;
        }
        axis.mul(1.0f / Mth.sqrt(lenSq));
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private record Node(int index, float cost) implements Comparable<Node> {
        @Override
        public int compareTo(Node other) {
            return Float.compare(cost, other.cost);
        }
    }
}
