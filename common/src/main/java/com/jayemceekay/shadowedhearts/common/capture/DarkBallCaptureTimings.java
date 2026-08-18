package com.jayemceekay.shadowedhearts.common.capture;

import java.util.Locale;

/** Shared client/server timing contract for the Dark Ball capture handoff. */
public final class DarkBallCaptureTimings {
    public static final String DURATION_PROPERTY =
            "shadowedhearts.darkBallVfxDuration";
    public static final float COBBLEMON_HIT_DURATION_SECONDS = 2.20F;
    public static final float BASE_VFX_DURATION_SECONDS = 3.70F;
    public static final float DEFAULT_VFX_DURATION_SECONDS = 5.00F;
    public static final float DIAGNOSTIC_VFX_DURATION_SECONDS = 30.00F;
    /** Keeps HIT alive just beyond the visual fade before Cobblemon starts FALL. */
    public static final float HIT_RELEASE_GRACE_SECONDS = 0.35F;
    private static final String CONFIGURED_DURATION =
            System.getProperty(DURATION_PROPERTY, "5");

    private DarkBallCaptureTimings() {
    }

    /**
     * Target duration from entering HIT until the last visible VFX frame.
     *
     * <p>Supported launch values are {@code 30}/{@code diagnostic},
     * {@code 10}, {@code 7}, {@code 5}, {@code 3.7}/{@code original}, and
     * {@code 2.2}/{@code cobblemon}. The 2.2-second profile reserves the
     * normal release grace inside Cobblemon's native HIT window, leaving 1.85
     * visible seconds. The five-second movie-paced profile is the default.
     * Both the client presentation and server HIT extension read this shared
     * constant, so a dedicated client/server pair must use the same launch
     * property.</p>
     */
    public static final float VFX_DURATION_SECONDS =
            configuredVfxDurationSeconds(CONFIGURED_DURATION);
    public static final float TIMING_SCALE =
            VFX_DURATION_SECONDS / BASE_VFX_DURATION_SECONDS;

    public static final float HIT_TO_FALL_DELAY_SECONDS =
            configuredHitToFallDelaySeconds(
                    CONFIGURED_DURATION,
                    VFX_DURATION_SECONDS);

    static float configuredVfxDurationSeconds(String value) {
        if (value == null) {
            return DEFAULT_VFX_DURATION_SECONDS;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "30", "30.0", "diagnostic", "trace", "ultraslow" ->
                    DIAGNOSTIC_VFX_DURATION_SECONDS;
            case "10", "10.0", "legacy", "slow" -> 10.0F;
            case "7", "7.0", "cinematic", "movie" -> 7.0F;
            case "5", "5.0", "default", "fast" -> 5.0F;
            case "3.7", "original", "base" -> BASE_VFX_DURATION_SECONDS;
            case "2.2", "cobblemon", "vanilla" ->
                    COBBLEMON_HIT_DURATION_SECONDS
                            - HIT_RELEASE_GRACE_SECONDS;
            default -> DEFAULT_VFX_DURATION_SECONDS;
        };
    }

    static float configuredHitToFallDelaySeconds(
            String value,
            float visibleDurationSeconds) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "2.2", "cobblemon", "vanilla" ->
                    COBBLEMON_HIT_DURATION_SECONDS;
            default -> visibleDurationSeconds
                    + HIT_RELEASE_GRACE_SECONDS;
        };
    }
}
