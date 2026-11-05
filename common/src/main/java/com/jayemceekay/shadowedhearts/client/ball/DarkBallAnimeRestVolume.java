package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/** Immutable, capture-local physical pose field used by the elastic energy envelope. */
final class DarkBallAnimeRestVolume {
    // The full preparation grid is the ceiling, not an assumed input resolution.
    static final int MAX_X_SIZE = 96;
    static final int MAX_SIDE_SIZE = 64;
    static final int MAX_CELL_COUNT = MAX_X_SIZE * MAX_SIDE_SIZE * MAX_SIDE_SIZE;

    private final int xSize, sideSize;
    private final Vec3 gridMinimum, gridMaximum, minimum, maximum;
    private final double distanceLipschitz;
    private final double distanceBand, distanceBandGradient;
    private final Vec3 distanceOuterBands, distanceOuterBandGradients;
    private final float[] atlas;

    private DarkBallAnimeRestVolume(int xSize, int sideSize, Vec3 gridMinimum, Vec3 gridMaximum,
                                    Vec3 minimum, Vec3 maximum, double distanceLipschitz,
                                    double distanceBand, double distanceBandGradient,
                                    Vec3 distanceOuterBands, Vec3 distanceOuterBandGradients, float[] atlas) {
        this.xSize = xSize;
        this.sideSize = sideSize;
        this.gridMinimum = gridMinimum;
        this.gridMaximum = gridMaximum;
        this.minimum = minimum;
        this.maximum = maximum;
        this.distanceLipschitz = distanceLipschitz;
        this.distanceBand = distanceBand;
        this.distanceBandGradient = distanceBandGradient;
        this.distanceOuterBands = distanceOuterBands;
        this.distanceOuterBandGradients = distanceOuterBandGradients;
        this.atlas = atlas;
    }

    static DarkBallAnimeRestVolume prepare(DarkBallVolumeBuildResult source, double sourceRadius) {
        return prepare(source, sourceRadius, null);
    }

    static DarkBallAnimeRestVolume prepare(DarkBallVolumeBuildResult source, double sourceRadius,
                                           BooleanSupplier cancellationRequested) {
        return DarkBallPreparationCancellation.run(cancellationRequested,
                () -> prepareInternal(source, sourceRadius));
    }

    private static DarkBallAnimeRestVolume prepareInternal(DarkBallVolumeBuildResult source,
                                                            double sourceRadius) {
        if (source == null || !positive(sourceRadius) || !positive(source.captureLength())
                || !positive(source.radius()) || !positive(source.sdfVoxelX())
                || !positive(source.sdfVoxelYz()) || source.signedDistanceField() == null
                || source.localThicknessField() == null) return null;
        int nx = inferSize(source.captureLength(), source.sdfVoxelX(), MAX_X_SIZE);
        int ns = inferSize(2.0 * source.radius(), source.sdfVoxelYz(), MAX_SIDE_SIZE);
        if (nx < 2 || ns < 2) return null;
        int count = nx * ns * ns;
        if (count > MAX_CELL_COUNT || source.signedDistanceField().length != count
                || source.localThicknessField().length != count) return null;

        double dx = source.captureLength() / (nx - 1.0);
        double ds = 2.0 * source.radius() / (ns - 1.0);
        double expansion = Math.min(sourceRadius * .065, Math.min(source.captureLength(),
                2.0 * source.radius()) * .125);
        if (!positive(expansion)) return null;
        float[] first = new float[count];
        boolean hasMaterial = false;
        for (int i = 0; i < count; i++) {
            DarkBallPreparationCancellation.checkEvery(i);
            int input = sourceIndex(i, ns);
            float distance = source.signedDistanceField()[input];
            float thickness = source.localThicknessField()[input];
            if (!Float.isFinite(distance) || !Float.isFinite(thickness) || thickness < 0) return null;
            hasMaterial |= distance <= 0;
            // Only the physical field owns material. The closed cohesive field is never sampled.
            first[i] = distance <= 0 ? (float) Math.min(expansion, thickness * .15) : 0;
        }
        if (!hasMaterial) return null;

        // Carry feature thickness into the exterior shell before offsetting the SDF. A separable
        // bounded max filter avoids allocating nearest-surface maps or a priority queue. Its reach
        // is only the maximum possible expansion plus one interpolation cell; fine appendages
        // remote from thick material retain their smaller offset.
        float[] second = new float[count];
        maxFilter(first, second, nx, ns, 0, Math.min(nx - 1, (int) Math.ceil(expansion / dx) + 1));
        maxFilter(second, first, nx, ns, 1, Math.min(ns - 1, (int) Math.ceil(expansion / ds) + 1));
        maxFilter(first, second, nx, ns, 2, Math.min(ns - 1, (int) Math.ceil(expansion / ds) + 1));
        for (int i = 0; i < count; i++) {
            DarkBallPreparationCancellation.checkEvery(i);
            first[i] = source.signedDistanceField()[sourceIndex(i, ns)] - second[i];
            if (!Float.isFinite(first[i])) return null;
        }

        // A trilinear scalar's gradient is a convex combination of its eight corner gradients.
        // Bounding their complete vector norm is tighter than unrelated global axis maxima and
        // includes the spatially varying thickness offset baked above.
        double maxGradientSquared = 0;
        double voxel = Math.min(dx, ds);
        double[] bands = {voxel * .25, voxel * .5, voxel, voxel * 2, voxel * 4};
        double[] bandGradientSquared = new double[bands.length];
        int minX = nx, minY = ns, minZ = ns, maxX = -1, maxY = -1, maxZ = -1;
        for (int x = 0; x < nx - 1; x++) {
            DarkBallPreparationCancellation.check();
            for (int y = 0; y < ns - 1; y++) for (int z = 0; z < ns - 1; z++) {
                boolean occupied = false;
                double cellMinimum = Double.POSITIVE_INFINITY, cellMaximum = Double.NEGATIVE_INFINITY;
                double cellGradientSquared = 0;
                for (int cx = 0; cx <= 1; cx++) for (int cy = 0; cy <= 1; cy++)
                    for (int cz = 0; cz <= 1; cz++) {
                        int ix = x + cx, iy = y + cy, iz = z + cz;
                        double gx = (first[index(x + 1, iy, iz, ns)] - (double) first[index(x, iy, iz, ns)]) / dx;
                        double gy = (first[index(ix, y + 1, iz, ns)] - (double) first[index(ix, y, iz, ns)]) / ds;
                        double gz = (first[index(ix, iy, z + 1, ns)] - (double) first[index(ix, iy, z, ns)]) / ds;
                        cellGradientSquared = Math.max(cellGradientSquared, gx * gx + gy * gy + gz * gz);
                        double value = first[index(ix, iy, iz, ns)];
                        cellMinimum = Math.min(cellMinimum, value);
                        cellMaximum = Math.max(cellMaximum, value);
                        occupied |= value <= 0;
                    }
                maxGradientSquared = Math.max(maxGradientSquared, cellGradientSquared);
                boolean boundary = x == 0 || y == 0 || z == 0 || x == nx - 2 || y == ns - 2 || z == ns - 2;
                double bandMargin = 16 * Math.ulp((float) Math.max(Math.abs(cellMinimum), Math.abs(cellMaximum)));
                for (int band = 0; band < bands.length; band++) {
                    // Clamp the interpolated scalar, never the stored nodes. Trilinear
                    // values lie between their corners, so only these complete cells
                    // can contribute a derivative inside the selected value band.
                    if (cellMinimum <= bands[band] + bandMargin && cellMaximum >= -bands[band] - bandMargin)
                        bandGradientSquared[band] = Math.max(bandGradientSquared[band], cellGradientSquared);
                    // A deeply negative boundary reaches the active band OUTSIDE the
                    // grid after adding distance. Include it even when every corner
                    // is below -band. Full cell gradient plus the orthogonal exterior
                    // distance slope also bounds faces, edges and grid corners.
                    if (boundary && cellMinimum <= bands[band] + bandMargin)
                        bandGradientSquared[band] = Math.max(bandGradientSquared[band], cellGradientSquared + 1);
                }
                if (occupied) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x + 1); maxY = Math.max(maxY, y + 1); maxZ = Math.max(maxZ, z + 1);
                }
            }
        }
        if (maxX < 0 || !Double.isFinite(maxGradientSquared)) return null;
        // Outside: f(clamp(p)) + length(p-clamp(p)). Its two derivatives are orthogonal;
        // sqrt(L^2 + 1) bounds this extension as well as every interior cell.
        double lipschitz = Math.sqrt(maxGradientSquared + 1.0) * 1.00001;
        if (!positive(lipschitz) || lipschitz > Float.MAX_VALUE) return null;
        double smallBandGradient = Math.min(lipschitz, Math.sqrt(bandGradientSquared[0]) * 1.00002 + 1e-12);
        double largeBandGradient = Math.min(lipschitz, Math.sqrt(bandGradientSquared[1]) * 1.00002 + 1e-12);
        // Prefer the smallest certified gradient; widen the interval only when
        // its bound is effectively tied. This affects tracing, not geometry.
        int selectedBand = largeBandGradient <= smallBandGradient * 1.000001 ? 1 : 0;
        double bandGradient = selectedBand == 0 ? smallBandGradient : largeBandGradient;
        Vec3 outerBands = new Vec3(bands[2] / lipschitz, bands[3] / lipschitz, bands[4] / lipschitz);
        Vec3 outerGradients = new Vec3(
                Math.min(lipschitz, Math.sqrt(bandGradientSquared[2]) * 1.00002 + 1e-12) / lipschitz,
                Math.min(lipschitz, Math.sqrt(bandGradientSquared[3]) * 1.00002 + 1e-12) / lipschitz,
                Math.min(lipschitz, Math.sqrt(bandGradientSquared[4]) * 1.00002 + 1e-12) / lipschitz);
        float[] atlas = new float[count];
        double boundaryDepth = 0;
        for (int x = 0; x < nx; x++) {
            DarkBallPreparationCancellation.check();
            for (int y = 0; y < ns; y++) for (int z = 0; z < ns; z++) {
                float value = first[index(x, y, z, ns)];
                atlas[y * nx * ns + z * nx + x] = (float) (value / lipschitz);
                if (x == 0 || x == nx - 1 || y == 0 || y == ns - 1 || z == 0 || z == ns - 1)
                    boundaryDepth = Math.max(boundaryDepth, -value);
            }
        }
        double radius = source.radius();
        Vec3 gridMin = new Vec3(0, -radius, -radius);
        Vec3 gridMax = new Vec3(source.captureLength(), radius, radius);
        // Trilinear interpolation cannot become negative in a cell whose corners are all positive.
        // Negative boundary values can extend outside the grid by at most boundaryDepth.
        double margin = Math.max(Math.max(dx, ds) * .0001,
                Math.ulp((float) Math.max(source.captureLength(), radius)) * 8.0);
        double pad = boundaryDepth + margin;
        Vec3 minimum = new Vec3(minX * dx - pad, minY * ds - radius - pad, minZ * ds - radius - pad);
        Vec3 maximum = new Vec3(maxX * dx + pad, maxY * ds - radius + pad, maxZ * ds - radius + pad);
        return new DarkBallAnimeRestVolume(nx, ns, gridMin, gridMax, minimum, maximum, lipschitz,
                bands[selectedBand] / lipschitz, bandGradient / lipschitz, outerBands, outerGradients, atlas);
    }

    private static int inferSize(double span, double voxel, int maximum) {
        double intervals = span / voxel;
        if (!Double.isFinite(intervals) || intervals < 1 || intervals > maximum - 1 + .001) return -1;
        long rounded = Math.round(intervals);
        return Math.abs(intervals - rounded) <= Math.max(.00001, rounded * .00001)
                ? (int) rounded + 1 : -1;
    }

    private static void maxFilter(float[] source, float[] target, int nx, int ns, int axis, int radius) {
        int length = axis == 0 ? nx : ns;
        int stride = axis == 0 ? ns * ns : axis == 1 ? ns : 1;
        int lines = source.length / length;
        int[] deque = new int[length];
        for (int line = 0; line < lines; line++) {
            DarkBallPreparationCancellation.checkEvery(line);
            int start = axis == 0 ? line : axis == 1
                    ? (line / ns) * ns * ns + line % ns : line * ns;
            int head = 0, tail = 0, inserted = -1;
            for (int at = 0; at < length; at++) {
                int right = Math.min(length - 1, at + radius);
                while (inserted < right) {
                    int next = ++inserted;
                    float value = source[start + next * stride];
                    while (tail > head && source[start + deque[tail - 1] * stride] <= value) tail--;
                    deque[tail++] = next;
                }
                while (deque[head] < at - radius) head++;
                target[start + at * stride] = source[start + deque[head] * stride];
            }
        }
    }

    int xSize() { return xSize; }
    int ySize() { return sideSize; }
    int zSize() { return sideSize; }
    int atlasWidth() { return xSize * sideSize; }
    int atlasHeight() { return sideSize; }
    Vec3 gridMinimum() { return gridMinimum; }
    Vec3 gridMaximum() { return gridMaximum; }
    Vec3 minimum() { return minimum; }
    Vec3 maximum() { return maximum; }
    double distanceLipschitz() { return distanceLipschitz; }
    /** Band and its derivative bound use the already normalized sampled field's units. */
    double distanceBand() { return distanceBand; }
    double distanceBandGradient() { return distanceBandGradient; }
    /** Three wider scalar certificates avoid the narrow interval's mid-distance plateau. */
    Vec3 distanceOuterBands() { return distanceOuterBands; }
    Vec3 distanceOuterBandGradients() { return distanceOuterBandGradients; }
    /** A defensive copy: upload ownership cannot mutate the prepared pose. */
    float[] atlas() { return atlas.clone(); }

    /** Globally conservative distance in physical capture-local units, including outside the grid. */
    double sample(Vec3 point) {
        if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)
                || !Double.isFinite(point.z)) return Double.POSITIVE_INFINITY;
        double px = clamp(point.x, gridMinimum.x, gridMaximum.x);
        double py = clamp(point.y, gridMinimum.y, gridMaximum.y);
        double pz = clamp(point.z, gridMinimum.z, gridMaximum.z);
        double gx = (px - gridMinimum.x) / (gridMaximum.x - gridMinimum.x) * (xSize - 1);
        double gy = (py - gridMinimum.y) / (gridMaximum.y - gridMinimum.y) * (sideSize - 1);
        double gz = (pz - gridMinimum.z) / (gridMaximum.z - gridMinimum.z) * (sideSize - 1);
        int x = Math.min((int) gx, xSize - 2), y = Math.min((int) gy, sideSize - 2);
        int z = Math.min((int) gz, sideSize - 2);
        double tx = gx - x, ty = gy - y, tz = gz - z;
        double near = mix(mix(value(x, y, z), value(x + 1, y, z), tx),
                mix(value(x, y + 1, z), value(x + 1, y + 1, z), tx), ty);
        double far = mix(mix(value(x, y, z + 1), value(x + 1, y, z + 1), tx),
                mix(value(x, y + 1, z + 1), value(x + 1, y + 1, z + 1), tx), ty);
        double ox = point.x - px, oy = point.y - py, oz = point.z - pz;
        return mix(near, far, tz) + Math.sqrt(ox * ox + oy * oy + oz * oz) / distanceLipschitz;
    }

    private double value(int x, int y, int z) { return atlas[y * atlasWidth() + z * xSize + x]; }
    private static int index(int x, int y, int z, int side) { return (x * side + y) * side + z; }
    /** The voxelizer publishes Y-contiguous fields; scratch storage here is Z-contiguous. */
    private static int sourceIndex(int scratchIndex, int side) {
        int x = scratchIndex / (side * side), y = scratchIndex / side % side, z = scratchIndex % side;
        return (x * side + z) * side + y;
    }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
    private static double clamp(double v, double low, double high) { return Math.max(low, Math.min(high, v)); }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
}
