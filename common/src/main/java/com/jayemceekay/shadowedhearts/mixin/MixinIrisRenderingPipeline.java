package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.aura.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline {
    @Inject(method = "beginTranslucents", at = @At(value = "HEAD"))
    private void shadowedhearts$onFinalizeLevelRendering(CallbackInfo ci) {
        AuraReaderPulseRenderer.renderIris();
    }

    @Inject(method = "beginTranslucents", at = @At(value = "TAIL"))
    private void shadowedhearts$renderShadowPokemonAura(CallbackInfo ci) {
        ((IrisRenderingPipeline) (Object) this).bindDefault();
        ShadowPokemonAuraSystem.renderIris();
    }

    @Inject(method = "finalizeLevelRendering", at = @At(value = "HEAD"))
    private void shadowedhearts$renderDarkBallFinalWorldColor(CallbackInfo ci) {
        // Submit after ordinary world translucents, but before Iris runs its
        // final/composite programs. This keeps water, glass, and particles
        // from blending over a nearer Dark Ball while still allowing the
        // shader pack to process the result.
        ((IrisRenderingPipeline) (Object) this).bindDefault();
        BallEmitters.renderDarkBallIris();
    }

    @Inject(method = "finalizeLevelRendering", at = @At(value = "TAIL"))
    private void shadowedhearts$compositeShadowPokemonAura(CallbackInfo ci) {
        ShadowPokemonAuraSystem.compositeIris();
    }
}
