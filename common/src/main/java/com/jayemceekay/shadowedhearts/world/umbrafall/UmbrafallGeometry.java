package com.jayemceekay.shadowedhearts.world.umbrafall;

/** Saved geometry identity. Never change a released profile: unfinished sites must resume exactly. */
public enum UmbrafallGeometry {
    LEGACY_V1(1, new Dimensions(17, 3, 3), new Dimensions(57, 10, 11),
            new Dimensions(81, 15, 16), new Dimensions(113, 21, 23), new Dimensions(145, 27, 30)),
    COMPACT_V2(2, new Dimensions(17, 3, 3), new Dimensions(57, 10, 11),
            new Dimensions(65, 12, 13), new Dimensions(73, 14, 15), new Dimensions(81, 16, 17)),
    VARIED_V3(3, new Dimensions(17, 3, 3), new Dimensions(57, 10, 11),
            new Dimensions(65, 12, 13), new Dimensions(73, 14, 15), new Dimensions(81, 16, 17)),
    LAYERED_FAINT_V4(4, new Dimensions(17, 3, 3), new Dimensions(57, 10, 11),
            new Dimensions(65, 12, 13), new Dimensions(73, 14, 15), new Dimensions(81, 16, 17)),
    BUDGETED_V5(5, new Dimensions(17, 3, 3, 80), new Dimensions(21, 4, 4, 150),
            new Dimensions(25, 5, 5, 250), new Dimensions(29, 6, 6, 400), new Dimensions(35, 7, 7, 600)),
    EXPEDITION_V6(6, new Dimensions(17, 3, 3, 80, 1), new Dimensions(21, 4, 4, 150, 2),
            new Dimensions(25, 5, 5, 250, 3), new Dimensions(29, 6, 6, 400, 5), new Dimensions(35, 7, 7, 600, 7)),
    STRATIFIED_V7(7, new Dimensions(17, 3, 3, 80, 1), new Dimensions(21, 4, 4, 150, 2),
            new Dimensions(25, 5, 5, 250, 3), new Dimensions(29, 6, 6, 400, 5), new Dimensions(35, 7, 7, 600, 7)),
    SEAMED_V8(8, new Dimensions(17, 3, 3, 80, 1), new Dimensions(21, 4, 4, 150, 2),
            new Dimensions(25, 5, 5, 250, 3), new Dimensions(29, 6, 6, 400, 5), new Dimensions(35, 7, 7, 600, 7)),
    SPARSE_VEINS_V9(9, new Dimensions(17, 3, 3, 80, 1), new Dimensions(21, 4, 4, 150, 2),
            new Dimensions(25, 5, 5, 250, 3), new Dimensions(29, 6, 6, 400, 5), new Dimensions(35, 7, 7, 600, 7)),
    SCATTERED_SURFACE_V10(10, new Dimensions(17, 3, 3, 80, 1), new Dimensions(21, 4, 4, 150, 2),
            new Dimensions(25, 5, 5, 250, 3), new Dimensions(29, 6, 6, 400, 5), new Dimensions(35, 7, 7, 600, 7));

    public static final UmbrafallGeometry CURRENT = SCATTERED_SURFACE_V10;
    private record Dimensions(int diameter, int craterDepth, int bodyRadius, int bodyTarget, int crystalBlocks) {
        private Dimensions(int diameter, int craterDepth, int bodyRadius) {
            this(diameter, craterDepth, bodyRadius, 0);
        }
        private Dimensions(int diameter, int craterDepth, int bodyRadius, int bodyTarget) {
            this(diameter, craterDepth, bodyRadius, bodyTarget, 0);
        }
    }

    private final int version;
    private final Dimensions[] dimensions;

    UmbrafallGeometry(int version, Dimensions... dimensions) {
        this.version = version;
        this.dimensions = dimensions;
    }

    public int version() { return version; }
    public int diameter(UmbrafallTier tier) { return dimensions[tier.ordinal()].diameter(); }
    public int radius(UmbrafallTier tier) { return diameter(tier) / 2; }
    public int craterDepth(UmbrafallTier tier) { return dimensions[tier.ordinal()].craterDepth(); }
    public int bodyRadius(UmbrafallTier tier) { return dimensions[tier.ordinal()].bodyRadius(); }
    /** Nominal connected body size; zero denotes an uncapped historical profile. */
    public int bodyTarget(UmbrafallTier tier) { return dimensions[tier.ordinal()].bodyTarget(); }
    public int maxBodyBlocks() { return usesBudgetedBody() ? 750 : Integer.MAX_VALUE; }
    public int crystalBlocks(UmbrafallTier tier) { return dimensions[tier.ordinal()].crystalBlocks(); }
    public boolean usesBudgetedBody() { return this == BUDGETED_V5 || usesExpeditionDeposits(); }
    public boolean usesExpeditionDeposits() { return this == EXPEDITION_V6 || usesStratifiedLayers(); }
    public boolean usesStratifiedLayers() { return this == STRATIFIED_V7 || usesSeamedDeposits(); }
    public boolean usesSeamedDeposits() { return this == SEAMED_V8 || usesSparseVeins(); }
    public boolean usesSparseVeins() { return this == SPARSE_VEINS_V9 || usesScatteredSurface(); }
    public boolean usesScatteredSurface() { return this == SCATTERED_SURFACE_V10; }
    public boolean usesVariedShape() { return this == VARIED_V3 || this == LAYERED_FAINT_V4 || usesBudgetedBody(); }
    public boolean usesLayeredFaint() { return this == LAYERED_FAINT_V4 || usesBudgetedBody(); }

    public static UmbrafallGeometry fromVersion(int version) {
        for (UmbrafallGeometry geometry : values()) {
            if (geometry.version == version) return geometry;
        }
        throw new IllegalArgumentException("Unsupported Umbrafall geometry version: " + version);
    }
}
