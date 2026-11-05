package com.jayemceekay.shadowedhearts.client.particle;

/** Shared Colosseum allowance; denied births are dropped instead of deferred. */
final class LuminousMoteEmissionBudget {
    static final double RATE_PER_SECOND = 240.0;
    private static final double MAX_BURST = 48.0;
    private final LuminousMoteEmissionClock clock = new LuminousMoteEmissionClock();
    private double available;
    private int nextSource;

    void advance(long gameTick, float partialTick, boolean active) {
        double elapsed = clock.sample(gameTick, partialTick, active);
        available = active ? Math.min(MAX_BURST, available + RATE_PER_SECOND * elapsed) : 0.0;
    }

    int take(int requested) {
        int granted = Math.min(Math.max(0, requested), (int) Math.floor(available));
        available -= granted;
        return granted;
    }

    int remaining() { return (int) Math.floor(available); }

    int firstSource(int sourceCount) { return Math.floorMod(nextSource, Math.max(1, sourceCount)); }

    void sourceServed(int sourceIndex, int sourceCount) {
        nextSource = (sourceIndex + 1) % Math.max(1, sourceCount);
    }
}
