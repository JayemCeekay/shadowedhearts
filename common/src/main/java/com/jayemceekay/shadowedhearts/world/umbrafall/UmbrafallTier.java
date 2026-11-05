package com.jayemceekay.shadowedhearts.world.umbrafall;

import java.util.Locale;

/** Difficulty and core identity. Dimension getters describe newly created sites only. */
public enum UmbrafallTier {
    FAINT(false),
    WEAK(true),
    MODERATE(true),
    STRONG(true),
    RESONANT(true);

    private final boolean activeCore;

    UmbrafallTier(boolean activeCore) {
        this.activeCore = activeCore;
    }

    public int diameter() { return UmbrafallGeometry.CURRENT.diameter(this); }
    public int radius() { return UmbrafallGeometry.CURRENT.radius(this); }
    public int craterDepth() { return UmbrafallGeometry.CURRENT.craterDepth(this); }
    public int bodyRadius() { return UmbrafallGeometry.CURRENT.bodyRadius(this); }
    public boolean hasActiveCore() { return activeCore; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static UmbrafallTier fromId(String id) {
        for (UmbrafallTier tier : values()) {
            if (tier.id().equalsIgnoreCase(id)) return tier;
        }
        throw new IllegalArgumentException("Unknown Umbrafall tier: " + id);
    }
}
