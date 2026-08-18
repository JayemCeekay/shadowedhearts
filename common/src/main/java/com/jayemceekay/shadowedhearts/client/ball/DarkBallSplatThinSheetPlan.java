package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects the two opposing SDF faces produced when a captured model part is
 * effectively a zero-thickness quad.
 *
 * <p>The detector is deliberately stricter than the ordinary surfel
 * neighborhood graph. A candidate must be close, face in the opposite
 * direction, lie almost directly across the first candidate's normal, and
 * carry compatible release/transport metadata. Only mutual nearest matches
 * become sheet pairs. This keeps nearby folds and separate limbs independent
 * while allowing the two sides of a fin, spine, ear, or ribbon to share one
 * deformation carrier.</p>
 */
final class DarkBallSplatThinSheetPlan {
    static final float MIN_PAIR_SCORE = 0.34f;
    static final float MAX_OPPOSING_NORMAL_DOT = -0.82f;
    static final float MIN_NORMAL_SEPARATION_ALIGNMENT = 0.78f;
    static final float MAX_PAIR_DISTANCE_VOXELS = 2.80f;
    static final float MAX_METADATA_DELTA = 0.15f;

    private static final float MIN_METADATA_DELTA = 0.035f;
    private static final float THINNESS_FULL_VOXELS = 1.20f;
    private static final float THINNESS_EMPTY_VOXELS = 4.00f;
    private static final float MIN_VOXEL_SIZE = 1.0e-4f;
    private static final float COLOCATED_EPSILON_SQUARED = 1.0e-12f;

    private DarkBallSplatThinSheetPlan() {
    }

    static Result stabilize(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            float maximumVoxel) {
        if (plan == null || plan.releaseOrder().length < 2) {
            return Result.empty();
        }
        int sampleCount = plan.releaseOrder().length;
        float voxelSize = resolvedVoxelSize(plan, maximumVoxel);
        seedThinSurfaceRisk(plan, voxelSize);
        float searchRadius = voxelSize * MAX_PAIR_DISTANCE_VOXELS;
        float searchRadiusSquared = searchRadius * searchRadius;
        float inverseCellSize = 1.0f / Math.max(
                searchRadius, MIN_VOXEL_SIZE);

        Map<Long, IntBucket> grid = new HashMap<>(
                Math.max(16, sampleCount * 2));
        for (int sample = 0; sample < sampleCount; sample++) {
            int triple = sample * 3;
            int cellX = cellCoordinate(
                    plan.positions()[triple], inverseCellSize);
            int cellY = cellCoordinate(
                    plan.positions()[triple + 1], inverseCellSize);
            int cellZ = cellCoordinate(
                    plan.positions()[triple + 2], inverseCellSize);
            grid.computeIfAbsent(
                    cellKey(cellX, cellY, cellZ),
                    ignored -> new IntBucket()).add(sample);
        }

        int[] bestPartner = new int[sampleCount];
        float[] bestScore = new float[sampleCount];
        java.util.Arrays.fill(bestPartner, -1);
        for (int sample = 0; sample < sampleCount; sample++) {
            int triple = sample * 3;
            int cellX = cellCoordinate(
                    plan.positions()[triple], inverseCellSize);
            int cellY = cellCoordinate(
                    plan.positions()[triple + 1], inverseCellSize);
            int cellZ = cellCoordinate(
                    plan.positions()[triple + 2], inverseCellSize);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        IntBucket bucket = grid.get(cellKey(
                                cellX + dx,
                                cellY + dy,
                                cellZ + dz));
                        if (bucket == null) {
                            continue;
                        }
                        for (int slot = 0; slot < bucket.size; slot++) {
                            int candidate = bucket.values[slot];
                            if (candidate == sample) {
                                continue;
                            }
                            float score = pairScore(
                                    plan,
                                    sample,
                                    candidate,
                                    voxelSize,
                                    searchRadiusSquared);
                            if (score > bestScore[sample]) {
                                bestScore[sample] = score;
                                bestPartner[sample] = candidate;
                            }
                        }
                    }
                }
            }
        }

        int pairCount = 0;
        int pairedSamples = 0;
        float scoreSum = 0.0f;
        for (int sample = 0; sample < sampleCount; sample++) {
            int partner = bestPartner[sample];
            if (partner <= sample
                    || partner >= sampleCount
                    || bestPartner[partner] != sample) {
                continue;
            }
            float score = (float) Math.sqrt(
                    Math.max(bestScore[sample], 0.0f)
                            * Math.max(bestScore[partner], 0.0f));
            if (score < MIN_PAIR_SCORE) {
                continue;
            }
            stabilizePair(plan, sample, partner, score);
            pairCount++;
            pairedSamples += 2;
            scoreSum += score * 2.0f;
        }
        return new Result(
                pairCount,
                pairedSamples,
                pairedSamples > 0 ? scoreSum / pairedSamples : 0.0f);
    }

    /**
     * Pair matching is intentionally strict, but thin fins and ribbons still
     * need conservative deformation and footprint handling when independently
     * sampled front/back faces do not produce a mutual nearest match. Seed the
     * same confidence channel from measured SDF thickness before attempting
     * pairing. Confirmed pairs can subsequently raise that confidence and
     * share an exact carrier; unmatched thin samples merely receive the
     * hole-resistant shader policy.
     */
    private static void seedThinSurfaceRisk(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            float voxelSize) {
        for (int sample = 0;
             sample < plan.localThickness().length;
             sample++) {
            float thickness = plan.localThickness()[sample];
            if (!Float.isFinite(thickness)) {
                continue;
            }
            float thicknessVoxels =
                    Math.max(thickness, 0.0f) / voxelSize;
            float riskScore = 1.0f - smoothstep(
                    THINNESS_FULL_VOXELS,
                    THINNESS_EMPTY_VOXELS,
                    thicknessVoxels);
            plan.thinSheetScore()[sample] = Math.max(
                    plan.thinSheetScore()[sample],
                    riskScore);
        }
    }

    private static float pairScore(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            int first,
            int second,
            float voxelSize,
            float searchRadiusSquared) {
        int firstTriple = first * 3;
        int secondTriple = second * 3;
        float firstNormalX = plan.normals()[firstTriple];
        float firstNormalY = plan.normals()[firstTriple + 1];
        float firstNormalZ = plan.normals()[firstTriple + 2];
        float secondNormalX = plan.normals()[secondTriple];
        float secondNormalY = plan.normals()[secondTriple + 1];
        float secondNormalZ = plan.normals()[secondTriple + 2];
        float normalDot = firstNormalX * secondNormalX
                + firstNormalY * secondNormalY
                + firstNormalZ * secondNormalZ;
        if (!Float.isFinite(normalDot)
                || normalDot > MAX_OPPOSING_NORMAL_DOT) {
            return 0.0f;
        }

        float dx = plan.positions()[secondTriple]
                - plan.positions()[firstTriple];
        float dy = plan.positions()[secondTriple + 1]
                - plan.positions()[firstTriple + 1];
        float dz = plan.positions()[secondTriple + 2]
                - plan.positions()[firstTriple + 2];
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        float existingSheetScore = Math.min(
                plan.thinSheetScore()[first],
                plan.thinSheetScore()[second]);
        if (distanceSquared <= COLOCATED_EPSILON_SQUARED) {
            return existingSheetScore >= MIN_PAIR_SCORE
                    ? existingSheetScore
                    : 0.0f;
        }
        if (!Float.isFinite(distanceSquared)
                || distanceSquared > searchRadiusSquared) {
            return 0.0f;
        }

        float inverseDistance = Mth.invSqrt(distanceSquared);
        float directionX = dx * inverseDistance;
        float directionY = dy * inverseDistance;
        float directionZ = dz * inverseDistance;
        float firstAlignment = Math.abs(
                directionX * firstNormalX
                        + directionY * firstNormalY
                        + directionZ * firstNormalZ);
        float secondAlignment = Math.abs(
                directionX * secondNormalX
                        + directionY * secondNormalY
                        + directionZ * secondNormalZ);
        float alignment = Math.min(firstAlignment, secondAlignment);
        if (alignment < MIN_NORMAL_SEPARATION_ALIGNMENT) {
            return 0.0f;
        }

        float metadataDelta = Math.max(
                Math.abs(plan.releaseOrder()[first]
                        - plan.releaseOrder()[second]),
                Math.abs(plan.transportOrder()[first]
                        - plan.transportOrder()[second]));
        metadataDelta = Math.max(
                metadataDelta,
                Math.abs(plan.presentationReleaseOrder()[first]
                        - plan.presentationReleaseOrder()[second]));
        if (metadataDelta > MAX_METADATA_DELTA) {
            return 0.0f;
        }

        float averageThickness = Math.max(
                (Math.max(plan.localThickness()[first], 0.0f)
                        + Math.max(
                        plan.localThickness()[second], 0.0f)) * 0.5f,
                0.0f);
        float thicknessVoxels = averageThickness / voxelSize;
        float thinnessScore = 1.0f - smoothstep(
                THINNESS_FULL_VOXELS,
                THINNESS_EMPTY_VOXELS,
                thicknessVoxels);
        if (thinnessScore <= 0.0f) {
            return 0.0f;
        }

        float distanceVoxels =
                (float) Math.sqrt(distanceSquared) / voxelSize;
        float normalScore = smoothstep(
                -MAX_OPPOSING_NORMAL_DOT,
                0.98f,
                -normalDot);
        float alignmentScore = smoothstep(
                MIN_NORMAL_SEPARATION_ALIGNMENT,
                0.96f,
                alignment);
        float distanceScore = 1.0f - smoothstep(
                0.35f,
                MAX_PAIR_DISTANCE_VOXELS,
                distanceVoxels);
        float metadataScore = 1.0f - smoothstep(
                MIN_METADATA_DELTA,
                MAX_METADATA_DELTA,
                metadataDelta);
        return Mth.clamp(
                normalScore
                        * alignmentScore
                        * distanceScore
                        * thinnessScore
                        * metadataScore,
                0.0f,
                1.0f);
    }

    private static void stabilizePair(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            int first,
            int second,
            float score) {
        int firstTriple = first * 3;
        int secondTriple = second * 3;
        for (int axis = 0; axis < 3; axis++) {
            float midpoint = (
                    plan.positions()[firstTriple + axis]
                            + plan.positions()[secondTriple + axis]) * 0.5f;
            plan.positions()[firstTriple + axis] = midpoint;
            plan.positions()[secondTriple + axis] = midpoint;
        }
        float thickness = (
                plan.localThickness()[first]
                        + plan.localThickness()[second]) * 0.5f;
        float release = (
                plan.releaseOrder()[first]
                        + plan.releaseOrder()[second]) * 0.5f;
        float presentationRelease = (
                plan.presentationReleaseOrder()[first]
                        + plan.presentationReleaseOrder()[second]) * 0.5f;
        float transport = (
                plan.transportOrder()[first]
                        + plan.transportOrder()[second]) * 0.5f;
        plan.localThickness()[first] = thickness;
        plan.localThickness()[second] = thickness;
        plan.releaseOrder()[first] = release;
        plan.releaseOrder()[second] = release;
        plan.presentationReleaseOrder()[first] = presentationRelease;
        plan.presentationReleaseOrder()[second] = presentationRelease;
        plan.transportOrder()[first] = transport;
        plan.transportOrder()[second] = transport;
        plan.thinSheetScore()[first] = Math.max(
                plan.thinSheetScore()[first], score);
        plan.thinSheetScore()[second] = Math.max(
                plan.thinSheetScore()[second], score);
    }

    private static float resolvedVoxelSize(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            float maximumVoxel) {
        if (Float.isFinite(maximumVoxel)
                && maximumVoxel >= MIN_VOXEL_SIZE) {
            return maximumVoxel;
        }
        float minimumPositiveThickness = Float.POSITIVE_INFINITY;
        for (float thickness : plan.localThickness()) {
            if (Float.isFinite(thickness)
                    && thickness >= MIN_VOXEL_SIZE) {
                minimumPositiveThickness = Math.min(
                        minimumPositiveThickness, thickness);
            }
        }
        return Float.isFinite(minimumPositiveThickness)
                ? Math.max(
                minimumPositiveThickness * 0.80f,
                MIN_VOXEL_SIZE)
                : 1.0f;
    }

    private static int cellCoordinate(
            float coordinate,
            float inverseCellSize) {
        return Mth.floor(coordinate * inverseCellSize);
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x & 0x1fffffL) << 42
                | ((long) y & 0x1fffffL) << 21
                | ((long) z & 0x1fffffL);
    }

    private static float smoothstep(
            float edge0,
            float edge1,
            float value) {
        float t = Mth.clamp(
                (value - edge0) / Math.max(edge1 - edge0, 1.0e-6f),
                0.0f,
                1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    record Result(
            int pairCount,
            int pairedSamples,
            float meanPairScore
    ) {
        static Result empty() {
            return new Result(0, 0, 0.0f);
        }
    }

    private static final class IntBucket {
        private int[] values = new int[4];
        private int size;

        private void add(int value) {
            if (size >= values.length) {
                values = java.util.Arrays.copyOf(
                        values, values.length * 2);
            }
            values[size++] = value;
        }
    }
}
