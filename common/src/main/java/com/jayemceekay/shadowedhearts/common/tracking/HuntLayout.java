package com.jayemceekay.shadowedhearts.common.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates a seeded sequence of {@link NodeEventType} events for a hunt based on the
 * {@link ShadowSignalTier}. The layout follows the design document's recommended rhythm:
 * <pre>observe → act → move → escalate</pre>
 *
 * <p>A typical hunt composition:
 * <ul>
 *   <li>One evidence interpretation or environmental search event (investigative)</li>
 *   <li>One calibration / signal stabilization event</li>
 *   <li>Optionally one disruption or provocation event (mid/late hunts)</li>
 *   <li>Final manifestation event (always last)</li>
 * </ul>
 *
 * <p>The hunt seed allows deterministic or semi-deterministic generation so that
 * the same Shadow Signal Data item always produces the same hunt layout.
 */
public final class HuntLayout {

    private final ShadowSignalTier tier;
    private final List<NodeEventType> events;
    private final long seed;

    private final ShadowSpeciesTrait speciesTrait;

    private HuntLayout(ShadowSignalTier tier, List<NodeEventType> events, long seed, ShadowSpeciesTrait speciesTrait) {
        this.tier = tier;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        this.seed = seed;
        this.speciesTrait = speciesTrait != null ? speciesTrait : ShadowSpeciesTrait.NEUTRAL;
    }

    /**
     * Generate a hunt layout for the given tier using the provided seed.
     * The seed can come from the Shadow Signal Data item's NBT for deterministic hunts.
     */
    public static HuntLayout generate(ShadowSignalTier tier, long seed) {
        return generate(tier, seed, ShadowSpeciesTrait.NEUTRAL);
    }

    /**
     * Generate a hunt layout for the given tier, seed, and species trait.
     * Species traits modify event selection weights.
     */
    public static HuntLayout generate(ShadowSignalTier tier, long seed, ShadowSpeciesTrait speciesTrait) {
        Random rng = new Random(seed);

        int min = tier.getMinNodes();
        int max = tier.getMaxNodes();
        int nodeCount = min + rng.nextInt(Math.max(1, max - min + 1));

        // The last event is always FINAL_MANIFESTATION
        int interactionNodes = nodeCount; // nodes before the final manifestation
        List<NodeEventType> events = new ArrayList<>(interactionNodes + 1);

        // Build the event pool following the rhythm: investigative → stabilization → pressure → escalation
        // Species traits modify event selection weights.
        ShadowSpeciesTrait trait = speciesTrait != null ? speciesTrait : ShadowSpeciesTrait.NEUTRAL;
        for (int i = 0; i < interactionNodes; i++) {
            float progress = interactionNodes <= 1 ? 1.0f : (float) i / (interactionNodes - 1);
            NodeEventType previousEvent = events.isEmpty() ? null : events.get(events.size() - 1);
            NodeEventType event = pickEventWithTrait(tier, progress, i, interactionNodes, rng, trait, previousEvent);
            events.add(event);
        }

        // Ensure at least one calibration event for longer hunts
        ensureCalibrationPresent(events, rng);

        // Always end with final manifestation
        events.add(NodeEventType.FINAL_MANIFESTATION);

        return new HuntLayout(tier, events, seed, speciesTrait);
    }

    public ShadowSpeciesTrait getSpeciesTrait() { return speciesTrait; }

    /**
     * Picks an event type for a given position in the hunt based on tier, progress, and species trait.
     * Species traits shift the probability weights:
     * - AGGRESSIVE: more wild interruptions
     * - CUNNING: more evidence interpretation (false clues)
     * - ELUSIVE: more calibration events
     * - SPECTRAL: more calibration with distortion
     */
    private static NodeEventType pickEventWithTrait(ShadowSignalTier tier, float progress, int index, int total, Random rng, ShadowSpeciesTrait trait) {
        return pickEventWithTrait(tier, progress, index, total, rng, trait, null);
    }

    private static NodeEventType pickEventWithTrait(ShadowSignalTier tier, float progress, int index, int total, Random rng, ShadowSpeciesTrait trait, NodeEventType previousEvent) {
        // Phase-based selection to maintain rhythm
        if (index == 0) {
            // First node: pick from all investigative/observational types, not just evidence
            float r = rng.nextFloat();
            if (r < 0.35f) return NodeEventType.EVIDENCE_INTERPRETATION;
            if (r < 0.65f) return NodeEventType.ENVIRONMENTAL_SEARCH;
            return NodeEventType.CALIBRATION;
        }

        if (total >= 3 && index == total - 1) {
            // Last interaction node before final: provocation or high-pressure calibration
            return rng.nextFloat() < 0.6f
                    ? NodeEventType.PROVOCATION
                    : NodeEventType.CALIBRATION;
        }

        // Base weights — balanced across all 5 types
        float wildWeight = 0.20f * trait.getWildInterruptionWeight();
        float calibWeight = 0.25f * trait.getCalibrationDifficultyMultiplier();
        float evidenceWeight = 0.20f;
        float searchWeight = 0.20f;
        float provocWeight = 0.15f;

        // Progress shifts: later = more pressure events
        if (progress > 0.6f) {
            wildWeight *= 1.3f;
            calibWeight *= 1.2f;
            provocWeight *= 1.5f;
            evidenceWeight *= 0.7f;
            searchWeight *= 0.5f;
        } else if (progress < 0.35f) {
            evidenceWeight *= 1.2f;
            searchWeight *= 1.3f;
            wildWeight *= 0.6f;
            provocWeight *= 0.5f;
        }

        // CUNNING: boost evidence (more false clues to sort through)
        if (trait == ShadowSpeciesTrait.CUNNING) {
            evidenceWeight *= 1.3f;
        }

        // Suppress the previous event type to avoid consecutive repeats
        if (previousEvent != null) {
            switch (previousEvent) {
                case CALIBRATION -> calibWeight *= 0.3f;
                case EVIDENCE_INTERPRETATION -> evidenceWeight *= 0.3f;
                case ENVIRONMENTAL_SEARCH -> searchWeight *= 0.3f;
                case WILD_INTERRUPTION -> wildWeight *= 0.3f;
                case PROVOCATION -> provocWeight *= 0.3f;
                default -> {}
            }
        }

        // Normalize and pick
        float totalW = wildWeight + calibWeight + evidenceWeight + searchWeight + provocWeight;
        float roll = rng.nextFloat() * totalW;

        if (roll < calibWeight) return NodeEventType.CALIBRATION;
        roll -= calibWeight;
        if (roll < evidenceWeight) return NodeEventType.EVIDENCE_INTERPRETATION;
        roll -= evidenceWeight;
        if (roll < wildWeight) return NodeEventType.WILD_INTERRUPTION;
        roll -= wildWeight;
        if (roll < searchWeight) return NodeEventType.ENVIRONMENTAL_SEARCH;
        return NodeEventType.PROVOCATION;
    }

    // ── Ensure variety ──

    /**
     * Guarantee that at least one calibration event exists in hunts with 3+ nodes.
     * Called internally; the generate method handles this.
     */
    private static void ensureCalibrationPresent(List<NodeEventType> events, Random rng) {
        if (events.size() < 3) return;
        boolean hasCalibration = false;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) == NodeEventType.CALIBRATION) {
                hasCalibration = true;
                break;
            }
        }
        if (!hasCalibration && events.size() >= 2) {
            // Replace the second event with calibration (keeps first as investigative)
            events.set(1, NodeEventType.CALIBRATION);
        }
    }

    // ── Query ──

    public ShadowSignalTier getTier() { return tier; }
    public List<NodeEventType> getEvents() { return events; }
    public long getSeed() { return seed; }

    /** Total number of events including the final manifestation. */
    public int getTotalNodes() { return events.size(); }

    /** Number of interactive nodes before the final manifestation. */
    public int getInteractionNodeCount() { return Math.max(0, events.size() - 1); }

    /** Get the event type at a specific node index. */
    public NodeEventType getEventAt(int index) {
        if (index < 0 || index >= events.size()) return NodeEventType.FINAL_MANIFESTATION;
        return events.get(index);
    }

    /** Whether the node at the given index is the final manifestation. */
    public boolean isFinalNode(int index) {
        return index == events.size() - 1;
    }
}
