package com.jayemceekay.shadowedhearts.mixin;

import com.jayemceekay.shadowedhearts.items.AuraReaderItem;
import com.jayemceekay.shadowedhearts.snag.SnagMachineItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

    @Inject(
            method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void shadowedhearts$cancelHeadItemRender(
            LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, boolean bl, PoseStack poseStack, MultiBufferSource multiBufferSource, Level level, int i, int j, int k, CallbackInfo ci
    ) {
        // Only suppress HEAD rendering
        if (itemDisplayContext == ItemDisplayContext.HEAD
                && itemStack.getItem() instanceof AuraReaderItem) {
            ci.cancel();
        }

        // Suppress Snag Machine Advanced rendering when in offhand slot
        if(itemStack.getItem() instanceof SnagMachineItem && livingEntity.getOffhandItem().is(itemStack.getItem())) {
            ci.cancel();
        }
    }
}
