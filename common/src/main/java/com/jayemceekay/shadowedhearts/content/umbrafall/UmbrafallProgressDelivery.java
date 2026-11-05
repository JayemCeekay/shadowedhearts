package com.jayemceekay.shadowedhearts.content.umbrafall;

/** Deduplicates progress snapshots and bounds changing work/candidate updates to twice a second. */
public final class UmbrafallProgressDelivery {
    public static final int UPDATE_INTERVAL_TICKS = 10;
    private AuraReaderSearchProgress lastSent;
    private long lastSentTick;

    public boolean shouldSend(AuraReaderSearchProgress progress, long tick) {
        if (progress.equals(lastSent)) return false;
        boolean immediate = lastSent == null || progress.stage().terminal()
                || progress.stage() == AuraReaderSearchProgress.Stage.PAUSED
                || lastSent.stage().terminal()
                || lastSent.stage() == AuraReaderSearchProgress.Stage.PAUSED
                || progress.stage() != lastSent.stage()
                    && (progress.stage() == AuraReaderSearchProgress.Stage.WAITING
                        || lastSent.stage() == AuraReaderSearchProgress.Stage.WAITING)
                || lastSent.stage().preflight() && !progress.stage().preflight();
        if (!immediate && tick >= lastSentTick && tick - lastSentTick < UPDATE_INTERVAL_TICKS) return false;
        lastSent = progress;
        lastSentTick = tick;
        return true;
    }
}
