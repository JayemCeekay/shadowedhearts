package com.jayemceekay.shadowedhearts.client.aura;

import com.cobblemon.mod.common.CobblemonSounds;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AuraReaderClientState {
    private static final int PANEL_FOCUS_INTERVALS = 9;
    private static final float RENDER_UPDATES_PER_SECOND = 1.0f / 0.0175f;
    private static final long DIRECTION_INTERPOLATION_MILLIS = 250L;
    private static final long PULSE_ANIMATION_MILLIS = 900L;
    private static final AuraReaderMode[] CYCLE = {
            AuraReaderMode.PASSIVE_AURA,
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
    private static final Map<String, EnumMap<AuraReadingSource, ReadingPresentation>> readings = new LinkedHashMap<>();
    private static long lastPulseResultMillis;
    private static float scanningProgress;
    private static String lastScanningId;
    private static long lastHudAnimationMillis;
    private static float usageIntervals;
    private static float innerRingRotation;
    private static float activationProgress;
    private static final boolean[] panelSides = new boolean[4];
    private static ClientLevel observedLevel;
    private static boolean observedLevelInitialized;

    private AuraReaderClientState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        if (AuraReaderClientState.active != active) {
            AuraReaderClientState.active = active;
            playToggleSound(active);
        }
    }

    private static void playToggleSound(boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    active ? ModSounds.AURA_READER_EQUIP.get() : ModSounds.AURA_READER_UNEQUIP.get(),
                    1.0f
            ));
        }
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
        observeClientLevel();
        long currentTick = clientLevelTick();
        pruneExpiredReadings(currentTick);
        long nowMillis = System.currentTimeMillis();
        return readings.values().stream()
                .map(AuraReaderClientState::resolvePresentation)
                .filter(Objects::nonNull)
                .map(reading -> reading.snapshotAt(nowMillis))
                .toList();
    }

    public static ReadingSnapshot getPrimaryReading() {
        for (ReadingSnapshot r : getReadings()) {
            if (r.selected()) return r;
        }
        return null;
    }

    public static boolean hasFocusedReading() {
        return getPrimaryReading() != null;
    }

    public static float getReadingAlpha(String id) {
        if (id == null || id.isBlank()) {
            return 0.0f;
        }

        observeClientLevel();
        long currentTick = clientLevelTick();
        pruneExpiredReadings(currentTick);
        ReadingPresentation reading = resolvePresentation(readings.get(id));
        if (reading == null) {
            return 0.0f;
        }
        ReadingSnapshot snapshot = reading.target();
        if (AuraReaderReadingLifecycle.isPulse(snapshot.source())) {
            return AuraReaderReadingLifecycle.pulseAlpha(snapshot.expiryTick(), currentTick);
        }
        return AuraReaderReadingLifecycle.staleAlpha(reading.staleSinceTick(), currentTick);
    }

    /**
     * Returns a normalized one-shot pulse animation progress. A value of zero
     * means that no pulse animation is currently active.
     */
    public static float getPulseAnimationProgress() {
        if (lastPulseResultMillis <= 0L) {
            return 0.0f;
        }
        long ageMillis = Math.max(0L, System.currentTimeMillis() - lastPulseResultMillis);
        if (ageMillis >= PULSE_ANIMATION_MILLIS) {
            lastPulseResultMillis = 0L;
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, ageMillis / (float) PULSE_ANIMATION_MILLIS));
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
        observeClientLevel();
        if (AuraReaderClientState.active != active) {
            AuraReaderClientState.active = active;
            playToggleSound(active);
        }
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
        int currentIndex = -1;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == mode) {
                currentIndex = i;
                break;
            }
        }

        mode = currentIndex < 0
                ? CYCLE[0]
                : CYCLE[(currentIndex + 1) % CYCLE.length];
        return mode;
    }

    public static void applyPulseResult(List<ReadingSnapshot> nextReadings) {
        observeClientLevel();
        long nowMillis = System.currentTimeMillis();
        long currentTick = clientLevelTick();
        pruneExpiredReadings(currentTick);
        lastPulseResultMillis = nowMillis;

        if (nextReadings == null) {
            return;
        }

        for (ReadingSnapshot reading : nextReadings) {
            if (reading == null || !AuraReaderReadingLifecycle.isPulse(reading.source())) {
                continue;
            }
            if (AuraReaderReadingLifecycle.isExpired(reading.expiryTick(), currentTick)) {
                continue;
            }

            upsertReading(reading, nowMillis);
        }
    }

    public static void applyReadings(List<ReadingSnapshot> nextReadings, boolean animate) {
        observeClientLevel();
        long nowMillis = System.currentTimeMillis();
        long currentTick = clientLevelTick();
        pruneExpiredReadings(currentTick);
        String previousPrimaryId = getPrimaryReading() == null ? "" : getPrimaryReading().id();
        Set<ReadingLayerKey> refreshedNonPulseLayers = new HashSet<>();

        if (nextReadings != null) {
            for (ReadingSnapshot reading : nextReadings) {
                if (reading == null) {
                    continue;
                }
                if (AuraReaderReadingLifecycle.isPulse(reading.source())) {
                    if (!AuraReaderReadingLifecycle.isExpired(reading.expiryTick(), currentTick)) {
                        upsertReading(reading, nowMillis);
                    }
                    continue;
                }

                refreshedNonPulseLayers.add(new ReadingLayerKey(reading.id(), reading.source()));
                upsertReading(reading, nowMillis).markSeen();
            }
        }

        markMissingNonPulseReadings(refreshedNonPulseLayers, currentTick);
        pruneExpiredReadings(currentTick);

        String nextPrimaryId = getPrimaryReading() == null ? "" : getPrimaryReading().id();
        if (animate) {
            lastPulseResultMillis = System.currentTimeMillis();
        }
        if (!Objects.equals(previousPrimaryId, nextPrimaryId)) {
            lastScanningId = null;
        }
    }

    public static int getPanelAnimationStep() {
        return Math.max(0, Math.min(PANEL_FOCUS_INTERVALS, (int) Math.floor((scanningProgress / 100.0f) * PANEL_FOCUS_INTERVALS)));
    }

    public static boolean shouldShowPanelText() {
        return getPanelAnimationStep() >= PANEL_FOCUS_INTERVALS;
    }

    public static boolean isFocusedScanOpening() {
        return hasFocusedReading() && !shouldShowPanelText();
    }

    public static float getReticleScanProgress() {
        return scanningProgress;
    }

    public static void updateHudAnimations() {
        observeClientLevel();
        pruneExpiredReadings(clientLevelTick());
        if (activationProgress <= 0.0f && !active) {
            lastHudAnimationMillis = 0L;
            clearTransientPresentationState();
            return;
        }

        long now = System.currentTimeMillis();
        if (lastHudAnimationMillis <= 0L) {
            lastHudAnimationMillis = now;
            return;
        }

        float elapsedSeconds = Math.max(0.0f, (now - lastHudAnimationMillis) / 1000.0f);
        lastHudAnimationMillis = now;
        float updateInterval = elapsedSeconds * RENDER_UPDATES_PER_SECOND;

        if (active) {
            activationProgress = Math.min(1.0f, activationProgress + updateInterval * 0.035f);
        } else {
            activationProgress = Math.max(0.0f, activationProgress - updateInterval * 0.035f);
            if (activationProgress <= 0.0f) {
                clearTransientPresentationState();
                lastHudAnimationMillis = 0L;
                return;
            }
        }

        ReadingSnapshot primary = getPrimaryReading();
        if (primary != null) {
            if (!Objects.equals(primary.id(), lastScanningId)) {
                scanningProgress = 0.0f;
                lastScanningId = primary.id();
                randomizePanelSides();
                playScanSound(CobblemonSounds.POKEDEX_SCAN_DETAIL);
            }

            if (scanningProgress < 100.0f) {
                float oldProgress = scanningProgress;
                scanningProgress = Math.min(100.0f, scanningProgress + updateInterval);

                if (Math.floor(oldProgress / 10.0f) != Math.floor(scanningProgress / 10.0f)) {
                    if (scanningProgress < 100.0f) {
                        playScanSound(CobblemonSounds.POKEDEX_SCAN_LOOP);
                    } else {
                        playScanSound(CobblemonSounds.POKEDEX_SCAN_REGISTER_POKEMON);
                    }
                }
            }
        } else {
            scanningProgress = 0.0f;
            lastScanningId = null;
        }

        boolean scanOpening = isFocusedScanOpening();
        boolean focused = hasFocusedReading();
        float ringSpeed = scanOpening ? 2.8f : focused ? 1.45f : 1.0f;
        float innerSpeed = scanOpening ? 10.0f : focused ? 3.0f : 1.0f;

        usageIntervals = wrapDegrees(usageIntervals + (updateInterval * ringSpeed));
        innerRingRotation = wrapDegrees(innerRingRotation + (updateInterval * innerSpeed));
    }

    private static void playScanSound(net.minecraft.sounds.SoundEvent sound) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
        }
    }

    public static float getUsageIntervals() {
        return usageIntervals;
    }

    public static float getInnerRingRotation() {
        return innerRingRotation;
    }

    public static float getActivationProgress() {
        return activationProgress;
    }

    public static boolean getPanelSide(int index) {
        if (index < 0 || index >= panelSides.length) return true;
        return panelSides[index];
    }

    private static void randomizePanelSides() {
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < panelSides.length; i++) {
            panelSides[i] = random.nextBoolean();
        }
    }

    private static void observeClientLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel currentLevel = minecraft == null ? null : minecraft.level;
        if (!observedLevelInitialized) {
            observedLevel = currentLevel;
            observedLevelInitialized = true;
            return;
        }
        if (currentLevel != observedLevel) {
            observedLevel = currentLevel;
            resetClientSessionState();
        }
    }

    private static long clientLevelTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    private static void pruneExpiredReadings(long currentTick) {
        readings.entrySet().removeIf(entry -> {
            EnumMap<AuraReadingSource, ReadingPresentation> layers = entry.getValue();
            layers.entrySet().removeIf(layerEntry -> {
                ReadingPresentation reading = layerEntry.getValue();
                ReadingSnapshot snapshot = reading.target();
                return AuraReaderReadingLifecycle.isPulse(snapshot.source())
                        ? AuraReaderReadingLifecycle.isExpired(snapshot.expiryTick(), currentTick)
                        : AuraReaderReadingLifecycle.isStaleExpired(reading.staleSinceTick(), currentTick);
            });
            return layers.isEmpty();
        });
    }

    private static ReadingPresentation upsertReading(ReadingSnapshot reading, long nowMillis) {
        EnumMap<AuraReadingSource, ReadingPresentation> layers = readings.computeIfAbsent(
                reading.id(),
                ignored -> new EnumMap<>(AuraReadingSource.class)
        );
        ReadingPresentation existing = layers.get(reading.source());
        if (existing == null) {
            existing = new ReadingPresentation(reading, nowMillis);
            layers.put(reading.source(), existing);
        } else {
            existing.retarget(reading, nowMillis);
        }
        return existing;
    }

    private static void markMissingNonPulseReadings(Set<ReadingLayerKey> refreshedLayers, long currentTick) {
        for (Map.Entry<String, EnumMap<AuraReadingSource, ReadingPresentation>> readingEntry : readings.entrySet()) {
            for (Map.Entry<AuraReadingSource, ReadingPresentation> layerEntry : readingEntry.getValue().entrySet()) {
                AuraReadingSource source = layerEntry.getKey();
                if (!AuraReaderReadingLifecycle.isPulse(source)
                        && !refreshedLayers.contains(new ReadingLayerKey(readingEntry.getKey(), source))) {
                    layerEntry.getValue().markMissing(currentTick);
                }
            }
        }
    }

    private static ReadingPresentation resolvePresentation(Map<AuraReadingSource, ReadingPresentation> layers) {
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        ReadingPresentation resolved = null;
        int resolvedPriority = Integer.MIN_VALUE;
        for (ReadingPresentation candidate : layers.values()) {
            ReadingSnapshot snapshot = candidate.target();
            int priority = AuraReaderReadingLifecycle.presentationPriority(snapshot.source(), snapshot.locked());
            if (resolved == null || priority > resolvedPriority) {
                resolved = candidate;
                resolvedPriority = priority;
            }
        }
        return resolved;
    }

    private static void resetClientSessionState() {
        active = false;
        mode = AuraReaderMode.PASSIVE_AURA;
        charge = 0;
        maxCharge = 1;
        tracking = false;
        selectedSignal = "";
        lockType = AuraReaderLockType.NONE;
        pulseCooldownRemainingTicks = 0;
        pulseCooldownMaxTicks = 0;
        pulseCooldownSnapshotMillis = 0L;
        activationProgress = 0.0f;
        clearTransientPresentationState();
        lastHudAnimationMillis = 0L;
    }

    private static void clearTransientPresentationState() {
        readings.clear();
        lastPulseResultMillis = 0L;
        scanningProgress = 0.0f;
        lastScanningId = null;
        usageIntervals = 0.0f;
        innerRingRotation = 0.0f;
        Arrays.fill(panelSides, false);
    }

    private static final class ReadingPresentation {
        private ReadingSnapshot target;
        private float startYaw;
        private float targetYaw;
        private double startHorizontalMagnitude;
        private double targetHorizontalMagnitude;
        private double startDirectionY;
        private double targetDirectionY;
        private float startBearingUncertainty;
        private float targetBearingUncertainty;
        private long transitionStartMillis;
        private long staleSinceTick = -1L;

        private ReadingPresentation(ReadingSnapshot target, long nowMillis) {
            this.target = target;
            this.startYaw = AuraReaderCompassMath.worldYaw(target.directionX(), target.directionZ());
            this.targetYaw = startYaw;
            this.startHorizontalMagnitude = horizontalMagnitude(target);
            this.targetHorizontalMagnitude = startHorizontalMagnitude;
            this.startDirectionY = finiteOrZero(target.directionY());
            this.targetDirectionY = startDirectionY;
            this.startBearingUncertainty = target.bearingUncertainty();
            this.targetBearingUncertainty = startBearingUncertainty;
            this.transitionStartMillis = nowMillis - DIRECTION_INTERPOLATION_MILLIS;
        }

        private ReadingSnapshot target() {
            return target;
        }

        private long staleSinceTick() {
            return staleSinceTick;
        }

        private void markSeen() {
            staleSinceTick = -1L;
        }

        private void markMissing(long currentTick) {
            if (staleSinceTick < 0L) {
                staleSinceTick = currentTick;
            }
        }

        private void retarget(ReadingSnapshot next, long nowMillis) {
            ReadingSnapshot current = snapshotAt(nowMillis);
            this.target = next;
            this.startYaw = AuraReaderCompassMath.worldYaw(current.directionX(), current.directionZ());
            this.targetYaw = AuraReaderCompassMath.worldYaw(next.directionX(), next.directionZ());
            this.startHorizontalMagnitude = horizontalMagnitude(current);
            this.targetHorizontalMagnitude = horizontalMagnitude(next);
            this.startDirectionY = finiteOrZero(current.directionY());
            this.targetDirectionY = finiteOrZero(next.directionY());
            this.startBearingUncertainty = current.bearingUncertainty();
            this.targetBearingUncertainty = next.bearingUncertainty();
            this.transitionStartMillis = nowMillis;
        }

        private ReadingSnapshot snapshotAt(long nowMillis) {
            float progress = Math.max(0.0f, Math.min(1.0f,
                    (nowMillis - transitionStartMillis) / (float) DIRECTION_INTERPOLATION_MILLIS));
            if (progress >= 1.0f) {
                return target;
            }

            float yaw = AuraReaderCompassMath.lerpShortestAngle(startYaw, targetYaw, progress);
            double horizontalMagnitude = lerp(startHorizontalMagnitude, targetHorizontalMagnitude, progress);
            double directionY = lerp(startDirectionY, targetDirectionY, progress);
            double yawRadians = Math.toRadians(yaw);
            double directionX = -Math.sin(yawRadians) * horizontalMagnitude;
            double directionZ = Math.cos(yawRadians) * horizontalMagnitude;
            double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
            if (length > 1.0e-8) {
                directionX /= length;
                directionY /= length;
                directionZ /= length;
            }

            float bearingUncertainty = (float) lerp(
                    startBearingUncertainty,
                    targetBearingUncertainty,
                    progress
            );
            return withPresentationDirection(target, directionX, directionY, directionZ, bearingUncertainty);
        }

        private static double horizontalMagnitude(ReadingSnapshot reading) {
            return Math.hypot(finiteOrZero(reading.directionX()), finiteOrZero(reading.directionZ()));
        }

        private static double finiteOrZero(double value) {
            return Double.isFinite(value) ? value : 0.0;
        }

        private static double lerp(double start, double end, float progress) {
            return start + (end - start) * progress;
        }

        private static ReadingSnapshot withPresentationDirection(
                ReadingSnapshot template,
                double directionX,
                double directionY,
                double directionZ,
                float bearingUncertainty
        ) {
            return new ReadingSnapshot(
                    template.id(),
                    template.type(),
                    template.source(),
                    template.label(),
                    template.status(),
                    directionX,
                    directionY,
                    directionZ,
                    bearingUncertainty,
                    template.distanceBand(),
                    template.verticalHint(),
                    template.strength(),
                    template.confidence(),
                    template.interference(),
                    template.priority(),
                    template.expiryTick(),
                    template.selected(),
                    template.locked(),
                    template.outOfDimension()
            );
        }
    }

    private record ReadingLayerKey(String id, AuraReadingSource source) {
    }

    public record ReadingSnapshot(
            String id,
            AuraReadingType type,
            AuraReadingSource source,
            String label,
            String status,
            double directionX,
            double directionY,
            double directionZ,
            float bearingUncertainty,
            String distanceBand,
            String verticalHint,
            float strength,
            float confidence,
            float interference,
            int priority,
            long expiryTick,
            boolean selected,
            boolean locked,
            boolean outOfDimension
    ) {
        public ReadingSnapshot {
            id = id == null ? "" : id;
            type = type == null ? AuraReadingType.UNKNOWN_ANOMALY : type;
            source = source == null ? AuraReadingSource.PASSIVE : source;
            label = label == null ? "" : label;
            status = status == null ? "" : status;
            distanceBand = distanceBand == null ? "" : distanceBand;
            verticalHint = verticalHint == null ? "" : verticalHint;
            strength = clamp01(strength);
            confidence = clamp01(confidence);
            interference = clamp01(interference);
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
