package com.jayemceekay.shadowedhearts.client.render.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic, client-independent geometry for the core's ink-in-water effect. */
public final class UmbrafallCoreSeepGeometry {
    public static final int APERTURE = 0;
    public static final int PLUME = 1;
    public static final int FILAMENT = 2;
    public static final double SURFACE_OFFSET = .001;
    private static final int STRANDS = 4;
    private static final int SEGMENTS = 20;
    private static final int PUFFS_PER_STRAND = 8;
    public static final int MAX_QUADS_PER_FACE = 1 + STRANDS * (SEGMENTS + PUFFS_PER_STRAND);
    private static final double MOTION_SPEED = 1.75;
    private static final double REACH_SCALE = 4.5;
    private static final double TENDRIL_WIDTH_SCALE = 3.0;
    private static final double INK_RADIUS_SCALE = 3.5;
    private static final double SPREAD_SCALE = 3.5;
    private static final Vec3 CENTER = new Vec3(.5, .5, .5);
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final double TAU = Math.PI * 2;

    private UmbrafallCoreSeepGeometry() { }

    /**
     * Local coordinates are relative to the block's minimum corner. The caller decides which
     * faces are exposed; the aperture comes first and the translucent geometry follows far to near.
     */
    public static List<Quad> buildFace(Direction face, double seconds, long positionSeed, Vec3 cameraLocal) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(cameraLocal, "cameraLocal");
        if (!Double.isFinite(seconds) || !finite(cameraLocal)) {
            throw new IllegalArgumentException("Core seep requires a finite clock and camera");
        }
        Basis basis = basis(face);
        long seed = faceSeed(positionSeed, face);
        List<Quad> translucent = new ArrayList<>(MAX_QUADS_PER_FACE - 1);
        for (int strand = 0; strand < STRANDS; strand++) {
            addStrand(translucent, basis, strand, seconds, seed, cameraLocal);
            for (int puff = 0; puff < PUFFS_PER_STRAND; puff++) {
                addPlume(translucent, basis, strand, puff, seconds, seed, cameraLocal);
            }
        }
        translucent.sort(Comparator.comparingDouble((Quad q) -> q.center().distanceToSqr(cameraLocal)).reversed());
        List<Quad> result = new ArrayList<>(translucent.size() + 1);
        // Vanilla cube_all V runs opposite this outward-wound frame's up axis on all six faces.
        result.add(new Quad(vertex(basis.point(-.5, -.5, SURFACE_OFFSET), 0, 1),
                vertex(basis.point(.5, -.5, SURFACE_OFFSET), 1, 1),
                vertex(basis.point(.5, .5, SURFACE_OFFSET), 1, 0),
                vertex(basis.point(-.5, .5, SURFACE_OFFSET), 0, 0), 1, shaderSeed(seed), APERTURE));
        result.addAll(translucent);
        return result;
    }

    public static long faceSeed(long positionSeed, Direction face) {
        return mix(positionSeed ^ (0x9E3779B97F4A7C15L * (face.ordinal() + 1L)));
    }

    private static void addStrand(List<Quad> quads, Basis basis, int strand, double seconds,
                                  long seed, Vec3 cameraLocal) {
        StrandState state = strandState(strand, seconds, seed);
        if (state.alpha < .005 || state.head <= .002) return;
        Vertex[] left = new Vertex[SEGMENTS + 1];
        Vertex[] right = new Vertex[SEGMENTS + 1];
        Vec3 previousSide = null;
        for (int step = 0; step <= SEGMENTS; step++) {
            double s = state.tail + (state.head - state.tail) * step / SEGMENTS;
            Vec3 point = strandPoint(basis, strand, s, seconds, seed, state.length);
            Vec3 tangent = safeNormal(strandPoint(basis, strand, s + .005, seconds, seed, state.length)
                    .subtract(point), basis.normal);
            Vec3 facing = safeNormal(cameraLocal.subtract(point), basis.normal);
            Vec3 side = tangent.cross(facing);
            if (side.lengthSqr() < 1E-8) {
                // Looking along the strand makes the facing frame ambiguous. Carry its
                // previous width direction forward, projected onto the new cross-section.
                Vec3 reference = previousSide != null ? previousSide : basis.right;
                side = reference.subtract(tangent.scale(reference.dot(tangent)));
                if (side.lengthSqr() < 1E-8) {
                    side = basis.up.subtract(tangent.scale(basis.up.dot(tangent)));
                }
            }
            side = safeNormal(side, basis.right);
            // The facing cross product can reverse at a bend. Swapping ribbon edges
            // there twists the connecting quad into a bow tie and leaves a visible cut.
            if (previousSide != null && side.dot(previousSide) < 0) side = side.scale(-1);
            previousSide = side;
            double width = strandWidth(state.head - s, seconds, strand);
            float u = (float) step / SEGMENTS;
            left[step] = vertex(basis.keepOutside(point.subtract(side.scale(width))), u, 0);
            right[step] = vertex(basis.keepOutside(point.add(side.scale(width))), u, 1);
        }
        float shaderSeed = shaderSeed(seed + strand * 97L);
        for (int step = 0; step < SEGMENTS; step++) {
            quads.add(new Quad(left[step], right[step], right[step + 1], left[step + 1],
                    (float) state.alpha, shaderSeed, FILAMENT));
        }
    }

    static StrandState strandState(int strand, double seconds, long faceSeed) {
        double period = 11.5 + strand * .85;
        double clock = (seconds * MOTION_SPEED + unit(faceSeed + strand * 71L) * 37) / period;
        long cycle = (long) Math.floor(clock);
        double phase = clock - Math.floor(clock);
        double head = Math.max(0, -.035 + 1.08 * smooth(0, .77, phase));
        // Only one of the four strands can make the occasional long excursion.
        boolean longWisp = strand == 0 && Math.floorMod(cycle + Math.floorMod(faceSeed, 5L), 5) == 0;
        double length = REACH_SCALE * (longWisp ? .96 : .40 + .13 * unit(faceSeed + strand * 137L));
        return new StrandState(phase, head, Math.max(0, head - .94),
                1 - smooth(.76, .99, phase), length, longWisp);
    }

    static double strandWidth(double behindTip, double seconds, int strand) {
        // Width is measured behind the advancing head, so the pointed tip emerges first.
        return TENDRIL_WIDTH_SCALE * (.012 + .002 * Math.sin(behindTip * 10 - seconds * MOTION_SPEED * .22 + strand))
                * smooth(0, .18, behindTip) * (1 - .26 * Math.max(0, behindTip));
    }

    static Vec3 strandPoint(Basis basis, int strand, double s, double seconds, long seed, double length) {
        double motionSeconds = seconds * MOTION_SPEED;
        double angle = strand * TAU / STRANDS + .30 * unit(seed + 3) + .12 * Math.sin(motionSeconds * .18 + strand * 2.3)
                + s * (.25 * Math.sin(motionSeconds * .16 + strand) + .76 * Math.sin(s * 3.4 + strand * 1.7));
        double radius = .018 + SPREAD_SCALE * s * (.105 + .02 * Math.sin(strand * 5.7));
        double curl = SPREAD_SCALE * .043 * Math.sin(s * 7.5 - motionSeconds * .25 + strand) * s;
        double outward = .003 + length * (.76 * s + .24 * s * s);
        Vec3 point = basis.point(radius * Math.cos(angle) + curl * Math.sin(angle),
                radius * Math.sin(angle) - curl * Math.cos(angle), outward);
        // Side faces drift upward. Downward strands remain outside their face while gently slowing their descent.
        return basis.keepOutside(point.add(WORLD_UP.scale(REACH_SCALE * .055 * s * s)));
    }

    private static void addPlume(List<Quad> quads, Basis basis, int strand, int puff,
                                 double seconds, long seed, Vec3 cameraLocal) {
        double motionSeconds = seconds * MOTION_SPEED;
        double clock = motionSeconds / (11.8 + strand * .8) + (double) puff / PUFFS_PER_STRAND
                + unit(seed + strand * 47L);
        double age = clock - Math.floor(clock);
        double alpha = smooth(0, .16, age) * (1 - smooth(.65, 1, age)) * .68;
        if (alpha < .005) return;
        double emittedAt = motionSeconds - age * (11.8 + strand * .8);
        double angle = strand * TAU / STRANDS + .3 * unit(seed + 3)
                + .15 * Math.sin(emittedAt * .19 + strand)
                + .65 * age * Math.sin(emittedAt * .13 + strand * 2.3) + .8 * age * age;
        double spread = .025 + SPREAD_SCALE * age * .17;
        double curl = SPREAD_SCALE * .06 * Math.sin(age * 5.8 + strand * 1.9) * age;
        Vec3 center = basis.point(Math.cos(angle) * spread - Math.sin(angle) * curl,
                Math.sin(angle) * spread + Math.cos(angle) * curl, .035 + REACH_SCALE * .43 * age)
                .add(WORLD_UP.scale(REACH_SCALE * .06 * age * age));
        Vec3 facing = safeNormal(cameraLocal.subtract(center), basis.normal);
        Vec3 right = safeNormal(WORLD_UP.cross(facing), basis.right);
        Vec3 up = safeNormal(facing.cross(right), basis.up);
        double radius = .018 + INK_RADIUS_SCALE * .105 * Math.pow(age, .8);
        double rotation = angle + age * 1.4;
        Vec3 across = right.scale(Math.cos(rotation)).add(up.scale(Math.sin(rotation))).scale(radius);
        Vec3 vertical = right.scale(-Math.sin(rotation)).add(up.scale(Math.cos(rotation))).scale(radius * 1.32);
        quads.add(new Quad(vertex(basis.keepOutside(center.subtract(across).subtract(vertical)), 0, 0),
                vertex(basis.keepOutside(center.add(across).subtract(vertical)), 1, 0),
                vertex(basis.keepOutside(center.add(across).add(vertical)), 1, 1),
                vertex(basis.keepOutside(center.subtract(across).add(vertical)), 0, 1),
                (float) alpha, shaderSeed(seed + 1009L + strand * 37L + puff), PLUME));
    }

    static Basis basis(Direction direction) {
        return switch (direction) {
            case SOUTH -> new Basis(new Vec3(0, 0, 1), new Vec3(1, 0, 0), WORLD_UP);
            case NORTH -> new Basis(new Vec3(0, 0, -1), new Vec3(-1, 0, 0), WORLD_UP);
            case EAST -> new Basis(new Vec3(1, 0, 0), new Vec3(0, 0, -1), WORLD_UP);
            case WEST -> new Basis(new Vec3(-1, 0, 0), new Vec3(0, 0, 1), WORLD_UP);
            case UP -> new Basis(WORLD_UP, new Vec3(1, 0, 0), new Vec3(0, 0, -1));
            case DOWN -> new Basis(new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1));
        };
    }

    private static Vertex vertex(Vec3 point, float u, float v) { return new Vertex(point.x, point.y, point.z, u, v); }

    private static Vec3 safeNormal(Vec3 vector, Vec3 fallback) {
        double length = vector.lengthSqr();
        return length > 1E-12 && Double.isFinite(length) ? vector.scale(1 / Math.sqrt(length)) : fallback;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static float shaderSeed(long seed) { return (float) (mix(seed) & 32767); }
    private static double unit(long seed) { return (mix(seed) >>> 11) * 0x1.0p-53; }
    private static long mix(long seed) {
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }
    private static double smooth(double from, double to, double value) {
        double t = Math.max(0, Math.min(1, (value - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }

    record Basis(Vec3 normal, Vec3 right, Vec3 up) {
        Vec3 point(double u, double v, double outward) {
            return CENTER.add(normal.scale(.5 + outward)).add(right.scale(u)).add(up.scale(v));
        }
        Vec3 keepOutside(Vec3 point) {
            double distance = point.subtract(CENTER).dot(normal) - .5;
            return distance >= SURFACE_OFFSET ? point : point.add(normal.scale(SURFACE_OFFSET - distance));
        }
    }

    record StrandState(double phase, double head, double tail, double alpha, double length, boolean longWisp) { }

    public record Vertex(double x, double y, double z, float u, float v) {
        public Vec3 position() { return new Vec3(x, y, z); }
        public boolean isFinite() {
            return Float.isFinite((float) x) && Float.isFinite((float) y) && Float.isFinite((float) z)
                    && Float.isFinite(u) && Float.isFinite(v);
        }
    }

    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, float alpha, float seed, int kind) {
        public Vec3 center() { return a.position().add(b.position()).add(c.position()).add(d.position()).scale(.25); }
        public boolean isFinite() {
            return a != null && b != null && c != null && d != null && a.isFinite() && b.isFinite()
                    && c.isFinite() && d.isFinite() && Float.isFinite(alpha) && Float.isFinite(seed);
        }
    }
}
