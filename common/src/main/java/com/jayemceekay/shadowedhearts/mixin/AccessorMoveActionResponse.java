package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.battles.MoveActionResponse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MoveActionResponse.class, remap = false)
public interface AccessorMoveActionResponse {
    @Accessor("moveName") String shadowedhearts$getMoveName();
}