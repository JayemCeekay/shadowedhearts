package com.jayemceekay.shadowedhearts.client.moonwound;

import com.jayemceekay.shadowedhearts.common.moonwound.MoonwoundPortalGeometry;
import com.jayemceekay.shadowedhearts.client.ClientEffects;
import com.jayemceekay.shadowedhearts.network.moonwound.MoonwoundSnapshot;
import com.jayemceekay.shadowedhearts.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundSurfaceFlow.*;

/** Bounded client-only observations of current rock. Rendering never reads blocks or grants gameplay authority. */
public final class MoonwoundSurfaceMiasma {
    private static final int READS_PER_TICK = 512;
    private static final int WORK_STEPS_PER_TICK = 2048;
    private static final int MAX_SITES = 8;
    private static final int MAX_BODY_BLOCKS = 1024;
    private static final int MAX_SCAN_READS = 6000;
    private static final int REFRESH_TICKS = 20;
    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final List<Cell> SEED_COLUMNS = seedColumns();
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();
    private static ClientLevel level;
    private static long ticks;
    private static int nextEntry;

    private MoonwoundSurfaceMiasma() { }

    /** Cached, immutable site-relative geometry only; no world access on the render path. */
    public static Surface surface(MoonwoundSnapshot snapshot) {
        Entry entry = ENTRIES.get(snapshot.siteId());
        return snapshot.portalActive() && entry != null && entry.matches(snapshot) ? entry.surface : null;
    }

    public static void clear() {
        ENTRIES.clear();
        level = null;
        ticks = 0;
        nextEntry = 0;
    }

    /** At most 512 explicit block observations across all sites, including stale-route checks. */
    public static void tick(Minecraft minecraft, List<MoonwoundSnapshot> snapshots) {
        if (!ClientEffects.moonwoundMiasma()) {
            clear();
            return;
        }
        if (level != minecraft.level) {
            clear();
            level = minecraft.level;
        }
        if (level == null || minecraft.player == null) {
            ENTRIES.clear();
            return;
        }
        ticks++;
        var eye = minecraft.player.position();
        var nearest = snapshots.stream().filter(MoonwoundSnapshot::portalActive)
                .filter(snapshot -> snapshot.portalSize() >= 0.001f
                        && snapshot.dimension().equals(level.dimension().location())
                        && snapshot.center().distToCenterSqr(eye.x, eye.y, eye.z) <= 64 * 64)
                .sorted(Comparator.comparingDouble(snapshot -> snapshot.center().distToCenterSqr(eye.x, eye.y, eye.z)))
                .limit(MAX_SITES).toList();
        Set<UUID> active = new HashSet<>();
        for (MoonwoundSnapshot snapshot : nearest) {
            active.add(snapshot.siteId());
            Entry existing = ENTRIES.get(snapshot.siteId());
            if (existing == null || !existing.matches(snapshot)) ENTRIES.put(snapshot.siteId(), new Entry(snapshot));
        }
        ENTRIES.keySet().removeIf(id -> !active.contains(id));
        if (ENTRIES.isEmpty()) return;

        Budget budget = new Budget();
        List<Entry> entries = new ArrayList<>(ENTRIES.values());
        for (Entry entry : entries) {
            validate(entry, budget);
            if (entry.work == null && ticks >= entry.refreshAt) entry.work = new Scan(entry.snapshot);
        }
        int plansBuilt = 0;
        for (int step = 0; step < WORK_STEPS_PER_TICK && budget.reads < READS_PER_TICK; step++) {
            Entry entry = entries.get(Math.floorMod(nextEntry++, entries.size()));
            if (entry.work == null) {
                if (entries.stream().noneMatch(candidate -> candidate.work != null)) break;
                continue;
            }
            if (entry.work.finished) {
                // Keep graph construction bounded too, rather than rebuilding all eight at once.
                if (plansBuilt == 0) {
                    install(entry);
                    plansBuilt++;
                }
                continue;
            }
            entry.work.advance(budget);
        }
    }

    private static void validate(Entry entry, Budget budget) {
        if (entry.surface == null || entry.checks.isEmpty()) return;
        // Always check the landing carrier, then rotate through the carriers and neighbors along paths.
        for (int index = 0; index < 12 && budget.reads < READS_PER_TICK; index++) {
            Observation expected = entry.checks.get(index == 0 ? 0 : Math.floorMod(entry.checkCursor++, entry.checks.size()));
            Kind actual = observe(entry.snapshot.center(), expected.cell, budget);
            if (actual != expected.kind) {
                entry.surface = null;
                entry.checks = List.of();
                entry.work = new Scan(entry.snapshot);
                return;
            }
        }
    }

    private static void install(Entry entry) {
        Scan scan = entry.work;
        // Unvisited neighbors at a hard cap are unknown walls, never invented open faces.
        for (Cell cell : scan.meteorite) for (int[] direction : NEIGHBORS) {
            Cell neighbor = offset(cell, direction);
            Kind kind = scan.observed.get(neighbor);
            if (kind == null || kind == Kind.BLOCKED || kind == Kind.UNKNOWN
                    || (kind == Kind.BODY && !scan.meteorite.contains(neighbor))) scan.blocked.add(neighbor);
        }
        Point incoming = new Point(0.5, scan.summit, 0.5);
        Surface surface = MoonwoundSurfaceFlow.plan(Set.copyOf(scan.meteorite), Set.copyOf(scan.blocked),
                incoming, entry.snapshot.siteId().getMostSignificantBits() ^ entry.snapshot.siteId().getLeastSignificantBits());
        entry.surface = surface.paths().isEmpty() ? null : surface;
        LinkedHashSet<Cell> watched = new LinkedHashSet<>();
        if (!surface.paths().isEmpty()) {
            Point landing = surface.landing();
            Cell landingCarrier = new Cell((int) Math.floor(landing.x()),
                    (int) Math.floor(landing.y() - 0.2), (int) Math.floor(landing.z()));
            if (scan.meteorite.contains(landingCarrier)) watched.add(landingCarrier);
        }
        if (scan.seed != null) watched.add(scan.seed);
        for (var path : surface.paths()) for (var waypoint : path.waypoints()) {
            Point p = waypoint.center(), n = waypoint.normal();
            Cell carrier = new Cell((int) Math.floor(p.x() - n.x() * 0.2),
                    (int) Math.floor(p.y() - n.y() * 0.2), (int) Math.floor(p.z() - n.z() * 0.2));
            if (!scan.meteorite.contains(carrier)) continue;
            watched.add(carrier);
            for (int[] direction : NEIGHBORS) watched.add(offset(carrier, direction));
        }
        // Include remaining observed cells after active path carriers, so mined holes and obstructions refresh too.
        watched.addAll(scan.observed.keySet());
        List<Observation> checks = new ArrayList<>();
        for (Cell cell : watched) if (scan.within(cell) && scan.observed.containsKey(cell))
            checks.add(new Observation(cell, scan.observed.get(cell)));
        entry.checks = List.copyOf(checks);
        entry.checkCursor = 0;
        entry.work = null;
        entry.refreshAt = ticks + REFRESH_TICKS;
    }

    private static Kind observe(BlockPos origin, Cell cell, Budget budget) {
        budget.reads++;
        BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return Kind.UNKNOWN;
        BlockState state = level.getBlockState(pos);
        if (meteorite(state)) return Kind.BODY;
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty() ? Kind.AIR : Kind.BLOCKED;
    }

    private static boolean meteorite(BlockState state) {
        return state.is(ModBlocks.UMBRAFALL_SHELL.get()) || state.is(ModBlocks.CRACKED_UMBRAFALL_SHELL.get())
                || state.is(ModBlocks.UMBRAL_LATTICE.get()) || state.is(ModBlocks.UMBRAFALL_CORE_CASING.get())
                || state.is(ModBlocks.UMBRAFALL_VEIN.get()) || state.is(ModBlocks.UMBRAFALL_CRYSTAL.get())
                || state.is(ModBlocks.UMBRAFALL_CORE.get());
    }

    private static Cell offset(Cell cell, int[] direction) {
        return new Cell(cell.x() + direction[0], cell.y() + direction[1], cell.z() + direction[2]);
    }

    private static List<Cell> seedColumns() {
        List<Cell> columns = new ArrayList<>();
        for (int z = -3; z <= 3; z++) for (int x = -3; x <= 3; x++)
            if (x * x + z * z <= 9) columns.add(new Cell(x, 0, z));
        columns.sort(Comparator.comparingInt(cell -> cell.x() * cell.x() + cell.z() * cell.z()));
        return List.copyOf(columns);
    }

    private enum Kind { BODY, AIR, BLOCKED, UNKNOWN }
    private record Observation(Cell cell, Kind kind) { }
    private static final class Budget { int reads; }

    private static final class Entry {
        final MoonwoundSnapshot snapshot;
        Surface surface;
        List<Observation> checks = List.of();
        int checkCursor;
        long refreshAt;
        Scan work;

        Entry(MoonwoundSnapshot snapshot) { this.snapshot = snapshot; }

        boolean matches(MoonwoundSnapshot other) {
            return snapshot.eventId().equals(other.eventId()) && snapshot.center().equals(other.center())
                    && snapshot.portalY() == other.portalY() && snapshot.portalSize() == other.portalSize()
                    && snapshot.dimension().equals(other.dimension());
        }
    }

    private static final class Scan {
        final BlockPos origin;
        final int summit;
        final Map<Cell, Kind> observed = new HashMap<>();
        final Set<Cell> meteorite = new LinkedHashSet<>();
        final Set<Cell> blocked = new HashSet<>();
        final ArrayDeque<Cell> frontier = new ArrayDeque<>();
        Cell seed;
        int seedColumn;
        int seedY;
        int neighborIndex;
        int reads;
        boolean finished;

        Scan(MoonwoundSnapshot snapshot) {
            origin = snapshot.center();
            summit = snapshot.portalY() - origin.getY()
                    - MoonwoundPortalGeometry.centerOffsetAboveBodyTop(snapshot.portalSize());
            seedY = summit + 2;
        }

        boolean within(Cell cell) {
            return Math.abs(cell.x()) <= 17 && Math.abs(cell.z()) <= 17
                    && cell.y() <= summit + 3 && cell.y() >= summit - 20;
        }

        void advance(Budget budget) {
            if (reads >= MAX_SCAN_READS) { finished = true; return; }
            if (seed == null) {
                if (seedColumn >= SEED_COLUMNS.size()) { finished = true; return; }
                Cell column = SEED_COLUMNS.get(seedColumn);
                Cell cell = new Cell(column.x(), seedY, column.z());
                Kind kind = sample(cell, budget);
                if (kind == Kind.BODY) {
                    seed = cell;
                    meteorite.add(cell);
                    frontier.add(cell);
                } else if (kind != Kind.AIR || --seedY < summit - 18) {
                    seedColumn++;
                    seedY = summit + 2;
                }
                return;
            }
            if (frontier.isEmpty()) { finished = true; return; }
            Cell neighbor = offset(frontier.peekFirst(), NEIGHBORS[neighborIndex++]);
            Kind kind = sample(neighbor, budget);
            if (kind == Kind.BODY && !meteorite.contains(neighbor)) {
                if (meteorite.size() < MAX_BODY_BLOCKS) {
                    meteorite.add(neighbor);
                    frontier.addLast(neighbor);
                } else blocked.add(neighbor);
            } else if (kind == Kind.BLOCKED || kind == Kind.UNKNOWN) blocked.add(neighbor);
            if (neighborIndex == NEIGHBORS.length) {
                frontier.removeFirst();
                neighborIndex = 0;
            }
        }

        Kind sample(Cell cell, Budget budget) {
            Kind cached = observed.get(cell);
            if (cached != null) return cached;
            Kind kind;
            if (!within(cell)) kind = Kind.UNKNOWN;
            else {
                kind = observe(origin, cell, budget);
                reads++;
            }
            observed.put(cell, kind);
            return kind;
        }
    }
}
