package com.jayemceekay.shadowedhearts.client.ball;

import com.jayemceekay.shadowedhearts.common.capture.DarkBallCaptureTimings;

/** Normalized authored beats; callers supply the readiness-adjusted presentation age. */
final class DarkBallAnimePresentation {
    private static final float AUTHORED_DURATION = DarkBallCaptureTimings.ANIME_VFX_DURATION_SECONDS;
    // Add a full second of held curls ahead of the existing 1.68-second intake.
    // Conversion stays quick; squeeze, pull, and final pulse shift together.
    static final float CONVERSION_END = .20f / AUTHORED_DURATION;
    static final float PULL_START = 1.72f / AUTHORED_DURATION;
    static final float LAST_TAIL_START = 3.15f / AUTHORED_DURATION;
    private static final float PULSE_PEAK = 3.316f / AUTHORED_DURATION;

    private DarkBallAnimePresentation() {
    }

    /** Readiness-aware ownership transfer; source coverage reaches exactly zero. */
    static float energyHandoffAt(float ageSeconds, float activationAgeSeconds, float durationSeconds) {
        if (!Float.isFinite(ageSeconds) || !Float.isFinite(activationAgeSeconds)
                || !Float.isFinite(durationSeconds) || durationSeconds <= 0) return 0;
        if (ageSeconds >= durationSeconds) return 1;
        float start = Math.max(CONVERSION_END * durationSeconds, Math.max(0, activationAgeSeconds));
        float interval = Math.min(.50f / AUTHORED_DURATION * durationSeconds,
                Math.max(0, durationSeconds - start) * .45f);
        if (ageSeconds <= start || interval <= 0) return 0;
        return smooth(start, start + interval, ageSeconds);
    }

    /**
     * A late first body frame starts at the authored pull boundary. The remaining
     * motion uses the remaining capture interval, keeping the shared HIT deadline.
     */
    static float presentationAgeAt(float ageSeconds, float activationAgeSeconds,
                                   float durationSeconds) {
        if (!Float.isFinite(ageSeconds) || !Float.isFinite(durationSeconds)
                || durationSeconds <= 0) return 0;
        float age = Math.max(0, Math.min(durationSeconds, ageSeconds));
        if (age >= durationSeconds) return durationSeconds;
        float pullStart = PULL_START * durationSeconds;
        if (!Float.isFinite(activationAgeSeconds)) return Math.min(age, pullStart);
        if (activationAgeSeconds <= pullStart) return age;
        if (age <= activationAgeSeconds) return Math.min(age, pullStart);
        float remaining = durationSeconds - activationAgeSeconds;
        if (remaining <= 0) return durationSeconds;
        float progress = clamp((age - activationAgeSeconds) / remaining);
        return pullStart + progress * (durationSeconds - pullStart);
    }

    static Tracks at(float ageSeconds, float durationSeconds) {
        float time = Float.isFinite(ageSeconds) && Float.isFinite(durationSeconds)
                && durationSeconds > 0 ? clamp(ageSeconds / durationSeconds) : 0;
        float conversion = smooth(0, CONVERSION_END, time);
        float pull = smooth(PULL_START, 1, time);
        float tail = smooth(LAST_TAIL_START, 1, time);
        // This is an activation track. The shared motion field calms each material
        // point as it arrives, so the lagging tail keeps rolling through late intake.
        // At the deadline every point has arrived and the entire track can retire.
        float lobes = time < 1 ? conversion : 0;
        float pulse = smooth(LAST_TAIL_START, PULSE_PEAK, time)
                * (1 - smooth(PULSE_PEAK, 1, time));
        // Lead intake with a brief squeeze after the extended rolling hold, then release while the
        // front begins stretching and the trailing material remains attached.
        float squash = conversion * smooth(1.432f / AUTHORED_DURATION, 1.648f / AUTHORED_DURATION, time)
                * (1 - smooth(1.744f / AUTHORED_DURATION, 2.104f / AUTHORED_DURATION, time));
        return new Tracks(time, conversion, lobes, pull, tail, pulse, squash);
    }

    private static float smooth(float start, float end, float time) {
        float t = clamp((time - start) / (end - start));
        return t * t * (3 - 2 * t);
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    record Tracks(float normalizedTime, float conversion, float broadLobes,
                  float pull, float lastTail, float intakePulse, float squash) {
    }
}
