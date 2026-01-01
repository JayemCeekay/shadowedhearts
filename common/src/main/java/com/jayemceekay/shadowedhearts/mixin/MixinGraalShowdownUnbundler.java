package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownUnbundler;
import com.jayemceekay.shadowedhearts.showdown.ShowdownRuntimePatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GraalShowdownUnbundler.class, remap = false)
public class MixinGraalShowdownUnbundler {
    @Inject(method = "attemptUnbundle", at = @At("TAIL"), order = 1100)
    private void afterUnbundle(CallbackInfo ci) {
        ShowdownRuntimePatcher.applyPatches();
    }
}
