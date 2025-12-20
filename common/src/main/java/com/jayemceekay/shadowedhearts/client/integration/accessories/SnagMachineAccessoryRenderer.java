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
        
        boolean leftHand = reference.slotName().contains("left"); // Heuristic
        // Better way: Accessories might provide which hand it is in the slot reference or similar.
        // For now, let's assume it's the right hand if not specified, but Accessories has 'hand' as a 2-amount slot.
        // By default, if it's just 'hand', index 0 is right, index 1 is left in many implementations.
        try {
            java.lang.reflect.Method indexMethod = reference.getClass().getMethod("index");
            int index = (int) indexMethod.invoke(reference);
            if (index == 1) leftHand = true;
        } catch (Exception ignored) {}

        if (leftHand) {
            humanoidModel.leftArm.translateAndRotate(matrices);
        } else {
            humanoidModel.rightArm.translateAndRotate(matrices);
        }

        // Handheld alignment:
        // Position it as if it's being held in the hand.
        matrices.translate(leftHand ? 0.1D : -0.1D, 0.6D, 0.1D);
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(leftHand ? -90.0F : 90.0F));
        matrices.scale(0.8f, 0.8f, 0.8f); 
        
        ItemDisplayContext context = leftHand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

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
