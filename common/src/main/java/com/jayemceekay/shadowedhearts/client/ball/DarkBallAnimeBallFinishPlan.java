package com.jayemceekay.shadowedhearts.client.ball;

/** One-shot aftermath clock, triggered by an observed completed open-to-shut pose. */
final class DarkBallAnimeBallFinishPlan {
    static final float ARC_SECONDS = .8f;
    private final double startedTick;
    private final double waitLimitTicks;
    private double latestTick;
    private double closedTick = Double.NaN;
    private boolean sawOpen;
    private boolean expired;

    DarkBallAnimeBallFinishPlan(double startedTick, float captureSeconds) {
        this.startedTick = this.latestTick = startedTick;
        this.waitLimitTicks = (Math.max(0, Math.min(30, captureSeconds)) + 5) * 20;
    }

    void observe(double tick, boolean openPose, boolean shutPose, boolean captureActive) {
        if (expired) return;
        if (!captureActive || !Double.isFinite(tick)) {
            expired = true;
            return;
        }
        latestTick = Math.max(latestTick, tick);
        sawOpen |= openPose;
        if (sawOpen && shutPose && Double.isNaN(closedTick)) closedTick = latestTick;
        expired = Double.isNaN(closedTick)
                ? latestTick - startedTick > waitLimitTicks
                : ageSeconds() >= ARC_SECONDS;
    }

    float ageSeconds() {
        return Double.isNaN(closedTick) ? -1 : (float) ((latestTick - closedTick) / 20);
    }

    boolean expired() {
        return expired;
    }

    static float arcStrength(float age) {
        if (!Float.isFinite(age) || age < 0 || age >= ARC_SECONDS) return 0;
        return smooth(0, .055f, age) * (1 - smooth(.35f, ARC_SECONDS, age));
    }

    static float closePulse(float age) {
        if (!Float.isFinite(age) || age < 0 || age >= .18f) return 0;
        return (1 - smooth(0, .18f, age));
    }

    private static float smooth(float start, float end, float value) {
        float t = Math.max(0, Math.min(1, (value - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }
}
