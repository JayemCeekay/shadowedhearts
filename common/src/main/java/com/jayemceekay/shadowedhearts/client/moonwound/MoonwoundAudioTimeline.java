package com.jayemceekay.shadowedhearts.client.moonwound;

/** Both preloaded clips start at activation; their gains follow the same age as the portal animation. */
public final class MoonwoundAudioTimeline {
    // The supplied opening's bright transient peaks near 2.63 s, just before the visual impact.
    public static final double CROSSFADE_START_SECONDS = MoonwoundPortalOpening.IMPACT_SECONDS;
    public static final double CROSSFADE_SECONDS = 2.0;
    public static final double CROSSFADE_END_SECONDS = CROSSFADE_START_SECONDS + CROSSFADE_SECONDS;
    private static final int RECOVERY_TICKS = 20;

    private MoonwoundAudioTimeline() { }

    public static float openingGain(double seconds) {
        if (seconds >= CROSSFADE_END_SECONDS) return 0;
        return (float) Math.cos(blend(seconds) * Math.PI * 0.5);
    }

    public static float ambienceGain(double seconds) {
        if (seconds <= CROSSFADE_START_SECONDS) return 0;
        return (float) Math.sin(blend(seconds) * Math.PI * 0.5);
    }

    private static double blend(double seconds) {
        return smooth((seconds - CROSSFADE_START_SECONDS) / CROSSFADE_SECONDS);
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Keep one loop through the handoff; recover from a missing opening without jumping to full volume. */
    public static final class AmbienceEnvelope {
        private boolean followingOpening;
        private int ticks;
        private float gain;
        private float recoveryFrom;

        public AmbienceEnvelope(boolean openingPlaying) {
            followingOpening = openingPlaying;
        }

        public float tick(double eventAge, boolean openingPlaying) {
            if (followingOpening && openingPlaying) {
                // A loop recovered during the crossfade still enters gently, even at a mature event age.
                ticks = Math.min(RECOVERY_TICKS, ticks + 1);
                gain = Math.min(ambienceGain(eventAge), (float) smooth(ticks / (double) RECOVERY_TICKS));
            } else {
                if (followingOpening) {
                    followingOpening = false;
                    recoveryFrom = gain;
                    ticks = 0;
                }
                if (eventAge < CROSSFADE_START_SECONDS) {
                    ticks = 0;
                    gain = 0;
                    recoveryFrom = 0;
                } else {
                    ticks = Math.min(RECOVERY_TICKS, ticks + 1);
                    gain = recoveryFrom + (1 - recoveryFrom) * (float) smooth(ticks / (double) RECOVERY_TICKS);
                }
            }
            return gain;
        }
    }
}
