package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.events.Cancelable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Cancelable.class, remap = false)
public interface AccessorCancelable {
    @Accessor("isCanceled")
    void setCanceled(boolean canceled);
}
