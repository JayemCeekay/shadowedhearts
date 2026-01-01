package com.jayemceekay.shadowedhearts;

import java.util.Set;

/**
 * Interface for entities that can hold and sync aspects.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
public interface AspectHolder {
    Set<String> shadowedhearts$getAspects();
    void shadowedhearts$setAspects(Set<String> aspects);
    void shadowedhearts$addAspect(String aspect);
}
