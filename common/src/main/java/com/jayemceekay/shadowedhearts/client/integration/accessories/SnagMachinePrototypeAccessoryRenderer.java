package com.jayemceekay.shadowedhearts.client.integration.accessories;

import com.jayemceekay.shadowedhearts.client.render.armor.SnagMachinePrototypeModel;
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

public class SnagMachinePrototypeAccessoryRenderer implements AccessoryRenderer {

    private SnagMachinePrototypeModel<LivingEntity> model;

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
            this.model = new SnagMachinePrototypeModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(SnagMachinePrototypeModel.LAYER_LOCATION));
        }

        model.prepareMobModel((M) reference.entity(), limbSwing, limbSwingAmount, ageInTicks);
        model.setupAnim((M) reference.entity(), limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        matrices.pushPose();

        humanoidModel.leftArm.translateAndRotate(matrices);

        float scale = 1.0f;
        matrices.scale(scale, scale, scale);
        //matrices.mulPose(Axis.YP.rotationDegrees(180));
        matrices.translate(-0.35F, -0.1250F, 0.0F);

        VertexConsumer vc = multiBufferSource.getBuffer(
                RenderType.entityTranslucent(SnagMachinePrototypeModel.TEXTURE)
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
