package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.aura.AuraPulseRenderer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline {
    @Inject(method = "beginTranslucents", at = @At(value = "HEAD"))
    private void shadowedhearts$onFinalizeLevelRendering(CallbackInfo ci) {
        AuraPulseRenderer.renderIris();
    }
}