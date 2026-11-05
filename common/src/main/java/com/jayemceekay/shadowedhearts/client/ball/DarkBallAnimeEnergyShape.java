package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Worker-built, deliberately coarse volume seeds for the converted energy body.
 * The fitted volume can depart from the source surface; it is not a replacement
 * for the exact source silhouette during conversion.
 */
final class DarkBallAnimeEnergyShape {
    static final int MAX_NODES = 12;
    static final int MAX_SAMPLES = 2_048;
    static final int EXTENT_ANCHORS = 14;
    private static final int MAX_SOURCE_TRIANGLES = 131_072;
    private static final int MAX_SOURCE_VERTICES = MAX_SOURCE_TRIANGLES * 3;
    private static final double MIN_RADIUS = .05;
    private static final double PADDING = .015;
    private final Vec3 center;
    private final Vec3 sourceHalfExtent;
    private final double radius;
    private final double supportRadius;
    private final float[] centers;
    private final float[] radii;
    private final int[] links;
    private final Summary summary;

    private DarkBallAnimeEnergyShape(Vec3 center, Vec3 sourceHalfExtent, double radius, double supportRadius,
                                     float[] centers, float[] radii, int[] links, Summary summary) {
        this.center = center;
        this.sourceHalfExtent = sourceHalfExtent;
        this.radius = radius;
        this.supportRadius = supportRadius;
        this.centers = centers;
        this.radii = radii;
        this.links = links;
        this.summary = summary;
    }

    static DarkBallAnimeEnergyShape prepare(DarkBallSurfaceMesh source) {
        DarkBallPreparationCancellation.check();
        if (source == null || source.triangleCount() < 1 || source.vertexCount() < 3
                || source.triangleCount() > MAX_SOURCE_TRIANGLES
                || source.vertexCount() > MAX_SOURCE_VERTICES)
            throw new IllegalArgumentException("Invalid or over-budget anime energy source");
        validateVertices(source);
        float[] positions = source.positions();
        int[] indices = source.indices();
        double[] cumulativeAreas = new double[source.triangleCount()];
        double[] minimum = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] maximum = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int[] extrema = new int[EXTENT_ANCHORS];
        double[] diagonalMaximum = new double[8];
        Arrays.fill(diagonalMaximum, Double.NEGATIVE_INFINITY);
        double area = 0;
        int nondegenerate = 0;
        for (int triangle = 0; triangle < source.triangleCount(); triangle++) {
            DarkBallPreparationCancellation.checkEvery(triangle);
            int at = triangle * 3;
            for (int corner = 0; corner < 3; corner++)
                if (indices[at + corner] < 0 || indices[at + corner] >= source.vertexCount())
                    throw new IllegalArgumentException("Invalid anime energy source index");
            double triangleArea = doubleArea(positions, indices[at], indices[at + 1], indices[at + 2]);
            if (triangleArea > 0) {
                nondegenerate++;
                area += triangleArea;
                for (int corner = 0; corner < 3; corner++) {
                    int vertex = indices[at + corner];
                    for (int axis = 0; axis < 3; axis++) {
                        double value = positions[vertex * 3 + axis];
                        if (value < minimum[axis]) { minimum[axis] = value; extrema[axis * 2] = vertex; }
                        if (value > maximum[axis]) { maximum[axis] = value; extrema[axis * 2 + 1] = vertex; }
                    }
                    for (int diagonal = 0; diagonal < 8; diagonal++) {
                        double projection = 0;
                        for (int axis = 0; axis < 3; axis++)
                            projection += (double) positions[vertex * 3 + axis]
                                    * ((diagonal & (1 << axis)) == 0 ? -1 : 1);
                        if (projection > diagonalMaximum[diagonal]) {
                            diagonalMaximum[diagonal] = projection;
                            extrema[6 + diagonal] = vertex;
                        }
                    }
                }
            }
            cumulativeAreas[triangle] = area;
        }
        if (!(area > 0) || !Double.isFinite(area))
            throw new IllegalArgumentException("Anime energy source has no finite positive-area faces");
        Vec3 center = new Vec3((minimum[0] + maximum[0]) * .5,
                (minimum[1] + maximum[1]) * .5, (minimum[2] + maximum[2]) * .5);
        double dx = maximum[0] - minimum[0], dy = maximum[1] - minimum[1], dz = maximum[2] - minimum[2];
        double radius = Math.sqrt(dx * dx + dy * dy + dz * dz) * .5;
        if (!(radius > 0) || radius > Float.MAX_VALUE / 8.0)
            throw new IllegalArgumentException("Unrepresentable anime energy source extent");
        // Equal area strata prevent densely tessellated alpha edges from
        // consuming the seed budget. Weighted directional extrema preserve small limbs
        // or a tail even when their surface area misses every ordinary stratum.
        Samples samples = sample(positions, indices, cumulativeAreas, area, extrema, center, radius);
        int[] all = new int[samples.count()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        List<Cluster> clusters = new ArrayList<>(MAX_NODES);
        clusters.add(new Cluster(all, samples));
        while (clusters.size() < MAX_NODES) {
            DarkBallPreparationCancellation.check();
            int selected = -1;
            double best = 1e-12;
            for (int i = 0; i < clusters.size(); i++) {
                Cluster cluster = clusters.get(i);
                if (cluster.members.length > 1 && cluster.score > best) {
                    best = cluster.score;
                    selected = i;
                }
            }
            if (selected < 0) break;
            Cluster cluster = clusters.get(selected);
            Cluster[] children = cluster.split(samples);
            if (children == null) { cluster.score = 0; continue; }
            clusters.set(selected, children[0]);
            clusters.add(children[1]);
        }
        float[] centers = new float[clusters.size() * 3];
        float[] radii = new float[centers.length];
        double supportRadius = 0;
        for (int i = 0; i < clusters.size(); i++) {
            DarkBallPreparationCancellation.check();
            Cluster cluster = clusters.get(i);
            double[] fittedCenter = new double[3], fittedRadii = new double[3];
            cluster.fit(samples, fittedCenter, fittedRadii);
            double squaredCenterDistance = 0, maximumRadius = 0;
            for (int axis = 0; axis < 3; axis++) {
                double sourceCenter = axis == 0 ? center.x : axis == 1 ? center.y : center.z;
                float packedCenter = (float) (sourceCenter + fittedCenter[axis] * radius);
                // Include the narrowing error in the fitted support. Tiny
                // sources remain representable even near float subnormal scale.
                double narrowingError = Math.abs(packedCenter - (sourceCenter + fittedCenter[axis] * radius));
                float packedRadius = (float) Math.max(Math.ulp((float) radius),
                        fittedRadii[axis] * radius + narrowingError);
                if (!Float.isFinite(packedCenter) || !Float.isFinite(packedRadius) || packedRadius <= 0)
                    throw new IllegalArgumentException("Unrepresentable anime energy seed");
                centers[i * 3 + axis] = packedCenter;
                radii[i * 3 + axis] = packedRadius;
                double displacement = packedCenter - sourceCenter;
                squaredCenterDistance += displacement * displacement;
                maximumRadius = Math.max(maximumRadius, packedRadius);
            }
            supportRadius = Math.max(supportRadius, Math.sqrt(squaredCenterDistance) + maximumRadius);
        }
        int[] links = spanningTree(centers, radius);
        DarkBallPreparationCancellation.check();
        return new DarkBallAnimeEnergyShape(center, new Vec3(dx * .5, dy * .5, dz * .5), radius, supportRadius, centers, radii, links,
                new Summary(source.triangleCount(), nondegenerate, samples.count(), clusters.size(),
                        links.length / 2, supportRadius / radius));
    }

    Vec3 center() { return center; }
    Vec3 sourceHalfExtent() { return sourceHalfExtent; }
    double radius() { return radius; }
    /** Encloses the static ellipsoids and linearly interpolated bridge radii. */
    double supportRadius() { return supportRadius; }
    /** Packed xyz positions in the same captured-source coordinates as the input mesh. */
    float[] centers() { return centers; }
    /** Packed positive xyz ellipsoid half axes in source units. */
    float[] radii() { return radii; }
    int[] links() { return links; }
    int nodeCount() { return centers.length / 3; }
    Summary summary() { return summary; }

    record Summary(int sourceTriangles, int positiveAreaTriangles, int samples, int nodes,
                   int links, double supportRadiusRatio) { }

    private static void validateVertices(DarkBallSurfaceMesh source) {
        float[][] triples = {source.positions(), source.normals()};
        for (int vertex = 0; vertex < source.vertexCount(); vertex++) {
            DarkBallPreparationCancellation.checkEvery(vertex);
            for (float[] values : triples) for (int axis = 0; axis < 3; axis++)
                if (!Float.isFinite(values[vertex * 3 + axis]))
                    throw new IllegalArgumentException("Nonfinite anime energy source vertex");
        }
    }

    private static double doubleArea(float[] p, int a, int b, int c) {
        double ux = (double) p[b * 3] - p[a * 3], uy = (double) p[b * 3 + 1] - p[a * 3 + 1];
        double uz = (double) p[b * 3 + 2] - p[a * 3 + 2];
        double vx = (double) p[c * 3] - p[a * 3], vy = (double) p[c * 3 + 1] - p[a * 3 + 1];
        double vz = (double) p[c * 3 + 2] - p[a * 3 + 2];
        double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static Samples sample(float[] positions, int[] indices, double[] cumulativeAreas,
                                  double area, int[] extrema, Vec3 center, double radius) {
        Samples samples = new Samples(new double[(MAX_SAMPLES + EXTENT_ANCHORS) * 3],
                new double[MAX_SAMPLES + EXTENT_ANCHORS]);
        int triangle = 0;
        for (int i = 0; i < MAX_SAMPLES; i++) {
            DarkBallPreparationCancellation.checkEvery(i);
            double stratum = area * ((i + .5) / MAX_SAMPLES);
            while (triangle < cumulativeAreas.length - 1 && cumulativeAreas[triangle] <= stratum) {
                DarkBallPreparationCancellation.checkEvery(triangle);
                triangle++;
            }
            double u = Math.sqrt(radicalInverse(i + 1, 2)), v = radicalInverse(i + 1, 3);
            double a = 1 - u, b = u * (1 - v), c = u * v;
            for (int axis = 0; axis < 3; axis++) {
                double offset = axis == 0 ? center.x : axis == 1 ? center.y : center.z;
                // Subtract before averaging to retain translation accuracy.
                samples.positions[i * 3 + axis] = (a * (positions[indices[triangle * 3] * 3 + axis] - offset)
                        + b * (positions[indices[triangle * 3 + 1] * 3 + axis] - offset)
                        + c * (positions[indices[triangle * 3 + 2] * 3 + axis] - offset)) / radius;
            }
            samples.weights[i] = 1;
        }
        for (int i = 0; i < extrema.length; i++) {
            for (int axis = 0; axis < 3; axis++) {
                double offset = axis == 0 ? center.x : axis == 1 ? center.y : center.z;
                samples.positions[(MAX_SAMPLES + i) * 3 + axis] =
                        (positions[extrema[i] * 3 + axis] - offset) / radius;
            }
            samples.weights[MAX_SAMPLES + i] = MAX_SAMPLES / 128.0;
        }
        return samples;
    }

    private static double radicalInverse(int value, int base) {
        double result = 0, fraction = 1.0 / base;
        while (value > 0) { result += value % base * fraction; value /= base; fraction /= base; }
        return result;
    }

    private static int[] spanningTree(float[] centers, double radius) {
        int count = centers.length / 3;
        int[] links = new int[Math.max(0, count - 1) * 2];
        boolean[] connected = new boolean[count];
        connected[0] = true;
        for (int edge = 0; edge < count - 1; edge++) {
            DarkBallPreparationCancellation.check();
            double shortest = Double.POSITIVE_INFINITY;
            int from = -1, to = -1;
            for (int a = 0; a < count; a++) if (connected[a])
                for (int b = 0; b < count; b++) if (!connected[b]) {
                    double squared = 0;
                    for (int axis = 0; axis < 3; axis++) {
                        double delta = ((double) centers[a * 3 + axis] - centers[b * 3 + axis]) / radius;
                        squared += delta * delta;
                    }
                    if (squared < shortest) { shortest = squared; from = a; to = b; }
                }
            connected[to] = true;
            links[edge * 2] = from;
            links[edge * 2 + 1] = to;
        }
        return links;
    }

    private record Samples(double[] positions, double[] weights) {
        int count() { return weights.length; }
    }

    private static final class Cluster {
        final int[] members;
        final double[] mean = new double[3];
        int splitAxis;
        double score;

        Cluster(int[] members, Samples samples) {
            this.members = members;
            double weight = 0;
            for (int i = 0; i < members.length; i++) {
                DarkBallPreparationCancellation.checkEvery(i);
                int sample = members[i];
                weight += samples.weights[sample];
                for (int axis = 0; axis < 3; axis++)
                    mean[axis] += samples.positions[sample * 3 + axis] * samples.weights[sample];
            }
            for (int axis = 0; axis < 3; axis++) mean[axis] /= weight;
            double[] spread = new double[3];
            for (int i = 0; i < members.length; i++) {
                DarkBallPreparationCancellation.checkEvery(i);
                int sample = members[i];
                for (int axis = 0; axis < 3; axis++) {
                    double difference = samples.positions[sample * 3 + axis] - mean[axis];
                    spread[axis] += difference * difference * samples.weights[sample];
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                score += spread[axis];
                if (spread[axis] > spread[splitAxis]) splitAxis = axis;
            }
        }

        Cluster[] split(Samples samples) {
            int[] low = new int[members.length], high = new int[members.length];
            int lowCount = 0, highCount = 0;
            for (int i = 0; i < members.length; i++) {
                DarkBallPreparationCancellation.checkEvery(i);
                int sample = members[i];
                if (samples.positions[sample * 3 + splitAxis] < mean[splitAxis]) low[lowCount++] = sample;
                else high[highCount++] = sample;
            }
            if (lowCount == 0 || highCount == 0) return null;
            return new Cluster[]{new Cluster(Arrays.copyOf(low, lowCount), samples),
                    new Cluster(Arrays.copyOf(high, highCount), samples)};
        }

        void fit(Samples samples, double[] center, double[] radii) {
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int i = 0; i < members.length; i++) {
                DarkBallPreparationCancellation.checkEvery(i);
                for (int axis = 0; axis < 3; axis++) {
                    double value = samples.positions[members[i] * 3 + axis];
                    min[axis] = Math.min(min[axis], value);
                    max[axis] = Math.max(max[axis], value);
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                center[axis] = (min[axis] + max[axis]) * .5;
                radii[axis] = Math.max(MIN_RADIUS, (max[axis] - min[axis]) * .5 + PADDING);
            }
            double expansion = 1;
            for (int i = 0; i < members.length; i++) {
                DarkBallPreparationCancellation.checkEvery(i);
                double squared = 0;
                for (int axis = 0; axis < 3; axis++) {
                    double distance = (samples.positions[members[i] * 3 + axis] - center[axis]) / radii[axis];
                    squared += distance * distance;
                }
                expansion = Math.max(expansion, Math.sqrt(squared));
            }
            // The area strata need not land exactly on a source corner. A
            // small margin keeps the coarse mass from shaving those corners.
            for (int axis = 0; axis < 3; axis++) radii[axis] *= expansion * 1.04;
        }
    }
}
