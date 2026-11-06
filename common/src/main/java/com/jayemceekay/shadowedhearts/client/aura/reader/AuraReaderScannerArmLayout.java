package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderScannerInfoFrameLayout.Geometry;
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderScannerInfoFrameLayout.Layout;
import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderScannerInfoFrameLayout.Request;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Native-style scanner arms whose complete stroke coverage respects the resolved HUD. */
public final class AuraReaderScannerArmLayout {
    private static final int RETICLE_RADIUS = 58;
    private static final int CLEAR_INTERIOR_RADIUS = 56;
    private static final int MAX_CHAMFER = 12;

    private AuraReaderScannerArmLayout() { }

    public enum Attachment { TOP, BOTTOM, LEFT, RIGHT }

    /** Preferred edge facing the scanner; routing may use another edge when this port is blocked. */
    public static Attachment attachment(Geometry panel) {
        Objects.requireNonNull(panel, "panel");
        if (panel.yOffset() + panel.height() <= -RETICLE_RADIUS) return Attachment.BOTTOM;
        if (panel.yOffset() >= RETICLE_RADIUS) return Attachment.TOP;
        if (panel.xOffset() + panel.width() <= -RETICLE_RADIUS) return Attachment.RIGHT;
        if (panel.xOffset() >= RETICLE_RADIUS) return Attachment.LEFT;
        return null;
    }

    public static List<Arm> resolve(Layout panels, Request request) {
        Objects.requireNonNull(panels, "panels");
        Objects.requireNonNull(request, "request");
        List<Geometry> bodies = panels.visibleFrames().stream().map(frame -> frame.geometry()).toList();
        List<Arm> arms = new ArrayList<>(bodies.size());
        for (Geometry panel : bodies) {
            Attachment edge = attachment(panel);
            if (edge == null) continue;
            Arm arm = resolveArm(panel, edge, bodies, request);
            if (arm == null) {
                // A safe body can have its scanner-facing port blocked by
                // another HUD element. Keep the body and route to a free edge.
                for (Attachment alternate : Attachment.values()) {
                    if (alternate == edge) continue;
                    Arm candidate = resolveArm(panel, alternate, bodies, request);
                    if (candidate != null && (arm == null || candidate.pixels().size() < arm.pixels().size())) {
                        arm = candidate;
                    }
                }
            }
            if (arm != null) arms.add(arm);
        }
        return List.copyOf(arms);
    }

    private static Arm resolveArm(Geometry panel, Attachment edge, List<Geometry> bodies, Request request) {
        Point port = port(panel, edge);
        if (!safeFootprint(port, bodies, request)) return null;
        List<Point> roots = roots(port, edge);
        List<AuraReaderHudRect> best = raster(direct(roots.getFirst(), port), bodies, request);
        if (best == null) {
            for (int index = 1; index < roots.size(); index++) {
                List<AuraReaderHudRect> candidate = raster(direct(roots.get(index), port), bodies, request);
                best = shorter(best, candidate);
            }
        }
        if (best == null) {
            Set<Integer> xs = new LinkedHashSet<>();
            Set<Integer> ys = new LinkedHashSet<>();
            xs.add(port.x());
            ys.add(port.y());
            xs.add(-61); xs.add(61);
            ys.add(-61); ys.add(61);
            for (Point root : roots) { xs.add(root.x()); ys.add(root.y()); }
            for (Geometry body : bodies) {
                xs.add(body.xOffset() - 2); xs.add(body.xOffset() + body.width() + 2);
                ys.add(body.yOffset() - 2); ys.add(body.yOffset() + body.height() + 2);
            }
            for (AuraReaderHudRect obstacle : forbidden(request)) {
                if (obstacle.width() == 0 || obstacle.height() == 0) continue;
                xs.add(nativeFloor(obstacle.left(), request.scannerCenterX(), request.scannerScale()) - 2);
                xs.add(nativeCeil(obstacle.right(), request.scannerCenterX(), request.scannerScale()) + 2);
                ys.add(nativeFloor(obstacle.top(), request.scannerCenterY(), request.scannerScale()) - 2);
                ys.add(nativeCeil(obstacle.bottom(), request.scannerCenterY(), request.scannerScale()) + 2);
            }
            for (Point root : roots) {
                for (int x : xs) {
                    List<Point> path = chamfer(List.of(root, new Point(x, root.y()), new Point(x, port.y()), port));
                    best = shorter(best, raster(path, bodies, request));
                }
                for (int y : ys) {
                    List<Point> path = chamfer(List.of(root, new Point(root.x(), y), new Point(port.x(), y), port));
                    best = shorter(best, raster(path, bodies, request));
                }
            }
        }
        return best == null || best.isEmpty() ? null : new Arm(panel, edge, best);
    }

    private static List<AuraReaderHudRect> shorter(List<AuraReaderHudRect> current, List<AuraReaderHudRect> candidate) {
        return candidate != null && (current == null || candidate.size() < current.size()) ? candidate : current;
    }

    private static Point port(Geometry panel, Attachment edge) {
        int centerX = midpoint(panel.xOffset(), panel.width());
        int centerY = midpoint(panel.yOffset(), panel.height());
        return switch (edge) {
            case TOP -> new Point(centerX, panel.yOffset() - 1);
            case BOTTOM -> new Point(centerX, panel.yOffset() + panel.height() + 1);
            case LEFT -> new Point(panel.xOffset() - 1, centerY);
            case RIGHT -> new Point(panel.xOffset() + panel.width() + 1, centerY);
        };
    }

    private static List<Point> roots(Point port, Attachment edge) {
        Set<Point> roots = new LinkedHashSet<>();
        int xSign = port.x() < 0 ? -1 : 1;
        int ySign = port.y() < 0 ? -1 : 1;
        roots.add(switch (edge) {
            case TOP -> new Point(port.x() == 0 ? 0 : 53 * xSign, port.x() == 0 ? 59 : 26);
            case BOTTOM -> new Point(port.x() == 0 ? 0 : 53 * xSign, port.x() == 0 ? -59 : -26);
            case LEFT -> new Point(58, 7 * ySign);
            case RIGHT -> new Point(-58, 7 * ySign);
        });
        double length = Math.hypot(port.x(), port.y());
        roots.add(new Point((int) Math.round(port.x() * 59.0 / length), (int) Math.round(port.y() * 59.0 / length)));
        roots.add(new Point(-59, 0)); roots.add(new Point(59, 0));
        roots.add(new Point(0, -59)); roots.add(new Point(0, 59));
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) roots.add(new Point(sx * 42, sy * 42));
        }
        return List.copyOf(roots);
    }

    /** From the rim, grow the 45-degree segment first, then the remaining axis stem. */
    private static List<Point> direct(Point root, Point port) {
        int dx = port.x() - root.x();
        int dy = port.y() - root.y();
        int diagonal = Math.min(Math.abs(dx), Math.abs(dy));
        return compact(List.of(root, new Point(root.x() + Integer.signum(dx) * diagonal,
                root.y() + Integer.signum(dy) * diagonal), port));
    }

    private static List<Point> chamfer(List<Point> original) {
        List<Point> path = compact(original);
        if (path.size() < 3) return path;
        List<Point> result = new ArrayList<>();
        result.add(path.getFirst());
        for (int index = 1; index < path.size() - 1; index++) {
            Point previous = path.get(index - 1), corner = path.get(index), next = path.get(index + 1);
            int beforeX = Integer.signum(corner.x() - previous.x());
            int beforeY = Integer.signum(corner.y() - previous.y());
            int afterX = Integer.signum(next.x() - corner.x());
            int afterY = Integer.signum(next.y() - corner.y());
            if (beforeX * afterX + beforeY * afterY != 0) {
                result.add(corner);
                continue;
            }
            int beforeLength = Math.abs(corner.x() - previous.x()) + Math.abs(corner.y() - previous.y());
            int afterLength = Math.abs(next.x() - corner.x()) + Math.abs(next.y() - corner.y());
            int amount = Math.min(MAX_CHAMFER, Math.min(beforeLength / 2, afterLength / 2));
            result.add(new Point(corner.x() - beforeX * amount, corner.y() - beforeY * amount));
            result.add(new Point(corner.x() + afterX * amount, corner.y() + afterY * amount));
        }
        result.add(path.getLast());
        return compact(result);
    }

    private static List<Point> compact(List<Point> points) {
        List<Point> result = new ArrayList<>(points.size());
        for (Point point : points) if (result.isEmpty() || !result.getLast().equals(point)) result.add(point);
        return result;
    }

    private static List<AuraReaderHudRect> raster(List<Point> path, List<Geometry> bodies, Request request) {
        Set<Long> coverage = new LinkedHashSet<>();
        for (Point point : path) if (!safeFootprint(point, bodies, request)) return null;
        Point first = path.getFirst();
        for (int y = first.y() - 1; y <= first.y(); y++) {
            for (int x = first.x() - 1; x <= first.x(); x++) coverage.add(key(x, y));
        }
        for (int index = 1; index < path.size(); index++) {
            Point start = path.get(index - 1), end = path.get(index);
            int x = start.x(), y = start.y();
            int dx = Math.abs(end.x() - x), sx = x < end.x() ? 1 : -1;
            int dy = -Math.abs(end.y() - y), sy = y < end.y() ? 1 : -1;
            int error = dx + dy;
            while (true) {
                for (int cellY = y - 1; cellY <= y; cellY++) {
                    for (int cellX = x - 1; cellX <= x; cellX++) {
                        long key = key(cellX, cellY);
                        if (!coverage.contains(key)) {
                            if (!safeCell(cellX, cellY, bodies, request)) return null;
                            coverage.add(key);
                        }
                    }
                }
                if (x == end.x() && y == end.y()) break;
                int twice = error * 2;
                if (twice >= dy) { error += dy; x += sx; }
                if (twice <= dx) { error += dx; y += sy; }
            }
        }
        return coverage.stream().map(cell -> pixel((int) (cell >> 32), (int) (long) cell)).toList();
    }

    private static boolean safeFootprint(Point point, List<Geometry> bodies, Request request) {
        return safeCell(point.x() - 1, point.y() - 1, bodies, request)
                && safeCell(point.x(), point.y() - 1, bodies, request)
                && safeCell(point.x() - 1, point.y(), bodies, request)
                && safeCell(point.x(), point.y(), bodies, request);
    }

    private static boolean safeCell(int x, int y, List<Geometry> bodies, Request request) {
        double nearestX = x >= 0 ? x : Math.min(0, x + 1);
        double nearestY = y >= 0 ? y : Math.min(0, y + 1);
        if (nearestX * nearestX + nearestY * nearestY < CLEAR_INTERIOR_RADIUS * CLEAR_INTERIOR_RADIUS) return false;
        for (Geometry body : bodies) {
            if (x >= body.xOffset() && x < body.xOffset() + body.width()
                    && y >= body.yOffset() && y < body.yOffset() + body.height()) return false;
        }
        float left = request.scannerCenterX() + x * request.scannerScale();
        float top = request.scannerCenterY() + y * request.scannerScale();
        AuraReaderHudRect bounds = new AuraReaderHudRect((int) Math.floor(left), (int) Math.floor(top),
                (int) Math.ceil(left + request.scannerScale()), (int) Math.ceil(top + request.scannerScale()));
        if (bounds.left() < 0 || bounds.top() < 0 || bounds.right() > request.viewportWidth()
                || bounds.bottom() > request.viewportHeight()) return false;
        if (bounds.intersects(request.partySafeZone()) || bounds.intersects(request.bottomSafeZone())) return false;
        return request.reservedZones().stream().noneMatch(bounds::intersects);
    }

    private static List<AuraReaderHudRect> forbidden(Request request) {
        List<AuraReaderHudRect> result = new ArrayList<>(request.reservedZones());
        result.add(request.partySafeZone()); result.add(request.bottomSafeZone());
        return result;
    }

    /** A global union prevents translucent crossings and elbows from being painted twice. */
    public static List<AuraReaderHudRect> quads(List<Arm> arms, float progress) {
        float visible = clamp(progress);
        if (arms == null || arms.isEmpty() || visible <= 0) return List.of();
        Map<Integer, TreeSet<Integer>> rows = new TreeMap<>();
        for (Arm arm : arms) {
            int count = Math.min(arm.pixels().size(), (int) Math.ceil(arm.pixels().size() * (double) visible));
            for (int index = 0; index < count; index++) {
                AuraReaderHudRect pixel = arm.pixels().get(index);
                rows.computeIfAbsent(pixel.top(), ignored -> new TreeSet<>()).add(pixel.left());
            }
        }
        List<AuraReaderHudRect> result = new ArrayList<>();
        Map<Run, AuraReaderHudRect> active = new LinkedHashMap<>();
        int previousY = Integer.MIN_VALUE;
        for (var row : rows.entrySet()) {
            int y = row.getKey();
            if (y != previousY + 1) { result.addAll(active.values()); active.clear(); }
            Map<Run, AuraReaderHudRect> next = new LinkedHashMap<>();
            for (Run run : runs(row.getValue())) {
                AuraReaderHudRect above = active.remove(run);
                next.put(run, new AuraReaderHudRect(run.left(), above == null ? y : above.top(), run.right(), y + 1));
            }
            result.addAll(active.values());
            active = next;
            previousY = y;
        }
        result.addAll(active.values());
        result.sort(Comparator.comparingInt(AuraReaderHudRect::top).thenComparingInt(AuraReaderHudRect::left));
        return List.copyOf(result);
    }

    private static List<Run> runs(TreeSet<Integer> columns) {
        List<Run> runs = new ArrayList<>();
        int start = 0, end = 0;
        boolean first = true;
        for (int x : columns) {
            if (first) { start = x; end = x + 1; first = false; }
            else if (x == end) end++;
            else { runs.add(new Run(start, end)); start = x; end = x + 1; }
        }
        if (!first) runs.add(new Run(start, end));
        return runs;
    }

    /** Keep the attachment edge fixed as the label opens outward. */
    public static Geometry reveal(Geometry panel, Attachment attachment, float progress) {
        Objects.requireNonNull(panel, "panel");
        Attachment edge = attachment == null ? attachment(panel) : attachment;
        int minimumWidth = Math.min(panel.width(), AuraReaderScannerInfoFrameLayout.BRACKET_WIDTH * 2);
        int width = Math.max(minimumWidth, Math.min(panel.width(), (int) Math.ceil(panel.width() * (double) clamp(progress))));
        int x = edge == Attachment.RIGHT ? panel.xOffset() + panel.width() - width
                : edge == Attachment.LEFT ? panel.xOffset() : panel.xOffset() + (panel.width() - width) / 2;
        return new Geometry(panel.tier(), panel.side(), x, panel.yOffset(), width, panel.height());
    }

    private static float clamp(float value) { return Float.isNaN(value) ? 0 : Math.max(0, Math.min(1, value)); }
    private static int midpoint(int start, int size) {
        double center = start + size / 2.0;
        return (int) (center < 0 ? Math.floor(center) : Math.ceil(center));
    }
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private static AuraReaderHudRect pixel(int x, int y) { return new AuraReaderHudRect(x, y, x + 1, y + 1); }
    private static int nativeFloor(float screen, float center, float scale) { return (int) Math.floor((screen - center) / scale); }
    private static int nativeCeil(float screen, float center, float scale) { return (int) Math.ceil((screen - center) / scale); }

    public record Arm(Geometry panel, Attachment attachment, List<AuraReaderHudRect> pixels) {
        public Arm {
            Objects.requireNonNull(panel, "panel");
            Objects.requireNonNull(attachment, "attachment");
            pixels = List.copyOf(pixels);
        }
    }
    private record Point(int x, int y) { }
    private record Run(int left, int right) { }
}
