package com.jayemceekay.shadowedhearts.client.render.armor;

import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.jayemceekay.shadowedhearts.items.AuraReaderItem;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class AuraReaderArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final AuraReaderModel<AbstractClientPlayer> model;

    public AuraReaderArmorLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            EntityModelSet modelSet
    ) {
        super(parent);
        this.model = new AuraReaderModel<>(
            modelSet.bakeLayer(AuraReaderModel.LAYER_LOCATION)
        );
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof AuraReaderItem)) {
            return;
        }

        // Sync pose with player
        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTicks);
        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        getParentModel().head.translateAndRotate(poseStack);
        poseStack.pushPose();
        float scale = 0.675f;
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.025F, ShadowedHeartsConfigs.getInstance().getClientConfig().auraReaderYOffset(), -0.05F);


        VertexConsumer vc = buffer.getBuffer(
            RenderType.entityTranslucent(AuraReaderModel.TEXTURE)
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
