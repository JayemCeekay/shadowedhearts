package com.jayemceekay.shadowedhearts.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinWorldRenderer {

    @Inject(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderWorldBorder(Lnet/minecraft/client/Camera;)V", shift = At.Shift.BEFORE)
    )
    private void shadowedhearts$hookPostWorldRender(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, Matrix4f modelViewMatrix, CallbackInfo ci) {
       // AuraPulseRenderer.renderIris();
    }
}
