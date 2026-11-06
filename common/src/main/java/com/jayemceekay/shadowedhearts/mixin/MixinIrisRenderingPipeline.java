package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.aura.reader.AuraReaderPulseRenderer;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.ball.BallEmitters;
import com.jayemceekay.shadowedhearts.client.render.SnagDissolveRenderer;
import com.jayemceekay.shadowedhearts.client.particle.PenumbraTrailSystem;
import com.jayemceekay.shadowedhearts.client.render.IrisWorldColorScope;
import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
import com.jayemceekay.shadowedhearts.client.voidshadow.VoidShadowIrisRenderer;
import com.jayemceekay.shadowedhearts.client.render.block.UmbraLiquidIrisRenderer;
import com.jayemceekay.shadowedhearts.client.render.block.UmbrafallCoreSeepIrisRenderer;
import com.jayemceekay.shadowedhearts.client.moonwound.MoonwoundPortalRenderer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"))
    private void shadowedhearts$beginCoreShaderFrame(CallbackInfo ci) {
        VoidShadowIrisRenderer.beginFrame();
        UmbraLiquidIrisRenderer.beginFrame();
        UmbrafallCoreSeepIrisRenderer.beginFrame();
        SnagDissolveRenderer.beginFrame();
        HeldBallSnagGlowRenderer.beginFrame();
    }

    @Inject(method = "beginTranslucents", at = @At(value = "HEAD"))
    private void shadowedhearts$onFinalizeLevelRendering(CallbackInfo ci) {
        AuraReaderPulseRenderer.renderIris();
    }

    @Inject(method = "beginTranslucents", at = @At(value = "TAIL"))
    private void shadowedhearts$renderShadowPokemonAura(CallbackInfo ci) {
        IrisRenderingPipeline pipeline = (IrisRenderingPipeline) (Object) this;
        pipeline.bindDefault();
        boolean wasMainBound = ((IrisRenderingPipelineAccessor) this)
                .shadowedhearts$isMainBound();
        try {
            // Density targets are ordinary core-shader targets. Their bindWrite
            // calls change Iris' logical state as well as the raw framebuffer.
            pipeline.setIsMainBound(false);
            ShadowPokemonAuraSystem.renderIris();
        } finally {
            pipeline.setIsMainBound(wasMainBound);
        }
    }

    @Inject(method = "finalizeLevelRendering", at = @At(value = "HEAD"))
    private void shadowedhearts$renderDarkBallFinalWorldColor(CallbackInfo ci) {
        // Submit after ordinary world translucents, but before Iris runs its
        // final/composite programs. This keeps water, glass, and particles
        // from blending over a nearer Dark Ball while still allowing the
        // shader pack to process the result.
        ((IrisRenderingPipeline) (Object) this).bindDefault();
        IrisRenderingPipeline pipeline = (IrisRenderingPipeline) (Object) this;
        boolean wasMainBound = ((IrisRenderingPipelineAccessor) this)
                .shadowedhearts$isMainBound();
        try {
            // Unknown core shaders must keep their own density/blur targets.
            // Iris otherwise suppresses their writes or rebinds its world FBO.
            // TextureTarget.bindWrite also changes this logical flag, so restore
            // it after Penumbra has restored the caller's raw GL state.
            pipeline.setIsMainBound(false);
            if (VoidShadowIrisRenderer.hasQueuedMeshes() || UmbraLiquidIrisRenderer.hasQueuedMeshes()
                    || UmbrafallCoreSeepIrisRenderer.hasQueuedMeshes()
                    || MoonwoundPortalRenderer.hasActivePortals()
                    || BallEmitters.hasSnagEffects()) {
                try (var color = IrisWorldColorScope.open()) {
                    if (color != null) {
                        VoidShadowIrisRenderer.renderIris();
                        UmbraLiquidIrisRenderer.renderIris();
                        UmbrafallCoreSeepIrisRenderer.renderIris();
                        MoonwoundPortalRenderer.renderIris();
                        BallEmitters.renderSnagIris();
                    } else {
                        UmbraLiquidIrisRenderer.rejectFrame();
                        UmbrafallCoreSeepIrisRenderer.rejectFrame();
                    }
                }
            }
            PenumbraTrailSystem.renderIris();
        } finally {
            pipeline.setIsMainBound(wasMainBound);
        }
        BallEmitters.renderDarkBallIris();
    }

    @Inject(method = "finalizeLevelRendering", at = @At(value = "TAIL"))
    private void shadowedhearts$compositeShadowPokemonAura(CallbackInfo ci) {
        IrisRenderingPipeline pipeline = (IrisRenderingPipeline) (Object) this;
        boolean wasMainBound = ((IrisRenderingPipelineAccessor) this)
                .shadowedhearts$isMainBound();
        try {
            // Iris has ended world rendering here. Retain the composite's main
            // target behavior, but undo logical changes from its blur targets.
            ShadowPokemonAuraSystem.compositeIris();
        } finally {
            pipeline.setIsMainBound(wasMainBound);
        }
    }
}
