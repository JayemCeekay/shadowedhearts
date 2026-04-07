package com.jayemceekay.shadowedhearts.common.tracking;

import com.cobblemon.mod.common.pokemon.Species;

import java.util.Map;

/**
 * Species-specific hunt behavior traits that modify the hunt experience.
 * Each shadow species is categorized into one primary trait based on its typing,
 * BST distribution, and thematic characteristics.
 * <p>
 * Trait effects on hunts:
 * <ul>
 *   <li><b>AGGRESSIVE</b> → more wild Pokémon interruptions during the hunt</li>
 *   <li><b>CUNNING</b> → more false trails and decoy clues</li>
 *   <li><b>ELUSIVE</b> → more calibration events, harder sequences</li>
 *   <li><b>SPECTRAL</b> → heavier visual distortion, more Shadow Echo calibration variants</li>
 *   <li><b>NEUTRAL</b> → balanced hunt with no trait modifiers</li>
 * </ul>
 */
public enum ShadowSpeciesTrait {
    /** Fighting, aggressive species — more wild interruptions. */
    AGGRESSIVE(
            0.15f,  // extra tension per node
            -0.05f, // trail quality modifier (slightly worse)
            1.5f,   // wild interruption weight multiplier
            1.0f,   // false trail chance multiplier
            1.0f,   // calibration difficulty multiplier
            0.0f    // extra visual distortion
    ),

    /** Dark, cunning species — more false trails and decoy clues. */
    CUNNING(
            0.10f,
            -0.10f, // trail quality notably worse (more noise)
            1.0f,
            2.0f,   // double false trail chance
            1.0f,
            0.0f
    ),

    /** Fast, evasive species — more calibrations, harder sequences. */
    ELUSIVE(
            0.05f,
            0.0f,
            1.0f,
            1.0f,
            1.5f,   // harder calibrations
            0.0f
    ),

    /** Ghost/psychic species — heavier visual distortion. */
    SPECTRAL(
            0.10f,
            -0.08f,
            1.0f,
            1.2f,
            1.2f,
            0.3f    // significant extra distortion
    ),

    /** Default — balanced, no special modifications. */
    NEUTRAL(
            0.0f,
            0.0f,
            1.0f,
            1.0f,
            1.0f,
            0.0f
    );

    private final float extraTensionPerNode;
    private final float trailQualityModifier;
    private final float wildInterruptionWeight;
    private final float falseTrailChanceMultiplier;
    private final float calibrationDifficultyMultiplier;
    private final float extraVisualDistortion;

    ShadowSpeciesTrait(float extraTensionPerNode, float trailQualityModifier,
                       float wildInterruptionWeight, float falseTrailChanceMultiplier,
                       float calibrationDifficultyMultiplier, float extraVisualDistortion) {
        this.extraTensionPerNode = extraTensionPerNode;
        this.trailQualityModifier = trailQualityModifier;
        this.wildInterruptionWeight = wildInterruptionWeight;
        this.falseTrailChanceMultiplier = falseTrailChanceMultiplier;
        this.calibrationDifficultyMultiplier = calibrationDifficultyMultiplier;
        this.extraVisualDistortion = extraVisualDistortion;
    }

    public float getExtraTensionPerNode() { return extraTensionPerNode; }
    public float getTrailQualityModifier() { return trailQualityModifier; }
    public float getWildInterruptionWeight() { return wildInterruptionWeight; }
    public float getFalseTrailChanceMultiplier() { return falseTrailChanceMultiplier; }
    public float getCalibrationDifficultyMultiplier() { return calibrationDifficultyMultiplier; }
    public float getExtraVisualDistortion() { return extraVisualDistortion; }

    /**
     * Determine the primary trait for a given species based on its typing and stats.
     * Uses a priority system: Ghost/Psychic → Spectral, Dark/Poison → Cunning,
     * Fighting/Dragon → Aggressive, high Speed → Elusive, else Neutral.
     */
    public static ShadowSpeciesTrait resolve(Species species) {
        if (species == null) return NEUTRAL;

        Map<com.cobblemon.mod.common.api.types.ElementalType, ?> types = null;
        boolean hasGhost = false;
        boolean hasPsychic = false;
        boolean hasDark = false;
        boolean hasPoison = false;
        boolean hasFighting = false;
        boolean hasDragon = false;
        boolean hasFire = false;

        // Check primary and secondary types
        try {
            var primaryType = species.getPrimaryType();
            var secondaryType = species.getSecondaryType();

            if (primaryType != null) {
                String typeName = primaryType.getName().toLowerCase();
                hasGhost |= typeName.equals("ghost");
                hasPsychic |= typeName.equals("psychic");
                hasDark |= typeName.equals("dark");
                hasPoison |= typeName.equals("poison");
                hasFighting |= typeName.equals("fighting");
                hasDragon |= typeName.equals("dragon");
                hasFire |= typeName.equals("fire");
            }
            if (secondaryType != null) {
                String typeName = secondaryType.getName().toLowerCase();
                hasGhost |= typeName.equals("ghost");
                hasPsychic |= typeName.equals("psychic");
                hasDark |= typeName.equals("dark");
                hasPoison |= typeName.equals("poison");
                hasFighting |= typeName.equals("fighting");
                hasDragon |= typeName.equals("dragon");
                hasFire |= typeName.equals("fire");
            }
        } catch (Exception ignored) {
            return NEUTRAL;
        }

        // Priority resolution
        if (hasGhost || hasPsychic) return SPECTRAL;
        if (hasDark || hasPoison) return CUNNING;
        if (hasFighting || hasDragon || hasFire) return AGGRESSIVE;

        // Check speed stat for elusiveness
        try {
            int speed = species.getBaseStats().getOrDefault(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED, 0);
            if (speed >= 100) return ELUSIVE;
        } catch (Exception ignored) {}

        return NEUTRAL;
    }

    /**
     * Network-safe integer ordinal for serialization.
     */
    public int toId() { return ordinal(); }

    /**
     * Reconstruct from a network-safe integer ordinal.
     */
    public static ShadowSpeciesTrait fromId(int id) {
        ShadowSpeciesTrait[] values = values();
        if (id < 0 || id >= values.length) return NEUTRAL;
        return values[id];
    }
}
