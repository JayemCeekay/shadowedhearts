package com.jayemceekay.shadowedhearts.mixin.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses Minecraft's separately rendered entity shadow throughout the
 * Dark Ball snapshot handoff and captured-replacement presentation.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Inject(
            method = "renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lnet/minecraft/world/entity/Entity;FF"
                    + "Lnet/minecraft/world/level/LevelReader;F)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void shadowedhearts$suppressDarkBallPokemonShadow(
            PoseStack poseStack,
            MultiBufferSource buffer,
            Entity entity,
            float strength,
            float partialTick,
            LevelReader level,
            float radius,
        CallbackInfo ci
    ) {
        if (entity instanceof PokemonEntity pokemon
                && DarkBallCaptureVfx.shouldHideEntityShadow(pokemon)) {
            ci.cancel();
        }
    }
}
