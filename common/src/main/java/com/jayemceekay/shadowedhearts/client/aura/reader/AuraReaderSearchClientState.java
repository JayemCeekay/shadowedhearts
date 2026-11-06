package com.jayemceekay.shadowedhearts.client.aura.reader;

import com.jayemceekay.shadowedhearts.content.umbrafall.AuraReaderSearchProgress;

/** Independent of live/pulse reading snapshots, so scanning cannot replace pending job feedback. */
public final class AuraReaderSearchClientState {
    public static final int RESULT_DURATION_TICKS = 160;
    private static long lastSequence = -1;
    private static long receivedAtTick;
    private static long processingStartedAtTick;
    private static AuraReaderSearchProgress progress;
    private static boolean collapsed;

    private AuraReaderSearchClientState() { }

    public static boolean apply(long sequence, AuraReaderSearchProgress next, long clientTick) {
        if (next == null || sequence <= lastSequence) return false;
        if (processing(next) && (!processing(progress) || next.tier() != progress.tier())) {
            processingStartedAtTick = clientTick;
        }
        if (progress == null || next.stage().terminal() || progress.stage().terminal()) collapsed = false;
        lastSequence = sequence;
        receivedAtTick = clientTick;
        progress = next;
        return true;
    }

    public static AuraReaderSearchProgress getProgress(long clientTick) {
        if (progress != null && progress.stage().terminal()
                && (clientTick < receivedAtTick || clientTick - receivedAtTick >= RESULT_DURATION_TICKS)) {
            progress = null;
            collapsed = false;
        }
        return progress;
    }

    public static boolean isCollapsed() { return collapsed; }

    /** The search sweep starts with the accepted work event, independently of HUD visibility. */
    public static long processingElapsedTicks(long clientTick) {
        return processing(progress) ? Math.max(0, clientTick - processingStartedAtTick) : 0;
    }

    private static boolean processing(AuraReaderSearchProgress value) {
        return value != null && (value.stage().preflight()
                || value.stage() == AuraReaderSearchProgress.Stage.ESTABLISHING);
    }

    public static void toggleCollapsed() {
        if (progress != null) collapsed = !collapsed;
    }

    public static void clear() {
        lastSequence = -1;
        receivedAtTick = 0;
        processingStartedAtTick = 0;
        progress = null;
        collapsed = false;
    }
}
