package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.client.render.HeldBallSnagGlowRenderer;
import com.jayemceekay.shadowedhearts.client.snag.ClientSnagState;
import com.jayemceekay.shadowedhearts.util.HeldItemAnchorCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inject into ItemRenderer#render so our quad is centered on the held item model's transform.
 * This makes the effect inherit the vanilla bobbing and hand sway naturally in both first- and third-person.
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {

    @Inject(
            method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", shift = At.Shift.BEFORE)
    )
    private void shadowedhearts$afterItemRender(LivingEntity livingEntity,
                                                ItemStack stack,
                                                ItemDisplayContext displayContext,
                                                HumanoidArm humanoidArm,
                                                PoseStack poseStack,
                                                MultiBufferSource buffers,
                                                int i,
                                                CallbackInfo ci) {
        if (!ClientSnagState.isArmed()) return;
        if (!HeldBallSnagGlowRenderer.isPokeball(stack)) return;

        poseStack.pushPose();
        if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            if (!(livingEntity instanceof net.minecraft.world.entity.player.Player p)) return;
            // cache camera-relative -> world
            int frameId = Minecraft.getInstance().getFrameTimeNs() != 0 ? (int)(Minecraft.getInstance().getFrameTimeNs() & 0x7fffffff) : (int)(System.nanoTime() & 0x7fffffff);
            HeldItemAnchorCache.capture(p, poseStack, frameId);
        }
        poseStack.popPose();
    }
}
