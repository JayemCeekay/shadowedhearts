package com.jayemceekay.shadowedhearts.mixin.raiddens;

import com.necro.raid.dens.common.raids.RaidInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Shared HP belongs to the encounter while eligible participants belong to the session. */
@Mixin(value = RaidInstance.class, remap = false)
public interface MoonwoundRaidHealthAccessor {
    @Accessor("initMaxHealth") float shadowedhearts$initialHealth();
    @Accessor("maxHealth") void shadowedhearts$maxHealth(float value);
    @Accessor("currentHealth") void shadowedhearts$currentHealth(float value);
}
