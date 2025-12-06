package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.pc.StorageSlot;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds Shadow aura rendering to PC storage slot preview model.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = StorageSlot.class, remap = false)
public abstract class MixinStorageSlotAura {

    @Shadow
    public abstract com.cobblemon.mod.common.pokemon.Pokemon getPokemon();

    @Unique
    private static boolean shadowedhearts$isShadow(RenderablePokemon rp) {
        return rp != null && rp.getAspects() != null && rp.getAspects().contains(SHAspects.SHADOW);
    }

    @Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0, shift = At.Shift.BEFORE))
    private void shadowedhearts$renderAura(GuiGraphics context, int posX, int posY, float partialTicks, CallbackInfo ci) {
        var p = getPokemon();
        if (p == null) return;
        RenderablePokemon rp = p.asRenderablePokemon();
        if (!shadowedhearts$isShadow(rp)) return;

        // Storage slot uses a small fixed scale (2.5 then 4.5 in draw call); choose conservative aura size
        float uiScale = 0.9F; // tuned constant for 25px slot
        float radius = 0.22F * uiScale;
        float halfHeight = 0.50F * uiScale;

        PoseStack matrices = context.pose();
        //AuraEmitters.renderInGui(matrices, context.bufferSource(), radius, halfHeight, 1.0F, partialTicks, QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13F, 35F, 0F)));
    }
}
