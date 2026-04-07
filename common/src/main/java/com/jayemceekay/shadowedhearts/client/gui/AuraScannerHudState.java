package com.jayemceekay.shadowedhearts.client.gui;

import com.jayemceekay.fluxui.hud.state.MutableValue;
import com.jayemceekay.shadowedhearts.client.ModKeybinds;
import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import com.jayemceekay.shadowedhearts.client.trail.TrailClientState;
import com.jayemceekay.shadowedhearts.common.aura.AuraReaderCharge;
import com.jayemceekay.shadowedhearts.common.aura.PokedexUsageContext;
import com.jayemceekay.shadowedhearts.common.tracking.CalibrationSequence;
import com.jayemceekay.shadowedhearts.common.tracking.NodeEventType;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.content.items.AuraReaderItem;
import com.jayemceekay.shadowedhearts.integration.accessories.SnagAccessoryBridgeHolder;
import com.jayemceekay.shadowedhearts.network.ShadowedHeartsNetwork;
import com.jayemceekay.shadowedhearts.network.aura.AuraScannerC2SPacket;
import com.jayemceekay.shadowedhearts.network.aura.AuraTrackingStateC2SPacket;
import com.jayemceekay.shadowedhearts.network.trail.CalibrationInputC2SPacket;
import com.jayemceekay.shadowedhearts.network.trail.EvidenceScanCompleteC2SPacket;
import com.jayemceekay.shadowedhearts.network.trail.NodeEventInputC2SPacket;
import com.jayemceekay.shadowedhearts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AuraScannerHudState {
    public static final float BOOT_DURATION = 0.5f;
    public static final float RENDER_UPDATES_PER_SECOND = (float) (1.0 / 0.0175);
    public static final int PULSE_COOLDOWN_TICKS = 400;
    public static final float SLIDE_HIDDEN_Y = -300f;
    public static final float SLIDE_VISIBLE_Y = 0f;
    public static final float SLIDE_STEP = (SLIDE_VISIBLE_Y - SLIDE_HIDDEN_Y) / 10f; // 30f/tick → 10 ticks = 0.5s
    public static final float FADE_STEP = 1.0f / 10f; // matches slide duration so both animate over 0.5s
    public static final int SCANNER_COOLDOWN_TICKS = 20;

    // Trail hotspot scanning (client-only, v1) — hold to scan
    public static final int HOTSPOT_SCAN_REQUIRED_TICKS = 40; // ~2 seconds
    public static final float HOTSPOT_RADIUS = 2.5f; // must match server EvidenceHotspot v1

    // Legacy systems toggle
    private static final boolean LEGACY_DETECTION_DISABLED = true;

    public static final int DETECTION_DURATION_POKEMON = 400;
    public static final int DETECTION_DURATION_METEOROIDS = 400;
    public static final int RESPONSE_DELAY = 100;
    public static final int ACQUISITION_WINDOW_TICKS = 400;

    public static final List<net.minecraft.world.level.block.Block> DOWSING_MATERIALS = Arrays.asList(
            net.minecraft.world.level.block.Blocks.COAL_ORE,
            net.minecraft.world.level.block.Blocks.IRON_ORE,
            net.minecraft.world.level.block.Blocks.GOLD_ORE,
            net.minecraft.world.level.block.Blocks.DIAMOND_ORE,
            net.minecraft.world.level.block.Blocks.REDSTONE_ORE,
            net.minecraft.world.level.block.Blocks.LAPIS_ORE,
            net.minecraft.world.level.block.Blocks.EMERALD_ORE,
            net.minecraft.world.level.block.Blocks.COPPER_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_COAL_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_REDSTONE_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_LAPIS_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE_COPPER_ORE
    );

    public static enum TargetType {POKEMON, METEOROID}

    public static class SignalTarget {
        public final TargetType type;
        public final UUID pokemonId; // when type == POKEMON
        public final com.cobblemon.mod.common.api.types.ElementalType elementalType;
        public final BlockPos meteoroidPos; // when type == METEOROID
        public double distance;
        public float strength; // 0..1
        public float interference; // simple placeholder metric 0..1

        public SignalTarget(TargetType type, @Nullable UUID pokemonId, @Nullable com.cobblemon.mod.common.api.types.ElementalType elementalType, BlockPos meteoroidPos) {
            this.type = type;
            this.pokemonId = pokemonId;
            this.elementalType = elementalType;
            this.meteoroidPos = meteoroidPos;
        }

        public boolean matches(SignalTarget other) {
            if (other == null) return false;
            if (this.type != other.type) return false;
            return this.type == TargetType.POKEMON ? Objects.equals(this.pokemonId, other.pokemonId)
                    : Objects.equals(this.meteoroidPos, other.meteoroidPos);
        }
    }

    // Logical State
    public boolean isActive = false;
    public float fadeAmountVal = 0.0f;
    public float prevFadeAmountVal = 0.0f;
    public float bootTimerVal = 0.0f;
    public float prevBootTimerVal = 0.0f;
    public float slideOffsetYVal = SLIDE_HIDDEN_Y;
    public float prevSlideOffsetYVal = SLIDE_HIDDEN_Y;
    public float sweepAngleVal = 0.0f;
    public float prevSweepAngleVal = 0.0f;
    public float glitchTimerVal = 0.0f;
    public float prevGlitchTimerVal = 0.0f;
    public int beepTimer = 0;
    public int pulseQueue = 0;
    public int pulseTimer = 0;
    public int pulseCooldown = 0;
    public int scannerCooldown = 0;
    public int pokedexTicksInUse = 0;
    public float operationalTempCVal = 0.0f;
    public float prevOperationalTempCVal = 0.0f;
    public boolean isModeMenuOpen = false;
    public float modeMenuAlphaVal = 0.0f;
    public float prevModeMenuAlphaVal = 0.0f;
    public float maxIntensityVal = 0;
    public float usageIntervals = 0.0F;
    public float innerRingRotationVal = 0.0F;

    // Aura Reader logic state
    // Legacy fields retained for compatibility but unused when LEGACY_DETECTION_DISABLED
    public final Map<UUID, Integer> detectedShadows = Collections.synchronizedMap(new HashMap<>());
    public final Map<UUID, Integer> pendingResponses = Collections.synchronizedMap(new HashMap<>());
    public final Map<BlockPos, Integer> detectedMeteoroids = Collections.synchronizedMap(new HashMap<>());
    public final Map<BlockPos, Integer> pendingMeteoroidResponses = Collections.synchronizedMap(new HashMap<>());
    public final List<SignalTarget> currentSignals = new ArrayList<>();
    public int selectedSignalIndex = 0;
    public SignalTarget lockedTargetObj = null;
    public float scanningProgressVal = 0.0f;
    public float prevScanningProgressVal = 0.0f;
    public int acquisitionTimer = 0;
    public boolean isAcquisitionMode = false;

    // Hotspot hold-to-scan (trail)
    public int hotspotScanTicks = 0;
    public boolean hotspotScanningActive = false;

    // Calibration arrow key debounce
    private int calibrationInputCooldown = 0;

    // Calibration HUD state (read from TrailClientState)
    public boolean calibrationActiveVal = false;
    public float calibrationProgressVal = 0.0f;
    public float calibrationTimeRemainingVal = 1.0f;
    public int calibrationCurrentIndexVal = 0;
    public int calibrationSequenceLengthVal = 0;
    public int calibrationLastCorrectFlash = 0; // ticks remaining for green flash
    public int calibrationLastWrongFlash = 0;   // ticks remaining for red flash
    public CalibrationSequence.Variant calibrationVariantVal = CalibrationSequence.Variant.HARMONIC_LOCK;
    public List<CalibrationSequence.Direction> calibrationSequenceVal = new ArrayList<>();
    public CalibrationSequence.Grade calibrationGradeVal = null;
    public int calibrationGradeDisplayTicks = 0; // ticks to show grade result

    // Dowsing logic state
    public int selectedDowsingMaterialIndex = 0;
    public BlockPos dowsingTargetPosObj = null;

    // Reactive values for FluxUI
    public final MutableValue<AuraReaderManager.AuraScannerMode> mode = new MutableValue<>(AuraReaderManager.AuraScannerMode.AURA_READER);
    public final MutableValue<Boolean> active = new MutableValue<>(false);
    public final MutableValue<Float> fadeAmount = new MutableValue<>(0f);
    public final MutableValue<Float> bootTimer = new MutableValue<>(0f);
    public final MutableValue<Float> glitchTimer = new MutableValue<>(0f);
    public final MutableValue<Float> sweepAngle = new MutableValue<>(0f);
    public final MutableValue<Float> slideOffsetY = new MutableValue<>(SLIDE_HIDDEN_Y);
    public final MutableValue<Float> innerRingRotation = new MutableValue<>(0f);
    public final MutableValue<Float> operationalTempC = new MutableValue<>(20f);
    public final MutableValue<Integer> charge = new MutableValue<>(0);

    public final MutableValue<Boolean> acquisitionMode = new MutableValue<>(false);
    public final MutableValue<SignalTarget> lockedTarget = new MutableValue<>(null);
    public final MutableValue<Float> scanningProgress = new MutableValue<>(0f);
    public final MutableValue<Float> signalStrength = new MutableValue<>(0f);
    public final MutableValue<Float> interference = new MutableValue<>(0f);
    public final MutableValue<Float> arrowDirectionDeg = new MutableValue<>(0f);
    public final MutableValue<String> signalLabel = new MutableValue<>("NO SIGNAL");
    public final MutableValue<Integer> signalColor = new MutableValue<>(0xAAAAAA);
    public final MutableValue<String> infoLine = new MutableValue<>(null);
    public final MutableValue<Float> maxIntensity = new MutableValue<>(0f);
    public final MutableValue<List<DirectionalPointer>> directionalPointers = new MutableValue<>(new ArrayList<>());

    // Trail scanning HUD state
    public final MutableValue<Boolean> hotspotScanning = new MutableValue<>(false);
    public final MutableValue<Float> hotspotScanProgress = new MutableValue<>(0f);

    // Calibration HUD reactive values
    public final MutableValue<Boolean> calibrationActive = new MutableValue<>(false);
    public final MutableValue<Float> calibrationProgress = new MutableValue<>(0f);
    public final MutableValue<Float> calibrationTimeRemaining = new MutableValue<>(1f);
    public final MutableValue<Integer> calibrationCurrentIndex = new MutableValue<>(0);
    public final MutableValue<Integer> calibrationSequenceLength = new MutableValue<>(0);
    public final MutableValue<List<CalibrationSequence.Direction>> calibrationDirections = new MutableValue<>(new ArrayList<>());
    public final MutableValue<Integer> calibrationVariantId = new MutableValue<>(0);
    public final MutableValue<Boolean> calibrationCorrectFlash = new MutableValue<>(false);
    public final MutableValue<Boolean> calibrationWrongFlash = new MutableValue<>(false);
    public final MutableValue<CalibrationSequence.Grade> calibrationGrade = new MutableValue<>(null);
    public final MutableValue<Integer> calibrationGradeDisplay = new MutableValue<>(0);

    // Hunt tension & trail quality for HUD effects
    public final MutableValue<Float> huntTension = new MutableValue<>(0f);
    public final MutableValue<Float> huntTrailQuality = new MutableValue<>(1f);

    // Node event HUD state
    public boolean nodeEventActiveVal = false;
    public int nodeEventPhaseVal = 0;
    public int nodeEventTypeVal = -1; // NodeEventType ordinal, -1 = none
    public int nodeEventTicksElapsedVal = 0;
    public int nodeEventMaxTicksVal = 0;
    // Evidence Interpretation
    public int nodeEventClueCountVal = 0;
    public int nodeEventWrongGuessesVal = 0;
    public int nodeEventRequiredValidCountVal = 1;
    public int nodeEventFoundValidCountVal = 0;
    public java.util.List<Integer> nodeEventSelectedClueIndicesVal = new java.util.ArrayList<>();
    // Environmental Search
    public float nodeEventSearchSignalVal = 0f;
    public float nodeEventSearchRadiusVal = 0f;
    // Wild Interruption
    public int nodeEventWildTimerVal = 0;
    public boolean nodeEventWildsResolvedVal = false;
    // Provocation
    public float nodeEventSignalBuildupVal = 0f;
    public int nodeEventProvocationRequiredVal = 0;
    // Reactive values
    public final MutableValue<Boolean> nodeEventActive = new MutableValue<>(false);
    public final MutableValue<Integer> nodeEventType = new MutableValue<>(-1);
    public final MutableValue<Integer> nodeEventPhase = new MutableValue<>(0);
    public final MutableValue<Float> nodeEventTimeRemaining = new MutableValue<>(1f);
    // Evidence
    public final MutableValue<Integer> nodeEventClueCount = new MutableValue<>(0);
    public final MutableValue<Integer> nodeEventWrongGuesses = new MutableValue<>(0);
    public final MutableValue<Integer> nodeEventRequiredValidCount = new MutableValue<>(1);
    public final MutableValue<Integer> nodeEventFoundValidCount = new MutableValue<>(0);
    public final MutableValue<java.util.List<Integer>> nodeEventSelectedClueIndices = new MutableValue<>(new java.util.ArrayList<>());
    // Search
    public final MutableValue<Float> nodeEventSearchSignal = new MutableValue<>(0f);
    // Wild
    public final MutableValue<Boolean> nodeEventWildsResolved = new MutableValue<>(false);
    // Provocation
    public final MutableValue<Float> nodeEventSignalBuildup = new MutableValue<>(0f);

    // Manifestation buildup HUD state
    public boolean manifestationActiveVal = false;
    public int manifestationPhaseVal = 0;
    public float manifestationProgressVal = 0f;
    public final MutableValue<Boolean> manifestationActive = new MutableValue<>(false);
    public final MutableValue<Integer> manifestationPhase = new MutableValue<>(0);
    public final MutableValue<Float> manifestationProgress = new MutableValue<>(0f);

    // Grade flash HUD state (for all event types)
    public String gradeFlashTextVal = null;
    public int gradeFlashTicksVal = 0;
    public int gradeFlashColorVal = 0xFFFFFF;
    public final MutableValue<String> gradeFlashText = new MutableValue<>(null);
    public final MutableValue<Integer> gradeFlashTicks = new MutableValue<>(0);
    public final MutableValue<Integer> gradeFlashColor = new MutableValue<>(0xFFFFFF);

    // Signal blackout HUD state
    public boolean signalBlackoutVal = false;
    public final MutableValue<Boolean> signalBlackout = new MutableValue<>(false);

    public static record DirectionalPointer(float angle, float intensity,
                                            boolean isLocked,
                                            boolean isSelected,
                                            boolean isMeteoroid) {
    }

    public final MutableValue<BlockPos> dowsingTargetPos = new MutableValue<>(null);
    public final MutableValue<Integer> dowsingMaterialIndex = new MutableValue<>(0);
    public final MutableValue<String> dowsingMaterialName = new MutableValue<>("");
    public final MutableValue<Float> dowsingDistance = new MutableValue<>(0f);
    public final MutableValue<org.joml.Quaternionf> dowsingArrowRotation = new MutableValue<>(new org.joml.Quaternionf());

    public final MutableValue<Integer> pokedexTicksInUseValue = new MutableValue<>(0);
    public final MutableValue<com.jayemceekay.shadowedhearts.common.aura.PokedexUsageContext> pokedexUsageContext = new MutableValue<>(null);

    public final MutableValue<Boolean> modeMenuOpen = new MutableValue<>(false);
    public final MutableValue<Float> modeMenuAlpha = new MutableValue<>(0f);

    public final MutableValue<AuraReaderManager.AuraScannerMode> hoveredMode = new MutableValue<>(null);

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        prevFadeAmountVal = fadeAmountVal;
        prevBootTimerVal = bootTimerVal;
        prevSlideOffsetYVal = slideOffsetYVal;
        prevSweepAngleVal = sweepAngleVal;
        prevGlitchTimerVal = glitchTimerVal;
        prevOperationalTempCVal = operationalTempCVal;
        prevModeMenuAlphaVal = modeMenuAlphaVal;
        prevScanningProgressVal = scanningProgressVal;
        maxIntensityVal = 0;

        if (isModeMenuOpen) {
            modeMenuAlphaVal = Math.min(1.0f, modeMenuAlphaVal + 0.15f);
        } else {
            modeMenuAlphaVal = Math.max(0.0f, modeMenuAlphaVal - 0.15f);
        }

        boolean hasAuraReader = SnagAccessoryBridgeHolder.INSTANCE.isAuraReaderEquipped(mc.player);

        /*if (isActive && ModKeybinds.AURA_MODE_SELECTOR.isDown()) {
            if (!isModeMenuOpen && !(mc.screen instanceof AuraReaderModeScreen)) {
                isModeMenuOpen = true;
                mc.setScreen(new AuraReaderModeScreen());
            }
        }*/

        if (ModKeybinds.consumeAuraScannerPress() && scannerCooldown <= 0) {
            if (hasAuraReader && ShadowedHeartsConfigs.getInstance().getClientConfig().auraScannerEnabled()) {
                ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(mc.player);

                if (!auraReader.isEmpty() && AuraReaderCharge.get(auraReader) > 0) {
                    isActive = !isActive;
                    scannerCooldown = SCANNER_COOLDOWN_TICKS;
                    ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(isActive));
                    if (isActive) {
                        mc.player.playSound(ModSounds.AURA_READER_EQUIP.get(), ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraReaderEquipVolume(), 1.0f);
                        bootTimerVal = BOOT_DURATION;
                        sweepAngleVal = 0.0f;
                        prevSweepAngleVal = 0.0f;
                    } else {
                        mc.player.playSound(ModSounds.AURA_READER_UNEQUIP.get(), ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraReaderUnequipVolume(), 1.0f);
                        ShadowedHeartsNetwork.sendToServer(new AuraTrackingStateC2SPacket(false));
                        onDeactivate();
                    }
                }
            } else if (isActive) {
                isActive = false;
                ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(false));
                ShadowedHeartsNetwork.sendToServer(new AuraTrackingStateC2SPacket(false));
                onDeactivate();
            }
        }

        if (isActive && !hasAuraReader) {
            isActive = false;
            ShadowedHeartsNetwork.sendToServer(new AuraScannerC2SPacket(false));
            ShadowedHeartsNetwork.sendToServer(new AuraTrackingStateC2SPacket(false));
            onDeactivate();
        }

        if (pulseCooldown > 0) pulseCooldown--;
        if (scannerCooldown > 0) scannerCooldown--;

        if (isActive) {
            handleInput(mc);
            tickActiveMode(mc);

            // v1: handle trail hotspot hold-to-scan
            tickHotspotScan(mc);

            if (pulseQueue > 0) {
                if (pulseTimer <= 0) {
                    AuraPulseRenderer.spawnPulse(mc.player.position());
                    pulseQueue--;
                    pulseTimer = 10;
                } else {
                    pulseTimer--;
                }
            }

            fadeAmountVal = Math.min(1.0f, fadeAmountVal + FADE_STEP);
            slideOffsetYVal = Math.min(SLIDE_VISIBLE_Y, slideOffsetYVal + SLIDE_STEP);
            if (bootTimerVal > 0) bootTimerVal -= 0.05f;
            sweepAngleVal += 0.1f;
            if (sweepAngleVal > (float) Math.PI * 2) {
                sweepAngleVal -= (float) Math.PI * 2;
                prevSweepAngleVal -= (float) Math.PI * 2;
            }

            ItemStack auraReader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(mc.player);
            if (!auraReader.isEmpty() && auraReader.getItem() instanceof AuraReaderItem) {
                if (AuraReaderCharge.get(auraReader) <= 0) {
                    isActive = false;
                }
            }

            if (beepTimer > 0) {
                beepTimer--;
            } else if (maxIntensityVal > 0) {
                int delay = (int) Mth.lerp(maxIntensityVal, 60, 5);
                float pitch = Mth.lerp(maxIntensityVal, 0.8f, 1.5f);
                mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.AURA_SCANNER_BEEP.get(), SoundSource.PLAYERS, 0.3f * ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraScannerBeepVolume(), pitch);
                beepTimer = delay;
            }
        } else {
            if (pokedexTicksInUse > 0) {
                AuraReaderManager.POKEDEX_USAGE_CONTEXT.stopUsing(pokedexTicksInUse, null);
                pokedexTicksInUse = 0;
            }
            fadeAmountVal = Math.max(0.0f, fadeAmountVal - FADE_STEP);
            slideOffsetYVal = Math.max(SLIDE_HIDDEN_Y, slideOffsetYVal - SLIDE_STEP);
            beepTimer = 0;
            maxIntensityVal = 0;
            pulseQueue = 0;
            pulseTimer = 0;
            AuraPulseRenderer.clearPulses();
            onDeactivate();
        }
    }

    private void tickActiveMode(Minecraft mc) {
        AuraReaderManager.AuraScannerMode currentMode = AuraReaderManager.currentMode;
        if (currentMode != AuraReaderManager.AuraScannerMode.AURA_READER) {
            AuraReaderManager.currentMode = AuraReaderManager.AuraScannerMode.AURA_READER;
            currentMode = AuraReaderManager.AuraScannerMode.AURA_READER;
        }

        if (currentMode == AuraReaderManager.AuraScannerMode.AURA_READER) {
            tickAuraReader(mc);
        } else if (currentMode == AuraReaderManager.AuraScannerMode.DOWSING_MACHINE) {
            tickDowsingMachine(mc);
        } else if (currentMode == AuraReaderManager.AuraScannerMode.POKEDEX_SCANNER) {
            tickPokedexScanner(mc);
        }
    }

    private void tickPokedexScanner(Minecraft mc) {
        pokedexTicksInUse++;
        AuraReaderManager.POKEDEX_USAGE_CONTEXT.useTick(mc.player, pokedexTicksInUse, true);
    }

    private void tickDowsingMachine(Minecraft mc) {
        if (dowsingTargetPosObj != null) {
            double dist = Math.sqrt(dowsingTargetPosObj.distSqr(mc.player.blockPosition()));
            int range = 32;
            maxIntensityVal = Math.max(maxIntensityVal, (float) Math.max(0, 1.0 - (dist / range)));
        }
    }

    private void tickAuraReader(Minecraft mc) {
        // Legacy detection disabled: only provide minimal intensity feedback using active trail hotspot
        if (!LEGACY_DETECTION_DISABLED) return; // safeguard for future toggles

        BlockPos hs = TrailClientState.INSTANCE.getHotspot();
        if (hs != null) {
            double dist = Math.sqrt(hs.distSqr(mc.player.blockPosition()));
            float range = 24.0f; // client-side indicator only
            float intensity = (float) Math.max(0, 1.0 - (dist / range));
            maxIntensityVal = Math.max(maxIntensityVal, intensity);
        }

        // Sync calibration state from TrailClientState
        tickCalibrationHud();

        // Sync node event state from TrailClientState
        tickNodeEventHud();

        // Sync manifestation buildup state
        tickManifestationHud();

        // Sync grade flash state
        tickGradeFlashHud();

        // Clear any legacy state to avoid HUD artifacts
        detectedShadows.clear();
        pendingResponses.clear();
        detectedMeteoroids.clear();
        pendingMeteoroidResponses.clear();
        currentSignals.clear();
        lockedTargetObj = null;
        isAcquisitionMode = false;
    }

    private void tickCalibrationHud() {
        var tcs = TrailClientState.INSTANCE;
        boolean wasActive = calibrationActiveVal;
        calibrationActiveVal = tcs.getCalibrationActive();
        calibrationVariantVal = tcs.getCalibrationVariant();

        if (calibrationActiveVal) {
            calibrationSequenceVal = tcs.getCalibrationSequence();
            calibrationSequenceLengthVal = calibrationSequenceVal.size();
            calibrationCurrentIndexVal = tcs.getCalibrationCurrentIndex();
            calibrationProgressVal = calibrationSequenceLengthVal > 0
                    ? (float) calibrationCurrentIndexVal / calibrationSequenceLengthVal : 0f;
            int timeLimit = tcs.getCalibrationTimeLimitTicks();
            int elapsed = tcs.getCalibrationElapsedTicks();
            calibrationTimeRemainingVal = timeLimit > 0 ? Math.max(0f, 1.0f - (float) elapsed / timeLimit) : 1.0f;
        } else {
            if (wasActive) {
                // Just finished — show grade
                calibrationGradeVal = tcs.getCalibrationGrade();
                if (calibrationGradeVal != null) {
                    calibrationGradeDisplayTicks = 40; // 2 seconds
                }
            }
            calibrationProgressVal = 0f;
            calibrationTimeRemainingVal = 1f;
        }

        // Flash timers
        if (calibrationLastCorrectFlash > 0) calibrationLastCorrectFlash--;
        if (calibrationLastWrongFlash > 0) calibrationLastWrongFlash--;
        if (calibrationInputCooldown > 0) calibrationInputCooldown--;
        if (calibrationGradeDisplayTicks > 0) calibrationGradeDisplayTicks--;
    }

    private void tickNodeEventHud() {
        var tcs = TrailClientState.INSTANCE;
        nodeEventActiveVal = tcs.getNodeEventActive();
        if (nodeEventActiveVal) {
            var evtType = tcs.getNodeEventType();
            nodeEventTypeVal = evtType != null ? evtType.toId() : -1;
            nodeEventPhaseVal = tcs.getNodeEventPhase();
            nodeEventTicksElapsedVal = tcs.getNodeEventTicksElapsed();
            nodeEventMaxTicksVal = tcs.getNodeEventMaxTicks();
            nodeEventClueCountVal = tcs.getNodeEventCluePositions().size();
            nodeEventWrongGuessesVal = tcs.getNodeEventWrongGuesses();
            nodeEventRequiredValidCountVal = tcs.getNodeEventRequiredValidCount();
            nodeEventFoundValidCountVal = tcs.getNodeEventFoundValidCount();
            nodeEventSelectedClueIndicesVal = tcs.getNodeEventSelectedClueIndices();
            nodeEventSearchSignalVal = tcs.getNodeEventSearchSignalStrength();
            nodeEventSearchRadiusVal = tcs.getNodeEventSearchRadius();
            nodeEventWildTimerVal = tcs.getNodeEventWildResolveTimer();
            nodeEventWildsResolvedVal = tcs.getNodeEventWildsResolved();
            nodeEventSignalBuildupVal = tcs.getNodeEventSignalBuildup();
            nodeEventProvocationRequiredVal = tcs.getNodeEventProvocationRequiredTicks();
        } else {
            nodeEventTypeVal = -1;
        }
    }

    private void tickManifestationHud() {
        var tcs = TrailClientState.INSTANCE;
        manifestationActiveVal = tcs.getManifestationActive();
        manifestationPhaseVal = tcs.getManifestationPhase();
        manifestationProgressVal = tcs.getManifestationProgress();
        manifestationActive.set(manifestationActiveVal);
        manifestationPhase.set(manifestationPhaseVal);
        manifestationProgress.set(manifestationProgressVal);
        signalBlackoutVal = tcs.getSignalBlackedOut();
        signalBlackout.set(signalBlackoutVal);
    }

    private void tickGradeFlashHud() {
        var tcs = TrailClientState.INSTANCE;
        String text = tcs.getLastGradeText();
        int ticks = tcs.getGradeFlashTicks();
        if (text != null && ticks > 0) {
            gradeFlashTextVal = text;
            gradeFlashTicksVal = ticks;
            gradeFlashColorVal = tcs.getGradeFlashColor();
            gradeFlashText.set(text);
            gradeFlashTicks.set(ticks);
            gradeFlashColor.set(gradeFlashColorVal);
        } else {
            if (gradeFlashTicksVal > 0) gradeFlashTicksVal = 0;
            gradeFlashTextVal = null;
            gradeFlashText.set(null);
            gradeFlashTicks.set(0);
        }
    }

    public void onDeactivate() {
        detectedShadows.clear();
        pendingResponses.clear();
        detectedMeteoroids.clear();
        pendingMeteoroidResponses.clear();
        currentSignals.clear();
        lockedTargetObj = null;
        exitAcquisitionMode();
        scanningProgressVal = 0.0f;
        dowsingTargetPosObj = null;
        hotspotScanTicks = 0;
        hotspotScanningActive = false;
    }

    public boolean handleShiftScroll(double scrollY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;

        boolean shiftDown = mc.options.keyShift.isDown();
        AuraReaderManager.AuraScannerMode currentMode = AuraReaderManager.currentMode;

        if (isActive && currentMode == AuraReaderManager.AuraScannerMode.DOWSING_MACHINE && shiftDown) {
            int dir = scrollY > 0 ? -1 : 1;
            selectedDowsingMaterialIndex = (selectedDowsingMaterialIndex + dir + DOWSING_MATERIALS.size()) % DOWSING_MATERIALS.size();
            return true;
        }

        if (!isActive || !isAcquisitionMode || lockedTargetObj != null || currentSignals.isEmpty() || !shiftDown) {
            return false;
        }

        int dir = scrollY > 0.0 ? 1 : (scrollY < 0.0 ? -1 : 0);
        if (dir == 0) return false;

        if (dir > 0) {
            selectedSignalIndex = (selectedSignalIndex + 1) % currentSignals.size();
        } else {
            selectedSignalIndex = (selectedSignalIndex - 1 + currentSignals.size()) % currentSignals.size();
        }

        mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get(), 0.08f * ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraScannerBeepVolume(), 1.3f);
        return true;
    }

    private void enterAcquisitionMode() {
        isAcquisitionMode = true;
        acquisitionTimer = ACQUISITION_WINDOW_TICKS;
        selectedSignalIndex = 0;
    }

    private void exitAcquisitionMode() {
        isAcquisitionMode = false;
        selectedSignalIndex = 0;
    }

    public void triggerLocalPulse(Minecraft mc) {
        pulseQueue = 3;
        pulseTimer = 0;
        pulseCooldown = PULSE_COOLDOWN_TICKS;
        mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get(), ShadowedHeartsConfigs.getInstance().getClientConfig().soundConfig().auraScannerBeepVolume(), 1.0f);
    }

    public boolean handleInput(Minecraft mc) {
        // Evidence scan now handled as hold-to-scan in tickHotspotScan()

        // If a node event is active, intercept relevant inputs
        if (nodeEventActiveVal) {
            return handleNodeEventInput(mc);
        }

        // If calibration is active, intercept WASD for directional inputs
        if (calibrationActiveVal) {
            return handleCalibrationInput(mc);
        }

        AuraReaderManager.AuraScannerMode currentMode = AuraReaderManager.currentMode;
        if (currentMode == AuraReaderManager.AuraScannerMode.AURA_READER) {
            // Pulse now handled by right-clicking Shadow Signal Data item
        } else if (currentMode == AuraReaderManager.AuraScannerMode.DOWSING_MACHINE) {
            // Dowsing pulse formerly on 'B' disabled for consistency
        }
        return false;
    }

    /**
     * Intercepts number keys during Evidence Interpretation node events
     * to select clues. Keys 1-4 map to clue indices 0-3.
     */
    private boolean handleNodeEventInput(Minecraft mc) {
        // Only Evidence Interpretation uses direct input (number keys for clue selection)
        if (nodeEventTypeVal == NodeEventType.EVIDENCE_INTERPRETATION.toId()) {
            // Check number keys 1-6 for up to 6 clue positions
            for (int i = 0; i < 6; i++) {
                if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                        mc.getWindow().getWindow(),
                        com.mojang.blaze3d.platform.InputConstants.KEY_1 + i)) {
                    if (i < nodeEventClueCountVal && !nodeEventSelectedClueIndicesVal.contains(i)) {
                        ShadowedHeartsNetwork.sendToServer(new NodeEventInputC2SPacket(0, i));
                        mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get(), 0.5f, 1.2f);
                        return true;
                    }
                }
            }
        }
        // Other event types (search, wild, provocation) don't need direct input —
        // they resolve via proximity/timer on the server
        return false;
    }

    /**
     * Intercepts Shift+WASD or Arrow keys during calibration and sends directional inputs to the server.
     * Arrow keys are always accepted. WASD is only accepted when Shift is held to avoid moving the player.
     */
    private boolean handleCalibrationInput(Minecraft mc) {
        CalibrationSequence.Direction dir = null;
        long window = mc.getWindow().getWindow();
        boolean shiftDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);

        // Arrow keys — always accepted during calibration (with debounce)
        if (calibrationInputCooldown > 0) return false;
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                com.mojang.blaze3d.platform.InputConstants.KEY_UP)) {
            dir = CalibrationSequence.Direction.UP;
        } else if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                com.mojang.blaze3d.platform.InputConstants.KEY_DOWN)) {
            dir = CalibrationSequence.Direction.DOWN;
        } else if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                com.mojang.blaze3d.platform.InputConstants.KEY_LEFT)) {
            dir = CalibrationSequence.Direction.LEFT;
        } else if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT)) {
            dir = CalibrationSequence.Direction.RIGHT;
        }
        // Shift+WASD — only accepted when shift is held
        else if (shiftDown) {
            if (mc.options.keyUp.consumeClick()) {
                dir = CalibrationSequence.Direction.UP;
            } else if (mc.options.keyDown.consumeClick()) {
                dir = CalibrationSequence.Direction.DOWN;
            } else if (mc.options.keyLeft.consumeClick()) {
                dir = CalibrationSequence.Direction.LEFT;
            } else if (mc.options.keyRight.consumeClick()) {
                dir = CalibrationSequence.Direction.RIGHT;
            }
        }

        if (dir != null) {
            calibrationInputCooldown = 4; // debounce: ~200ms between arrow key inputs
            ShadowedHeartsNetwork.sendToServer(new CalibrationInputC2SPacket(dir));
            // Optimistic client-side feedback: check against local sequence
            if (calibrationCurrentIndexVal < calibrationSequenceVal.size()) {
                CalibrationSequence.Direction expected = calibrationSequenceVal.get(calibrationCurrentIndexVal);
                // Handle REVERSE_INTERFERENCE variant inversion
                if (calibrationVariantVal == CalibrationSequence.Variant.REVERSE_INTERFERENCE) {
                    int len = calibrationSequenceVal.size();
                    int start = len / 3;
                    int end = (len * 2) / 3;
                    if (calibrationCurrentIndexVal >= start && calibrationCurrentIndexVal <= end) {
                        expected = invertDirection(expected);
                    }
                }
                if (dir == expected) {
                    calibrationLastCorrectFlash = 6;
                    mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get(), 0.4f, 1.4f + calibrationProgressVal * 0.4f);
                } else {
                    calibrationLastWrongFlash = 8;
                    mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get(), 0.5f, 0.6f);
                }
            }
            return true;
        }
        return false;
    }

    private static CalibrationSequence.Direction invertDirection(CalibrationSequence.Direction d) {
        return switch (d) {
            case UP -> CalibrationSequence.Direction.DOWN;
            case DOWN -> CalibrationSequence.Direction.UP;
            case LEFT -> CalibrationSequence.Direction.RIGHT;
            case RIGHT -> CalibrationSequence.Direction.LEFT;
        };
    }

    private void tickHotspotScan(Minecraft mc) {
        if (mc.player == null) return;
        BlockPos hs = TrailClientState.INSTANCE.getHotspot();
        boolean withinHotspot = false;
        if (hs != null) {
            BlockPos pp = mc.player.blockPosition();
            double dx = (pp.getX() + 0.5) - (hs.getX() + 0.5);
            double dy = (pp.getY() + 0.5) - (hs.getY() + 0.5);
            double dz = (pp.getZ() + 0.5) - (hs.getZ() + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;
            withinHotspot = distSq <= (HOTSPOT_RADIUS * HOTSPOT_RADIUS);
        }

        if (withinHotspot && scannerCooldown <= 0) {
            hotspotScanningActive = true;
            hotspotScanTicks++;
            // Soft haptic cue via intensity
            maxIntensityVal = Math.max(maxIntensityVal, Math.min(1.0f, hotspotScanTicks / (float) HOTSPOT_SCAN_REQUIRED_TICKS));

            if (hotspotScanTicks >= HOTSPOT_SCAN_REQUIRED_TICKS) {
                ShadowedHeartsNetwork.sendToServer(new EvidenceScanCompleteC2SPacket());
                mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get(), 0.8f, 1.3f);
                scannerCooldown = SCANNER_COOLDOWN_TICKS;
                hotspotScanTicks = 0;
                hotspotScanningActive = false;
            }
        } else {
            hotspotScanningActive = false;
            hotspotScanTicks = 0;
        }
    }

    private void doDowsingPulse(Minecraft mc) {
        net.minecraft.world.level.block.Block targetBlock = DOWSING_MATERIALS.get(selectedDowsingMaterialIndex);
        BlockPos playerPos = mc.player.blockPosition();
        int range = 32;
        BlockPos closest = null;
        double minDistSq = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-range, -range, -range), playerPos.offset(range, range, range))) {
            if (mc.level.getBlockState(pos).is(targetBlock)) {
                double distSq = pos.distSqr(playerPos);
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    closest = pos.immutable();
                }
            }
        }

        if (closest != null) {
            dowsingTargetPosObj = closest;
            mc.player.playSound(ModSounds.AURA_SCANNER_BEEP.get(), 1.0f, 1.5f);
            AuraPulseRenderer.spawnPulse(mc.player.position(), 0.0f, 1.0f, 1.0f, range);
        } else {
            dowsingTargetPosObj = null;
            mc.player.playSound(ModSounds.AURA_READER_UNEQUIP.get(), 1.0f, 0.5f);
        }
    }

    public void update(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        updateAnimations(partialTick);

        mode.set(AuraReaderManager.currentMode);
        active.set(isActive);
        fadeAmount.set(Mth.lerp(partialTick, prevFadeAmountVal, fadeAmountVal));
        slideOffsetY.set(Mth.lerp(partialTick, prevSlideOffsetYVal, slideOffsetYVal));
        bootTimer.set(Mth.lerp(partialTick, prevBootTimerVal, bootTimerVal));
        glitchTimer.set(Mth.lerp(partialTick, prevGlitchTimerVal, glitchTimerVal));
        sweepAngle.set(Mth.lerp(partialTick, prevSweepAngleVal, sweepAngleVal));
        innerRingRotation.set(innerRingRotationVal);
        operationalTempC.set(Mth.lerp(partialTick, prevOperationalTempCVal, operationalTempCVal));
        modeMenuOpen.set(isModeMenuOpen);
        modeMenuAlpha.set(Mth.lerp(partialTick, prevModeMenuAlphaVal, modeMenuAlphaVal));

        ItemStack reader = SnagAccessoryBridgeHolder.INSTANCE.getAuraReaderStack(mc.player);
        if (!reader.isEmpty()) {
            charge.set(AuraReaderCharge.get(reader));
        }

        acquisitionMode.set(isAcquisitionMode);
        lockedTarget.set(lockedTargetObj);
        scanningProgress.set(Mth.lerp(partialTick, prevScanningProgressVal, scanningProgressVal));

        updateSignalStrength(mc);
        updateArrowDirection(mc, partialTick);
        updateDirectionalPointers(mc, partialTick);

        maxIntensity.set(maxIntensityVal);

        // Trail scan HUD values
        hotspotScanning.set(hotspotScanningActive);
        hotspotScanProgress.set(Mth.clamp(hotspotScanTicks / (float) HOTSPOT_SCAN_REQUIRED_TICKS, 0.0f, 1.0f));

        // Calibration HUD values
        calibrationActive.set(calibrationActiveVal);
        calibrationProgress.set(calibrationProgressVal);
        calibrationTimeRemaining.set(calibrationTimeRemainingVal);
        calibrationCurrentIndex.set(calibrationCurrentIndexVal);
        calibrationSequenceLength.set(calibrationSequenceLengthVal);
        calibrationDirections.set(calibrationSequenceVal);
        calibrationVariantId.set(calibrationVariantVal.toId());
        calibrationCorrectFlash.set(calibrationLastCorrectFlash > 0);
        calibrationWrongFlash.set(calibrationLastWrongFlash > 0);
        calibrationGrade.set(calibrationGradeVal);
        calibrationGradeDisplay.set(calibrationGradeDisplayTicks);

        // Hunt tension & quality
        huntTension.set(TrailClientState.INSTANCE.getTension());
        huntTrailQuality.set(TrailClientState.INSTANCE.getTrailQuality());

        // Node event HUD values
        nodeEventActive.set(nodeEventActiveVal);
        nodeEventType.set(nodeEventTypeVal);
        nodeEventPhase.set(nodeEventPhaseVal);
        nodeEventTimeRemaining.set(nodeEventMaxTicksVal > 0
                ? Math.max(0f, 1.0f - (float) nodeEventTicksElapsedVal / nodeEventMaxTicksVal) : 1f);
        nodeEventClueCount.set(nodeEventClueCountVal);
        nodeEventWrongGuesses.set(nodeEventWrongGuessesVal);
        nodeEventRequiredValidCount.set(nodeEventRequiredValidCountVal);
        nodeEventFoundValidCount.set(nodeEventFoundValidCountVal);
        nodeEventSelectedClueIndices.set(nodeEventSelectedClueIndicesVal);
        nodeEventSearchSignal.set(nodeEventSearchSignalVal);
        nodeEventWildsResolved.set(nodeEventWildsResolvedVal);
        nodeEventSignalBuildup.set(nodeEventSignalBuildupVal);

        dowsingTargetPos.set(dowsingTargetPosObj);
        dowsingMaterialIndex.set(selectedDowsingMaterialIndex);
        if (selectedDowsingMaterialIndex < DOWSING_MATERIALS.size()) {
            dowsingMaterialName.set(DOWSING_MATERIALS.get(selectedDowsingMaterialIndex).getName().getString());
        }
        updateDowsingInfo(mc, partialTick);
        pokedexTicksInUseValue.set(pokedexTicksInUse);
        pokedexUsageContext.set(AuraReaderManager.POKEDEX_USAGE_CONTEXT);
    }

    private void updateAnimations(float tickDelta) {
        if (!isActive) return;
        float updateInterval = (tickDelta / 20.0F) * RENDER_UPDATES_PER_SECOND;
        usageIntervals += updateInterval;

        PokedexUsageContext usageContext = AuraReaderManager.POKEDEX_USAGE_CONTEXT;
        if (usageContext.getScanningGuiOpen() && usageContext.getViewInfoTicks() < PokedexUsageContext.VIEW_INFO_BUFFER_TICKS) {
            if (usageContext.getScannableEntityInFocus() != null) {
                innerRingRotationVal = (innerRingRotationVal + (updateInterval * 10.0F)) % 360;
            } else {
                innerRingRotationVal = (innerRingRotationVal + updateInterval) % 360;
            }
        } else {
            innerRingRotationVal = (innerRingRotationVal + updateInterval) % 360;
        }
    }

    private void updateDowsingInfo(Minecraft mc, float partialTick) {
        if (dowsingTargetPosObj == null) {
            dowsingDistance.set(0f);
            return;
        }

        BlockPos target = dowsingTargetPosObj;
        net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 camPos = cam.getPosition().add(mc.player.getLookAngle().normalize().scale(1.25f));

        org.joml.Vector3f dirWorld = new org.joml.Vector3f(
                (float) (target.getX() + 0.5 - camPos.x),
                (float) (target.getY() + 0.5 - camPos.y),
                (float) (target.getZ() + 0.5 - camPos.z)
        );

        dowsingDistance.set(dirWorld.length());
        dirWorld.normalize();

        org.joml.Vector3f forward = cam.getLookVector().normalize();
        org.joml.Vector3f camUp = cam.getUpVector().normalize();
        org.joml.Vector3f camRight = cam.getLeftVector().normalize();

        float x = dirWorld.dot(camRight);
        float y = dirWorld.dot(camUp);
        float z = dirWorld.dot(forward);

        org.joml.Vector3f dirCamera = new org.joml.Vector3f(x, y, z).normalize();
        dowsingArrowRotation.set(new org.joml.Quaternionf().rotationTo(dirCamera, new org.joml.Vector3f(0, 1, 0)));
    }

    private void updateDirectionalPointers(Minecraft mc, float partialTick) {
        // Legacy directional pointers disabled for v1 trail-focused experience
        directionalPointers.set(Collections.emptyList());
    }

    private void updateArrowDirection(Minecraft mc, float partialTick) {
        if (mode.get() == AuraReaderManager.AuraScannerMode.DOWSING_MACHINE && dowsingTargetPosObj != null) {
            BlockPos target = dowsingTargetPosObj;
            net.minecraft.world.phys.Vec3 targetCenter = new net.minecraft.world.phys.Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            net.minecraft.world.phys.Vec3 dirToTarget = targetCenter.subtract(mc.player.getEyePosition(partialTick)).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(dirToTarget.z, dirToTarget.x)) - 90;
            float playerYaw = mc.player.getViewYRot(partialTick);
            float angle = Mth.wrapDegrees(yaw - playerYaw);
            arrowDirectionDeg.set(angle);
        } else if (lockedTargetObj != null) {
            SignalTarget target = lockedTargetObj;
            net.minecraft.world.phys.Vec3 targetPos;
            if (target.type == TargetType.POKEMON) {
                Entity e = null;
                for (Entity cand : mc.level.entitiesForRendering()) {
                    if (cand.getUUID().equals(target.pokemonId)) {
                        e = cand;
                        break;
                    }
                }
                if (e != null) targetPos = e.position();
                else targetPos = mc.player.position();
            } else {
                targetPos = new net.minecraft.world.phys.Vec3(target.meteoroidPos.getX() + 0.5, target.meteoroidPos.getY() + 0.5, target.meteoroidPos.getZ() + 0.5);
            }
            net.minecraft.world.phys.Vec3 dirToTarget = targetPos.subtract(mc.player.getEyePosition(partialTick)).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(dirToTarget.z, dirToTarget.x)) - 90;
            float playerYaw = mc.player.getViewYRot(partialTick);
            float angle = Mth.wrapDegrees(yaw - playerYaw);
            arrowDirectionDeg.set(angle);
        } else {
            arrowDirectionDeg.set(0f);
        }
    }

    private void updateSignalStrength(Minecraft mc) {
        SignalTarget target = lockedTargetObj;
        if (target == null && isAcquisitionMode && !currentSignals.isEmpty()) {
            int idx = Math.max(0, Math.min(selectedSignalIndex, currentSignals.size() - 1));
            target = currentSignals.get(idx);
        } else if (target == null && !currentSignals.isEmpty()) {
            target = currentSignals.stream().max(Comparator.comparingDouble(s -> s.strength)).orElse(null);
        }

        if (target == null || LEGACY_DETECTION_DISABLED) {
            signalStrength.set(0f);
            interference.set(0f);
            signalLabel.set("NO SIGNAL");
            signalColor.set(0xAAAAAA);
            infoLine.set(null);
            return;
        }

        String label = "NO SIGNAL";
        int color = 0xAAAAAA;
        if (maxIntensityVal > 0.8) {
            label = "STRONG SHADOW PRESENCE";
            color = 0xA330FF;
        } else if (maxIntensityVal > 0.5) {
            label = "MODERATE DISTURBANCE";
            color = 0x00A3FF;
        } else if (maxIntensityVal > 0) {
            label = "WEAK SIGNAL";
            color = 0x00FFFF;
        }
        signalLabel.set(label);
        signalColor.set(color);

        float maxRange = (target.type == TargetType.METEOROID)
                ? ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerMeteoroidRange()
                : ShadowedHeartsConfigs.getInstance().getShadowConfig().auraScannerShadowRange();

        double dynamicDistance = -1.0;
        String typeName = "";
        if (target.type == TargetType.POKEMON) {
            typeName = "SHADOW POKÉMON";
            for (Entity cand : mc.level.entitiesForRendering()) {
                if (cand.getUUID().equals(target.pokemonId)) {
                    dynamicDistance = cand.distanceTo(mc.player);
                    break;
                }
            }
        } else if (target.meteoroidPos != null) {
            typeName = "SHADOW METEOROID";
            dynamicDistance = Math.sqrt(target.meteoroidPos.distSqr(mc.player.blockPosition()));
        }

        float base = target.strength;
        if (dynamicDistance >= 0.0) {
            base = (float) Math.max(0.0, 1.0 - (dynamicDistance / maxRange));
            infoLine.set(String.format("%s: %s  |  %.0fm", lockedTargetObj != null ? "LOCKED" : "SELECT", typeName, dynamicDistance));
        } else if (target.distance > 0.0) {
            base = (float) Math.max(0.0, 1.0 - (target.distance / maxRange));
            infoLine.set(String.format("%s: %s", lockedTargetObj != null ? "LOCKED" : "SELECT", typeName));
        } else {
            infoLine.set(String.format("%s: %s", lockedTargetObj != null ? "LOCKED" : "SELECT", typeName));
        }
        signalStrength.set(Mth.clamp(base, 0.0f, 1.0f));
        interference.set(Mth.clamp(1.0f - base, 0.0f, 1.0f));
    }
}
