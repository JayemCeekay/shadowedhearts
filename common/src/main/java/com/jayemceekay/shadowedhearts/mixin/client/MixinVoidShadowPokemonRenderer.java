package com.jayemceekay.shadowedhearts.mixin.client;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.voidshadow.VoidShadowBufferSource;
import com.jayemceekay.shadowedhearts.client.voidshadow.VoidShadowEyeRenderer;
import com.jayemceekay.shadowedhearts.client.voidshadow.VoidShadowIrisRenderer;
import com.jayemceekay.shadowedhearts.client.voidshadow.VoidShadowTransitions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Installs the liquid material only on the copied shape of a synced Void Shadow. */
@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class MixinVoidShadowPokemonRenderer {
    @WrapOperation(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/render/pokemon/PokemonRenderer;drawPlatform(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FLnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 1,
            allow = 1
    )
    private void shadowedhearts$voidShadowPlatform(PokemonRenderer renderer, PoseStack poses,
                                                 PokemonEntity entity, float scale, MultiBufferSource buffer,
                                                 int packedLight, Operation<Void> original) {
        // A battle raft is a support prop; keep it out of the copied body material and mesh.
        original.call(renderer, poses, entity, scale, VoidShadowTransitions.nativeBuffers(buffer), packedLight);
    }

    @WrapOperation(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/render/pokemon/AlphaEyeRendererKt;doAlphaEyeRendering(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"),
            require = 1,
            allow = 1
    )
    private void shadowedhearts$voidShadowEyes(PokemonEntity entity, float partialTicks,
                                             PoseStack poses, MultiBufferSource buffer, Operation<Void> original) {
        if (VoidShadowEyeRenderer.isDisguised(entity)) {
            VoidShadowEyeRenderer.render(entity, poses, buffer);
        } else {
            original.call(entity, partialTicks, poses, buffer);
        }
    }

    @Inject(
            method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void shadowedhearts$voidShadowMaterial(
            PokemonEntity entity, float yaw, float partialTicks,
            PoseStack poses, MultiBufferSource buffer, int packedLight, CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) LocalRef<MultiBufferSource> bufferRef
    ) {
        if (!VoidShadowIrisRenderer.isShadowPass()) {
            bufferRef.set(VoidShadowTransitions.beginRender(entity, poses, bufferRef.get(), partialTicks));
        }
        if (!entity.getAspects().contains("void-shadow")
                || entity.getPokemon().getSpecies().getResourceIdentifier().equals(
                        ResourceLocation.parse("shadowedhearts:void_shadow"))
                || entity.getBeamMode() == 3
                || DarkBallCaptureVfx.getByPokemonId(entity.getId()) != null) {
            return;
        }
        boolean iris = ShadowPokemonAuraSystem.isIrisShaderPackActive();
        bufferRef.set(new VoidShadowBufferSource(bufferRef.get(), !iris));
        if (iris && VoidShadowTransitions.hasStableMaterial(bufferRef.get())) {
            bufferRef.set(VoidShadowIrisRenderer.capture(entity, bufferRef.get()));
        }
    }

    @Inject(method = "render(Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void shadowedhearts$finishVoidShadowTransition(PokemonEntity entity, float yaw, float partialTicks,
            PoseStack poses, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        VoidShadowTransitions.endRender(entity, poses, buffer, partialTicks);
        VoidShadowIrisRenderer.finishCapture(buffer);
    }
}
