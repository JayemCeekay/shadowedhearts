package com.jayemceekay.shadowedhearts.mixin.client;

import com.cobblemon.mod.common.client.render.AnimatedModelTextureSupplier;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.jayemceekay.shadowedhearts.client.render.DarkBallSnapshotLayerPolicy;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PosableModel.class, remap = false)
public abstract class MixinPosableModelDarkBallLayers {
    @WrapOperation(
            method = "render(Lcom/cobblemon/mod/common/client/render/models/blockbench/repository/RenderContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/render/AnimatedModelTextureSupplier;interpolatedTexture(Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;)Lnet/minecraft/client/renderer/texture/DynamicTexture;"),
            require = 1,
            allow = 1
    )
    private DynamicTexture shadowedhearts$skipDiscardedInterpolatedLayer(
            AnimatedModelTextureSupplier supplier, PosableState state,
            Operation<DynamicTexture> original) {
        return DarkBallSnapshotLayerPolicy.createInterpolatedTexture(
                ((PosableModel) (Object) this).getBufferProvider(),
                () -> original.call(supplier, state));
    }
}
