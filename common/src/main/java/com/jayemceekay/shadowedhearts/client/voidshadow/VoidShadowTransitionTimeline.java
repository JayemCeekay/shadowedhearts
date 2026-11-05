package com.jayemceekay.shadowedhearts.client.voidshadow;

import java.util.ArrayList;
import java.util.List;

/** Pure client presentation timing; never delays or changes the server's battle species. */
public final class VoidShadowTransitionTimeline {
    public static final int DURATION_TICKS = 40;
    private static final List<Burst> BURSTS = List.of(new Burst(0, 14), new Burst(9, 10), new Burst(20, 8));
    private static final Sample COMPLETE = new Sample(0, 1, 0, true);

    private VoidShadowTransitionTimeline() { }

    /**
     * The old and new posed meshes overlap only while the cloud is fully present.
     * Clock values are supplied by the owner so pause, world and entity lifetime
     * remain with the renderer instead of depending on render-call frequency.
     */
    public static Sample sample(double elapsedTicks) {
        // Invalid clocks must not leave the actual current species invisible.
        if (!Double.isFinite(elapsedTicks) || elapsedTicks >= DURATION_TICKS) return COMPLETE;
        double progress = Math.max(0, elapsedTicks) / DURATION_TICKS;
        float oldOpacity = (float) (1 - smoothstep(0.14, 0.57, progress));
        float newOpacity = (float) smoothstep(0.40, 0.88, progress);
        float cloudOpacity = (float) (smoothstep(0, 0.20, progress)
                * (1 - smoothstep(0.72, 1, progress)));
        return new Sample(oldOpacity, newOpacity, cloudOpacity, false);
    }

    private static double smoothstep(double start, double end, double value) {
        double progress = Math.max(0, Math.min(1, (value - start) / (end - start)));
        return progress * progress * (3 - 2 * progress);
    }

    public record Sample(float oldOpacity, float newOpacity, float cloudOpacity, boolean finished) { }

    /** Spawn metadata only: the renderer owns the particles, their positions and RNG. */
    public record Burst(int offsetTicks, int count) { }

    /** Per-transition edge-triggered particle schedule, independent of draw/pass count. */
    public static final class State {
        private final long startedAtGameTick;
        private int nextBurst;

        public State(long startedAtGameTick) { this.startedAtGameTick = startedAtGameTick; }

        public long startedAtGameTick() { return startedAtGameTick; }

        /**
         * Returns each crossed burst once. Expired or rewound clocks spawn no
         * catch-up cloud; the lifecycle owner clears stale snapshots and states.
         */
        public List<Burst> consumeBursts(long nowTick) {
            if (nowTick < startedAtGameTick) return List.of();
            long elapsedTicks = nowTick - startedAtGameTick;
            // A negative difference after ordered timestamps indicates overflow.
            if (elapsedTicks < 0 || elapsedTicks >= DURATION_TICKS) {
                nextBurst = BURSTS.size();
                return List.of();
            }
            List<Burst> crossed = new ArrayList<>(BURSTS.size() - nextBurst);
            while (nextBurst < BURSTS.size() && BURSTS.get(nextBurst).offsetTicks() <= elapsedTicks) {
                crossed.add(BURSTS.get(nextBurst++));
            }
            return List.copyOf(crossed);
        }
    }
}
