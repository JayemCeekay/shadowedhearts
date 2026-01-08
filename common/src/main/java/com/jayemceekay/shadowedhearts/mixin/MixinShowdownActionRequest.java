package com.jayemceekay.shadowedhearts.mixin;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = com.cobblemon.mod.common.battles.ShowdownActionRequest.class, remap = false)
public abstract class MixinShowdownActionRequest {

    @Shadow
    private @NotNull List<@NotNull Boolean> forceSwitch;

    @Shadow
    public abstract @NotNull List<@NotNull Boolean> getForceSwitch();

}