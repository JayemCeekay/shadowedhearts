package com.jayemceekay.shadowedhearts;

/**
 * Marker interface exposed by our PokemonEntity mixin to read shadow flags safely.
 */
public interface ShadowFlag {
    /** @return true if the entity is a shadow. */
    boolean shadowedHearts$isShadow();

    /** @return corruption value in [0..1]. */
    float shadowedHearts$getCorruption();
}
