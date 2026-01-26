package com.jayemceekay.shadowedhearts.client.render.armor;

import com.jayemceekay.shadowedhearts.core.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class SnagMachinePrototypeArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final SnagMachinePrototypeModel<AbstractClientPlayer> model;

    public SnagMachinePrototypeArmorLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            EntityModelSet modelSet
    ) {
        super(parent);
        this.model = new SnagMachinePrototypeModel<>(
                modelSet.bakeLayer(SnagMachinePrototypeModel.LAYER_LOCATION)
        );
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch
    ) {
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offhand.getItem() != ModItems.SNAG_MACHINE_PROTOTYPE.get()) {
            return;
        }

        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTicks);
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, 0, 0);

        poseStack.pushPose();

        getParentModel().leftArm.translateAndRotate(poseStack);

        float scale = 1.0f;
        poseStack.scale(scale, scale, scale);
        // poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.translate(-0.35F, -0.1250F, 0.0F);

        VertexConsumer vc = buffer.getBuffer(
                RenderType.entityTranslucent(SnagMachinePrototypeModel.TEXTURE)
        );

        model.renderToBuffer(
                poseStack,
                vc,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                -1
        );
        poseStack.popPose();
    }
}
