package com.jayemceekay.shadowedhearts.world.umbrafall;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A coordinate-stable excavation plan with no Minecraft/bootstrap dependency.
 * The origin is the original surface at the impact centre. Nothing is copied from
 * Cobblemon's meteorite habitat: the crater, layered body and branches are built here.
 *
 * <p>One cursor visits at most its caller's budget, including KEEP cells. This avoids
 * both a giant placement list and an unbounded tick searching for the next write.
 * Bounds include every write, including AIR used to clear the impact opening.</p>
 */
public final class UmbrafallShapeBuilder {
    public enum Material {
        KEEP, AIR, RIM, SHELL, CRACKED_SHELL, VEIN, CRYSTAL, CORE, RESIDUE, FRAGMENT,
        UMBRAL_LATTICE, CORE_CASING
    }

    /** Structural membership beneath mineral deposits; NONE also covers the active core and old profiles. */
    public enum StructuralLayer { NONE, OUTER_SHELL, LATTICE_MANTLE, CORE_CASING }

    public record Point(int x, int y, int z) { }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Inverted Umbrafall bounds");
            }
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public int volume() {
            return Math.toIntExact((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
        }
    }

    @FunctionalInterface
    public interface WriteConsumer {
        void accept(int x, int y, int z, Material material);
    }

    private record Segment(double ax, double ay, double az, double bx, double by, double bz, double radius) {
        boolean contains(double x, double y, double z) {
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy + (z - az) * dz)
                    / (dx * dx + dy * dy + dz * dz)));
            double px = x - ax - dx * t, py = y - ay - dy * t, pz = z - az - dz * t;
            return px * px + py * py + pz * pz <= radius * radius;
        }
    }

    private final UmbrafallTier tier;
    private final UmbrafallGeometry geometry;
    private final long seed;
    private final Bounds bounds;
    private final Point coreOffset;
    private final double bodyHeight;
    private final double angle;
    private final double phase;
    private final List<Segment> branches;
    private final int[] columnShifts;
    private final CompactBody compactBody;
    private final UmbrafallVariedShape variedShape;
    private Integer cachedBodyTopY;

    public UmbrafallShapeBuilder(UmbrafallTier tier, long seed) {
        this(tier, seed, UmbrafallGeometry.CURRENT);
    }

    public UmbrafallShapeBuilder(UmbrafallTier tier, long seed, UmbrafallGeometry geometry) {
        this(tier, seed, geometry, null, true);
    }

    /** Fits crater/rim/debris to saved terrain; the body and branches keep their authored coordinates. */
    public UmbrafallShapeBuilder(UmbrafallTier tier, long seed, int[] terrainOffsets) {
        this(tier, seed, UmbrafallGeometry.CURRENT, terrainOffsets);
    }

    public UmbrafallShapeBuilder(UmbrafallTier tier, long seed, UmbrafallGeometry geometry, int[] terrainOffsets) {
        this(tier, seed, geometry, Objects.requireNonNull(terrainOffsets), false);
    }

    private UmbrafallShapeBuilder(UmbrafallTier tier, long seed, UmbrafallGeometry geometry,
                                 int[] terrainOffsets, boolean flat) {
        this.tier = Objects.requireNonNull(tier);
        this.geometry = Objects.requireNonNull(geometry);
        this.seed = seed;
        this.variedShape = geometry.usesVariedShape()
                ? new UmbrafallVariedShape(tier, seed, geometry) : null;
        this.bodyHeight = variedShape == null ? Math.max(2, bodyRadius() * 0.65) : variedShape.height();
        // V1 is immutable. V2 lowers only the body: about 40% of its height sits below the basin floor.
        double elevation = geometry == UmbrafallGeometry.LEGACY_V1 ? 0.65 : 0.20;
        this.coreOffset = new Point(0, -craterDepth() + (int) Math.ceil(bodyHeight * elevation), 0);
        Random random = new Random(seed);
        this.angle = random.nextDouble() * Math.PI * 2;
        this.phase = random.nextDouble() * Math.PI * 2;
        this.branches = buildBranches(random);
        this.compactBody = geometry == UmbrafallGeometry.COMPACT_V2 ? new CompactBody() : null;
        int minShift = 0, maxShift = 0, maxSurface = 0;
        if (flat) {
            this.columnShifts = null;
        } else {
            int diameter = diameter();
            if (terrainOffsets.length != diameter * diameter) {
                throw new IllegalArgumentException("Terrain snapshot must cover every footprint column");
            }
            int[] snapshot = terrainOffsets.clone();
            this.columnShifts = new int[snapshot.length];
            int limit = Math.max(16, radius());
            double innerRadius = bodyRadius() + 1;
            for (int index = 0; index < snapshot.length; index++) {
                int delta = snapshot[index];
                if (delta < -limit || delta > limit) {
                    throw new IllegalArgumentException("Terrain snapshot exceeds supported relief");
                }
                int x = index % diameter - radius(), z = index / diameter - radius();
                double distance = Math.sqrt(x * (double) x + z * (double) z);
                // Match the actual elliptical rim, including its narrow side, to the saved surface.
                double blendEnd = variedShape != null
                        ? (distance == 0 ? innerRadius + 1 : distance * 0.90 / variedShape.craterRadial(x, z))
                        : compactBody == null ? radius() * 0.65
                        : distance == 0 ? innerRadius + 1 : distance * 0.90 / compactCraterRadial(x, z);
                // Narrow V3 bowls need an earlier start so even their short rim reaches the surface.
                double blendStart = variedShape == null ? innerRadius : Math.min(innerRadius, blendEnd - 1.0);
                double blendWidth = Math.max(1.0, blendEnd - blendStart);
                double blend = Math.max(0, Math.min(1, (distance - blendStart) / blendWidth));
                blend = blend * blend * (3 - 2 * blend);
                int shift = (int) Math.round(delta * blend);
                columnShifts[index] = shift;
                minShift = Math.min(minShift, shift);
                maxShift = Math.max(maxShift, shift);
                maxSurface = Math.max(maxSurface, delta);
            }
        }
        int minY = -craterDepth() - (int) Math.ceil(bodyHeight) - 2 + minShift;
        if (variedShape != null) minY = Math.min(minY, variedShape.minBodyY() - 2);
        this.bounds = new Bounds(-radius(), minY,
                -radius(), radius(), Math.max(16, (int) Math.ceil(bodyHeight) + 12)
                + Math.max(maxShift, maxSurface), radius());
        if (geometry.usesExpeditionDeposits() && tier == UmbrafallTier.FAINT)
            variedShape.configureStarterDeposit(this::columnShift);
        if (geometry.usesScatteredSurface())
            variedShape.configureSurfaceDebris(tier,this::columnShift);
    }

    public UmbrafallTier tier() { return tier; }
    public UmbrafallGeometry geometry() { return geometry; }
    public int diameter() { return geometry.diameter(tier); }
    public int radius() { return geometry.radius(tier); }
    public int bodyRadius() { return geometry.bodyRadius(tier); }
    public int craterDepth() { return geometry.craterDepth(tier); }
    public long seed() { return seed; }
    /** Descriptive anatomy for source previews and diagnostics; it does not change the signal tier. */
    public String morphology() { return variedShape == null ? geometry.name().toLowerCase(java.util.Locale.ROOT) : variedShape.morphology(); }
    public boolean hasMajorFracture() { return variedShape == null ? geometry == UmbrafallGeometry.COMPACT_V2 : variedShape.hasMajorFracture(); }
    public Bounds bounds() { return bounds; }
    /** Highest original body block, relative to the saved surface; excludes rim and debris. */
    public int bodyTopY() {
        if (cachedBodyTopY != null) return cachedBodyTopY;
        for (int y = bounds.maxY; y >= bounds.minY; y--)
            for (int z = bounds.minZ; z <= bounds.maxZ; z++)
                for (int x = bounds.minX; x <= bounds.maxX; x++)
                    if (isBodyBlock(x, y, z)) return cachedBodyTopY = y;
        throw new IllegalStateException("Umbrafall shape has no meteorite body");
    }

    /** Connected meteorite body only, excluding crater residue, rim and surface debris. */
    public int bodyBlockCount() {
        if (variedShape != null) return variedShape.bodyBlockCount();
        if (compactBody != null) return compactBody.connectedMass.cardinality();
        int count = 0;
        for (int z = bounds.minZ; z <= bounds.maxZ; z++)
            for (int x = bounds.minX; x <= bounds.maxX; x++)
                for (int y = bounds.minY; y <= bounds.maxY; y++)
                    if (isBodyBlock(x, y, z)) count++;
        return count;
    }
    public boolean isBodyBlock(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) return false;
        if (variedShape != null) return variedShape.isBodyBlock(x, y, z);
        if (compactBody != null) return compactBody.contains(x, y, z);
        double dy = y - coreOffset.y;
        double bx = x - dy * Math.cos(angle) * 0.16;
        double bz = z - dy * Math.sin(angle) * 0.16;
        double bodyDistance = (bx * bx + bz * bz) / (bodyRadius() * (double) bodyRadius())
                + dy * dy / (bodyHeight * bodyHeight);
        double surface = 0.91 + 0.085 * Math.sin(x * 0.31 + phase)
                * Math.cos(z * 0.27 - phase) + 0.045 * Math.sin(y * 0.47 + x * 0.13 + phase);
        return bodyDistance <= surface;
    }
    /** Faint sites return their inert body centre here; use tier().hasActiveCore() before activating. */
    public Point coreOffset() { return coreOffset; }

    /** Structural membership in V7+ is independent of embedded vein/crystal material. */
    public StructuralLayer structuralLayerAt(int x, int y, int z) {
        return !bounds.contains(x, y, z) || variedShape == null ? StructuralLayer.NONE
                : variedShape.structuralLayerAt(x, y, z);
    }

    /** Authored exposed Dust deposit, fitted to the saved terrain; empty outside V6+ Faint sites. */
    public List<Point> starterDepositOffsets() {
        return variedShape == null ? List.of() : variedShape.starterDepositOffsets();
    }

    /** Saved terrain displacement for excavation/debris; the central body is never displaced. */
    public int columnShift(int x, int z) {
        if (columnShifts == null || x < -radius() || x > radius()
                || z < -radius() || z > radius()) return 0;
        return columnShifts[(z + radius()) * diameter() + x + radius()];
    }

    /** Surface ejecta require terrain support; body voxels must never be skipped by that check. */
    public boolean isSurfaceDebris(int x, int y, int z, Material material) {
        if (material == Material.FRAGMENT) {
            return y == 1 + columnShift(x, z)
                    && x * (long) x + z * (long) z > bodyRadius() * (long) bodyRadius();
        }
        return geometry.usesLayeredFaint() && tier == UmbrafallTier.FAINT
                && (material == Material.SHELL || material == Material.CRACKED_SHELL)
                && variedShape.isSurfaceDebris(x, y, z, columnShift(x, z));
    }

    public Material materialAt(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) return Material.KEEP;
        if (tier.hasActiveCore() && x == coreOffset.x && y == coreOffset.y && z == coreOffset.z) {
            return Material.CORE;
        }

        if (variedShape != null) return variedShape.materialAt(x, y, z, columnShift(x, z));
        if (compactBody != null) return compactMaterialAt(x, y, z);

        double dy = y - coreOffset.y;
        // A tilted, broad impact body with continuous low-frequency surface variation.
        double bx = x - dy * Math.cos(angle) * 0.16;
        double bz = z - dy * Math.sin(angle) * 0.16;
        double bodyDistance = (bx * bx + bz * bz) / (bodyRadius() * (double) bodyRadius())
                + dy * dy / (bodyHeight * bodyHeight);
        double surface = 0.91 + 0.085 * Math.sin(x * 0.31 + phase)
                * Math.cos(z * 0.27 - phase) + 0.045 * Math.sin(y * 0.47 + x * 0.13 + phase);
        if (bodyDistance <= surface) {
            long detail = hash(x, y, z);
            if (!tier.hasActiveCore()) {
                return Math.floorMod(detail, 9) == 0 ? Material.CRYSTAL : Material.FRAGMENT;
            }
            for (Segment branch : branches) {
                if (branch.contains(x, dy, z)) {
                    return bodyDistance > surface * 0.82 && Math.floorMod(detail, 3) == 0
                            ? Material.CRYSTAL : Material.VEIN;
                }
            }
            if (bodyDistance < surface * 0.60) {
                return Math.floorMod(detail, 17) == 0 ? Material.RESIDUE : Material.CRACKED_SHELL;
            }
            return Math.floorMod(detail, 13) == 0 ? Material.CRACKED_SHELL : Material.SHELL;
        }

        // Only the excavation is warped. Testing the tilted body above this point
        // keeps every shell, branch, crystal and core coordinate unchanged.
        int terrainY = y - columnShift(x, z);
        double horizontalDistance = Math.sqrt(x * (double) x + z * (double) z);
        double azimuth = Math.atan2(z, x);
        double craterRadius = radius() * (0.80 + 0.035 * Math.sin(azimuth * 3 + phase)
                + 0.022 * Math.cos(azimuth * 5 - phase));
        double radial = horizontalDistance / craterRadius;
        if (radial < 1) {
            int floor = (int) Math.floor(-craterDepth() * (1 - radial * radial));
            if (terrainY > floor) return Material.AIR;
            if (terrainY == floor && Math.floorMod(hash(x, floor, z), 11) == 0) return Material.RESIDUE;
        }

        // Terrain displacement comes from the saved snapshot, never partly altered
        // heights. The Minecraft adapter chooses rim material from the biome.
        if (radial >= 0.90 && radial <= 1.18) {
            double ridge = Math.max(0, 1 - Math.abs(radial - 1.04) / 0.14);
            int rimHeight = (int) Math.floor(ridge * Math.max(1, craterDepth() * 0.20));
            if (terrainY >= -2 && terrainY <= rimHeight) return Material.RIM;
        }
        if (radial > 1.15 && horizontalDistance <= radius() && terrainY == 1
                && Math.floorMod(hash(x, 0, z), 47) == 0) {
            return Material.FRAGMENT;
        }
        return Material.KEEP;
    }

    public Cursor cursor() { return new Cursor(bounds, 0); }
    public Cursor cursor(int position) { return new Cursor(bounds, position); }

    /** The supplied local bounds must be wholly inside this plan's complete bounds. */
    public Cursor cursor(Bounds slice) { return new Cursor(slice, 0); }
    public Cursor cursor(Bounds slice, int position) { return new Cursor(slice, position); }

    public final class Cursor {
        private final Bounds slice;
        private final int width;
        private final int height;
        private final int volume;
        private int position;

        private Cursor(Bounds slice, int position) {
            this.slice = Objects.requireNonNull(slice);
            if (!bounds.contains(slice.minX, slice.minY, slice.minZ)
                    || !bounds.contains(slice.maxX, slice.maxY, slice.maxZ)) {
                throw new IllegalArgumentException("Slice extends beyond the validated footprint");
            }
            this.width = slice.maxX - slice.minX + 1;
            this.height = slice.maxY - slice.minY + 1;
            this.volume = slice.volume();
            if (position < 0 || position > volume) throw new IllegalArgumentException("Invalid cursor position");
            this.position = position;
        }

        public int position() { return position; }
        public int total() { return volume; }
        public boolean done() { return position == volume; }

        /** Returns cells examined, never more than maxVisited, and emits each write once. */
        public int scan(int maxVisited, WriteConsumer consumer) {
            if (maxVisited < 0) throw new IllegalArgumentException("Negative scan budget");
            Objects.requireNonNull(consumer);
            int end = position + Math.min(maxVisited, volume - position);
            int start = position;
            while (position < end) {
                int x = slice.minX + position / height % width;
                int y = slice.minY + position % height;
                int z = slice.minZ + position / (height * width);
                Material material = materialAt(x, y, z);
                if (material != Material.KEEP) consumer.accept(x, y, z, material);
                position++;
            }
            return position - start;
        }
    }

    private List<Segment> buildBranches(Random random) {
        if (!tier.hasActiveCore() || variedShape != null) return List.of();
        if (geometry == UmbrafallGeometry.COMPACT_V2) return buildCompactBranches(random);
        List<Segment> result = new ArrayList<>();
        int count = 4 + tier.ordinal();
        for (int i = 0; i < count; i++) {
            double direction = angle + i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.35;
            double length = bodyRadius() * (0.78 + random.nextDouble() * 0.12);
            double ex = Math.cos(direction) * length;
            double ez = Math.sin(direction) * length;
            double ey = (random.nextDouble() - 0.30) * bodyHeight;
            double mx = ex * 0.52 + Math.sin(direction) * length * 0.11;
            double mz = ez * 0.52 - Math.cos(direction) * length * 0.11;
            double my = ey * 0.25;
            double thickness = Math.max(0.85, bodyRadius() * 0.065);
            result.add(new Segment(0, 0, 0, mx, my, mz, thickness));
            result.add(new Segment(mx, my, mz, ex, ey, ez, Math.max(0.85, thickness * 0.8)));
            double twigAngle = direction + (i % 2 == 0 ? 0.55 : -0.55);
            result.add(new Segment(mx, my, mz,
                    Math.cos(twigAngle) * length * 0.84,
                    Math.min(bodyHeight * 0.85, ey + bodyHeight * 0.50),
                    Math.sin(twigAngle) * length * 0.84, Math.max(0.85, thickness * 0.72)));
        }
        return List.copyOf(result);
    }

    private Material compactMaterialAt(int x, int y, int z) {
        if (compactBody.contains(x, y, z)) {
            double dy = y - coreOffset.y;
            double u = x * compactBody.cos + z * compactBody.sin - dy * 0.12;
            double v = -x * compactBody.sin + z * compactBody.cos + dy * 0.06;
            double inner = ellipsoid(u, dy, v, compactBody.along, bodyHeight, compactBody.across);
            long detail = hash(x, y, z);
            if (!tier.hasActiveCore()) return Math.floorMod(detail, 9) == 0 ? Material.CRYSTAL : Material.FRAGMENT;
            // A convex envelope inside the protected mass prevents fracture cuts from severing veins.
            if (inner <= 0.58) {
                for (Segment branch : branches) {
                    if (branch.contains(x, dy, z)) {
                        return inner > 0.30 && Math.floorMod(detail, 3) == 0 ? Material.CRYSTAL : Material.VEIN;
                    }
                }
            }
            if (inner < 0.50) return Math.floorMod(detail, 17) == 0 ? Material.RESIDUE : Material.CRACKED_SHELL;
            return Math.floorMod(detail, 13) == 0 ? Material.CRACKED_SHELL : Material.SHELL;
        }

        int terrainY = y - columnShift(x, z);
        double along = x * compactBody.cos + z * compactBody.sin;
        double across = -x * compactBody.sin + z * compactBody.cos;
        double azimuth = Math.atan2(across, along);
        double radial = compactCraterRadial(along, across, azimuth);
        if (radial < 1) {
            int floor = (int) Math.floor(-craterDepth() * (1 - radial * radial));
            if (terrainY > floor) return Material.AIR;
            if (terrainY == floor && Math.floorMod(hash(x, floor, z), 11) == 0) return Material.RESIDUE;
        }
        if (radial >= 0.90 && radial <= 1.18) {
            double ridge = Math.max(0, 1 - Math.abs(radial - 1.04) / 0.14);
            double gap = Math.max(0, (Math.cos(azimuth - compactBody.rimGap) - 0.93) / 0.07);
            double bank = (0.85 + 0.40 * Math.cos(azimuth)) * (1 - gap * 0.95);
            int rimHeight = (int) Math.floor(ridge * Math.max(1, craterDepth() * 0.24) * bank);
            if (terrainY >= -2 && terrainY <= rimHeight) return Material.RIM;
        }
        int debrisSpacing = 24 + (int) Math.round((1 - Math.cos(azimuth)) * 28);
        if (radial > 1.15 && x * (long) x + z * (long) z <= radius() * (long) radius()
                && terrainY == 1 && Math.floorMod(hash(x, 0, z), debrisSpacing) == 0) return Material.FRAGMENT;
        return Material.KEEP;
    }

    private double compactCraterRadial(int x, int z) {
        double along = x * compactBody.cos + z * compactBody.sin;
        double across = -x * compactBody.sin + z * compactBody.cos;
        return compactCraterRadial(along, across, Math.atan2(across, along));
    }

    private double compactCraterRadial(double along, double across, double azimuth) {
        // The outermost rim stays inside radius(): 0.80 * 1.04 * 1.18 < 1.
        double craterRadius = radius() * (0.735 + 0.030 * Math.sin(azimuth * 3 + phase)
                + 0.020 * Math.cos(azimuth * 5 - phase) + 0.015 * Math.cos(azimuth));
        return Math.sqrt(along * along / (1.04 * 1.04) + across * across / (0.90 * 0.90)) / craterRadius;
    }

    /** Large overlapping masses and broad facets establish the silhouette before small surface variation. */
    private final class CompactBody {
        private final double cos = Math.cos(angle), sin = Math.sin(angle);
        private final double along, across, lobeU, lobeV, lobeRadius, facet, rimGap;
        private final int horizontalLimit = (int) Math.ceil(bodyRadius() * 2.0 + bodyHeight * 0.2);
        private final int verticalLimit = (int) Math.ceil(bodyHeight * 1.1);
        private final int width = horizontalLimit * 2 + 1;
        private final int layerSize = width * width;
        private final BitSet connectedMass;

        private CompactBody() {
            Random random = new Random(seed ^ 0xA24BAED4963EE407L);
            along = bodyRadius() * (0.90 + random.nextDouble() * 0.16);
            across = bodyRadius() * (0.67 + random.nextDouble() * 0.12);
            lobeU = bodyRadius() * (0.55 + random.nextDouble() * 0.15);
            lobeV = bodyRadius() * (-0.10 + random.nextDouble() * 0.25);
            lobeRadius = bodyRadius() * (0.53 + random.nextDouble() * 0.08);
            facet = bodyRadius() * (0.84 + random.nextDouble() * 0.12);
            rimGap = phase + 0.65;
            connectedMass = buildConnectedMass();
        }

        private boolean contains(int x, int y, int z) {
            int dy = y - coreOffset.y;
            if (Math.abs(x) > horizontalLimit || Math.abs(z) > horizontalLimit || Math.abs(dy) > verticalLimit) return false;
            return connectedMass.get(index(x, dy, z));
        }

        private int index(int x, int dy, int z) {
            return (dy + verticalLimit) * layerSize + (z + horizontalLimit) * width + x + horizontalLimit;
        }

        /**
         * Fractures can leave isolated voxel chips at intersecting facets. Retain the face-connected
         * component containing the core. This small body-only mask is immutable after construction;
         * crater writes still use the bounded cursor, and chunk order cannot change the silhouette.
         */
        private BitSet buildConnectedMass() {
            int volume = layerSize * (verticalLimit * 2 + 1);
            BitSet mass = new BitSet(volume), visited = new BitSet(volume);
            int[] pending = new int[volume];
            int end = visit(index(0, 0, 0), visited, mass, pending, 0);
            for (int next = 0; next < end; next++) {
                int cell = pending[next];
                int x = cell % width, z = cell / width % width, y = cell / layerSize;
                if (x > 0) end = visit(cell - 1, visited, mass, pending, end);
                if (x + 1 < width) end = visit(cell + 1, visited, mass, pending, end);
                if (z > 0) end = visit(cell - width, visited, mass, pending, end);
                if (z + 1 < width) end = visit(cell + width, visited, mass, pending, end);
                if (y > 0) end = visit(cell - layerSize, visited, mass, pending, end);
                if (y < verticalLimit * 2) end = visit(cell + layerSize, visited, mass, pending, end);
            }
            return mass;
        }

        private int visit(int cell, BitSet visited, BitSet mass, int[] pending, int end) {
            if (visited.get(cell)) return end;
            visited.set(cell);
            int x = cell % width - horizontalLimit;
            int z = cell / width % width - horizontalLimit;
            int dy = cell / layerSize - verticalLimit;
            double u = x * cos + z * sin - dy * 0.12;
            double v = -x * sin + z * cos + dy * 0.06;
            double inner = ellipsoid(u, dy, v, along, bodyHeight, across);
            if (rawContains(x, coreOffset.y + dy, z, u, dy, v, inner)) {
                mass.set(cell);
                pending[end++] = cell;
            }
            return end;
        }

        private boolean rawContains(int x, int y, int z, double u, double dy, double v, double inner) {
            // Preserve a continuous inner mantle and at least three blocks of core cover at active tiers.
            if (inner <= 0.58) return true;
            if (Math.abs(dy) > bodyHeight * 1.1 || Math.abs(u) > bodyRadius() * 1.5
                    || Math.abs(v) > bodyRadius() * 1.2) return false;
            double mass = Math.min(inner, ellipsoid(u - lobeU, dy - bodyHeight * 0.08, v - lobeV,
                    lobeRadius, bodyHeight * 0.78, bodyRadius() * 0.54));
            mass = Math.min(mass, ellipsoid(u + bodyRadius() * 0.48, dy + bodyHeight * 0.12,
                    v + bodyRadius() * 0.32, bodyRadius() * 0.57, bodyHeight * 0.72, bodyRadius() * 0.52));
            // Two tilted fracture planes remove broad shoulders rather than peppering holes through the body.
            mass = Math.max(mass, square(Math.max(0, (u + v * 0.36 + dy * 0.12) / facet)));
            mass = Math.max(mass, square(Math.max(0, (-v + u * 0.18 - dy * 0.14) / (bodyRadius() * 0.65))));
            if (mass > 1.10) return false;
            // An open upper fracture terminates at the protected mantle, leaving the lower mass connected.
            if (u > bodyRadius() * 0.18 && dy > -bodyHeight * 0.10
                    && Math.abs(v + u * 0.35 - bodyRadius() * 0.22) < Math.max(0.45, bodyRadius() * 0.085)) return false;
            double coarse = valueNoise(x / (bodyRadius() * 0.38), y / (bodyRadius() * 0.38), z / (bodyRadius() * 0.38));
            double fine = valueNoise(x * 0.71 + 19, y * 0.71 - 7, z * 0.71 + 31);
            return mass <= 0.97 + coarse * 0.07 + fine * 0.025;
        }
    }

    private List<Segment> buildCompactBranches(Random random) {
        List<Segment> result = new ArrayList<>();
        int count = 4 + tier.ordinal();
        double cos = Math.cos(angle), sin = Math.sin(angle);
        for (int i = 0; i < count; i++) {
            double direction = i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.35;
            double length = bodyRadius() * (0.64 + random.nextDouble() * 0.10);
            double u = Math.cos(direction) * length, v = Math.sin(direction) * length * 0.72;
            double ex = u * cos - v * sin, ez = u * sin + v * cos;
            double ey = (random.nextDouble() - 0.30) * bodyHeight;
            double mx = ex * 0.50, mz = ez * 0.50, my = ey * 0.22;
            double thickness = Math.max(1.05, bodyRadius() * 0.065);
            result.add(new Segment(0, 0, 0, mx, my, mz, thickness));
            result.add(new Segment(mx, my, mz, ex, ey, ez, thickness));
            result.add(new Segment(mx, my, mz, ex * 0.73 - ez * 0.16,
                    Math.min(bodyHeight * 0.65, ey + bodyHeight * 0.40), ez * 0.73 + ex * 0.16, thickness));
        }
        return List.copyOf(result);
    }

    private static double square(double value) { return value * value; }

    private static double ellipsoid(double x, double y, double z, double rx, double ry, double rz) {
        return square(x / rx) + square(y / ry) + square(z / rz);
    }

    /** Trilinear smooth value noise, seeded with the plan; no mutable RNG or chunk-order dependency. */
    private double valueNoise(double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double tx = smooth(x - ix), ty = smooth(y - iy), tz = smooth(z - iz), result = 0;
        for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = 0; dz <= 1; dz++) {
            double value = (hash(ix + dx, iy + dy, iz + dz) >>> 11) * 0x1.0p-52 - 1;
            result += value * (dx == 0 ? 1 - tx : tx) * (dy == 0 ? 1 - ty : ty) * (dz == 0 ? 1 - tz : tz);
        }
        return result;
    }

    private static double smooth(double value) { return value * value * (3 - 2 * value); }

    private long hash(int x, int y, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
