package com.jayemceekay.shadowedhearts.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * Accessor mixin to peek Brain's internal activity -> behaviors map so we can merge
 * our custom behavior into CORE without wiping Cobblemon's defaults.
 */
@Mixin(Brain.class)
public interface AccessorBrain<E> {
    @Accessor("availableBehaviorsByPriority")
    Map<Activity, List<Pair<Integer, BehaviorControl<? super E>>>> shadowedhearts$getAvailableBehaviorsByPriority();
}
