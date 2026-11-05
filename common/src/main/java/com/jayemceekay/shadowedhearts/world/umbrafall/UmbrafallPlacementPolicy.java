package com.jayemceekay.shadowedhearts.world.umbrafall;

import java.util.Objects;

/** Pure placement limits shared by signal searches and their deterministic fixtures. */
public final class UmbrafallPlacementPolicy {
    private UmbrafallPlacementPolicy() { }

    /** Four nearby square rings keep each footprint clear of the player without distant random outliers. */
    public static UmbrafallShapeBuilder.Point candidateOffset(UmbrafallTier tier, int candidateIndex,
                                                              double startAngle) {
        Objects.requireNonNull(tier);
        if (candidateIndex < 0 || candidateIndex >= 32 || !Double.isFinite(startAngle)) {
            throw new IllegalArgumentException("Invalid Umbrafall search candidate");
        }
        int distance = tier.radius() + 32 + candidateIndex / 8 * 32;
        double angle = startAngle + candidateIndex % 8 * Math.PI / 4;
        double x = Math.cos(angle), z = Math.sin(angle);
        double scale = distance / Math.max(Math.abs(x), Math.abs(z));
        return new UmbrafallShapeBuilder.Point((int) Math.round(x * scale), 0, (int) Math.round(z * scale));
    }

    /** Broad hills can be fitted; limit extreme relief and the resulting scan volume by tier. */
    public static int terrainReliefLimit(UmbrafallTier tier) {
        return Math.max(16,tier.radius());
    }

    public static boolean acceptsSurfaceDelta(UmbrafallTier tier, int delta) {
        return Math.abs((long)delta) <= terrainReliefLimit(tier);
    }

    /** Crater, body and rim columns need suitable terrain; untouched corners and supported debris do not. */
    public static boolean requiresSurfaceCheck(UmbrafallShapeBuilder shape, int x, int z) {
        return shape.materialAt(x, 0, z) != UmbrafallShapeBuilder.Material.KEEP
                || shape.materialAt(x, 1, z) != UmbrafallShapeBuilder.Material.KEEP;
    }

    /** Fluids must remain outside both the actual writes and the faces opened beside them. */
    public static boolean fluidTouchesWrites(UmbrafallShapeBuilder shape, int x, int y, int z) {
        return shape.materialAt(x, y, z) != UmbrafallShapeBuilder.Material.KEEP
                || shape.materialAt(x - 1, y, z) != UmbrafallShapeBuilder.Material.KEEP
                || shape.materialAt(x + 1, y, z) != UmbrafallShapeBuilder.Material.KEEP
                || shape.materialAt(x, y - 1, z) != UmbrafallShapeBuilder.Material.KEEP
                || shape.materialAt(x, y + 1, z) != UmbrafallShapeBuilder.Material.KEEP
                || shape.materialAt(x, y, z - 1) != UmbrafallShapeBuilder.Material.KEEP
                || shape.materialAt(x, y, z + 1) != UmbrafallShapeBuilder.Material.KEEP;
    }

    /** Square footprints must not intersect each other's sixteen-block separation buffer. */
    public static boolean overlaps(int dx, int dz, int radiusA, int radiusB) {
        long separation = (long) radiusA + radiusB + 16;
        return Math.abs((long) dx) <= separation && Math.abs((long) dz) <= separation;
    }
}
