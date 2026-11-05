package com.jayemceekay.shadowedhearts.client.moonwound;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded visual flow on exposed voxel faces. All coordinates are relative to the meteorite site. */
public final class MoonwoundSurfaceFlow {
    public static final int MAX_CELLS = 1024;
    public static final int MAX_PATHS = 12;
    public static final int MAX_STEPS = 32;
    public static final int MAX_WISPS = 96;
    public static final double MAX_PATH_LENGTH = 18;
    public static final double SPEED = 0.6;
    public static final double MIN_LIFETIME = 12;
    public static final double MAX_LIFETIME = 16;
    public static final double INITIAL_RADIUS = 0.50;
    public static final double RADIUS_GROWTH = 0.35;
    public static final double PEAK_ALPHA = 0.34;
    private static final double CLEARANCE = 0.06;
    private static final Comparator<Cell> CELL_ORDER = Comparator.comparingInt(Cell::x)
            .thenComparingInt(Cell::y).thenComparingInt(Cell::z);

    private MoonwoundSurfaceFlow() { }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Nonfinite surface-flow point");
        }
        public Point add(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
        public Point scale(double amount) { return new Point(x * amount, y * amount, z * amount); }
        public double distanceTo(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        private Point normalized() {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length < 1.0e-9 ? new Point(0, 1, 0) : scale(1 / length);
        }
    }

    public record Cell(int x, int y, int z) {
        private Cell move(Direction direction) { return new Cell(x + direction.x, y + direction.y, z + direction.z); }
        private Point point() { return new Point(x, y, z); }
    }
    public record Waypoint(Point center, Point normal) { }
    public record Path(List<Waypoint> waypoints, double length) {
        public Path { waypoints = List.copyOf(waypoints); }
    }
    /** Radius is in blocks, alpha is in 0..1, and phase is a stable shader seed in radians. */
    public record Wisp(Point center, Point normal, double radius, double alpha, double phase) { }

    public static final class Surface {
        private final Point landing;
        private final List<Path> paths;
        private final Set<Cell> obstacles;
        private final long seed;

        private Surface(Point landing, List<Path> paths, Set<Cell> obstacles, long seed) {
            this.landing = landing;
            this.paths = List.copyOf(paths);
            this.obstacles = Set.copyOf(obstacles);
            this.seed = seed;
        }

        public Point landing() { return landing; }
        public List<Path> paths() { return paths; }

        /** Stateless emission: revisiting a time produces the same bounded set without accumulating particles. */
        public List<Wisp> sample(double timeSeconds) {
            return sample(timeSeconds, MoonwoundPortalOpening.SETTLED_SECONDS);
        }

        /** The first falling stream reveals this flow along its routes, keeping the eventual emission unchanged. */
        public List<Wisp> sample(double timeSeconds, double openingAge) {
            if (!Double.isFinite(timeSeconds)) throw new IllegalArgumentException("Nonfinite surface-flow time");
            if (!Double.isFinite(openingAge)) throw new IllegalArgumentException("Nonfinite portal opening age");
            if (paths.isEmpty() || openingAge <= MoonwoundPortalOpening.IMPACT_SECONDS) return List.of();
            List<Wisp> wisps = new ArrayList<>(MAX_WISPS);
            for (int p = 0; p < paths.size(); p++) {
                Path path = paths.get(p);
                double lifetime = MIN_LIFETIME + unit(seed + p * 103L) * (MAX_LIFETIME - MIN_LIFETIME);
                for (int slot = 0; slot < 8; slot++) {
                    double phase = unit(seed + p * 8191L + slot * 131L + 7) * Math.PI * 2;
                    double age = positiveMod(timeSeconds + slot * lifetime / 8 + phase, lifetime);
                    double travel = age * SPEED;
                    if (travel >= path.length) continue;
                    double normalizedAge = age / lifetime;
                    double alpha = PEAK_ALPHA * smooth(0, 1.2, age)
                            * (1 - smooth(0.45, 1, normalizedAge))
                            * (1 - smooth(path.length * 0.70, path.length, travel))
                            * MoonwoundPortalOpening.surfaceReveal(openingAge, travel);
                    if (alpha < 0.002) continue;
                    Waypoint point = atDistance(path, travel);
                    double lift = 0.02 + 0.24 * smooth(0.25, 1, normalizedAge);
                    Point center = point.center.add(point.normal.scale(lift));
                    // Concave corners and nearby unrelated blocks can limit the dissipating lift.
                    for (int attempt = 0; attempt < 5 && !clearSegment(point.center, center, obstacles); attempt++) {
                        lift *= 0.5;
                        center = point.center.add(point.normal.scale(lift));
                    }
                    if (!clearSegment(point.center, center, obstacles)) center = point.center;
                    wisps.add(new Wisp(center, point.normal, INITIAL_RADIUS + normalizedAge * RADIUS_GROWTH, alpha, phase));
                }
            }
            return List.copyOf(wisps);
        }
    }

    /** Unknown or colliding neighboring cells belong in blocked; they never become flow surfaces. */
    public static Surface plan(Set<Cell> meteorite, Set<Cell> blocked, Point incoming, long seed) {
        Objects.requireNonNull(meteorite);
        Objects.requireNonNull(blocked);
        Objects.requireNonNull(incoming);
        if (meteorite.size() > MAX_CELLS) throw new IllegalArgumentException("Surface flow exceeds its meteorite cell budget");
        Set<Cell> obstacles = new HashSet<>(blocked);
        obstacles.addAll(meteorite);
        List<Cell> sorted = meteorite.stream().sorted(CELL_ORDER).toList();
        Face start = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Cell cell : sorted) {
            if (obstacles.contains(cell.move(Direction.UP))) continue;
            Face candidate = new Face(cell, Direction.UP);
            double distance = candidate.center().distanceTo(incoming);
            if (distance < nearest) { start = candidate; nearest = distance; }
        }
        if (start == null) return new Surface(incoming, List.of(), obstacles, seed);

        // Edge-touching fragments and disconnected islands cannot receive flow through empty space.
        Set<Cell> connected = connectedCells(start.cell, meteorite);
        List<Face> faces = new ArrayList<>();
        Map<Edge, List<Face>> sharedEdges = new HashMap<>();
        for (Cell cell : sorted) if (connected.contains(cell)) for (Direction direction : Direction.values()) {
            // Surface miasma flows down the sides; it does not turn upward onto hidden undersides.
            if (direction == Direction.DOWN || obstacles.contains(cell.move(direction))) continue;
            Face face = new Face(cell, direction);
            faces.add(face);
            for (Edge edge : face.edges()) sharedEdges.computeIfAbsent(edge, ignored -> new ArrayList<>()).add(face);
        }
        Map<Face, List<Link>> graph = new HashMap<>();
        for (Face face : faces) {
            List<Link> links = new ArrayList<>();
            for (Edge edge : face.edges()) for (Face adjacent : sharedEdges.get(edge)) {
                if (face.equals(adjacent) || face.direction.opposite(adjacent.direction)) continue;
                Point sum = crossingNormal(face, adjacent);
                Waypoint crossing = new Waypoint(edge.middle().add(sum.scale(CLEARANCE)), sum.normalized());
                if (clearSegment(face.center(), crossing.center, obstacles)
                        && clearSegment(crossing.center, adjacent.center(), obstacles))
                    links.add(new Link(adjacent, crossing, edge));
            }
            graph.put(face, List.copyOf(links));
        }
        Point landing = start.center();
        List<Path> paths = new ArrayList<>();
        for (int index = 0; index < MAX_PATHS; index++) {
            Path path = route(start, graph, landing, connected, obstacles, seed, index);
            if (path.length > 0.25) paths.add(path);
        }
        return new Surface(landing, paths, obstacles, seed);
    }

    private static Path route(Face start, Map<Face, List<Link>> graph, Point landing,
                              Set<Cell> connected, Set<Cell> obstacles, long seed, int index) {
        double heading = (unit(seed + 997) + (index + unit(seed + index * 271L) * 0.7) / MAX_PATHS) * Math.PI * 2;
        double phase = unit(seed + index * 131L + 37) * Math.PI * 2;
        double curl = (unit(seed + index * 719L + 11) - 0.5) * 0.12;
        List<Waypoint> points = new ArrayList<>();
        points.add(new Waypoint(landing, start.direction.normal()));
        Set<Face> visited = new HashSet<>();
        visited.add(start);
        Face current = start;
        double length = 0;
        for (int step = 0; step < MAX_STEPS; step++) {
            Link best = null;
            double bestScore = -Double.MAX_VALUE, bestLength = 0;
            Point here = current.center();
            double radius = Math.hypot(here.x - landing.x, here.z - landing.z);
            // An impact-relative target corrects cross-track error. Scoring a fixed heading
            // from each new face instead collapses even diagonal headings to four spokes.
            double angle = heading + (Math.sin(radius * 0.55 + phase) - Math.sin(phase)) * 0.30 + radius * curl;
            double targetX = landing.x + Math.cos(angle) * (radius + 0.75);
            double targetZ = landing.z + Math.sin(angle) * (radius + 0.75);
            double targetDistance = Math.hypot(targetX - here.x, targetZ - here.z);
            double radialX = radius > 0.1 ? (here.x - landing.x) / radius : Math.cos(angle);
            double radialZ = radius > 0.1 ? (here.z - landing.z) / radius : Math.sin(angle);
            for (Link link : graph.getOrDefault(current, List.of())) {
                if (visited.contains(link.next)) continue;
                Point there = link.next.center();
                double nextRadius = Math.hypot(there.x - landing.x, there.z - landing.z);
                // Allow tangential curling at an edge, without returning through the impact.
                if (nextRadius < radius - 0.3) continue;
                double added = here.distanceTo(link.crossing.center) + link.crossing.center.distanceTo(there);
                if (length + added > MAX_PATH_LENGTH) continue;
                double dx = there.x - here.x, dz = there.z - here.z;
                double score = (targetDistance - Math.hypot(targetX - there.x, targetZ - there.z)) * 2.0
                        + (nextRadius - radius) * 0.45
                        + (dx * radialZ - dz * radialX) * Math.copySign(0.16, curl)
                        + Math.max(0, here.y - there.y)
                        - Math.max(0, there.y - here.y) * 0.2
                        + (link.next.direction == Direction.UP ? 0 : 0.08)
                        + unit(seed + index * 271L + step * 8191L + link.next.stableId()) * 0.14;
                if (score > bestScore) { best = link; bestScore = score; bestLength = added; }
            }
            if (best == null) break;
            // Each stream crosses at a different position along the shared edge. Keep the
            // checked midpoint as a conservative fallback beside concave obstructions.
            Point edgePoint = lerp(best.edge.a.point(), best.edge.b.point(),
                    0.2 + unit(seed + index * 8191L + step * 271L + best.next.stableId()) * 0.6);
            Point sum = crossingNormal(current, best.next);
            Waypoint crossing = new Waypoint(edgePoint.add(sum.scale(CLEARANCE)), sum.normalized());
            if (!clearSegment(here, crossing.center, obstacles)
                    || !clearSegment(crossing.center, best.next.center(), obstacles)) crossing = best.crossing;
            points.add(crossing);
            points.add(new Waypoint(best.next.center(), best.next.direction.normal()));
            current = best.next;
            visited.add(current);
            length += bestLength;
        }
        return smoothPath(points, connected, obstacles);
    }

    private static Point crossingNormal(Face from, Face to) {
        return from.direction == to.direction ? from.direction.normal()
                : from.direction.normal().add(to.direction.normal());
    }

    /** Relax only coplanar runs; explicit corner bridges still wrap around each ledge. */
    private static Path smoothPath(List<Waypoint> points, Set<Cell> connected, Set<Cell> obstacles) {
        for (int pass = 0; pass < 3; pass++) {
            List<Waypoint> original = List.copyOf(points);
            for (int i = 1; i < points.size() - 1; i++) {
                Waypoint a = points.get(i - 1), b = original.get(i), c = original.get(i + 1);
                if (!a.normal.equals(b.normal) || !b.normal.equals(c.normal)) continue;
                Point candidate = a.center.scale(0.25).add(b.center.scale(0.5)).add(c.center.scale(0.25));
                if (supportedSegment(a.center, candidate, b.normal, connected, obstacles)
                        && supportedSegment(candidate, c.center, b.normal, connected, obstacles))
                    points.set(i, new Waypoint(candidate, b.normal));
            }
        }
        List<Waypoint> bounded = new ArrayList<>();
        bounded.add(points.getFirst());
        double length = 0;
        for (int i = 1; i < points.size(); i++) {
            Waypoint a = bounded.getLast(), b = points.get(i);
            double segment = a.center.distanceTo(b.center);
            if (length + segment > MAX_PATH_LENGTH) {
                double t = (MAX_PATH_LENGTH - length) / segment;
                bounded.add(new Waypoint(lerp(a.center, b.center, t), lerp(a.normal, b.normal, t).normalized()));
                length = MAX_PATH_LENGTH;
                break;
            }
            bounded.add(b);
            length += segment;
        }
        return new Path(bounded, length);
    }

    private static boolean supportedSegment(Point from, Point to, Point normal,
                                            Set<Cell> connected, Set<Cell> obstacles) {
        if (!clearSegment(from, to, obstacles)) return false;
        Point a = from.add(normal.scale(-0.2)), b = to.add(normal.scale(-0.2));
        // Check every crossed grid interval, including narrow corner slivers. Collision alone
        // would permit a shortcut over missing faces or a gap between fragments.
        List<Double> cuts = new ArrayList<>(List.of(0.0, 1.0));
        double[] starts = {a.x, a.y, a.z}, ends = {b.x, b.y, b.z};
        for (int axis = 0; axis < 3; axis++) {
            double delta = ends[axis] - starts[axis];
            if (Math.abs(delta) < 1.0e-9) continue;
            for (int grid = (int) Math.floor(Math.min(starts[axis], ends[axis])) + 1;
                 grid < Math.max(starts[axis], ends[axis]); grid++)
                cuts.add((grid - starts[axis]) / delta);
        }
        cuts.sort(Double::compare);
        for (int i = 1; i < cuts.size(); i++) {
            Point p = lerp(a, b, (cuts.get(i - 1) + cuts.get(i)) * 0.5);
            if (!connected.contains(new Cell((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)))) return false;
        }
        return true;
    }

    private static Waypoint atDistance(Path path, double distance) {
        List<Waypoint> points = path.waypoints;
        for (int i = 1; i < points.size(); i++) {
            Waypoint from = points.get(i - 1), to = points.get(i);
            double segment = from.center.distanceTo(to.center);
            if (distance <= segment) {
                double t = segment == 0 ? 0 : distance / segment;
                return new Waypoint(lerp(from.center, to.center, t), lerp(from.normal, to.normal, t).normalized());
            }
            distance -= segment;
        }
        return points.getLast();
    }

    private static Set<Cell> connectedCells(Cell start, Set<Cell> cells) {
        Set<Cell> result = new HashSet<>();
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        result.add(start);
        pending.add(start);
        while (!pending.isEmpty()) {
            Cell cell = pending.removeFirst();
            for (Direction direction : Direction.values()) {
                Cell adjacent = cell.move(direction);
                if (cells.contains(adjacent) && result.add(adjacent)) pending.addLast(adjacent);
            }
        }
        return result;
    }

    private enum Direction {
        UP(0, 1, 0), DOWN(0, -1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);
        final int x, y, z;
        Direction(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        Point normal() { return new Point(x, y, z); }
        boolean opposite(Direction other) { return x == -other.x && y == -other.y && z == -other.z; }
    }

    private record Face(Cell cell, Direction direction) {
        Point center() {
            return new Point(cell.x + 0.5 + direction.x * (0.5 + CLEARANCE),
                    cell.y + 0.5 + direction.y * (0.5 + CLEARANCE), cell.z + 0.5 + direction.z * (0.5 + CLEARANCE));
        }
        long stableId() { return cell.x * 73856093L ^ cell.y * 19349663L ^ cell.z * 83492791L ^ direction.ordinal(); }
        List<Edge> edges() {
            int x = cell.x, y = cell.y, z = cell.z;
            Cell a, b, c, d;
            if (direction.y != 0) {
                y += direction.y > 0 ? 1 : 0;
                a = new Cell(x, y, z); b = new Cell(x + 1, y, z);
                c = new Cell(x + 1, y, z + 1); d = new Cell(x, y, z + 1);
            } else if (direction.x != 0) {
                x += direction.x > 0 ? 1 : 0;
                a = new Cell(x, y, z); b = new Cell(x, y + 1, z);
                c = new Cell(x, y + 1, z + 1); d = new Cell(x, y, z + 1);
            } else {
                z += direction.z > 0 ? 1 : 0;
                a = new Cell(x, y, z); b = new Cell(x + 1, y, z);
                c = new Cell(x + 1, y + 1, z); d = new Cell(x, y + 1, z);
            }
            return List.of(Edge.of(a, b), Edge.of(b, c), Edge.of(c, d), Edge.of(d, a));
        }
    }
    private record Edge(Cell a, Cell b) {
        static Edge of(Cell a, Cell b) { return CELL_ORDER.compare(a, b) <= 0 ? new Edge(a, b) : new Edge(b, a); }
        Point middle() { return new Point((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5); }
    }
    private record Link(Face next, Waypoint crossing, Edge edge) { }

    private static boolean clearSegment(Point from, Point to, Set<Cell> obstacles) {
        for (int x = (int) Math.floor(Math.min(from.x, to.x)); x <= Math.floor(Math.max(from.x, to.x)); x++)
            for (int y = (int) Math.floor(Math.min(from.y, to.y)); y <= Math.floor(Math.max(from.y, to.y)); y++)
                for (int z = (int) Math.floor(Math.min(from.z, to.z)); z <= Math.floor(Math.max(from.z, to.z)); z++) {
                    Cell cell = new Cell(x, y, z);
                    if (obstacles.contains(cell) && intersectsInterior(from, to, cell)) return false;
                }
        return true;
    }

    private static boolean intersectsInterior(Point from, Point to, Cell cell) {
        double enter = 0, exit = 1;
        double[] origin = {from.x, from.y, from.z}, end = {to.x, to.y, to.z};
        int[] cube = {cell.x, cell.y, cell.z};
        for (int axis = 0; axis < 3; axis++) {
            double low = cube[axis] + 1.0e-7, high = cube[axis] + 1 - 1.0e-7, delta = end[axis] - origin[axis];
            if (Math.abs(delta) < 1.0e-12) {
                if (origin[axis] <= low || origin[axis] >= high) return false;
            } else {
                double a = (low - origin[axis]) / delta, b = (high - origin[axis]) / delta;
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (enter > exit) return false;
            }
        }
        return enter <= exit;
    }

    private static Point lerp(Point a, Point b, double t) {
        return new Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
    private static double smooth(double low, double high, double value) {
        double t = Math.max(0, Math.min(1, (value - low) / (high - low)));
        return t * t * (3 - 2 * t);
    }
    private static double positiveMod(double value, double period) {
        double remainder = value % period;
        return remainder < 0 ? remainder + period : remainder;
    }
    private static double unit(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return ((value ^ value >>> 31) >>> 11) * 0x1.0p-53;
    }
}
