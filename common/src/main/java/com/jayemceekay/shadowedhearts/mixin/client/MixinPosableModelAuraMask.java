package com.jayemceekay.shadowedhearts.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.frame.ModelFrame;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.client.aura.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.ball.DarkBallCaptureVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PosableModel.class, remap = false)
public abstract class MixinPosableModelAuraMask {

    @Inject(
            method = "render(Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/RenderContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/pose/Bone;render(Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/RenderContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    ordinal = 0
            )
    )
    private void shadowedhearts$beginShadowAuraBoneCapture(
            RenderContext context,
            PoseStack stack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo ci
    ) {
        Entity renderedEntity = context.getEntity();
        if (!(renderedEntity instanceof PokemonEntity pokemonEntity)) {
            return;
        }

        Bone rootPart = ((ModelFrame) (Object) this).getRootPart();
        ShadowPokemonAuraSystem.beginRenderedModelAnchorCapture(pokemonEntity, stack, rootPart);
        DarkBallCaptureVfx.beginModelSnapshotCapture(pokemonEntity, stack, rootPart);
    }

    @Inject(
            method = "render(Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/RenderContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/pose/Bone;render(Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/RenderContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void shadowedhearts$endShadowAuraBoneCapture(
            RenderContext context,
            PoseStack stack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo ci
    ) {
        Entity renderedEntity = context.getEntity();
        if (renderedEntity instanceof PokemonEntity pokemonEntity) {
            ShadowPokemonAuraSystem.endRenderedModelAnchorCapture(pokemonEntity);
            DarkBallCaptureVfx.endModelSnapshotCapture(pokemonEntity);
        }
    }
}
