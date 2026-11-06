package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;

/** Pure lifecycle rules for layered Aura Reader snapshots. */
public final class AuraReaderReadingLifecycle {
    public static final int DEFAULT_PULSE_FADE_TICKS = 40;
    public static final int DEFAULT_NON_PULSE_STALE_FADE_TICKS = 20;
    public static final int LAST_KNOWN_TRACKED_FADE_TICKS = 60;

    private AuraReaderReadingLifecycle() {
    }

    public static boolean isPulse(AuraReadingSource source) {
        return source == AuraReadingSource.PULSE;
    }

    public static boolean isExpired(long expiryTick, long currentTick) {
        return expiryTick > 0L && currentTick >= expiryTick;
    }

    public static float pulseAlpha(long expiryTick, long currentTick) {
        return pulseAlpha(expiryTick, currentTick, DEFAULT_PULSE_FADE_TICKS);
    }

    public static float pulseAlpha(long expiryTick, long currentTick, int fadeTicks) {
        if (expiryTick <= 0L) {
            return 1.0f;
        }
        if (currentTick >= expiryTick) {
            return 0.0f;
        }

        int safeFadeTicks = Math.max(1, fadeTicks);
        long remainingTicks = expiryTick - currentTick;
        return Math.max(0.0f, Math.min(1.0f, remainingTicks / (float) safeFadeTicks));
    }

    public static boolean supersedesPulse(AuraReadingSource incomingSource) {
        return incomingSource != null && incomingSource != AuraReadingSource.PULSE;
    }

    public static int presentationPriority(AuraReadingSource source, boolean locked) {
        if (locked || source == AuraReadingSource.LOCKED) {
            return 5;
        }
        if (source == AuraReadingSource.TRACKED) {
            return 4;
        }
        if (source == AuraReadingSource.PASSIVE) {
            return 3;
        }
        if (source == AuraReadingSource.PULSE) {
            return 2;
        }
        if (source == AuraReadingSource.INTERFERENCE) {
            return 1;
        }
        return 0;
    }

    public static boolean isStaleExpired(long staleSinceTick, long currentTick) {
        return staleSinceTick >= 0L
                && currentTick - staleSinceTick >= DEFAULT_NON_PULSE_STALE_FADE_TICKS;
    }

    public static float staleAlpha(long staleSinceTick, long currentTick) {
        return staleAlpha(AuraReadingSource.PASSIVE, staleSinceTick, currentTick);
    }

    public static int staleFadeTicks(AuraReadingSource source) {
        if (source == AuraReadingSource.TRACKED || source == AuraReadingSource.LOCKED) {
            return LAST_KNOWN_TRACKED_FADE_TICKS;
        }
        return isPulse(source) ? DEFAULT_PULSE_FADE_TICKS : DEFAULT_NON_PULSE_STALE_FADE_TICKS;
    }

    public static boolean layerExpired(AuraReadingSource source, long expiryTick, long staleSinceTick, long currentTick) {
        return isExpired(expiryTick, currentTick)
                || (staleSinceTick >= 0L && currentTick - staleSinceTick >= staleFadeTicks(source));
    }

    public static float layerAlpha(AuraReadingSource source, long expiryTick, long staleSinceTick, long currentTick) {
        return Math.min(pulseAlpha(expiryTick, currentTick), staleAlpha(source, staleSinceTick, currentTick));
    }

    public static float staleAlpha(AuraReadingSource source, long staleSinceTick, long currentTick) {
        if (staleSinceTick < 0L || currentTick <= staleSinceTick) {
            return 1.0f;
        }
        long staleTicks = currentTick - staleSinceTick;
        return Math.max(0.0f, Math.min(1.0f,
                1.0f - staleTicks / (float) staleFadeTicks(source)));
    }
}
