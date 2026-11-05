package com.jayemceekay.shadowedhearts.content.umbrafall;

/** Site outcomes are monotonic; a depleted site cannot issue encounters or rewards again. */
public enum UmbrafallCoreState {
    DORMANT, DISTURBED, EXPOSED, SURGING, STABILIZED, RUPTURED, DEPLETED;

    public boolean active() { return ordinal() < STABILIZED.ordinal(); }

    public static UmbrafallCoreState load(String name) {
        try { return valueOf(name); }
        catch (IllegalArgumentException ignored) { return DEPLETED; }
    }
}
