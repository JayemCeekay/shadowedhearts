package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * One-time local metadata preparation for topology-independent surface splats.
 *
 * <p>The render path deliberately does not trust mesh topology, so this plan
 * builds a tiny geometric nearest-neighbor graph from the retained splat
 * centers themselves. Release smoothing is edge preserving: the original
 * farthest-first value remains the authority and the neighborhood result may
 * move it only inside a narrow bounded interval.</p>
 */
final class DarkBallSplatNeighborhoodPlan {
    static final int NEIGHBOR_COUNT = 6;
    static final float MAX_RELEASE_DEVIATION = 0.045f;
    static final float MIN_SPACING_SCALE = 0.72f;
    static final float MAX_SPACING_SCALE = 1.35f;
    static final float MIN_AREA_SCALE =
            MIN_SPACING_SCALE * MIN_SPACING_SCALE;
    static final float MAX_AREA_SCALE =
            MAX_SPACING_SCALE * MAX_SPACING_SCALE;
    static final float MIN_SURFACE_NEIGHBOR_NORMAL_DOT = -0.25f;

    private DarkBallSplatNeighborhoodPlan() {
    }

    static Metadata build(float[] positions, float[] releaseOrder) {
        int count = releaseOrder == null ? 0 : releaseOrder.length;
        float[] normals = new float[Math.multiplyExact(count, 3)];
        for (int sample = 0; sample < count; sample++) {
            normals[sample * 3 + 2] = 1.0f;
        }
        return build(positions, normals, releaseOrder);
    }

    static Metadata build(float[] positions,
                          float[] normals,
                          float[] releaseOrder) {
        if (positions == null
                || positions.length % 3 != 0
                || normals == null
                || normals.length != positions.length
                || releaseOrder == null
                || releaseOrder.length != positions.length / 3) {
            throw new IllegalArgumentException(
                    "positions, normals, and release order must describe "
                            + "the same samples");
        }
        int count = releaseOrder.length;
        float[] smoothed = releaseOrder.clone();
        float[] spacingScale = new float[count];
        float[] areaScale = new float[count];
        float[] coherence = new float[count];
        float[] tangentAngle = new float[count];
        float[] tangentConfidence = new float[count];
        if (count < 2) {
            Arrays.fill(spacingScale, 1.0f);
            Arrays.fill(areaScale, 1.0f);
            Arrays.fill(coherence, 1.0f);
            return new Metadata(
                    smoothed,
                    spacingScale,
                    areaScale,
                    coherence,
                    tangentAngle,
                    tangentConfidence);
        }

        int neighborCount = Math.min(NEIGHBOR_COUNT, count - 1);
        int[] nearest = new int[Math.multiplyExact(count, neighborCount)];
        float[] nearestDistanceSquared =
                new float[Math.multiplyExact(count, neighborCount)];
        Arrays.fill(nearest, -1);
        Arrays.fill(nearestDistanceSquared, Float.POSITIVE_INFINITY);

        // At the High cap this is fewer than 2.5 million pairs, is performed
        // once with static preparation, and avoids per-frame topology or graph
        // work on integrated GPUs.
        for (int first = 0; first < count; first++) {
            int firstTriple = first * 3;
            for (int second = first + 1; second < count; second++) {
                int secondTriple = second * 3;
                float dx = positions[firstTriple]
                        - positions[secondTriple];
                float dy = positions[firstTriple + 1]
                        - positions[secondTriple + 1];
                float dz = positions[firstTriple + 2]
                        - positions[secondTriple + 2];
                float distanceSquared =
                        dx * dx + dy * dy + dz * dz;
                if (!Float.isFinite(distanceSquared)) {
                    continue;
                }
                // Euclidean proximity alone can join the opposite faces of a
                // thin tail, fin, ear, or ribbon. Those false neighbors corrupt
                // release smoothing, spacing, and the reconstructed principal
                // direction. Retain sharp creases, but reject samples whose
                // captured normals clearly face away from one another.
                if (unitNormalDot(
                        normals,
                        firstTriple,
                        secondTriple)
                        < MIN_SURFACE_NEIGHBOR_NORMAL_DOT) {
                    continue;
                }
                retainNearest(
                        nearest,
                        nearestDistanceSquared,
                        neighborCount,
                        first,
                        second,
                        distanceSquared);
                retainNearest(
                        nearest,
                        nearestDistanceSquared,
                        neighborCount,
                        second,
                        first,
                        distanceSquared);
            }
        }

        float[] averageSpacing = new float[count];
        float[] sortableSpacing = new float[count];
        for (int sample = 0; sample < count; sample++) {
            float distanceSum = 0.0f;
            int retained = 0;
            int base = sample * neighborCount;
            for (int neighbor = 0; neighbor < neighborCount; neighbor++) {
                float distanceSquared =
                        nearestDistanceSquared[base + neighbor];
                if (nearest[base + neighbor] < 0
                        || !Float.isFinite(distanceSquared)) {
                    continue;
                }
                distanceSum += (float) Math.sqrt(
                        Math.max(distanceSquared, 0.0f));
                retained++;
            }
            float spacing = retained > 0
                    ? distanceSum / retained
                    : 1.0f;
            averageSpacing[sample] = spacing;
            sortableSpacing[sample] = spacing;
        }
        Arrays.sort(sortableSpacing);
        float medianSpacing = Math.max(
                sortableSpacing[sortableSpacing.length / 2],
                1.0e-6f);

        for (int sample = 0; sample < count; sample++) {
            int base = sample * neighborCount;
            float weightedRelease = 0.0f;
            float weightSum = 0.0f;
            float varianceSum = 0.0f;
            float original = Mth.clamp(
                    releaseOrder[sample], 0.0f, 0.9999f);
            for (int neighbor = 0; neighbor < neighborCount; neighbor++) {
                int neighborIndex = nearest[base + neighbor];
                float distanceSquared =
                        nearestDistanceSquared[base + neighbor];
                if (neighborIndex < 0
                        || !Float.isFinite(distanceSquared)) {
                    continue;
                }
                float weight = 1.0f / Math.max(
                        (float) Math.sqrt(distanceSquared),
                        medianSpacing * 0.10f);
                float neighborRelease = Mth.clamp(
                        releaseOrder[neighborIndex],
                        0.0f,
                        0.9999f);
                weightedRelease += neighborRelease * weight;
                weightSum += weight;
                float difference = neighborRelease - original;
                varianceSum += difference * difference * weight;
            }

            float neighborMean = weightSum > 0.0f
                    ? weightedRelease / weightSum
                    : original;
            float boundedNeighbor = Mth.clamp(
                    neighborMean,
                    original - MAX_RELEASE_DEVIATION,
                    original + MAX_RELEASE_DEVIATION);
            float endpointAnchor = smoothstep(
                    0.0f, 0.075f, original)
                    * (1.0f - smoothstep(
                    0.925f, 0.9999f, original));
            smoothed[sample] = Mth.clamp(
                    Mth.lerp(
                            0.42f * endpointAnchor,
                            original,
                            boundedNeighbor),
                    0.0f,
                    0.9999f);

            float localSpacingScale = Mth.clamp(
                    averageSpacing[sample] / medianSpacing,
                    MIN_SPACING_SCALE,
                    MAX_SPACING_SCALE);
            spacingScale[sample] = localSpacingScale;
            areaScale[sample] = Mth.clamp(
                    localSpacingScale * localSpacingScale,
                    MIN_AREA_SCALE,
                    MAX_AREA_SCALE);
            float releaseDeviation = weightSum > 0.0f
                    ? (float) Math.sqrt(varianceSum / weightSum)
                    : 0.0f;
            coherence[sample] = 1.0f - smoothstep(
                    0.015f, 0.16f, releaseDeviation);
            PrincipalTangent principal = principalTangent(
                    sample,
                    positions,
                    normals,
                    nearest,
                    nearestDistanceSquared,
                    neighborCount,
                    medianSpacing);
            tangentAngle[sample] = principal.angle();
            tangentConfidence[sample] = principal.confidence();
        }
        preserveFarthestFirstRank(releaseOrder, smoothed);
        return new Metadata(
                smoothed,
                spacingScale,
                areaScale,
                coherence,
                tangentAngle,
                tangentConfidence);
    }

    private static PrincipalTangent principalTangent(
            int sample,
            float[] positions,
            float[] normals,
            int[] nearest,
            float[] nearestDistanceSquared,
            int neighborCount,
            float medianSpacing) {
        int triple = sample * 3;
        float normalX = normals[triple];
        float normalY = normals[triple + 1];
        float normalZ = normals[triple + 2];
        float normalLength = (float) Math.sqrt(
                normalX * normalX
                        + normalY * normalY
                        + normalZ * normalZ);
        if (!Float.isFinite(normalLength) || normalLength < 1.0e-6f) {
            normalX = 0.0f;
            normalY = 0.0f;
            normalZ = 1.0f;
        } else {
            normalX /= normalLength;
            normalY /= normalLength;
            normalZ /= normalLength;
        }

        float referenceX = 0.0f;
        float referenceY = Math.abs(normalZ) < 0.75f ? 0.0f : 1.0f;
        float referenceZ = Math.abs(normalZ) < 0.75f ? 1.0f : 0.0f;
        float tangent0X = referenceY * normalZ
                - referenceZ * normalY;
        float tangent0Y = referenceZ * normalX
                - referenceX * normalZ;
        float tangent0Z = referenceX * normalY
                - referenceY * normalX;
        float tangent0Length = (float) Math.sqrt(
                tangent0X * tangent0X
                        + tangent0Y * tangent0Y
                        + tangent0Z * tangent0Z);
        if (!Float.isFinite(tangent0Length)
                || tangent0Length < 1.0e-6f) {
            tangent0X = 1.0f;
            tangent0Y = 0.0f;
            tangent0Z = 0.0f;
        } else {
            tangent0X /= tangent0Length;
            tangent0Y /= tangent0Length;
            tangent0Z /= tangent0Length;
        }
        float tangent1X = normalY * tangent0Z
                - normalZ * tangent0Y;
        float tangent1Y = normalZ * tangent0X
                - normalX * tangent0Z;
        float tangent1Z = normalX * tangent0Y
                - normalY * tangent0X;

        float covariance00 = 0.0f;
        float covariance01 = 0.0f;
        float covariance11 = 0.0f;
        float weightSum = 0.0f;
        int base = sample * neighborCount;
        for (int neighbor = 0; neighbor < neighborCount; neighbor++) {
            int neighborIndex = nearest[base + neighbor];
            float distanceSquared =
                    nearestDistanceSquared[base + neighbor];
            if (neighborIndex < 0
                    || !Float.isFinite(distanceSquared)) {
                continue;
            }
            int neighborTriple = neighborIndex * 3;
            float dx = positions[neighborTriple] - positions[triple];
            float dy = positions[neighborTriple + 1]
                    - positions[triple + 1];
            float dz = positions[neighborTriple + 2]
                    - positions[triple + 2];
            float tangentX = dx * tangent0X
                    + dy * tangent0Y
                    + dz * tangent0Z;
            float tangentY = dx * tangent1X
                    + dy * tangent1Y
                    + dz * tangent1Z;
            float neighborNormalDot = Mth.clamp(
                    normalX * normals[neighborTriple]
                            + normalY * normals[neighborTriple + 1]
                            + normalZ * normals[neighborTriple + 2],
                    -1.0f,
                    1.0f);
            float normalWeight = smoothstep(
                    -0.10f, 0.70f, neighborNormalDot);
            float distance = (float) Math.sqrt(
                    Math.max(distanceSquared, 0.0f));
            float weight = normalWeight / Math.max(
                    distance, medianSpacing * 0.10f);
            covariance00 += tangentX * tangentX * weight;
            covariance01 += tangentX * tangentY * weight;
            covariance11 += tangentY * tangentY * weight;
            weightSum += weight;
        }
        if (weightSum <= 1.0e-6f) {
            return new PrincipalTangent(0.0f, 0.0f);
        }
        covariance00 /= weightSum;
        covariance01 /= weightSum;
        covariance11 /= weightSum;
        float trace = covariance00 + covariance11;
        if (!Float.isFinite(trace) || trace <= 1.0e-8f) {
            return new PrincipalTangent(0.0f, 0.0f);
        }
        float difference = covariance00 - covariance11;
        float separation = (float) Math.sqrt(
                difference * difference
                        + 4.0f * covariance01 * covariance01);
        float anisotropy = Mth.clamp(
                separation / trace, 0.0f, 1.0f);
        float angle = 0.5f * (float) Math.atan2(
                2.0f * covariance01, difference);
        if (angle < 0.0f) {
            angle += (float) Math.PI;
        }
        return new PrincipalTangent(
                angle,
                smoothstep(0.18f, 0.72f, anisotropy));
    }

    private static float unitNormalDot(float[] normals,
                                       int firstTriple,
                                       int secondTriple) {
        float firstX = normals[firstTriple];
        float firstY = normals[firstTriple + 1];
        float firstZ = normals[firstTriple + 2];
        float secondX = normals[secondTriple];
        float secondY = normals[secondTriple + 1];
        float secondZ = normals[secondTriple + 2];
        float firstLengthSquared = firstX * firstX
                + firstY * firstY
                + firstZ * firstZ;
        float secondLengthSquared = secondX * secondX
                + secondY * secondY
                + secondZ * secondZ;
        if (!Float.isFinite(firstLengthSquared)
                || !Float.isFinite(secondLengthSquared)
                || firstLengthSquared < 1.0e-12f
                || secondLengthSquared < 1.0e-12f) {
            return 1.0f;
        }
        return Mth.clamp(
                (firstX * secondX
                        + firstY * secondY
                        + firstZ * secondZ)
                        / (float) Math.sqrt(
                        firstLengthSquared * secondLengthSquared),
                -1.0f,
                1.0f);
    }

    private static void preserveFarthestFirstRank(
            float[] original,
            float[] smoothed) {
        Integer[] order = new Integer[original.length];
        for (int sample = 0; sample < order.length; sample++) {
            order[sample] = sample;
        }
        Arrays.sort(order, (first, second) -> {
            int valueOrder = Float.compare(
                    original[first], original[second]);
            return valueOrder != 0
                    ? valueOrder
                    : Integer.compare(first, second);
        });
        float previous = 0.0f;
        for (int sample : order) {
            smoothed[sample] = Math.max(
                    smoothed[sample], previous);
            previous = smoothed[sample];
        }
    }

    private static void retainNearest(
            int[] nearest,
            float[] distances,
            int neighborCount,
            int sample,
            int candidate,
            float distanceSquared) {
        int base = sample * neighborCount;
        int insertion = -1;
        for (int slot = 0; slot < neighborCount; slot++) {
            if (distanceSquared < distances[base + slot]) {
                insertion = slot;
                break;
            }
        }
        if (insertion < 0) {
            return;
        }
        for (int slot = neighborCount - 1;
             slot > insertion;
             slot--) {
            nearest[base + slot] = nearest[base + slot - 1];
            distances[base + slot] = distances[base + slot - 1];
        }
        nearest[base + insertion] = candidate;
        distances[base + insertion] = distanceSquared;
    }

    private static float smoothstep(
            float edge0,
            float edge1,
            float value) {
        float amount = Mth.clamp(
                (value - edge0) / (edge1 - edge0),
                0.0f,
                1.0f);
        return amount * amount * (3.0f - 2.0f * amount);
    }

    record Metadata(
            float[] smoothedReleaseOrder,
            float[] spacingScale,
            float[] areaScale,
            float[] coherence,
            float[] tangentAngle,
            float[] tangentConfidence
    ) {
    }

    private record PrincipalTangent(
            float angle,
            float confidence
    ) {
    }
}
