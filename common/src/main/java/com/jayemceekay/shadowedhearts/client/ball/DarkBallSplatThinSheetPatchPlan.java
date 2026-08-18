package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * Builds stable, mid-surface sampling patches for fins, ribbons, spines, and
 * other captured parts whose two SDF faces are closer than an ordinary surfel
 * neighborhood.
 *
 * <p>The ordinary neighborhood pass intentionally rejects opposing normals.
 * That is correct for unrelated surfaces, but it leaves a thin model part with
 * two sparse and independently oriented carrier sets. This pass joins only
 * locally planar, metadata-compatible thin samples, treats opposing faces as
 * one logical sheet, and derives a locally coherent tangent and in-plane
 * spacing field across each patch. The tangent fit remains local even when a
 * broad body surface and several attached fins are graph-connected, preventing
 * one whole-model principal axis from being imposed on every appendage. All
 * work happens during asynchronous preparation; the render-time vertex format
 * and draw count are unchanged.</p>
 */
final class DarkBallSplatThinSheetPatchPlan {
    static final float MIN_PATCH_SCORE = 0.24f;
    static final int MIN_PATCH_SAMPLE_COUNT = 3;
    static final int MIN_PATCH_FEATURE_QUOTA = 6;

    private static final float MIN_ABSOLUTE_NORMAL_DOT = 0.72f;
    private static final float MAX_SAME_FACE_NORMAL_SEPARATION = 0.58f;
    private static final float MIN_OPPOSING_FACE_NORMAL_SEPARATION = 0.62f;
    private static final float MAX_PATCH_DISTANCE_VOXELS = 6.0f;
    private static final float MAX_PATCH_METADATA_DELTA = 0.22f;
    private static final float COLOCATED_DISTANCE_VOXELS = 0.30f;
    private static final float DISTINCT_SPACING_VOXELS = 0.16f;
    private static final float LOCAL_FRAME_RADIUS_VOXELS = 7.0f;
    private static final float MIN_VOXEL_SIZE = 1.0e-4f;

    private DarkBallSplatThinSheetPatchPlan() {
    }

    static Result apply(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            float maximumVoxel) {
        if (plan == null || plan.releaseOrder().length == 0) {
            return Result.empty();
        }
        Layout layout = analyze(
                plan,
                0,
                plan.releaseOrder().length,
                maximumVoxel);
        System.arraycopy(
                layout.patchIds,
                0,
                plan.thinSheetPatchId(),
                0,
                layout.patchIds.length);
        if (layout.patchCount == 0) {
            return Result.empty();
        }

        int sampleCount = plan.releaseOrder().length;
        int[] patchSizes = layout.patchSizes;
        double[] centerX = new double[layout.patchCount];
        double[] centerY = new double[layout.patchCount];
        double[] centerZ = new double[layout.patchCount];
        float[] referenceNormalX = new float[layout.patchCount];
        float[] referenceNormalY = new float[layout.patchCount];
        float[] referenceNormalZ = new float[layout.patchCount];
        boolean[] hasReferenceNormal = new boolean[layout.patchCount];
        double[] normalX = new double[layout.patchCount];
        double[] normalY = new double[layout.patchCount];
        double[] normalZ = new double[layout.patchCount];

        for (int sample = 0; sample < sampleCount; sample++) {
            int patch = layout.patchIds[sample];
            if (patch < 0) {
                continue;
            }
            int triple = sample * 3;
            centerX[patch] += plan.positions()[triple];
            centerY[patch] += plan.positions()[triple + 1];
            centerZ[patch] += plan.positions()[triple + 2];
            float nx = plan.normals()[triple];
            float ny = plan.normals()[triple + 1];
            float nz = plan.normals()[triple + 2];
            if (!hasReferenceNormal[patch]) {
                referenceNormalX[patch] = nx;
                referenceNormalY[patch] = ny;
                referenceNormalZ[patch] = nz;
                hasReferenceNormal[patch] = true;
            }
            float sign = nx * referenceNormalX[patch]
                    + ny * referenceNormalY[patch]
                    + nz * referenceNormalZ[patch] < 0.0f
                    ? -1.0f
                    : 1.0f;
            normalX[patch] += nx * sign;
            normalY[patch] += ny * sign;
            normalZ[patch] += nz * sign;
        }

        float[] patchNormal = new float[layout.patchCount * 3];
        float[] patchTangent = new float[layout.patchCount * 3];
        float[] patchBitangent = new float[layout.patchCount * 3];
        for (int patch = 0; patch < layout.patchCount; patch++) {
            double inverseCount = 1.0 / Math.max(patchSizes[patch], 1);
            centerX[patch] *= inverseCount;
            centerY[patch] *= inverseCount;
            centerZ[patch] *= inverseCount;
            int triple = patch * 3;
            normalize(
                    (float) normalX[patch],
                    (float) normalY[patch],
                    (float) normalZ[patch],
                    referenceNormalX[patch],
                    referenceNormalY[patch],
                    referenceNormalZ[patch],
                    patchNormal,
                    triple);
            buildTangentFrame(
                    patchNormal[triple],
                    patchNormal[triple + 1],
                    patchNormal[triple + 2],
                    patchTangent,
                    patchBitangent,
                    triple);
        }

        double[] covarianceXX = new double[layout.patchCount];
        double[] covarianceXY = new double[layout.patchCount];
        double[] covarianceYY = new double[layout.patchCount];
        for (int sample = 0; sample < sampleCount; sample++) {
            int patch = layout.patchIds[sample];
            if (patch < 0) {
                continue;
            }
            int sampleTriple = sample * 3;
            int patchTriple = patch * 3;
            double dx = plan.positions()[sampleTriple] - centerX[patch];
            double dy = plan.positions()[sampleTriple + 1] - centerY[patch];
            double dz = plan.positions()[sampleTriple + 2] - centerZ[patch];
            double u = dx * patchTangent[patchTriple]
                    + dy * patchTangent[patchTriple + 1]
                    + dz * patchTangent[patchTriple + 2];
            double v = dx * patchBitangent[patchTriple]
                    + dy * patchBitangent[patchTriple + 1]
                    + dz * patchBitangent[patchTriple + 2];
            covarianceXX[patch] += u * u;
            covarianceXY[patch] += u * v;
            covarianceYY[patch] += v * v;
        }

        float[] principalX = new float[layout.patchCount];
        float[] principalY = new float[layout.patchCount];
        float[] principalZ = new float[layout.patchCount];
        float[] patchAnisotropy = new float[layout.patchCount];
        for (int patch = 0; patch < layout.patchCount; patch++) {
            double angle = 0.5 * Math.atan2(
                    2.0 * covarianceXY[patch],
                    covarianceXX[patch] - covarianceYY[patch]);
            float tangentWeight = (float) Math.cos(angle);
            float bitangentWeight = (float) Math.sin(angle);
            int triple = patch * 3;
            principalX[patch] = patchTangent[triple] * tangentWeight
                    + patchBitangent[triple] * bitangentWeight;
            principalY[patch] = patchTangent[triple + 1] * tangentWeight
                    + patchBitangent[triple + 1] * bitangentWeight;
            principalZ[patch] = patchTangent[triple + 2] * tangentWeight
                    + patchBitangent[triple + 2] * bitangentWeight;
            canonicalizeDirection(
                    principalX, principalY, principalZ, patch);

            double trace = covarianceXX[patch] + covarianceYY[patch];
            double discriminant = Math.sqrt(Math.max(
                    (covarianceXX[patch] - covarianceYY[patch])
                            * (covarianceXX[patch] - covarianceYY[patch])
                            + 4.0 * covarianceXY[patch]
                            * covarianceXY[patch],
                    0.0));
            patchAnisotropy[patch] = trace > 1.0e-12
                    ? Mth.clamp((float) (discriminant / trace),
                    0.0f, 1.0f)
                    : 0.0f;
        }

        float voxelSize = resolvedVoxelSize(plan, maximumVoxel);
        float minimumDistinctSpacing =
                voxelSize * DISTINCT_SPACING_VOXELS;
        float localFrameRadius =
                voxelSize * LOCAL_FRAME_RADIUS_VOXELS;
        float localFrameRadiusSquared =
                localFrameRadius * localFrameRadius;
        float[] nearestSpacing = new float[sampleCount];
        float[] sampleTangent = new float[sampleCount * 3];
        float[] sampleBitangent = new float[sampleCount * 3];
        double[] localCovarianceXX = new double[sampleCount];
        double[] localCovarianceXY = new double[sampleCount];
        double[] localCovarianceYY = new double[sampleCount];
        double[] localCovarianceWeight = new double[sampleCount];
        int[] localNeighborCount = new int[sampleCount];
        Arrays.fill(nearestSpacing, Float.POSITIVE_INFINITY);
        for (int sample = 0; sample < sampleCount; sample++) {
            int triple = sample * 3;
            buildTangentFrame(
                    plan.normals()[triple],
                    plan.normals()[triple + 1],
                    plan.normals()[triple + 2],
                    sampleTangent,
                    sampleBitangent,
                    triple);
        }
        for (int first = 0; first < sampleCount; first++) {
            int patch = layout.patchIds[first];
            if (patch < 0) {
                continue;
            }
            for (int second = first + 1;
                 second < sampleCount;
                 second++) {
                if (layout.patchIds[second] != patch) {
                    continue;
                }
                float spacing = (float) Math.sqrt(
                        layout.inPlaneDistanceSquared(
                                plan, first, second));
                if (!Float.isFinite(spacing)
                        || spacing < minimumDistinctSpacing) {
                    continue;
                }
                nearestSpacing[first] = Math.min(
                        nearestSpacing[first], spacing);
                nearestSpacing[second] = Math.min(
                        nearestSpacing[second], spacing);
                double distanceSquared =
                        layout.inPlaneDistanceSquared(
                                plan, first, second);
                if (distanceSquared > localFrameRadiusSquared) {
                    continue;
                }
                double distance = Math.sqrt(
                        Math.max(distanceSquared, 0.0));
                double weight = Math.max(
                        1.0 - distance
                                / Math.max(
                                localFrameRadius,
                                MIN_VOXEL_SIZE),
                        0.05);
                accumulateLocalCovariance(
                        plan,
                        first,
                        second,
                        sampleTangent,
                        sampleBitangent,
                        weight,
                        localCovarianceXX,
                        localCovarianceXY,
                        localCovarianceYY,
                        localCovarianceWeight,
                        localNeighborCount);
                accumulateLocalCovariance(
                        plan,
                        second,
                        first,
                        sampleTangent,
                        sampleBitangent,
                        weight,
                        localCovarianceXX,
                        localCovarianceXY,
                        localCovarianceYY,
                        localCovarianceWeight,
                        localNeighborCount);
            }
        }

        float[] patchMedianSpacing =
                new float[layout.patchCount];
        for (int patch = 0; patch < layout.patchCount; patch++) {
            float[] values = new float[patchSizes[patch]];
            int valueCount = 0;
            for (int sample = 0; sample < sampleCount; sample++) {
                if (layout.patchIds[sample] == patch
                        && Float.isFinite(nearestSpacing[sample])) {
                    values[valueCount++] = nearestSpacing[sample];
                }
            }
            if (valueCount == 0) {
                patchMedianSpacing[patch] = voxelSize;
            } else {
                Arrays.sort(values, 0, valueCount);
                patchMedianSpacing[patch] =
                        values[(valueCount - 1) / 2];
            }
        }

        float spacingScaleSum = 0.0f;
        int coherentTangentCount = 0;
        int patchedSamples = 0;
        int largestPatch = 0;
        for (int patchSize : patchSizes) {
            largestPatch = Math.max(largestPatch, patchSize);
        }
        for (int sample = 0; sample < sampleCount; sample++) {
            int patch = layout.patchIds[sample];
            if (patch < 0) {
                continue;
            }
            patchedSamples++;
            int triple = sample * 3;
            float nx = plan.normals()[triple];
            float ny = plan.normals()[triple + 1];
            float nz = plan.normals()[triple + 2];
            double localXX = localCovarianceXX[sample];
            double localXY = localCovarianceXY[sample];
            double localYY = localCovarianceYY[sample];
            double localTrace = localXX + localYY;
            boolean localFrameValid =
                    localNeighborCount[sample] >= 2
                            && localCovarianceWeight[sample] > 0.0
                            && localTrace > 1.0e-12;
            float angle;
            float tangentAnisotropy;
            if (localFrameValid) {
                angle = (float) (0.5 * Math.atan2(
                        2.0 * localXY,
                        localXX - localYY));
                double localDiscriminant = Math.sqrt(Math.max(
                        (localXX - localYY)
                                * (localXX - localYY)
                                + 4.0 * localXY * localXY,
                        0.0));
                tangentAnisotropy = Mth.clamp(
                        (float) (localDiscriminant / localTrace),
                        0.0f,
                        1.0f);
            } else {
                float px = principalX[patch];
                float py = principalY[patch];
                float pz = principalZ[patch];
                float normalProjection =
                        px * nx + py * ny + pz * nz;
                px -= nx * normalProjection;
                py -= ny * normalProjection;
                pz -= nz * normalProjection;
                float inversePrincipalLength =
                        inverseLength(px, py, pz);
                if (inversePrincipalLength <= 0.0f) {
                    continue;
                }
                px *= inversePrincipalLength;
                py *= inversePrincipalLength;
                pz *= inversePrincipalLength;
                angle = (float) Math.atan2(
                        px * sampleBitangent[triple]
                                + py * sampleBitangent[triple + 1]
                                + pz * sampleBitangent[triple + 2],
                        px * sampleTangent[triple]
                                + py * sampleTangent[triple + 1]
                                + pz * sampleTangent[triple + 2]);
                tangentAnisotropy = patchAnisotropy[patch];
            }
            if (angle < 0.0f) {
                angle += (float) Math.PI;
            }
            if (angle >= Math.PI) {
                angle -= (float) Math.PI;
            }
            plan.tangentAngle()[sample] = angle;
            float patchConfidence = Mth.clamp(
                    0.68f + 0.29f * tangentAnisotropy,
                    0.0f, 0.98f);
            plan.tangentConfidence()[sample] = Math.max(
                    plan.tangentConfidence()[sample],
                    patchConfidence);
            plan.coherence()[sample] = Math.max(
                    plan.coherence()[sample],
                    0.74f + 0.22f * tangentAnisotropy);

            float localSpacing = Float.isFinite(nearestSpacing[sample])
                    ? nearestSpacing[sample]
                    : patchMedianSpacing[patch];
            float patchSpacingScale = Mth.clamp(
                    localSpacing / Math.max(
                            patchMedianSpacing[patch],
                            MIN_VOXEL_SIZE),
                    0.90f,
                    DarkBallSplatNeighborhoodPlan.MAX_SPACING_SCALE);
            plan.spacingScale()[sample] = Math.max(
                    plan.spacingScale()[sample],
                    patchSpacingScale);
            plan.areaScale()[sample] = Mth.clamp(
                    plan.spacingScale()[sample]
                            * plan.spacingScale()[sample],
                    DarkBallSplatNeighborhoodPlan.MIN_AREA_SCALE,
                    DarkBallSplatNeighborhoodPlan.MAX_AREA_SCALE);
            spacingScaleSum += plan.spacingScale()[sample];
            if (plan.tangentConfidence()[sample] >= 0.50f) {
                coherentTangentCount++;
            }
        }
        return new Result(
                layout.patchCount,
                patchedSamples,
                largestPatch,
                coherentTangentCount,
                patchedSamples > 0
                        ? spacingScaleSum / patchedSamples
                        : 1.0f);
    }

    private static void accumulateLocalCovariance(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            int center,
            int neighbor,
            float[] tangent,
            float[] bitangent,
            double weight,
            double[] covarianceXX,
            double[] covarianceXY,
            double[] covarianceYY,
            double[] covarianceWeight,
            int[] neighborCount) {
        int centerTriple = center * 3;
        int neighborTriple = neighbor * 3;
        double dx = plan.positions()[neighborTriple]
                - plan.positions()[centerTriple];
        double dy = plan.positions()[neighborTriple + 1]
                - plan.positions()[centerTriple + 1];
        double dz = plan.positions()[neighborTriple + 2]
                - plan.positions()[centerTriple + 2];
        double u = dx * tangent[centerTriple]
                + dy * tangent[centerTriple + 1]
                + dz * tangent[centerTriple + 2];
        double v = dx * bitangent[centerTriple]
                + dy * bitangent[centerTriple + 1]
                + dz * bitangent[centerTriple + 2];
        covarianceXX[center] += weight * u * u;
        covarianceXY[center] += weight * u * v;
        covarianceYY[center] += weight * v * v;
        covarianceWeight[center] += weight;
        neighborCount[center]++;
    }

    static Layout analyze(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            int sampleOffset,
            int sampleCount,
            float maximumVoxel) {
        if (plan == null
                || sampleOffset < 0
                || sampleCount < 0
                || sampleOffset + sampleCount
                > plan.releaseOrder().length) {
            throw new IllegalArgumentException(
                    "invalid thin-sheet patch sample range");
        }
        int totalSamples = plan.releaseOrder().length;
        int[] patchIds = new int[totalSamples];
        Arrays.fill(patchIds, -1);
        if (sampleCount < MIN_PATCH_SAMPLE_COUNT) {
            return new Layout(
                    patchIds, new int[0], new float[0], 0);
        }

        float voxelSize = resolvedVoxelSize(plan, maximumVoxel);
        float maximumDistance =
                voxelSize * MAX_PATCH_DISTANCE_VOXELS;
        float maximumDistanceSquared =
                maximumDistance * maximumDistance;
        int[] parent = new int[sampleCount];
        byte[] rank = new byte[sampleCount];
        for (int local = 0; local < sampleCount; local++) {
            parent[local] = local;
        }

        for (int firstLocal = 0;
             firstLocal < sampleCount;
             firstLocal++) {
            int first = sampleOffset + firstLocal;
            if (plan.thinSheetScore()[first] < MIN_PATCH_SCORE) {
                continue;
            }
            for (int secondLocal = firstLocal + 1;
                 secondLocal < sampleCount;
                 secondLocal++) {
                int second = sampleOffset + secondLocal;
                if (plan.thinSheetScore()[second] < MIN_PATCH_SCORE
                        || !patchNeighbors(
                        plan,
                        first,
                        second,
                        voxelSize,
                        maximumDistanceSquared)) {
                    continue;
                }
                union(parent, rank, firstLocal, secondLocal);
            }
        }

        int[] rootSizes = new int[sampleCount];
        for (int local = 0; local < sampleCount; local++) {
            int sample = sampleOffset + local;
            if (plan.thinSheetScore()[sample] >= MIN_PATCH_SCORE) {
                rootSizes[find(parent, local)]++;
            }
        }
        int[] rootPatchIds = new int[sampleCount];
        Arrays.fill(rootPatchIds, -1);
        int patchCount = 0;
        for (int local = 0; local < sampleCount; local++) {
            int root = find(parent, local);
            if (rootSizes[root] >= MIN_PATCH_SAMPLE_COUNT
                    && rootPatchIds[root] < 0) {
                rootPatchIds[root] = patchCount++;
            }
        }
        int[] patchSizes = new int[patchCount];
        for (int local = 0; local < sampleCount; local++) {
            int patch = rootPatchIds[find(parent, local)];
            if (patch >= 0) {
                int sample = sampleOffset + local;
                patchIds[sample] = patch;
                patchSizes[patch]++;
            }
        }

        float[] patchNormals = new float[patchCount * 3];
        for (int patch = 0; patch < patchCount; patch++) {
            int reference = -1;
            for (int sample = sampleOffset;
                 sample < sampleOffset + sampleCount;
                 sample++) {
                if (patchIds[sample] == patch) {
                    reference = sample;
                    break;
                }
            }
            if (reference < 0) {
                continue;
            }
            int referenceTriple = reference * 3;
            float referenceX = plan.normals()[referenceTriple];
            float referenceY = plan.normals()[referenceTriple + 1];
            float referenceZ = plan.normals()[referenceTriple + 2];
            float sumX = 0.0f;
            float sumY = 0.0f;
            float sumZ = 0.0f;
            for (int sample = sampleOffset;
                 sample < sampleOffset + sampleCount;
                 sample++) {
                if (patchIds[sample] != patch) {
                    continue;
                }
                int triple = sample * 3;
                float sign = plan.normals()[triple] * referenceX
                        + plan.normals()[triple + 1] * referenceY
                        + plan.normals()[triple + 2] * referenceZ < 0.0f
                        ? -1.0f
                        : 1.0f;
                sumX += plan.normals()[triple] * sign;
                sumY += plan.normals()[triple + 1] * sign;
                sumZ += plan.normals()[triple + 2] * sign;
            }
            normalize(
                    sumX, sumY, sumZ,
                    referenceX, referenceY, referenceZ,
                    patchNormals, patch * 3);
        }
        return new Layout(
                patchIds, patchSizes, patchNormals, patchCount);
    }

    private static boolean patchNeighbors(
            DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
            int first,
            int second,
            float voxelSize,
            float maximumDistanceSquared) {
        int firstTriple = first * 3;
        int secondTriple = second * 3;
        float dx = plan.positions()[secondTriple]
                - plan.positions()[firstTriple];
        float dy = plan.positions()[secondTriple + 1]
                - plan.positions()[firstTriple + 1];
        float dz = plan.positions()[secondTriple + 2]
                - plan.positions()[firstTriple + 2];
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!Float.isFinite(distanceSquared)
                || distanceSquared > maximumDistanceSquared) {
            return false;
        }

        float normalDot = plan.normals()[firstTriple]
                * plan.normals()[secondTriple]
                + plan.normals()[firstTriple + 1]
                * plan.normals()[secondTriple + 1]
                + plan.normals()[firstTriple + 2]
                * plan.normals()[secondTriple + 2];
        if (!Float.isFinite(normalDot)
                || Math.abs(normalDot) < MIN_ABSOLUTE_NORMAL_DOT) {
            return false;
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
        if (metadataDelta > MAX_PATCH_METADATA_DELTA) {
            return false;
        }

        float colocatedDistance =
                voxelSize * COLOCATED_DISTANCE_VOXELS;
        if (distanceSquared
                <= colocatedDistance * colocatedDistance) {
            return true;
        }
        float inverseDistance = Mth.invSqrt(distanceSquared);
        float directionX = dx * inverseDistance;
        float directionY = dy * inverseDistance;
        float directionZ = dz * inverseDistance;
        float firstNormalSeparation = Math.abs(
                directionX * plan.normals()[firstTriple]
                        + directionY
                        * plan.normals()[firstTriple + 1]
                        + directionZ
                        * plan.normals()[firstTriple + 2]);
        float secondNormalSeparation = Math.abs(
                directionX * plan.normals()[secondTriple]
                        + directionY
                        * plan.normals()[secondTriple + 1]
                        + directionZ
                        * plan.normals()[secondTriple + 2]);
        float normalSeparation = Math.max(
                firstNormalSeparation, secondNormalSeparation);
        return normalDot >= 0.0f
                ? normalSeparation
                <= MAX_SAME_FACE_NORMAL_SEPARATION
                : Math.min(
                firstNormalSeparation,
                secondNormalSeparation)
                >= MIN_OPPOSING_FACE_NORMAL_SEPARATION;
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

    private static void buildTangentFrame(
            float nx,
            float ny,
            float nz,
            float[] tangent,
            float[] bitangent,
            int offset) {
        float referenceX = 0.0f;
        float referenceY = Math.abs(nz) < 0.75f ? 0.0f : 1.0f;
        float referenceZ = Math.abs(nz) < 0.75f ? 1.0f : 0.0f;
        float tangentX = referenceY * nz - referenceZ * ny;
        float tangentY = referenceZ * nx - referenceX * nz;
        float tangentZ = referenceX * ny - referenceY * nx;
        normalize(
                tangentX, tangentY, tangentZ,
                1.0f, 0.0f, 0.0f,
                tangent, offset);
        bitangent[offset] = ny * tangent[offset + 2]
                - nz * tangent[offset + 1];
        bitangent[offset + 1] = nz * tangent[offset]
                - nx * tangent[offset + 2];
        bitangent[offset + 2] = nx * tangent[offset + 1]
                - ny * tangent[offset];
        float inverseLength = inverseLength(
                bitangent[offset],
                bitangent[offset + 1],
                bitangent[offset + 2]);
        bitangent[offset] *= inverseLength;
        bitangent[offset + 1] *= inverseLength;
        bitangent[offset + 2] *= inverseLength;
    }

    private static void normalize(
            float x,
            float y,
            float z,
            float fallbackX,
            float fallbackY,
            float fallbackZ,
            float[] output,
            int offset) {
        float inverseLength = inverseLength(x, y, z);
        if (inverseLength <= 0.0f) {
            inverseLength = inverseLength(
                    fallbackX, fallbackY, fallbackZ);
            x = inverseLength > 0.0f ? fallbackX : 1.0f;
            y = inverseLength > 0.0f ? fallbackY : 0.0f;
            z = inverseLength > 0.0f ? fallbackZ : 0.0f;
        }
        inverseLength = inverseLength(x, y, z);
        output[offset] = x * inverseLength;
        output[offset + 1] = y * inverseLength;
        output[offset + 2] = z * inverseLength;
    }

    private static float inverseLength(
            float x,
            float y,
            float z) {
        float lengthSquared = x * x + y * y + z * z;
        return Float.isFinite(lengthSquared)
                && lengthSquared > 1.0e-12f
                ? Mth.invSqrt(lengthSquared)
                : 0.0f;
    }

    private static void canonicalizeDirection(
            float[] x,
            float[] y,
            float[] z,
            int index) {
        float ax = Math.abs(x[index]);
        float ay = Math.abs(y[index]);
        float az = Math.abs(z[index]);
        boolean flip = ax >= ay && ax >= az
                ? x[index] < 0.0f
                : ay >= az
                ? y[index] < 0.0f
                : z[index] < 0.0f;
        if (flip) {
            x[index] = -x[index];
            y[index] = -y[index];
            z[index] = -z[index];
        }
    }

    private static int find(int[] parent, int value) {
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

    private static void union(
            int[] parent,
            byte[] rank,
            int first,
            int second) {
        int firstRoot = find(parent, first);
        int secondRoot = find(parent, second);
        if (firstRoot == secondRoot) {
            return;
        }
        if (rank[firstRoot] < rank[secondRoot]) {
            parent[firstRoot] = secondRoot;
        } else if (rank[firstRoot] > rank[secondRoot]) {
            parent[secondRoot] = firstRoot;
        } else {
            parent[secondRoot] = firstRoot;
            rank[firstRoot]++;
        }
    }

    record Result(
            int patchCount,
            int patchedSamples,
            int largestPatch,
            int coherentTangentSamples,
            float meanSpacingScale
    ) {
        static Result empty() {
            return new Result(0, 0, 0, 0, 1.0f);
        }
    }

    static final class Layout {
        private final int[] patchIds;
        private final int[] patchSizes;
        private final float[] patchNormals;
        private final int patchCount;

        private Layout(
                int[] patchIds,
                int[] patchSizes,
                float[] patchNormals,
                int patchCount) {
            this.patchIds = patchIds;
            this.patchSizes = patchSizes;
            this.patchNormals = patchNormals;
            this.patchCount = patchCount;
        }

        int patchCount() {
            return patchCount;
        }

        int patchId(int sample) {
            return patchIds[sample];
        }

        int patchSize(int patch) {
            return patchSizes[patch];
        }

        double inPlaneDistanceSquared(
                DarkBallSurfaceSplatRenderer.SurfaceSamplePlan plan,
                int first,
                int second) {
            int patch = patchIds[first];
            if (patch < 0 || patchIds[second] != patch) {
                return Double.POSITIVE_INFINITY;
            }
            int firstTriple = first * 3;
            int secondTriple = second * 3;
            int patchTriple = patch * 3;
            double dx = plan.positions()[firstTriple]
                    - plan.positions()[secondTriple];
            double dy = plan.positions()[firstTriple + 1]
                    - plan.positions()[secondTriple + 1];
            double dz = plan.positions()[firstTriple + 2]
                    - plan.positions()[secondTriple + 2];
            double normalDistance = dx * patchNormals[patchTriple]
                    + dy * patchNormals[patchTriple + 1]
                    + dz * patchNormals[patchTriple + 2];
            return Math.max(
                    dx * dx + dy * dy + dz * dz
                            - normalDistance * normalDistance,
                    0.0);
        }
    }
}
