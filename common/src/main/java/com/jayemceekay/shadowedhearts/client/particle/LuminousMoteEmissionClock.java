package com.jayemceekay.shadowedhearts.client.particle;

/** Converts render interpolation samples into elapsed, active game seconds. */
final class LuminousMoteEmissionClock {
    private static final double MAX_ELAPSED_TICKS = 4.0;
    private long previousTick;
    private double previousPartial;
    private boolean initialized;

    double sample(long gameTick, float partialTick, boolean active) {
        double partial = Float.isFinite(partialTick) ? Math.max(0.0, Math.min(1.0, partialTick)) : 0.0;
        if (!active || !initialized || gameTick < previousTick) {
            previousTick = gameTick;
            previousPartial = partial;
            initialized = active;
            return 0.0;
        }
        double elapsedTicks = (gameTick - previousTick) + partial - previousPartial;
        // A duplicate or older interpolation sample must not advance emission.
        if (elapsedTicks <= 0.0) return 0.0;
        previousTick = gameTick;
        previousPartial = partial;
        // Window inactivity or a long hitch must not replay a particle backlog.
        return Math.min(elapsedTicks, MAX_ELAPSED_TICKS) / 20.0;
    }
}
