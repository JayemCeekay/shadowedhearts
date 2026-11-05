package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.necro.raid.dens.common.raids.RaidInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact 0.13.5 fields; preserves native scaling after removing reward-ineligible participant references. */
@Mixin(value = RaidInstance.class, remap = false)
public interface MoonwoundRaidHealthAccessor {
    @Accessor("initMaxHealth") float shadowedhearts$initialHealth();
    @Accessor("maxHealth") void shadowedhearts$maxHealth(float value);
    @Accessor("currentHealth") void shadowedhearts$currentHealth(float value);
}
