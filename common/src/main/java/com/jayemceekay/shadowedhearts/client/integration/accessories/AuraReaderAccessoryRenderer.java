package com.jayemceekay.shadowedhearts.client.integration.accessories;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class AuraReaderAccessoryRenderer implements AccessoryRenderer {

    @Override
    public <M extends LivingEntity> void render(
            ItemStack stack,
            SlotReference reference,
            PoseStack matrices,
            EntityModel<M> model,
            MultiBufferSource multiBufferSource,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(model instanceof HumanoidModel<?> humanoidModel)) return;

        matrices.pushPose();

        humanoidModel.head.translateAndRotate(matrices);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.HEAD,
                light,
                OverlayTexture.NO_OVERLAY,
                matrices,
                multiBufferSource,
                Minecraft.getInstance().level,
                0
        );

        matrices.popPose();
    }
}
