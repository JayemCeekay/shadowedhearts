package com.jayemceekay.shadowedhearts.common.tracking;

/**
 * Categories of interactive micro-events that can occur at evidence nodes.
 * Each node in a hunt is assigned one of these types, which determines the
 * interaction the player must perform to reveal the next trail segment.
 */
public enum NodeEventType {

    /**
     * A – Calibration / Signal Reacquisition.
     * The signal destabilizes and the player must input a directional sequence
     * (Helldivers-style arrow inputs) to re-lock the Aura Reader.
     */
    CALIBRATION,

    /**
     * B – Evidence Interpretation.
     * The player arrives at a node with multiple suspicious environmental traces
     * and must scan or inspect the correct clue to progress.
     */
    EVIDENCE_INTERPRETATION,

    /**
     * C – Wild Pokémon Interruption.
     * Local wild Pokémon react to the shadow aura or scanning process,
     * creating a short combat or avoidance encounter.
     */
    WILD_INTERRUPTION,

    /**
     * D – Environmental Search.
     * The clue is nearby but not immediately obvious; the player must locate it
     * using scanner feedback, visual distortions, or environmental tells.
     */
    ENVIRONMENTAL_SEARCH,

    /**
     * E – Provocation.
     * The player performs an action that forces the Shadow Pokémon target to react,
     * such as sending a stronger pulse or holding the scanner steady.
     */
    PROVOCATION,

    /**
     * Special – Final Manifestation.
     * The last node in the hunt: signal peaks, trail collapses inward,
     * and the Shadow Pokémon is forced to manifest and battle.
     */
    FINAL_MANIFESTATION;

    /**
     * Whether this event type involves a calibration directional-input minigame.
     */
    public boolean requiresCalibration() {
        return this == CALIBRATION;
    }

    /**
     * Whether this event type can produce a wild Pokémon combat encounter.
     */
    public boolean canTriggerCombat() {
        return this == WILD_INTERRUPTION || this == FINAL_MANIFESTATION;
    }

    /**
     * Whether this event type is an investigative/observation event.
     */
    public boolean isInvestigative() {
        return this == EVIDENCE_INTERPRETATION || this == ENVIRONMENTAL_SEARCH;
    }

    /**
     * Whether this event type requires an interactive node event
     * (as opposed to a simple proximity scan or calibration).
     */
    public boolean isInteractive() {
        return this == EVIDENCE_INTERPRETATION || this == ENVIRONMENTAL_SEARCH
                || this == WILD_INTERRUPTION || this == PROVOCATION;
    }

    /**
     * Network-safe integer ordinal for serialization.
     */
    public int toId() {
        return ordinal();
    }

    /**
     * Reconstruct from a network-safe integer ordinal.
     */
    public static NodeEventType fromId(int id) {
        NodeEventType[] values = values();
        if (id < 0 || id >= values.length) return CALIBRATION;
        return values[id];
    }
}
