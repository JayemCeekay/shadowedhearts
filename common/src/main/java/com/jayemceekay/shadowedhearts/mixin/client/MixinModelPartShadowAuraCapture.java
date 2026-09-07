package com.jayemceekay.shadowedhearts.mixin.client;

import com.jayemceekay.shadowedhearts.client.aura.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.aura.ShadowPokemonAuraGuiRenderer;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class MixinModelPartShadowAuraCapture {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void shadowedhearts$captureShadowAuraRenderedPart(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo ci
    ) {
        ShadowPokemonAuraGuiRenderer.captureRenderedModelPart((ModelPart) (Object) this, poseStack);
        ShadowPokemonAuraSystem.captureRenderedModelPart((ModelPart) (Object) this, poseStack);
        DarkBallCaptureVfx.captureRenderedModelPart((ModelPart) (Object) this, poseStack);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("RETURN")
    )
    private void shadowedhearts$finishShadowAuraRenderedPart(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo ci
    ) {
        ShadowPokemonAuraGuiRenderer.endRenderedModelPart((ModelPart) (Object) this);
        ShadowPokemonAuraSystem.endRenderedModelPart((ModelPart) (Object) this);
        DarkBallCaptureVfx.endRenderedModelPart((ModelPart) (Object) this);
    }
}
