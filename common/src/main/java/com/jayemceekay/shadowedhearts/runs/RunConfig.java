package com.jayemceekay.shadowedhearts.runs;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Minimal run configuration payload seeded from a Mission Signal. */
public record RunConfig(long seed,
                        int tier,
                        ResourceLocation theme,
                        List<ResourceLocation> affixes,
                        @Nullable ResourceLocation puzzleBias) {}
