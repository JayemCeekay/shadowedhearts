package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
import com.jayemceekay.shadowedhearts.client.render.DensityFboPipeline;
import com.jayemceekay.shadowedhearts.client.snag.ClientSnagState;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class)
public class MixinItemInHandRenderer {


    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;applyModelViewMatrix()V",ordinal = 1, shift = At.Shift.AFTER))
    private void shadowedhearts$afterItemRender(Camera camera, float f, Matrix4f matrix4f, CallbackInfo ci, @Local PoseStack poseStack) {

        if (!ClientSnagState.isArmed()) return;
        if (Minecraft.getInstance().player == null
                || !HeldBallSnagGlowRenderer.isPokeball(Minecraft.getInstance().player.getMainHandItem()))
            return;
        // This hook follows the native hand model-view pop. Its projection is
        // still the hand projection, including when Iris renders hands earlier.
        try (var state = DensityFboPipeline.preserveRenderState();
             ByteBufferBuilder memory = new ByteBufferBuilder(8192)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(memory);
            poseStack.pushPose();
            try {
                HeldBallSnagGlowRenderer.renderHeldBallRingsFirstPerson(poseStack, bufferSource,
                        Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
                bufferSource.endBatch();
            } finally {
                poseStack.popPose();
            }
        }

    }

}
