package com.jayemceekay.shadowedhearts.client.ball;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Cached cross-sections of the physical trailing component, in the fixed intake basis. */
final class DarkBallAnimeTailProfile {
    private static final int SIZE = 64;
    private static final int BISECTIONS = 12;
    private static final int[] NEIGHBORS = {-SIZE, SIZE, -1, 1};
    static final int MAX_SAMPLE_COUNT = 96 * 64 * 64;

    record Section(Vec3 source, double sideRadius, double upRadius) {
        double radius() { return Math.min(sideRadius, upRadius); }
    }

    private final Vec3 rearPoint;
    private final double[] axial;
    private final Section[] sections;
    private final int preparationSamples;

    private DarkBallAnimeTailProfile(Vec3 rearPoint, List<Double> axial, List<Section> sections, int samples) {
        this.rearPoint = rearPoint;
        this.axial = axial.stream().mapToDouble(Double::doubleValue).toArray();
        this.sections = sections.toArray(Section[]::new);
        this.preparationSamples = samples;
    }

    static DarkBallAnimeTailProfile prepare(DarkBallAnimeRestVolume volume, Vec3 center,
            Vec3 direction, Vec3 side, Vec3 up, double radius) {
        if (volume == null || !finite(center) || !unit(direction) || !unit(side) || !unit(up)
                || Math.abs(direction.dot(side)) > 1e-6 || Math.abs(direction.dot(up)) > 1e-6
                || Math.abs(side.dot(up)) > 1e-6 || !Double.isFinite(radius) || radius <= 0) return null;
        DarkBallPreparationCancellation.check();
        Grid grid = new Grid(volume, center, direction, side, up);
        if (!grid.valid()) return null;
        byte[] occupied = new byte[SIZE * SIZE * SIZE];
        Entry rear = null;
        // Keep the sampled occupancy for connected-component selection. Boundary
        // bisection happens only once per ray, not at every surface crossing.
        for (int y = 0; y < SIZE; y++) for (int z = 0; z < SIZE; z++) {
            DarkBallPreparationCancellation.check();
            double u = grid.u(y), v = grid.v(z);
            boolean entered = false;
            for (int x = 0; x < SIZE; x++) {
                double a = grid.a(x);
                if (grid.value(a, u, v) <= 0) {
                    occupied[index(x, y, z)] = 1;
                    if (!entered) {
                        entered = true;
                        double entry = grid.entry(x == 0 ? a - grid.da : grid.a(x - 1), a, u, v);
                        if (rear == null || entry < rear.axial) rear = new Entry(entry, u, v);
                    }
                }
            }
        }
        if (rear == null) return null;
        // A regular transverse grid can miss the very apex of a pointed tail.
        // Refine its first physical zero without inventing a center between limbs.
        double stepU = grid.du * .5, stepV = grid.dv * .5;
        for (int level = 0; level < 4; level++) {
            Entry origin = rear;
            for (int oy = -1; oy <= 1; oy++) for (int oz = -1; oz <= 1; oz++) {
                if (oy == 0 && oz == 0) continue;
                Entry candidate = grid.firstEntry(origin.side + oy * stepU, origin.up + oz * stepV);
                if (candidate != null && candidate.axial < rear.axial) rear = candidate;
            }
            stepU *= .5; stepV *= .5;
        }
        Vec3 rearPoint = grid.point(rear.axial, rear.side, rear.up);
        List<Double> positions = new ArrayList<>();
        List<Section> result = new ArrayList<>();
        positions.add(rear.axial);
        result.add(new Section(rearPoint, 0, 0));
        boolean[] previous = new boolean[SIZE * SIZE], visited = new boolean[SIZE * SIZE];
        int[] queue = new int[SIZE * SIZE], chosen = new int[SIZE * SIZE];
        double anchorU = rear.side, anchorV = rear.up;
        boolean started = false;
        for (int x = 0; x < SIZE; x++) {
            DarkBallPreparationCancellation.check();
            double a = grid.a(x);
            if (a <= rear.axial) continue;
            java.util.Arrays.fill(visited, false);
            int chosenCount = 0, bestOverlap = -1;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int seed = 0; seed < SIZE * SIZE; seed++) {
                if (visited[seed] || occupied[x * SIZE * SIZE + seed] == 0) continue;
                int head = 0, tail = 1, overlap = 0;
                double nearest = Double.POSITIVE_INFINITY;
                queue[0] = seed; visited[seed] = true;
                while (head < tail) {
                    int cell = queue[head++], y = cell / SIZE, z = cell % SIZE;
                    double dy = grid.u(y) - anchorU, dz = grid.v(z) - anchorV;
                    nearest = Math.min(nearest, dy * dy + dz * dz);
                    if (previous[cell]) overlap++;
                    for (int step : NEIGHBORS) {
                        if (step == -SIZE && y == 0 || step == SIZE && y == SIZE - 1
                                || step == -1 && z == 0 || step == 1 && z == SIZE - 1) continue;
                        int next = cell + step;
                        if (!visited[next] && occupied[x * SIZE * SIZE + next] != 0) {
                            visited[next] = true; queue[tail++] = next;
                        }
                    }
                }
                if (overlap > bestOverlap || overlap == bestOverlap && nearest < bestDistance) {
                    bestOverlap = overlap; bestDistance = nearest; chosenCount = tail;
                    System.arraycopy(queue, 0, chosen, 0, tail);
                }
            }
            if (chosenCount == 0) { if (started) break; else continue; }
            // Do not jump from an ended appendage across open air to another limb.
            if (started && bestOverlap == 0 && bestDistance > 4 * (grid.du * grid.du + grid.dv * grid.dv)) break;
            double meanY = 0, meanZ = 0;
            for (int i = 0; i < chosenCount; i++) { meanY += chosen[i] / SIZE; meanZ += chosen[i] % SIZE; }
            meanY /= chosenCount; meanZ /= chosenCount;
            int anchor = chosen[0]; double nearest = Double.POSITIVE_INFINITY;
            java.util.Arrays.fill(previous, false);
            for (int i = 0; i < chosenCount; i++) {
                int cell = chosen[i]; previous[cell] = true;
                double dy = cell / SIZE - meanY, dz = cell % SIZE - meanZ;
                if (dy * dy + dz * dz < nearest) { nearest = dy * dy + dz * dz; anchor = cell; }
            }
            int ay = anchor / SIZE, az = anchor % SIZE;
            double left = grid.radialBoundary(a, ay, az, -1, 0, previous);
            double right = grid.radialBoundary(a, ay, az, 1, 0, previous);
            double bottom = grid.radialBoundary(a, ay, az, 0, -1, previous);
            double top = grid.radialBoundary(a, ay, az, 0, 1, previous);
            anchorU = (left + right) * .5; anchorV = (bottom + top) * .5;
            if (grid.value(a, anchorU, anchorV) > 0) { anchorU = grid.u(ay); anchorV = grid.v(az); }
            positions.add(a);
            result.add(new Section(grid.point(a, anchorU, anchorV),
                    Math.min(anchorU - left, right - anchorU), Math.min(anchorV - bottom, top - anchorV)));
            started = true;
        }
        if (!started) return null;
        // End at this same component's physical exit, not a later disconnected part.
        double last = positions.getLast();
        double end = grid.exit(last, Math.min(grid.aMaximum + grid.da, last + grid.da), anchorU, anchorV);
        if (end > last) {
            positions.add(end);
            result.add(new Section(grid.point(end, anchorU, anchorV), 0, 0));
        }
        return new DarkBallAnimeTailProfile(rearPoint, positions, result, grid.samples);
    }

    Vec3 rearPoint() { return rearPoint; }
    /** Occupied zero-boundary coordinates relative to the binding center; no AABB padding. */
    double minimum() { return axial[0]; }
    double maximum() { return axial[axial.length - 1]; }
    int preparationSamples() { return preparationSamples; }

    /** Local interpolation of the tracked component; this performs no field sampling. */
    Section sample(double sourceAxial) {
        if (!Double.isFinite(sourceAxial) || sourceAxial <= axial[0]) return sections[0];
        int last = axial.length - 1;
        if (sourceAxial >= axial[last]) return sections[last];
        int found = java.util.Arrays.binarySearch(axial, sourceAxial);
        if (found >= 0) return sections[found];
        int high = -found - 1, low = high - 1;
        double t = (sourceAxial - axial[low]) / (axial[high] - axial[low]);
        Section a = sections[low], b = sections[high];
        return new Section(a.source.lerp(b.source, t), a.sideRadius + (b.sideRadius - a.sideRadius) * t,
                a.upRadius + (b.upRadius - a.upRadius) * t);
    }

    private record Entry(double axial, double side, double up) { }
    private static int index(int x, int y, int z) { return (x * SIZE + y) * SIZE + z; }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static boolean unit(Vec3 v) { return finite(v) && Math.abs(v.lengthSqr() - 1) < 1e-6; }

    private static final class Grid {
        final DarkBallAnimeRestVolume volume;
        final Vec3 center, direction, side, up;
        final double aMinimum, aMaximum, uMinimum, vMinimum, da, du, dv;
        int samples;
        Grid(DarkBallAnimeRestVolume volume, Vec3 center, Vec3 direction, Vec3 side, Vec3 up) {
            this.volume = volume; this.center = center; this.direction = direction; this.side = side; this.up = up;
            double[] ar = range(volume, center, direction), ur = range(volume, center, side), vr = range(volume, center, up);
            aMinimum = ar[0]; aMaximum = ar[1]; uMinimum = ur[0]; vMinimum = vr[0];
            da = (ar[1] - ar[0]) / (SIZE - 1); du = (ur[1] - ur[0]) / (SIZE - 1); dv = (vr[1] - vr[0]) / (SIZE - 1);
        }
        boolean valid() { return Double.isFinite(da + du + dv) && da > 0 && du > 0 && dv > 0; }
        double a(int x) { return aMinimum + x * da; }
        double u(int y) { return uMinimum + y * du; }
        double v(int z) { return vMinimum + z * dv; }
        Vec3 point(double a, double u, double v) {
            return new Vec3(center.x + direction.x * a + side.x * u + up.x * v,
                    center.y + direction.y * a + side.y * u + up.y * v,
                    center.z + direction.z * a + side.z * u + up.z * v);
        }
        double value(double a, double u, double v) {
            DarkBallPreparationCancellation.checkEvery(samples++);
            if (samples > MAX_SAMPLE_COUNT) throw new IllegalStateException("Tail profile sample bound exceeded");
            return volume.sample(point(a, u, v));
        }
        double entry(double outside, double inside, double u, double v) {
            for (int i = 0; i < BISECTIONS; i++) {
                double middle = (outside + inside) * .5;
                if (value(middle, u, v) <= 0) inside = middle; else outside = middle;
            }
            return inside;
        }
        double exit(double inside, double outside, double u, double v) {
            if (value(outside, u, v) <= 0) return inside;
            for (int i = 0; i < BISECTIONS; i++) {
                double middle = (outside + inside) * .5;
                if (value(middle, u, v) <= 0) inside = middle; else outside = middle;
            }
            return inside;
        }
        Entry firstEntry(double u, double v) {
            for (int x = 0; x < SIZE; x++) if (value(a(x), u, v) <= 0)
                return new Entry(entry(x == 0 ? a(x) - da : a(x - 1), a(x), u, v), u, v);
            return null;
        }
        double radialBoundary(double a, int y, int z, int dy, int dz, boolean[] component) {
            int lastY = y, lastZ = z, nextY = y + dy, nextZ = z + dz;
            while (nextY >= 0 && nextY < SIZE && nextZ >= 0 && nextZ < SIZE && component[nextY * SIZE + nextZ]) {
                lastY = nextY; lastZ = nextZ; nextY += dy; nextZ += dz;
            }
            double inside = dy != 0 ? u(lastY) : v(lastZ), outside = dy != 0 ? u(nextY) : v(nextZ);
            for (int i = 0; i < BISECTIONS; i++) {
                double middle = (inside + outside) * .5;
                if (value(a, dy != 0 ? middle : u(y), dz != 0 ? middle : v(z)) <= 0) inside = middle; else outside = middle;
            }
            return inside;
        }
        private static double[] range(DarkBallAnimeRestVolume volume, Vec3 center, Vec3 axis) {
            Vec3 middle = volume.minimum().add(volume.maximum()).scale(.5).subtract(center);
            Vec3 half = volume.maximum().subtract(volume.minimum()).scale(.5);
            double extent = Math.abs(axis.x) * half.x + Math.abs(axis.y) * half.y + Math.abs(axis.z) * half.z;
            return new double[]{middle.dot(axis) - extent, middle.dot(axis) + extent};
        }
    }
}
