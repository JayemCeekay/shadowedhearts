package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
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

@Mixin(value = GameRenderer.class, remap = false)
public class MixinItemInHandRenderer {


    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;applyModelViewMatrix()V",ordinal = 1, shift = At.Shift.AFTER))
    private void shadowedhearts$afterItemRender(Camera camera, float f, Matrix4f matrix4f, CallbackInfo ci, @Local PoseStack poseStack) {

        if (!ClientSnagState.isArmed()) return;
        if (!HeldBallSnagGlowRenderer.isPokeball(Minecraft.getInstance().player.getMainHandItem()))
            return;
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(8192));
        poseStack.pushPose();
        HeldBallSnagGlowRenderer.renderHeldBallRingsFirstPerson(poseStack, bufferSource, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        poseStack.popPose();
        bufferSource.endBatch();

    }

}
