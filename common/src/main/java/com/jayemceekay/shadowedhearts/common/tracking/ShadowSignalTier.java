package com.jayemceekay.shadowedhearts.common.tracking;

/**
 * Defines the five tiers of Shadow Signal Data.
 * Each tier controls the complexity of the hunt minigame (node count, calibration difficulty)
 * as well as the quality of the Shadow Pokémon encountered (rarity, level range, IV quality).
 */
public enum ShadowSignalTier {
    /**
     * Tier 1 – Faint Signal.
     * Short, introductory hunts with simple calibration and common encounters.
     */
    FAINT(1,
            2, 2,   // nodeCount min/max
            3, 3,   // calibration input length min/max
            5, 15,  // encounter level min/max
            0, 1,   // guaranteed perfect IVs min/max
            0.0,    // shiny chance bonus
            "common"),

    /**
     * Tier 2 – Weak Signal.
     * Slightly longer hunts with moderate calibration difficulty.
     */
    WEAK(2,
            2, 3,
            3, 4,
            10, 25,
            1, 2,
            0.0,
            "uncommon"),

    /**
     * Tier 3 – Moderate Signal.
     * Mid-length hunts with varied events and balanced encounters.
     */
    MODERATE(3,
            3, 4,
            4, 5,
            20, 40,
            2, 3,
            0.001,
            "rare"),

    /**
     * Tier 4 – Strong Signal.
     * Demanding hunts with longer calibration, higher-level encounters.
     */
    STRONG(4,
            3, 5,
            5, 6,
            35, 55,
            3, 4,
            0.002,
            "elite"),

    /**
     * Tier 5 – Resonant Signal.
     * The most challenging hunts with elite calibration and top-tier encounters.
     */
    RESONANT(5,
            4, 6,
            5, 7,
            50, 70,
            4, 5,
            0.005,
            "legendary");

    private final int tier;
    private final int minNodes;
    private final int maxNodes;
    private final int minCalibrationInputs;
    private final int maxCalibrationInputs;
    private final int minEncounterLevel;
    private final int maxEncounterLevel;
    private final int minGuaranteedPerfectIVs;
    private final int maxGuaranteedPerfectIVs;
    private final double shinyChanceBonus;
    private final String rarityPool;

    ShadowSignalTier(int tier,
                     int minNodes, int maxNodes,
                     int minCalibrationInputs, int maxCalibrationInputs,
                     int minEncounterLevel, int maxEncounterLevel,
                     int minGuaranteedPerfectIVs, int maxGuaranteedPerfectIVs,
                     double shinyChanceBonus,
                     String rarityPool) {
        this.tier = tier;
        this.minNodes = minNodes;
        this.maxNodes = maxNodes;
        this.minCalibrationInputs = minCalibrationInputs;
        this.maxCalibrationInputs = maxCalibrationInputs;
        this.minEncounterLevel = minEncounterLevel;
        this.maxEncounterLevel = maxEncounterLevel;
        this.minGuaranteedPerfectIVs = minGuaranteedPerfectIVs;
        this.maxGuaranteedPerfectIVs = maxGuaranteedPerfectIVs;
        this.shinyChanceBonus = shinyChanceBonus;
        this.rarityPool = rarityPool;
    }

    public int getTier() { return tier; }

    public int getMinNodes() { return minNodes; }
    public int getMaxNodes() { return maxNodes; }

    public int getMinCalibrationInputs() { return minCalibrationInputs; }
    public int getMaxCalibrationInputs() { return maxCalibrationInputs; }

    public int getMinEncounterLevel() { return minEncounterLevel; }
    public int getMaxEncounterLevel() { return maxEncounterLevel; }

    public int getMinGuaranteedPerfectIVs() { return minGuaranteedPerfectIVs; }
    public int getMaxGuaranteedPerfectIVs() { return maxGuaranteedPerfectIVs; }

    /** Additional shiny chance added on top of the base rate for this tier. */
    public double getShinyChanceBonus() { return shinyChanceBonus; }

    /**
     * A string key identifying which species rarity pool to draw from.
     * Used by the encounter resolver to filter or weight species selection.
     */
    public String getRarityPool() { return rarityPool; }

    /**
     * The scan duration in ticks required to complete evidence scans for this tier.
     * Higher tiers require longer scans.
     */
    public int getScanTicksRequired() {
        return switch (this) {
            case FAINT -> 30;      // 1.5 seconds
            case WEAK -> 40;       // 2 seconds
            case MODERATE -> 50;   // 2.5 seconds
            case STRONG -> 60;     // 3 seconds
            case RESONANT -> 80;   // 4 seconds
        };
    }

    /**
     * Base tension escalation rate per completed node.
     * Higher tiers escalate tension faster.
     */
    public float getTensionPerNode() {
        return switch (this) {
            case FAINT -> 0.25f;
            case WEAK -> 0.22f;
            case MODERATE -> 0.20f;
            case STRONG -> 0.18f;
            case RESONANT -> 0.16f;
        };
    }

    /**
     * Look up a tier by its numeric value (1-5). Returns FAINT for invalid values.
     */
    public static ShadowSignalTier fromTier(int tier) {
        for (ShadowSignalTier t : values()) {
            if (t.tier == tier) return t;
        }
        return FAINT;
    }

    /**
     * Look up a tier by its registry suffix name (e.g. "faint", "weak").
     */
    public static ShadowSignalTier fromName(String name) {
        if (name == null) return FAINT;
        for (ShadowSignalTier t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return FAINT;
    }
}
