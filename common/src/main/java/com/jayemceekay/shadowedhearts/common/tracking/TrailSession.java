package com.jayemceekay.shadowedhearts.common.tracking;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-player trail progression session with a state machine, tier metadata,
 * tension tracking, hunt layout, and calibration support.
 */
public class TrailSession {
    public enum State {
        IDLE,
        TRAIL_ACTIVE,
        EVIDENCE_LOCATED,
        EVIDENCE_SCAN,
        CALIBRATION_ACTIVE,
        NODE_EVENT_ACTIVE,
        SEGMENT_REVEALED,
        FINAL_LOCK,
        MANIFESTATION_BUILDUP,
        ENCOUNTER_ACTIVE
    }

    private final UUID playerId;
    private final List<TrailNode> nodes = new ArrayList<>();
    private int index = 0; // index of next hotspot
    private EvidenceHotspot currentHotspot;
    private State state = State.IDLE;

    // ── Tier & Hunt Layout ──
    private ShadowSignalTier tier = ShadowSignalTier.FAINT;
    private HuntLayout huntLayout;
    private long huntSeed;

    // ── Tension system ──
    private float tension = 0.0f;

    // ── Calibration ──
    private CalibrationSequence currentCalibration;
    private CalibrationSequence.Grade lastCalibrationGrade;

    // ── Scan timing ──
    private int scanTicksAccumulated = 0;
    private int scanTicksRequired = 40; // default ~2 seconds at 20 TPS

    // ── Trail quality (affected by calibration performance) ──
    private float trailQuality = 1.0f; // 1.0 = clean, 0.0 = maximum noise

    // ── Segment reveal tracking ──
    private int revealedSegmentCount = 0;

    // ── Active node event ──
    private NodeEventState activeNodeEvent;

    // ── Manifestation buildup ──
    private int manifestationBuildupTicks = 0;
    private int manifestationBuildupDuration = 80; // ~4 seconds at 20 TPS
    private int manifestationPhase = 0; // 0=not active, 1=signal spike, 2=convergence, 3=distortion crescendo, 4=ready

    // ── Outcome branching ──
    private float nextSearchRadiusModifier = 1.0f; // <1 = easier search, >1 = harder
    private int signalBlackoutTicksRemaining = 0;
    private boolean pendingWildConsequence = false;
    private String lastNodeGrade = null; // "PERFECT", "STANDARD", "SLOPPY", "FAILED"

    // ── Species trait ──
    private ShadowSpeciesTrait speciesTrait = ShadowSpeciesTrait.NEUTRAL;

    // ── False trails ──
    private final List<List<BlockPos>> falseTrails = new ArrayList<>();
    private int falseTrailCooldownTicks = 0;

    public TrailSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public List<TrailNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<TrailNode> nodes) {
        this.nodes.clear();
        if (nodes != null) this.nodes.addAll(nodes);
        this.index = 0;
        this.currentHotspot = null;
        this.state = this.nodes.isEmpty() ? State.IDLE : State.TRAIL_ACTIVE;
        this.scanTicksAccumulated = 0;
        this.revealedSegmentCount = 0;
    }

    /**
     * Initialize the session with tier metadata and a hunt layout.
     */
    public void initHunt(ShadowSignalTier tier, long seed) {
        this.tier = tier;
        this.huntSeed = seed;
        this.huntLayout = HuntLayout.generate(tier, seed, speciesTrait);
        this.scanTicksRequired = tier.getScanTicksRequired();
        this.tension = 0.0f;
        this.trailQuality = 1.0f;
        this.lastCalibrationGrade = null;
        this.currentCalibration = null;
        this.falseTrails.clear();
        this.falseTrailCooldownTicks = 0;
    }

    // ── Species Trait accessors ──
    public ShadowSpeciesTrait getSpeciesTrait() { return speciesTrait; }
    public void setSpeciesTrait(ShadowSpeciesTrait trait) {
        this.speciesTrait = trait != null ? trait : ShadowSpeciesTrait.NEUTRAL;
    }

    // ── False Trail accessors ──
    public List<List<BlockPos>> getFalseTrails() { return falseTrails; }

    public void addFalseTrail(List<BlockPos> trail) {
        falseTrails.add(trail);
    }

    public void clearFalseTrails() {
        falseTrails.clear();
    }

    public int getFalseTrailCooldownTicks() { return falseTrailCooldownTicks; }
    public void setFalseTrailCooldownTicks(int ticks) { this.falseTrailCooldownTicks = ticks; }
    public void tickFalseTrailCooldown() {
        if (falseTrailCooldownTicks > 0) falseTrailCooldownTicks--;
        if (falseTrailCooldownTicks <= 0) falseTrails.clear();
    }

    public int getIndex() {
        return index;
    }

    public EvidenceHotspot getCurrentHotspot() {
        return currentHotspot;
    }

    public boolean hasMore() {
        return index < nodes.size();
    }

    public State getState() {
        return state;
    }

    // ── Tier & Layout ──

    public ShadowSignalTier getTier() { return tier; }
    public HuntLayout getHuntLayout() { return huntLayout; }
    public long getHuntSeed() { return huntSeed; }

    /**
     * Get the event type for the current node index.
     */
    public NodeEventType getCurrentEventType() {
        if (huntLayout != null && index < huntLayout.getTotalNodes()) {
            return huntLayout.getEventAt(index);
        }
        // Fallback: if no layout, use the node's own event type
        if (index < nodes.size()) {
            return nodes.get(index).eventType();
        }
        return NodeEventType.FINAL_MANIFESTATION;
    }

    // ── Tension ──

    public float getTension() { return tension; }

    public void addTension(float amount) {
        this.tension = Math.min(1.0f, Math.max(0.0f, this.tension + amount));
    }

    /**
     * Escalate tension based on completing a node. Uses tier-specific rate.
     */
    public void escalateTension() {
        addTension(tier.getTensionPerNode());
    }

    // ── Trail Quality ──

    public float getTrailQuality() { return trailQuality; }

    public void setTrailQuality(float quality) {
        this.trailQuality = Math.min(1.0f, Math.max(0.0f, quality));
    }

    /**
     * Adjust trail quality based on calibration grade.
     */
    public void applyCalibrationGrade(CalibrationSequence.Grade grade) {
        this.lastCalibrationGrade = grade;
        switch (grade) {
            case PERFECT -> setTrailQuality(Math.min(1.0f, trailQuality + 0.15f));
            case STANDARD -> { /* no change */ }
            case SLOPPY -> setTrailQuality(trailQuality - 0.2f);
            case FAILED -> {
                setTrailQuality(trailQuality - 0.35f);
                addTension(0.1f); // failure increases tension
            }
        }
    }

    public CalibrationSequence.Grade getLastCalibrationGrade() { return lastCalibrationGrade; }

    // ── Scan timing ──

    public void setScanTicksRequired(int ticks) {
        this.scanTicksRequired = Math.max(1, ticks);
    }

    public int getScanTicksRequired() {
        return scanTicksRequired;
    }

    public int getScanTicksAccumulated() {
        return scanTicksAccumulated;
    }

    public void resetScanTicks() {
        this.scanTicksAccumulated = 0;
    }

    public void tickScan() {
        if (state == State.EVIDENCE_SCAN) {
            this.scanTicksAccumulated++;
        }
    }

    // ── Calibration ──

    public CalibrationSequence getCurrentCalibration() { return currentCalibration; }

    /**
     * Begin a calibration event at the current node.
     * Generates a sequence based on the tier and current tension.
     */
    public CalibrationSequence beginCalibration() {
        if (state != State.EVIDENCE_LOCATED && state != State.EVIDENCE_SCAN) return null;
        this.state = State.CALIBRATION_ACTIVE;
        this.currentCalibration = CalibrationSequence.generate(tier, tension, new java.util.Random(huntSeed + index));
        return this.currentCalibration;
    }

    /**
     * Tick the active calibration sequence.
     */
    public void tickCalibration() {
        if (state == State.CALIBRATION_ACTIVE && currentCalibration != null) {
            currentCalibration.tick();
            if (currentCalibration.isFailed()) {
                applyCalibrationGrade(CalibrationSequence.Grade.FAILED);
                // Reset for retry — generate a new sequence
                this.currentCalibration = CalibrationSequence.generate(tier, tension, new java.util.Random(System.nanoTime()));
            }
        }
    }

    /**
     * Submit a calibration input from the player.
     * Returns true if the input was correct.
     */
    public boolean submitCalibrationInput(CalibrationSequence.Direction input) {
        if (state != State.CALIBRATION_ACTIVE || currentCalibration == null) return false;
        boolean correct = currentCalibration.submitInput(input);
        if (currentCalibration.isCompleted()) {
            CalibrationSequence.Grade grade = currentCalibration.evaluate();
            applyCalibrationGrade(grade);
            // Calibration complete — mark node as scanned
            markScanned();
        }
        return correct;
    }

    // ── Segment reveal ──

    public int getRevealedSegmentCount() { return revealedSegmentCount; }

    // ── State transitions ──

    public EvidenceHotspot advanceToNextHotspot(float radius) {
        if (!hasMore()) {
            this.currentHotspot = null;
            this.state = State.FINAL_LOCK;
            return null;
        }
        BlockPos pos = nodes.get(index).pos();
        this.currentHotspot = new EvidenceHotspot(pos, radius);
        this.state = State.EVIDENCE_LOCATED;
        this.scanTicksAccumulated = 0;
        return this.currentHotspot;
    }

    public void beginScan() {
        if (state == State.EVIDENCE_LOCATED && currentHotspot != null) {
            NodeEventType eventType = getCurrentEventType();
            if (eventType.requiresCalibration()) {
                beginCalibration();
            } else if (eventType.isInteractive()) {
                // Interactive events are initiated via beginNodeEvent()
                this.state = State.EVIDENCE_SCAN;
                this.scanTicksAccumulated = 0;
            } else {
                this.state = State.EVIDENCE_SCAN;
                this.scanTicksAccumulated = 0;
            }
        }
    }

    // ── Node Events ──

    public NodeEventState getActiveNodeEvent() { return activeNodeEvent; }

    /**
     * Begin an interactive node event at the current node.
     * Called after initial scan completes for interactive event types.
     */
    public NodeEventState beginNodeEvent(java.util.Random rng) {
        return beginNodeEvent(rng, BiomeHuntFlavor.BiomeCategory.PLAINS);
    }

    /**
     * Begin an interactive node event with biome-specific flavor.
     */
    public NodeEventState beginNodeEvent(java.util.Random rng, BiomeHuntFlavor.BiomeCategory biome) {
        NodeEventType eventType = getCurrentEventType();
        if (currentHotspot == null) return null;
        net.minecraft.core.BlockPos nodePos = currentHotspot.pos();

        this.activeNodeEvent = switch (eventType) {
            case EVIDENCE_INTERPRETATION -> NodeEventState.createEvidenceInterpretation(nodePos, tier, rng, biome);
            case ENVIRONMENTAL_SEARCH -> NodeEventState.createEnvironmentalSearch(nodePos, tier, rng, biome);
            case WILD_INTERRUPTION -> NodeEventState.createWildInterruption(tier, rng);
            case PROVOCATION -> NodeEventState.createProvocation(tier, rng);
            default -> null;
        };

        if (this.activeNodeEvent != null) {
            this.state = State.NODE_EVENT_ACTIVE;
        }
        return this.activeNodeEvent;
    }

    /**
     * Tick the active node event. Returns true if the event completed or failed.
     */
    public boolean tickNodeEvent(net.minecraft.core.BlockPos playerPos) {
        if (state != State.NODE_EVENT_ACTIVE || activeNodeEvent == null) return false;

        // Type-specific continuous checks
        if (activeNodeEvent.getEventType() == NodeEventType.ENVIRONMENTAL_SEARCH) {
            if (activeNodeEvent.checkSearchProximity(playerPos)) {
                return true;
            }
        } else if (activeNodeEvent.getEventType() == NodeEventType.PROVOCATION) {
            // Use the expanded provocation zone radius from the event (15–20 blocks)
            boolean inZone = isWithinProvocationZone(playerPos, activeNodeEvent.getProvocationZoneRadius());
            if (activeNodeEvent.tickProvocation(inZone)) {
                return true;
            }
        }

        return activeNodeEvent.tick();
    }

    /**
     * Complete the active node event and advance the hunt.
     */
    public void completeNodeEvent() {
        this.activeNodeEvent = null;
        markScanned();
    }

    /**
     * Handle a failed node event — apply penalties but allow retry or continue.
     */
    public void failNodeEvent() {
        addTension(0.1f);
        setTrailQuality(trailQuality - 0.15f);
        this.activeNodeEvent = null;
        // Don't advance — let the scan complete handler re-trigger
        this.state = State.EVIDENCE_LOCATED;
    }

    private boolean isWithinHotspot(net.minecraft.core.BlockPos playerPos) {
        if (currentHotspot == null) return false;
        double dx = (playerPos.getX() + 0.5) - (currentHotspot.pos().getX() + 0.5);
        double dy = (playerPos.getY() + 0.5) - (currentHotspot.pos().getY() + 0.5);
        double dz = (playerPos.getZ() + 0.5) - (currentHotspot.pos().getZ() + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= (currentHotspot.radius() * currentHotspot.radius() * 4);
    }

    /**
     * Check if the player is within the expanded provocation zone radius around the current hotspot.
     * Used for provocation events which operate over a 15–20 block radius instead of the tight hotspot area.
     */
    private boolean isWithinProvocationZone(net.minecraft.core.BlockPos playerPos, int zoneRadius) {
        if (currentHotspot == null) return false;
        double dx = (playerPos.getX() + 0.5) - (currentHotspot.pos().getX() + 0.5);
        double dz = (playerPos.getZ() + 0.5) - (currentHotspot.pos().getZ() + 0.5);
        // Use horizontal-only distance (ignore Y) so the zone is a cylinder
        return (dx * dx + dz * dz) <= ((double) zoneRadius * zoneRadius);
    }

    public boolean isScanComplete() {
        return state == State.EVIDENCE_SCAN && scanTicksAccumulated >= scanTicksRequired;
    }

    public void markScanned() {
        // Move past current index
        if (hasMore()) index++;
        this.currentHotspot = null;
        this.scanTicksAccumulated = 0;
        this.currentCalibration = null;
        this.activeNodeEvent = null;
        this.revealedSegmentCount++;

        // Escalate tension
        escalateTension();

        if (hasMore()) {
            this.state = State.SEGMENT_REVEALED;
        } else {
            this.state = State.FINAL_LOCK;
        }
    }

    /**
     * Transition to encounter state when the Shadow Pokémon manifests.
     */
    public void beginEncounter() {
        this.state = State.ENCOUNTER_ACTIVE;
    }

    // ── Manifestation Buildup ──

    public void beginManifestationBuildup() {
        this.state = State.MANIFESTATION_BUILDUP;
        this.manifestationBuildupTicks = 0;
        this.manifestationPhase = 1; // signal spike
        // Duration scales with tier: higher tiers get longer, more dramatic buildup
        this.manifestationBuildupDuration = 60 + tier.getTier() * 10; // 70-110 ticks (3.5-5.5s)
    }

    /**
     * Tick the manifestation buildup. Returns the current phase (1-4).
     * Phase transitions: 1=signal spike (0-25%), 2=convergence (25-55%), 3=crescendo (55-85%), 4=ready (85-100%).
     */
    public int tickManifestationBuildup() {
        if (state != State.MANIFESTATION_BUILDUP) return 0;
        manifestationBuildupTicks++;
        float progress = (float) manifestationBuildupTicks / manifestationBuildupDuration;
        if (progress < 0.25f) {
            manifestationPhase = 1; // signal spike
        } else if (progress < 0.55f) {
            manifestationPhase = 2; // trail convergence
        } else if (progress < 0.85f) {
            manifestationPhase = 3; // distortion crescendo
        } else {
            manifestationPhase = 4; // ready to spawn
        }
        return manifestationPhase;
    }

    public boolean isManifestationReady() {
        return state == State.MANIFESTATION_BUILDUP && manifestationPhase >= 4
                && manifestationBuildupTicks >= manifestationBuildupDuration;
    }

    public int getManifestationPhase() { return manifestationPhase; }
    public float getManifestationProgress() {
        return manifestationBuildupDuration > 0
                ? Math.min(1.0f, (float) manifestationBuildupTicks / manifestationBuildupDuration)
                : 0f;
    }

    // ── Outcome Branching ──

    public float getNextSearchRadiusModifier() { return nextSearchRadiusModifier; }
    public void setNextSearchRadiusModifier(float mod) { this.nextSearchRadiusModifier = Math.max(0.5f, Math.min(2.0f, mod)); }

    public int getSignalBlackoutTicksRemaining() { return signalBlackoutTicksRemaining; }
    public void beginSignalBlackout(int ticks) { this.signalBlackoutTicksRemaining = ticks; }
    public void tickSignalBlackout() {
        if (signalBlackoutTicksRemaining > 0) signalBlackoutTicksRemaining--;
    }
    public boolean isSignalBlackedOut() { return signalBlackoutTicksRemaining > 0; }

    public boolean isPendingWildConsequence() { return pendingWildConsequence; }
    public void setPendingWildConsequence(boolean pending) { this.pendingWildConsequence = pending; }

    public String getLastNodeGrade() { return lastNodeGrade; }
    public void setLastNodeGrade(String grade) { this.lastNodeGrade = grade; }
}
