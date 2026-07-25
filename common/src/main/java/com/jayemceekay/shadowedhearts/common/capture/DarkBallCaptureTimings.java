package com.jayemceekay.shadowedhearts.common.capture;

/** Shared client/server timing contract for the Dark Ball capture handoff. */
public final class DarkBallCaptureTimings {
    private DarkBallCaptureTimings() {
    }

    /** Duration of the original visual choreography before slow-motion scaling. */
    public static final float BASE_VFX_DURATION_SECONDS = 3.70F;
    /** Target duration from entering HIT until the last visible VFX frame. */
    public static final float VFX_DURATION_SECONDS = 10.0F;
    public static final float TIMING_SCALE =
            VFX_DURATION_SECONDS / BASE_VFX_DURATION_SECONDS;

    /** Keeps HIT alive just beyond the visual fade before Cobblemon starts FALL. */
    public static final float HIT_RELEASE_GRACE_SECONDS = 0.35F;
    public static final float HIT_TO_FALL_DELAY_SECONDS =
            VFX_DURATION_SECONDS + HIT_RELEASE_GRACE_SECONDS;
}
