package com.jayemceekay.shadowedhearts.common.voidshadow;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.function.Supplier;

/** A live, completed appearance mutation emits one cue, independent of renderer and entity tracking. */
final class VoidShadowTransformationSound {
    private VoidShadowTransformationSound() { }

    static void afterChange(Supplier<Appearance> appearance, Runnable mutation, Runnable sound) {
        Appearance previous = appearance.get();
        mutation.run();
        if (!previous.equals(appearance.get())) sound.run();
    }

    record Appearance(ResourceLocation species, String form, Set<String> aspects) {
        Appearance { aspects = Set.copyOf(aspects); }
    }
}
