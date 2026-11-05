package com.jayemceekay.shadowedhearts.client.ball;

/** Monotonic choreography time that excludes pauses and inactive intervals. */
final class DarkBallPresentationClock {
    private long previousNanos;
    private boolean advancing;

    float sample(long now, boolean paused, boolean active) {
        boolean advance = active && !paused;
        float elapsed = advance && advancing ? elapsedSeconds(previousNanos, now) : 0f;
        previousNanos = now;
        advancing = advance;
        return elapsed;
    }

    void reset() {
        previousNanos = 0L;
        advancing = false;
    }

    static float elapsedSeconds(long previousNanos, long currentNanos) {
        if (previousNanos == 0L || currentNanos <= previousNanos) return 0f;
        return (currentNanos - previousNanos) / 1_000_000_000.0f;
    }
}
