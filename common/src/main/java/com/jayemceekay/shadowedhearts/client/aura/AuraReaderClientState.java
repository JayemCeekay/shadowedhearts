package com.jayemceekay.shadowedhearts.client.aura;

import net.minecraft.network.chat.Component;

import com.cobblemon.mod.common.CobblemonSounds;
import com.jayemceekay.shadowedhearts.common.aura.AuraHeartGaugeReadout;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderLockType;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderMode;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingSource;
import com.jayemceekay.shadowedhearts.common.aura.AuraReadingType;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
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
    private static final float PULSE_ANIMATION_SECONDS = 0.9f;
    private static boolean active;
    private static boolean tutorialPreview;
    private static final AuraReaderBattleActivation battleActivation = new AuraReaderBattleActivation();
    private static List<ReadingSnapshot> battleReadings = List.of();
    private static int scanRange;
    private static boolean tracking;
    private static String selectedSignal = "";
    private static AuraReaderLockType lockType = AuraReaderLockType.NONE;
    private static int pulseCooldownRemainingTicks;
    private static int pulseCooldownMaxTicks;
    private static long pulseCooldownSnapshotTick;
    private static int lastPulseContactCount;
    private static long lastPulseResultTick = -1L;
    private static final Map<String, EnumMap<AuraReadingSource, ReadingPresentation>> readings = new LinkedHashMap<>();
    private static float pulseAnimationSeconds = -1.0f;
    private static final AuraReaderScanAnimation scanAnimation = new AuraReaderScanAnimation();
    private static ReadingSnapshot focusedReading;
    private static ReadingSnapshot panelReading;
    private static String lastTrackedSignalId;
    private static float signalLostSeconds;
    private static float usageIntervals;
    private static float innerRingRotation;
    private static float activationProgress;
    private static final boolean[] panelSides = new boolean[4];
    private static ClientLevel observedLevel;
    private static boolean observedLevelInitialized;

    private AuraReaderClientState() {
    }

    public static boolean isActive() {
        return tutorialPreview || battleActivation.isActive(active);
    }

    public static void setActive(boolean active) {
        boolean wasActive = isActive();
        battleActivation.onManualToggle(active);
        AuraReaderClientState.active = active;
        if (wasActive != isActive()) playToggleSound(isActive());
    }

    public static boolean isManuallyActive() { return active; }
    public static boolean isInBattle() { return battleActivation.inBattle(); }
    public static void setTutorialPreview(boolean preview) { tutorialPreview = preview; }
    public static boolean isTutorialPreview() { return tutorialPreview; }

    public static void applyBattleReadings(String battleId, List<ReadingSnapshot> next) {
        observeClientLevel();
        boolean wasActive = isActive();
        battleReadings = next == null ? List.of() : next.stream().filter(Objects::nonNull)
                .filter(reading -> !reading.outOfDimension()).toList();
        battleActivation.update(battleId, !battleReadings.isEmpty());
        if (wasActive != isActive()) playToggleSound(isActive());
    }

    private static ReadingSnapshot battleFocus() {
        return isActive() && !battleReadings.isEmpty() ? battleReadings.getFirst() : null;
    }

    private static void playToggleSound(boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    active ? ModSounds.AURA_READER_EQUIP.get() : ModSounds.AURA_READER_UNEQUIP.get(),
                    1.0f, active
                            ? ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraReaderEquipVolume()
                            : ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraReaderUnequipVolume()
            ));
        }
    }

    public static AuraReaderMode getMode() {
        return AuraReaderMode.PASSIVE_AURA;
    }

    public static void setMode(AuraReaderMode mode) {
        // Legacy packets and saves are accepted; the visor has one mode.
    }

    public static int getScanRange() {
        return scanRange;
    }

    public static void setScanRange(int range) {
        observeClientLevel();
        scanRange = Math.max(0, range);
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
        Map<String, ReadingSnapshot> visible = new LinkedHashMap<>();
        readings.values().stream()
                .map(AuraReaderClientState::resolvePresentation)
                .filter(Objects::nonNull)
                .map(reading -> reading.snapshotAt(nowMillis))
                .forEach(reading -> visible.put(reading.id(), reading));
        for (ReadingSnapshot reading : battleReadings) visible.put(reading.id(), reading);
        return List.copyOf(visible.values());
    }

    public static ReadingSnapshot getPrimaryReading() {
        for (ReadingSnapshot r : getReadings()) {
            if (r.id().equals(selectedSignal)) return r;
        }
        for (ReadingSnapshot r : getReadings()) {
            if (r.selected()) return r;
        }
        return null;
    }

    public static boolean hasFocusedReading() {
        return scanAnimation.focusId() != null;
    }

    public static String getFocusedReadingId() {
        return scanAnimation.focusId();
    }

    public static ReadingSnapshot getFocusedReading() {
        return focusedReading;
    }

    public static ReadingSnapshot getPanelReading() {
        return panelReading;
    }

    public static boolean isSignalRecentlyLost() {
        return signalLostSeconds > 0.0f;
    }

    public static float getReadingAlpha(String id) {
        if (id == null || id.isBlank()) {
            return 0.0f;
        }
        if (battleReadings.stream().anyMatch(reading -> reading.id().equals(id))) return 1.0f;

        observeClientLevel();
        long currentTick = clientLevelTick();
        pruneExpiredReadings(currentTick);
        ReadingPresentation reading = resolvePresentation(readings.get(id));
        if (reading == null) {
            return 0.0f;
        }
        ReadingSnapshot snapshot = reading.target();
        return AuraReaderReadingLifecycle.layerAlpha(snapshot.source(), snapshot.expiryTick(),
                reading.staleSinceTick(), currentTick);
    }

    public static boolean isReadingStale(String id) {
        observeClientLevel();
        if (battleReadings.stream().anyMatch(reading -> reading.id().equals(id))) return false;
        ReadingPresentation reading = resolvePresentation(readings.get(id));
        return reading != null && reading.staleSinceTick() >= 0L;
    }

    public static boolean hasRecentPulseResult() {
        return lastPulseResultTick >= 0L && clientLevelTick() - lastPulseResultTick < 80L;
    }

    public static int getLastPulseContactCount() {
        return lastPulseContactCount;
    }

    /**
     * Returns a normalized one-shot pulse animation progress. A value of zero
     * means that no pulse animation is currently active.
     */
    public static float getPulseAnimationProgress() {
        if (pulseAnimationSeconds < 0.0f) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, pulseAnimationSeconds / PULSE_ANIMATION_SECONDS));
    }

    public static int getPulseCooldownMaxTicks() {
        return Math.max(0, pulseCooldownMaxTicks);
    }

    public static float getPulseCooldownRemainingTicks() {
        if (pulseCooldownRemainingTicks <= 0) {
            return 0.0f;
        }

        long elapsedTicks = Math.max(0L, clientLevelTick() - pulseCooldownSnapshotTick);
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
            return Component.translatable("hud.shadowedhearts.aura_reader.pulse.ready_short").getString();
        }
        return Component.translatable("hud.shadowedhearts.aura_reader.pulse.cooldown", String.format(Locale.ROOT, "%.1f", remaining / 20.0f)).getString();
    }

    public static void applyServerState(
            boolean active,
            AuraReaderMode mode,
            boolean tracking,
            String selectedSignal,
            AuraReaderLockType lockType,
            int pulseCooldownRemainingTicks,
            int pulseCooldownMaxTicks
    ) {
        observeClientLevel();
        boolean wasActive = isActive();
        AuraReaderClientState.active = active;
        if (wasActive != isActive()) playToggleSound(isActive());
        AuraReaderClientState.tracking = tracking;
        AuraReaderClientState.selectedSignal = selectedSignal == null ? "" : selectedSignal;
        AuraReaderClientState.lockType = lockType == null ? AuraReaderLockType.NONE : lockType;
        AuraReaderClientState.pulseCooldownRemainingTicks = Math.max(0, pulseCooldownRemainingTicks);
        AuraReaderClientState.pulseCooldownMaxTicks = Math.max(0, pulseCooldownMaxTicks);
        AuraReaderClientState.pulseCooldownSnapshotTick = clientLevelTick();
    }

    public static void applyPulseResult(List<ReadingSnapshot> nextReadings) {
        observeClientLevel();
        long nowMillis = System.currentTimeMillis();
        long currentTick = clientLevelTick();
        pruneExpiredReadings(currentTick);
        pulseAnimationSeconds = 0.0f;
        lastPulseResultTick = currentTick;
        lastPulseContactCount = nextReadings == null ? 0 : (int) nextReadings.stream()
                .filter(Objects::nonNull)
                .filter(reading -> reading.type() == AuraReadingType.SHADOW_POKEMON)
                .map(ReadingSnapshot::id).distinct().count();

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

            upsertReading(reading, nowMillis).markSeen();
        }
    }

    public static void applyReadings(List<ReadingSnapshot> nextReadings, boolean animate) {
        observeClientLevel();
        long nowMillis = System.currentTimeMillis();
        long currentTick = clientLevelTick();
        pruneExpiredReadings(currentTick);
        Set<ReadingLayerKey> refreshedLayers = new HashSet<>();

        if (nextReadings != null) {
            for (ReadingSnapshot reading : nextReadings) {
                if (reading == null) {
                    continue;
                }
                if (AuraReaderReadingLifecycle.isExpired(reading.expiryTick(), currentTick)) {
                    continue;
                }

                refreshedLayers.add(new ReadingLayerKey(reading.id(), reading.source()));
                upsertReading(reading, nowMillis).markSeen();
            }
        }

        markMissingReadings(refreshedLayers, currentTick);
        pruneExpiredReadings(currentTick);

        if (animate) {
            pulseAnimationSeconds = 0.0f;
        }
    }

    public static int getPanelAnimationStep() {
        return Math.max(0, Math.min(PANEL_FOCUS_INTERVALS,
                (int) Math.ceil(scanAnimation.panelProgress() * PANEL_FOCUS_INTERVALS)));
    }

    public static boolean shouldShowPanelText() {
        return scanAnimation.textAlpha() > 0.0f;
    }

    public static float getPanelProgress() {
        return scanAnimation.panelProgress();
    }

    public static float getPanelTextAlpha() {
        return scanAnimation.textAlpha();
    }

    public static boolean isFocusedScanOpening() {
        return scanAnimation.scanning();
    }

    public static float getReticleScanProgress() {
        return scanAnimation.scanProgress();
    }

    public static void updateHudAnimations(ReadingSnapshot observedFocus, ReadingSnapshot tracked,
                                           float elapsedSeconds) {
        observeClientLevel();
        pruneExpiredReadings(clientLevelTick());
        boolean enabled = isActive();
        if (observedFocus == null) observedFocus = battleFocus();
        if (activationProgress <= 0.0f && !enabled) {
            clearTransientPresentationState();
            return;
        }
        if (!Float.isFinite(elapsedSeconds) || elapsedSeconds <= 0.0f) {
            return;
        }
        float updateInterval = elapsedSeconds * RENDER_UPDATES_PER_SECOND;

        if (enabled) {
            activationProgress = Math.min(1.0f, activationProgress + updateInterval * 0.035f);
        } else {
            activationProgress = Math.max(0.0f, activationProgress - updateInterval * 0.035f);
            if (activationProgress <= 0.0f) {
                clearTransientPresentationState();
                return;
            }
        }

        String previousFocusId = scanAnimation.focusId();
        String previousPanelId = scanAnimation.panelTargetId();
        float previousScanProgress = scanAnimation.scanProgress();
        String observedId = enabled && observedFocus != null ? observedFocus.id() : null;
        scanAnimation.update(observedId, elapsedSeconds);
        if (observedId != null && observedId.equals(scanAnimation.focusId())) {
            focusedReading = observedFocus;
        } else if (scanAnimation.focusId() == null) {
            focusedReading = null;
        }

        String panelId = scanAnimation.panelTargetId();
        if (panelId == null) {
            panelReading = null;
        } else if (!Objects.equals(previousPanelId, panelId)) {
            panelReading = focusedReading;
            randomizePanelSides();
        } else if (panelId.equals(observedId)) {
            panelReading = observedFocus;
        }

        if (observedId != null && !Objects.equals(previousFocusId, observedId)) {
            playScanSound(CobblemonSounds.POKEDEX_SCAN_DETAIL);
        }
        float oldProgress = Objects.equals(previousFocusId, observedId) ? previousScanProgress : 0.0f;
        float progress = scanAnimation.scanProgress();
        if (observedId != null && Math.floor(oldProgress / 10.0f) != Math.floor(progress / 10.0f)) {
            playScanSound(progress >= 100.0f
                    ? CobblemonSounds.POKEDEX_SCAN_REGISTER_POKEMON
                    : CobblemonSounds.POKEDEX_SCAN_LOOP);
        }

        if (tracked != null) {
            lastTrackedSignalId = tracked.id();
            signalLostSeconds = 0.0f;
        } else if (lastTrackedSignalId != null) {
            lastTrackedSignalId = null;
            signalLostSeconds = 3.0f;
        } else {
            signalLostSeconds = Math.max(0.0f, signalLostSeconds - elapsedSeconds);
        }
        if (pulseAnimationSeconds >= 0.0f) {
            pulseAnimationSeconds += elapsedSeconds;
            if (pulseAnimationSeconds >= PULSE_ANIMATION_SECONDS) {
                pulseAnimationSeconds = -1.0f;
            }
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
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f,
                    ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraScannerBeepVolume()));
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
                return AuraReaderReadingLifecycle.layerExpired(snapshot.source(), snapshot.expiryTick(),
                        reading.staleSinceTick(), currentTick);
            });
            return layers.isEmpty();
        });
    }

    private static ReadingPresentation upsertReading(ReadingSnapshot reading, long nowMillis) {
        EnumMap<AuraReadingSource, ReadingPresentation> layers = readings.computeIfAbsent(
                reading.id(),
                ignored -> new EnumMap<>(AuraReadingSource.class)
        );
        // Once live server refresh has replaced a pulse bearing, its older layer
        // must not reappear after the live layer's shorter stale fade ends.
        if (AuraReaderReadingLifecycle.supersedesPulse(reading.source())) {
            layers.remove(AuraReadingSource.PULSE);
        }
        ReadingPresentation existing = layers.get(reading.source());
        if (existing == null) {
            existing = new ReadingPresentation(reading, nowMillis);
            layers.put(reading.source(), existing);
        } else {
            existing.retarget(reading, nowMillis);
        }
        return existing;
    }

    private static void markMissingReadings(Set<ReadingLayerKey> refreshedLayers, long currentTick) {
        for (Map.Entry<String, EnumMap<AuraReadingSource, ReadingPresentation>> readingEntry : readings.entrySet()) {
            for (Map.Entry<AuraReadingSource, ReadingPresentation> layerEntry : readingEntry.getValue().entrySet()) {
                AuraReadingSource source = layerEntry.getKey();
                if (!refreshedLayers.contains(new ReadingLayerKey(readingEntry.getKey(), source))) {
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
            if (candidate.staleSinceTick() >= 0L) priority -= 10;
            if (resolved == null || priority > resolvedPriority) {
                resolved = candidate;
                resolvedPriority = priority;
            }
        }
        return resolved;
    }

    private static void resetClientSessionState() {
        AuraReaderSearchClientState.clear();
        scanRange = 0;
        active = false;
        tutorialPreview = false;
        battleActivation.clear();
        battleReadings = List.of();
        tracking = false;
        selectedSignal = "";
        lockType = AuraReaderLockType.NONE;
        pulseCooldownRemainingTicks = 0;
        pulseCooldownMaxTicks = 0;
        pulseCooldownSnapshotTick = 0L;
        activationProgress = 0.0f;
        clearTransientPresentationState();
    }

    private static void clearTransientPresentationState() {
        readings.clear();
        pulseAnimationSeconds = -1.0f;
        lastPulseResultTick = -1L;
        lastPulseContactCount = 0;
        scanAnimation.reset();
        focusedReading = null;
        panelReading = null;
        lastTrackedSignalId = null;
        signalLostSeconds = 0.0f;
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
                    template.labelText(),
                    template.statusText(),
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
                    template.outOfDimension(),
                    template.heartGauge()
            );
        }
    }

    private record ReadingLayerKey(String id, AuraReadingSource source) {
    }

    public record ReadingSnapshot(
            String id,
            AuraReadingType type,
            AuraReadingSource source,
            Component labelText,
            Component statusText,
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
            boolean outOfDimension,
            AuraHeartGaugeReadout heartGauge
    ) {
        public ReadingSnapshot(
                String id, AuraReadingType type, AuraReadingSource source, String label,
                String status, double directionX, double directionY, double directionZ,
                float bearingUncertainty, String distanceBand, String verticalHint, float strength,
                float confidence, float interference, int priority, long expiryTick,
                boolean selected, boolean locked, boolean outOfDimension, AuraHeartGaugeReadout heartGauge) {
            this(id, type, source, Component.literal(label == null ? "" : label),
                    Component.literal(status == null ? "" : status), directionX, directionY, directionZ,
                    bearingUncertainty, distanceBand, verticalHint, strength, confidence, interference,
                    priority, expiryTick, selected, locked, outOfDimension, heartGauge);
        }

        public ReadingSnapshot(
                String id, AuraReadingType type, AuraReadingSource source, Component label,
                Component status, double directionX, double directionY, double directionZ,
                float bearingUncertainty, String distanceBand, String verticalHint, float strength,
                float confidence, float interference, int priority, long expiryTick,
                boolean selected, boolean locked, boolean outOfDimension) {
            this(id, type, source, label, status, directionX, directionY, directionZ,
                    bearingUncertainty, distanceBand, verticalHint, strength, confidence, interference,
                    priority, expiryTick, selected, locked, outOfDimension, null);
        }

        public ReadingSnapshot(
                String id, AuraReadingType type, AuraReadingSource source,
                String label, String status, double directionX, double directionY,
                double directionZ, float bearingUncertainty, String distanceBand,
                String verticalHint, float strength, float confidence, float interference,
                int priority, long expiryTick, boolean selected, boolean locked,
                boolean outOfDimension) {
            this(id, type, source, label, status, directionX, directionY, directionZ,
                    bearingUncertainty, distanceBand, verticalHint, strength, confidence,
                    interference, priority, expiryTick, selected, locked, outOfDimension, null);
        }

        public String label() { return labelText.getString(); }
        public String status() { return statusText.getString(); }

        public ReadingSnapshot {
            id = id == null ? "" : id;
            type = type == null ? AuraReadingType.UNKNOWN_ANOMALY : type;
            source = source == null ? AuraReadingSource.PASSIVE : source;
            labelText = labelText == null ? Component.empty() : labelText.copy();
            statusText = statusText == null ? Component.empty() : statusText.copy();
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
