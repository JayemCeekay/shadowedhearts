package com.jayemceekay.shadowedhearts.client.integration.accessories;

import com.jayemceekay.shadowedhearts.core.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

public class SnagMachineAccessoryRenderer implements AccessoryRenderer {

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
        
        // Align to hand. The "hand" slot in Accessories usually maps to the left or right hand.
        // We can check the index or name of the slot if needed, but standard Accessories "hand" slots
        // often come in pairs.

        humanoidModel.leftArm.translateAndRotate(matrices);

        // Handheld alignment:
        // Position it as if it's being held in the hand.
        if(stack.is(ModItems.SNAG_MACHINE_PROTOTYPE.get())) {
            matrices.translate(-0.30D, 0.95D, 0.0D);
            //matrices.mulPose(Axis.ZP.rotationDegrees(180.0F));
            matrices.mulPose(Axis.XP.rotationDegrees(180.0F));
            matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
        } else if(stack.is(ModItems.SNAG_MACHINE_ADVANCED.get())) {
            matrices.translate(-0.19D, 0.9D, 00D);
            matrices.mulPose(Axis.XP.rotationDegrees(0.0F));
            matrices.mulPose(Axis.ZP.rotationDegrees(-180.0F));
            matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
        //matrices.scale(0.8f, 0.8f, 0.8f);
        
        ItemDisplayContext context = ItemDisplayContext.NONE;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(
                stack,
                context, // Use a context that fits
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
