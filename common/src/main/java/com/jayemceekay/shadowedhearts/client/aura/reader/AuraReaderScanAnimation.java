package com.jayemceekay.shadowedhearts.client.aura.reader;

import java.util.Objects;

/**
 * Elapsed-time phases for acquiring a reading and then revealing its scanner
 * callouts. A completed scan owns its panel content until those panels finish
 * retracting, even if a different target is already being scanned.
 *
 * <p>The caller supplies unpaused elapsed time. Brief focus loss freezes every
 * phase; only time beyond the loss grace period starts retraction.</p>
 */
public final class AuraReaderScanAnimation {
    public static final float SCAN_SECONDS = 1.75f;
    public static final float OPEN_SECONDS = 0.1575f;
    public static final float CLOSE_SECONDS = 0.14f;
    public static final float TEXT_FADE_SECONDS = 0.08f;
    public static final float LOSS_GRACE_SECONDS = 0.12f;

    private String focusId;
    private String panelTargetId;
    private double scanElapsedSeconds;
    private double panelProgress;
    private double textAlpha;
    private double lossElapsedSeconds;
    private boolean focusObserved;
    private boolean panelClosing;

    /**
     * Observes focus and advances by the actual elapsed time. Null or blank
     * focus enters the loss grace period. Invalid or negative elapsed time is
     * treated as zero; focus changes still take effect without advancing time.
     */
    public void update(String observedFocusId, float deltaSeconds) {
        String observed = observedFocusId == null || observedFocusId.isBlank()
                ? null : observedFocusId;
        double elapsed = Float.isFinite(deltaSeconds) && deltaSeconds > 0.0f
                ? deltaSeconds : 0.0;

        if (observed == null) {
            focusObserved = false;
            advanceWithoutFocus(elapsed);
            return;
        }

        focusObserved = true;
        lossElapsedSeconds = 0.0;
        if (!Objects.equals(focusId, observed)) {
            focusId = observed;
            scanElapsedSeconds = 0.0;
            beginClosing();
        }
        advanceWithFocus(elapsed);
    }

    /** The current scan target, retained while a brief loss remains in grace. */
    public String focusId() {
        return focusId;
    }

    /** Returns 100 only after the scan phase has actually completed. */
    public float scanProgress() {
        if (scanElapsedSeconds >= SCAN_SECONDS) {
            return 100.0f;
        }
        return Math.min(Math.nextDown(100.0f),
                (float) (scanElapsedSeconds / SCAN_SECONDS * 100.0));
    }

    /**
     * The completed reading whose panels are opening, visible, or retracting.
     * This may differ from {@link #focusId()} while another target is scanned.
     */
    public String panelTargetId() {
        return panelTargetId;
    }

    public float panelProgress() {
        return unitProgress(panelProgress);
    }

    public float textAlpha() {
        return unitProgress(textAlpha);
    }

    /** Whether acquisition is advancing, excluding the paused loss grace. */
    public boolean scanning() {
        return focusObserved && focusId != null && scanElapsedSeconds < SCAN_SECONDS;
    }

    public void reset() {
        focusId = null;
        panelTargetId = null;
        scanElapsedSeconds = 0.0;
        panelProgress = 0.0;
        textAlpha = 0.0;
        lossElapsedSeconds = 0.0;
        focusObserved = false;
        panelClosing = false;
    }

    private void advanceWithoutFocus(double elapsed) {
        if (focusId != null) {
            double graceRemaining = LOSS_GRACE_SECONDS - lossElapsedSeconds;
            if (elapsed < graceRemaining) {
                lossElapsedSeconds += elapsed;
                return;
            }
            elapsed -= graceRemaining;
            focusId = null;
            scanElapsedSeconds = 0.0;
            lossElapsedSeconds = 0.0;
            beginClosing();
        }
        if (panelClosing && elapsed > 0.0) {
            closePanel(elapsed);
        }
    }

    private void advanceWithFocus(double elapsed) {
        while (true) {
            if (!panelClosing && panelTargetId == null
                    && scanElapsedSeconds >= SCAN_SECONDS) {
                panelTargetId = focusId;
            }
            if (elapsed <= 0.0) {
                return;
            }

            if (panelClosing) {
                double step = Math.min(elapsed, panelProgress * CLOSE_SECONDS);
                // A previous reading closes while the new scan progresses.
                scanElapsedSeconds = Math.min(SCAN_SECONDS, scanElapsedSeconds + step);
                closePanel(step);
                elapsed -= step;
            } else if (scanElapsedSeconds < SCAN_SECONDS) {
                double remainingScan = SCAN_SECONDS - scanElapsedSeconds;
                double step = Math.min(elapsed, remainingScan);
                scanElapsedSeconds = step >= remainingScan
                        ? SCAN_SECONDS : scanElapsedSeconds + step;
                elapsed -= step;
            } else if (panelProgress < 1.0) {
                double remainingOpen = (1.0 - panelProgress) * OPEN_SECONDS;
                double step = Math.min(elapsed, remainingOpen);
                panelProgress = step >= remainingOpen
                        ? 1.0 : panelProgress + step / OPEN_SECONDS;
                elapsed -= step;
            } else {
                textAlpha = Math.min(1.0, textAlpha + elapsed / TEXT_FADE_SECONDS);
                return;
            }
        }
    }

    private void beginClosing() {
        // Native label artwork shrinks during retraction, so hide text before
        // the body becomes too small. Keep the old panel's target identity.
        textAlpha = 0.0;
        if (panelTargetId != null && panelProgress > 0.0) {
            panelClosing = true;
        } else {
            clearPanel();
        }
    }

    private void closePanel(double elapsed) {
        double remainingClose = panelProgress * CLOSE_SECONDS;
        if (elapsed >= remainingClose) {
            clearPanel();
        } else {
            panelProgress -= elapsed / CLOSE_SECONDS;
        }
    }

    private void clearPanel() {
        panelTargetId = null;
        panelProgress = 0.0;
        textAlpha = 0.0;
        panelClosing = false;
    }

    private static float unitProgress(double value) {
        return value >= 1.0 ? 1.0f
                : Math.min(Math.nextDown(1.0f), (float) Math.max(0.0, value));
    }
}
