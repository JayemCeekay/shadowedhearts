package com.jayemceekay.shadowedhearts.client.aura;

import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AuraReaderClientState {
    private static final int PANEL_FOCUS_INTERVALS = 9;
    private static final float RENDER_UPDATES_PER_SECOND = 1.0f / 0.0175f;
    private static final AuraReaderMode[] CYCLE = {
            AuraReaderMode.PASSIVE_AURA,
            AuraReaderMode.PULSE_SCAN,
            AuraReaderMode.SIGNAL_TRACKING
    };

    private static boolean active;
    private static AuraReaderMode mode = AuraReaderMode.PASSIVE_AURA;
    private static int charge;
    private static int maxCharge = 1;
    private static boolean tracking;
    private static String selectedSignal = "";
    private static AuraReaderLockType lockType = AuraReaderLockType.NONE;
    private static int pulseCooldownRemainingTicks;
    private static int pulseCooldownMaxTicks;
    private static long pulseCooldownSnapshotMillis;
    private static final List<ReadingSnapshot> readings = new ArrayList<>();
    private static long lastPulseResultMillis;
    private static long lastHudAnimationMillis;
    private static float usageIntervals;
    private static float innerRingRotation;

    private AuraReaderClientState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        AuraReaderClientState.active = active;
    }

    public static AuraReaderMode getMode() {
        return mode;
    }

    public static void setMode(AuraReaderMode mode) {
        AuraReaderClientState.mode = mode == null ? AuraReaderMode.PASSIVE_AURA : mode;
    }

    public static int getCharge() {
        return charge;
    }

    public static int getMaxCharge() {
        return Math.max(1, maxCharge);
    }

    public static boolean isTracking() {
        return tracking;
    }

    public static String getSelectedSignal() {
        return selectedSignal;
    }

    public static AuraReaderLockType getLockType() {
        return lockType;
    }

    public static List<ReadingSnapshot> getReadings() {
        return List.copyOf(readings);
    }

    public static ReadingSnapshot getPrimaryReading() {
        return readings.isEmpty() ? null : readings.get(0);
    }

    public static boolean hasFocusedReading() {
        return getPrimaryReading() != null;
    }

    public static float getChargeFraction() {
        return Math.max(0.0f, Math.min(1.0f, (float) charge / (float) getMaxCharge()));
    }

    public static int getPulseCooldownMaxTicks() {
        return Math.max(0, pulseCooldownMaxTicks);
    }

    public static float getPulseCooldownRemainingTicks() {
        if (pulseCooldownRemainingTicks <= 0) {
            return 0.0f;
        }

        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - pulseCooldownSnapshotMillis);
        float elapsedTicks = elapsedMillis / 50.0f;
        return Math.max(0.0f, pulseCooldownRemainingTicks - elapsedTicks);
    }

    public static float getPulseCooldownFraction() {
        int maxTicks = getPulseCooldownMaxTicks();
        if (maxTicks <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, getPulseCooldownRemainingTicks() / (float) maxTicks));
    }

    public static boolean isPulseOnCooldown() {
        return getPulseCooldownRemainingTicks() > 0.0f;
    }

    public static String getPulseCooldownLabel() {
        float remaining = getPulseCooldownRemainingTicks();
        if (remaining <= 0.0f) {
            return "RDY";
        }
        return String.format(Locale.ROOT, "%.1fs", remaining / 20.0f);
    }

    public static void applyServerState(
            boolean active,
            AuraReaderMode mode,
            int charge,
            int maxCharge,
            boolean tracking,
            String selectedSignal,
            AuraReaderLockType lockType,
            int pulseCooldownRemainingTicks,
            int pulseCooldownMaxTicks
    ) {
        AuraReaderClientState.active = active;
        AuraReaderClientState.mode = mode == null ? AuraReaderMode.PASSIVE_AURA : mode;
        AuraReaderClientState.charge = Math.max(0, charge);
        AuraReaderClientState.maxCharge = Math.max(1, maxCharge);
        AuraReaderClientState.tracking = tracking;
        AuraReaderClientState.selectedSignal = selectedSignal == null ? "" : selectedSignal;
        AuraReaderClientState.lockType = lockType == null ? AuraReaderLockType.NONE : lockType;
        AuraReaderClientState.pulseCooldownRemainingTicks = Math.max(0, pulseCooldownRemainingTicks);
        AuraReaderClientState.pulseCooldownMaxTicks = Math.max(0, pulseCooldownMaxTicks);
        AuraReaderClientState.pulseCooldownSnapshotMillis = System.currentTimeMillis();
    }

    public static AuraReaderMode cycleMode() {
        int currentIndex = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == mode) {
                currentIndex = i;
                break;
            }
        }

        mode = CYCLE[(currentIndex + 1) % CYCLE.length];
        return mode;
    }

    public static void applyPulseResult(List<ReadingSnapshot> nextReadings) {
        applyReadings(nextReadings, true);
    }

    public static void applyReadings(List<ReadingSnapshot> nextReadings, boolean animate) {
        String previousPrimaryId = getPrimaryReading() == null ? "" : getPrimaryReading().id();
        readings.clear();
        if (nextReadings != null) {
            readings.addAll(nextReadings);
        }

        String nextPrimaryId = getPrimaryReading() == null ? "" : getPrimaryReading().id();
        if (animate || !Objects.equals(previousPrimaryId, nextPrimaryId)) {
            lastPulseResultMillis = System.currentTimeMillis();
        }
    }

    public static int getPanelAnimationStep() {
        if (lastPulseResultMillis <= 0L) {
            return PANEL_FOCUS_INTERVALS;
        }

        long ageMillis = Math.max(0L, System.currentTimeMillis() - lastPulseResultMillis);
        int step = (int) Math.ceil(ageMillis / 50.0);
        return Math.max(0, Math.min(PANEL_FOCUS_INTERVALS, step));
    }

    public static boolean shouldShowPanelText() {
        return getPanelAnimationStep() >= PANEL_FOCUS_INTERVALS;
    }

    public static boolean isFocusedScanOpening() {
        return hasFocusedReading() && !shouldShowPanelText();
    }

    public static float getReticleScanProgress() {
        if (!hasFocusedReading()) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(100.0f, (getPanelAnimationStep() / (float) PANEL_FOCUS_INTERVALS) * 100.0f));
    }

    public static void updateHudAnimations() {
        long now = System.currentTimeMillis();
        if (lastHudAnimationMillis <= 0L) {
            lastHudAnimationMillis = now;
            return;
        }

        float elapsedSeconds = Math.max(0.0f, (now - lastHudAnimationMillis) / 1000.0f);
        lastHudAnimationMillis = now;
        float updateInterval = elapsedSeconds * RENDER_UPDATES_PER_SECOND;

        boolean scanOpening = isFocusedScanOpening();
        boolean focused = hasFocusedReading();
        float ringSpeed = scanOpening ? 2.8f : focused ? 1.45f : 1.0f;
        float innerSpeed = scanOpening ? 10.0f : focused ? 3.0f : 1.0f;

        usageIntervals = wrapDegrees(usageIntervals + (updateInterval * ringSpeed));
        innerRingRotation = wrapDegrees(innerRingRotation + (updateInterval * innerSpeed));
    }

    public static float getUsageIntervals() {
        return usageIntervals;
    }

    public static float getInnerRingRotation() {
        return innerRingRotation;
    }

    public record ReadingSnapshot(
            String id,
            AuraReadingType type,
            String label,
            String status,
            String distanceBand,
            String verticalHint,
            float strength,
            float confidence
    ) {
        public ReadingSnapshot {
            id = id == null ? "" : id;
            type = type == null ? AuraReadingType.UNKNOWN_ANOMALY : type;
            label = label == null ? "" : label;
            status = status == null ? "" : status;
            distanceBand = distanceBand == null ? "" : distanceBand;
            verticalHint = verticalHint == null ? "" : verticalHint;
            strength = clamp01(strength);
            confidence = clamp01(confidence);
        }
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }
}
