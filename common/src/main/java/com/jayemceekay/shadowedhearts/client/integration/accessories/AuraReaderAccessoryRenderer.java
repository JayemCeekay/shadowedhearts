package com.jayemceekay.shadowedhearts.client.integration.accessories;

import com.jayemceekay.shadowedhearts.client.render.armor.AuraReaderModel;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class AuraReaderAccessoryRenderer implements AccessoryRenderer {

    private AuraReaderModel<LivingEntity> model;

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

        if (this.model == null) {
            this.model = new AuraReaderModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(AuraReaderModel.LAYER_LOCATION));
        }

        matrices.pushPose();

        humanoidModel.head.translateAndRotate(matrices);

        float scale = 0.675f;
        matrices.scale(scale, scale, scale);
        matrices.translate(-0.025F, ShadowedHeartsConfigs.getInstance().getClientConfig().auraReaderYOffset(), -0.05F);

        VertexConsumer vc = multiBufferSource.getBuffer(
                RenderType.entityTranslucent(AuraReaderModel.TEXTURE)
        );

        this.model.renderToBuffer(
                matrices,
                vc,
                light,
                OverlayTexture.NO_OVERLAY,
                -1
        );

        matrices.popPose();
    }
}
