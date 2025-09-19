package com.jayemceekay.shadowedhearts.runs;

import com.jayemceekay.shadowedhearts.world.RunBounds;

/** Container for an active missions run. */
public final class ActiveRun {
    private final RunId id;
    private final RunParty party;
    private final RunConfig cfg;
    private final RunBounds bounds;
    private final RunState state;

    public ActiveRun(RunId id, RunParty party, RunConfig cfg, RunBounds bounds, RunState state) {
        this.id = id;
        this.party = party;
        this.cfg = cfg;
        this.bounds = bounds;
        this.state = state;
    }

    public RunId id() { return id; }
    public RunParty party() { return party; }
    public RunConfig cfg() { return cfg; }
    public RunBounds bounds() { return bounds; }
    public RunState state() { return state; }
}
