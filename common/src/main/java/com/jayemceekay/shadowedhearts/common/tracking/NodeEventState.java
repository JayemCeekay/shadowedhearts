package com.jayemceekay.shadowedhearts.common.tracking;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Server-side state for an active interactive node event.
 * Each event type uses different subsets of these fields.
 */
public class NodeEventState {

    public enum Phase {
        /** Waiting for the player to initiate the event. */
        PENDING,
        /** Event is active and awaiting player interaction. */
        ACTIVE,
        /** Player completed the event successfully. */
        COMPLETED,
        /** Player failed (wrong choice, timeout, etc.) — may retry or take penalty. */
        FAILED
    }

    // ── Common ──
    private final NodeEventType eventType;
    private Phase phase = Phase.PENDING;
    private int ticksElapsed = 0;
    private int maxTicks = 200; // 10 seconds default timeout

    // ── Evidence Interpretation ──
    /** Positions of scannable clue objects around the node. */
    private final List<BlockPos> cluePositions = new ArrayList<>();
    /** Indices of the valid (correct) clues within cluePositions. */
    private final List<Integer> validClueIndices = new ArrayList<>();
    /** How many valid clues the player must find to complete the event. */
    private int requiredValidCount = 1;
    /** How many valid clues the player has found so far. */
    private int foundValidCount = 0;
    /** Which clue indices the player has already selected (valid or not). */
    private final List<Integer> selectedClueIndices = new ArrayList<>();
    /** Number of wrong guesses so far. */
    private int wrongGuesses = 0;
    /** Legacy single correct index (kept for backward compat with old code paths). */
    private int correctClueIndex = 0;

    // ── Environmental Search ──
    /** The hidden clue position the player must locate. */
    private BlockPos hiddenCluePos;
    /** Search radius around the node center. */
    private float searchRadius = 8.0f;

    // ── Wild Interruption ──
    /** Whether wild Pokémon have been spawned for this event. */
    private boolean wildsSpawned = false;
    /** Whether the wild encounter has been resolved. */
    private boolean wildsResolved = false;
    /** Ticks remaining before auto-resolve (fallback). */
    private int wildResolveTimer = 0;

    // ── Provocation ──
    /** Ticks the player has remained within the provocation zone. */
    private int provocationHoldTicks = 0;
    /** Required hold ticks to complete provocation. */
    private int provocationRequiredTicks = 60; // 3 seconds
    /** Radius (in blocks) of the expanded provocation zone (15–20 blocks). */
    private int provocationZoneRadius = 15;
    /** Signal buildup progress (0.0–1.0). */
    private float signalBuildup = 0.0f;

    // ── Biome flavor ──
    /** Biome category for contextual clue descriptions. */
    private BiomeHuntFlavor.BiomeCategory biomeCategory = BiomeHuntFlavor.BiomeCategory.PLAINS;
    /** Contextual clue description strings (one per clue position for evidence events). */
    private final List<String> clueDescriptions = new ArrayList<>();
    /** Biome-specific search hint. */
    private String searchHint = "";

    public NodeEventState(NodeEventType eventType) {
        this.eventType = eventType;
    }

    // ── Factory methods ──

    /**
     * Generate an Evidence Interpretation event: 2–4 clue positions around the node.
     * Optionally accepts a biome category for contextual clue descriptions.
     */
    public static NodeEventState createEvidenceInterpretation(BlockPos nodePos, ShadowSignalTier tier, Random rng) {
        return createEvidenceInterpretation(nodePos, tier, rng, BiomeHuntFlavor.BiomeCategory.PLAINS);
    }

    public static NodeEventState createEvidenceInterpretation(BlockPos nodePos, ShadowSignalTier tier, Random rng, BiomeHuntFlavor.BiomeCategory biome) {
        NodeEventState state = new NodeEventState(NodeEventType.EVIDENCE_INTERPRETATION);
        state.biomeCategory = biome;

        // Total clue count scales with tier: 4–8 scannable positions (expanded for larger area)
        int numClues = 4 + Math.min(4, tier.getTier()); // tier 1=5, tier 2=6, tier 3=7, tier 4+=8
        numClues = Math.max(4, Math.min(numClues, 8));

        // Valid clue count: a subset are real evidence (1–3 valid out of the total)
        int validCount = 1 + rng.nextInt(Math.min(3, Math.max(1, numClues - 1))); // 1–3 valid
        validCount = Math.min(validCount, numClues - 1); // always at least one decoy
        state.requiredValidCount = validCount;

        // Generate biome-contextual clue descriptions
        List<String> descriptions = BiomeHuntFlavor.randomClueDescriptions(biome, numClues, rng);

        // Clue spread scales with tier: Tier 1 = ±15 blocks, Tier 5 = ±25 blocks
        int clueSpread = 15 + (tier.getTier() - 1) * 2; // 15–23 blocks radius
        for (int i = 0; i < numClues; i++) {
            int dx = rng.nextInt(clueSpread * 2 + 1) - clueSpread;
            int dz = rng.nextInt(clueSpread * 2 + 1) - clueSpread;
            // Ensure minimum distance of 5 blocks from center
            if (Math.abs(dx) < 5 && Math.abs(dz) < 5) dx = dx >= 0 ? 5 : -5;
            state.cluePositions.add(nodePos.offset(dx, 0, dz));
            if (i < descriptions.size()) {
                state.clueDescriptions.add(descriptions.get(i));
            }
        }

        // Randomly select which clue indices are valid
        List<Integer> allIndices = new ArrayList<>();
        for (int i = 0; i < numClues; i++) allIndices.add(i);
        java.util.Collections.shuffle(allIndices, rng);
        for (int i = 0; i < validCount; i++) {
            state.validClueIndices.add(allIndices.get(i));
        }
        // Set legacy correctClueIndex to first valid for backward compat
        state.correctClueIndex = state.validClueIndices.get(0);

        state.maxTicks = 400 + tier.getTier() * 40; // 20–28 seconds scaling with tier
        state.phase = Phase.ACTIVE;
        return state;
    }

    /**
     * Generate an Environmental Search event: hidden clue within search radius.
     */
    public static NodeEventState createEnvironmentalSearch(BlockPos nodePos, ShadowSignalTier tier, Random rng) {
        return createEnvironmentalSearch(nodePos, tier, rng, BiomeHuntFlavor.BiomeCategory.PLAINS);
    }

    public static NodeEventState createEnvironmentalSearch(BlockPos nodePos, ShadowSignalTier tier, Random rng, BiomeHuntFlavor.BiomeCategory biome) {
        NodeEventState state = new NodeEventState(NodeEventType.ENVIRONMENTAL_SEARCH);
        state.biomeCategory = biome;
        state.searchHint = biome.getSearchHint();
        state.searchRadius = 12.0f + tier.getTier() * 3.2f; // 15.2–28 blocks (Tier 1→5)
        // Place hidden clue at a random offset within the search radius, at least 8 blocks from center
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = 8.0 + rng.nextDouble() * (state.searchRadius - 8.0);
        int dx = (int) Math.round(Math.cos(angle) * dist);
        int dz = (int) Math.round(Math.sin(angle) * dist);
        state.hiddenCluePos = nodePos.offset(dx, 0, dz);
        state.maxTicks = 600; // 30 seconds
        state.phase = Phase.ACTIVE;
        return state;
    }

    /**
     * Generate a Wild Interruption event.
     */
    public static NodeEventState createWildInterruption(ShadowSignalTier tier, Random rng) {
        NodeEventState state = new NodeEventState(NodeEventType.WILD_INTERRUPTION);
        state.wildResolveTimer = 300 + tier.getTier() * 50; // 15–25 seconds auto-resolve (expanded for larger area)
        state.maxTicks = state.wildResolveTimer + 100;
        state.phase = Phase.ACTIVE;
        return state;
    }

    /**
     * Generate a Provocation event: hold position while signal builds.
     */
    public static NodeEventState createProvocation(ShadowSignalTier tier, Random rng) {
        NodeEventState state = new NodeEventState(NodeEventType.PROVOCATION);
        state.provocationRequiredTicks = 40 + tier.getTier() * 15; // 2.75–4.75 seconds
        // Store the expanded provocation zone radius (15–20 blocks); used by TrailSession.tickNodeEvent
        // when determining if the player is "in zone" for provocation (replaces hotspot-only check).
        state.provocationZoneRadius = 15 + tier.getTier(); // 16–20 blocks (Tier 1→5)
        state.maxTicks = state.provocationRequiredTicks + 200; // extra time for leaving/re-entering
        state.phase = Phase.ACTIVE;
        return state;
    }

    // ── Tick ──

    /**
     * Tick the event. Returns true if the event auto-completed or auto-failed.
     */
    public boolean tick() {
        if (phase != Phase.ACTIVE) return false;
        ticksElapsed++;

        if (eventType == NodeEventType.WILD_INTERRUPTION) {
            if (wildResolveTimer > 0) wildResolveTimer--;
            if (wildResolveTimer <= 0 && !wildsResolved) {
                wildsResolved = true;
                phase = Phase.COMPLETED;
                return true;
            }
        }

        if (eventType == NodeEventType.PROVOCATION) {
            // Signal buildup is updated externally via tickProvocation()
        }

        // Timeout
        if (ticksElapsed >= maxTicks) {
            phase = Phase.FAILED;
            return true;
        }

        return false;
    }

    // ── Evidence Interpretation ──

    /**
     * Player selects a clue by index. Returns true if the clue is valid evidence.
     * The event completes when all required valid clues have been found.
     * Already-selected clues are ignored.
     */
    public boolean selectClue(int index) {
        if (phase != Phase.ACTIVE || eventType != NodeEventType.EVIDENCE_INTERPRETATION) return false;
        if (index < 0 || index >= cluePositions.size()) return false;
        if (selectedClueIndices.contains(index)) return false; // already selected

        selectedClueIndices.add(index);

        if (validClueIndices.contains(index)) {
            foundValidCount++;
            if (foundValidCount >= requiredValidCount) {
                phase = Phase.COMPLETED;
            }
            return true;
        } else {
            wrongGuesses++;
            if (wrongGuesses >= 3) {
                phase = Phase.FAILED;
            }
            return false;
        }
    }

    // ── Environmental Search ──

    /**
     * Check if the player is close enough to the hidden clue to discover it.
     * Returns true if found.
     */
    public boolean checkSearchProximity(BlockPos playerPos) {
        if (phase != Phase.ACTIVE || eventType != NodeEventType.ENVIRONMENTAL_SEARCH) return false;
        if (hiddenCluePos == null) return false;

        double distSq = playerPos.distSqr(hiddenCluePos);
        if (distSq <= 4.0) { // within 2 blocks
            phase = Phase.COMPLETED;
            return true;
        }
        return false;
    }

    /**
     * Get the signal strength (0.0–1.0) based on distance to hidden clue.
     * Used for hot/cold feedback.
     */
    public float getSearchSignalStrength(BlockPos playerPos) {
        if (hiddenCluePos == null) return 0.0f;
        double dist = Math.sqrt(playerPos.distSqr(hiddenCluePos));
        return (float) Math.max(0.0, 1.0 - (dist / searchRadius));
    }

    // ── Wild Interruption ──

    public void markWildsSpawned() { this.wildsSpawned = true; }
    public void markWildsResolved() {
        this.wildsResolved = true;
        if (phase == Phase.ACTIVE) phase = Phase.COMPLETED;
    }

    // ── Provocation ──

    /**
     * Tick provocation while player is in the zone. Returns true if complete.
     */
    public boolean tickProvocation(boolean playerInZone) {
        if (phase != Phase.ACTIVE || eventType != NodeEventType.PROVOCATION) return false;

        if (playerInZone) {
            provocationHoldTicks++;
            signalBuildup = Math.min(1.0f, (float) provocationHoldTicks / provocationRequiredTicks);
        } else {
            // Signal decays when player leaves
            provocationHoldTicks = Math.max(0, provocationHoldTicks - 2);
            signalBuildup = Math.min(1.0f, (float) provocationHoldTicks / provocationRequiredTicks);
        }

        if (provocationHoldTicks >= provocationRequiredTicks) {
            phase = Phase.COMPLETED;
            return true;
        }
        return false;
    }

    // ── Getters ──

    public NodeEventType getEventType() { return eventType; }
    public Phase getPhase() { return phase; }
    public int getTicksElapsed() { return ticksElapsed; }
    public int getMaxTicks() { return maxTicks; }
    public List<BlockPos> getCluePositions() { return cluePositions; }
    public int getCorrectClueIndex() { return correctClueIndex; }
    public List<Integer> getValidClueIndices() { return validClueIndices; }
    public int getRequiredValidCount() { return requiredValidCount; }
    public int getFoundValidCount() { return foundValidCount; }
    public List<Integer> getSelectedClueIndices() { return selectedClueIndices; }
    public int getWrongGuesses() { return wrongGuesses; }
    public BlockPos getHiddenCluePos() { return hiddenCluePos; }
    public float getSearchRadius() { return searchRadius; }
    public boolean isWildsSpawned() { return wildsSpawned; }
    public boolean isWildsResolved() { return wildsResolved; }
    public int getWildResolveTimer() { return wildResolveTimer; }
    public int getProvocationHoldTicks() { return provocationHoldTicks; }
    public int getProvocationRequiredTicks() { return provocationRequiredTicks; }
    public int getProvocationZoneRadius() { return provocationZoneRadius; }
    public float getSignalBuildup() { return signalBuildup; }
    public BiomeHuntFlavor.BiomeCategory getBiomeCategory() { return biomeCategory; }
    public List<String> getClueDescriptions() { return clueDescriptions; }
    public String getSearchHint() { return searchHint; }
}
