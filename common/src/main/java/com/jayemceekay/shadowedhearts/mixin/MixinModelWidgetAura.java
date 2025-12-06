package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Shadow aura around the preview model in Summary/PC screens.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = ModelWidget.class, remap = false)
public abstract class MixinModelWidgetAura {

    @Shadow @Final
    private float baseScale;
    @Shadow
    private RenderablePokemon pokemon;

    @Unique
    private static boolean shadowedhearts$isShadow(RenderablePokemon rp) {
        return rp != null && rp.getAspects().contains(SHAspects.SHADOW);
    }

   /* @Inject(method = "renderPKM", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0))
    public void shadowedhearts$setBaseScale(GuiGraphics context, float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        try {
            System.out.println(((ModelWidget) (Object) this).getPokemon());
            System.out.println(((ModelWidget) (Object) this).getBaseScale());
            System.out.println(((ModelWidget) (Object) this).getX());
            System.out.println(((ModelWidget) (Object) this).getY());
            System.out.println(((ModelWidget) (Object) this).getWidth());
            System.out.println(((ModelWidget) (Object) this).getHeight());
            System.out.println(((ModelWidget) (Object) this).getOffsetY());
        } catch (Throwable ignored) {}
    }*/

    @Inject(method = "renderPKM", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/PokemonGuiUtilsKt;drawProfilePokemon$default(Lcom/cobblemon/mod/common/pokemon/RenderablePokemon;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Quaternionf;Lcom/cobblemon/mod/common/entity/PoseType;Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableState;FFZZFFFFFFILjava/lang/Object;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void shadowedhearts$renderAuraAndAxes(GuiGraphics context, float partialTicks, int mouseX, int mouseY, CallbackInfo ci, @Local(name = "matrices") PoseStack matrices) {
        if (this.pokemon == null) return;

        // Always render debug axes to visualize the current PoseStack orientation in Summary/PC screens.
        //AuraEmitters.renderAxesInGui(matrices, context.bufferSource(), this.pokemon, ((ModelWidget) (Object) this));

        // Render the Shadow aura only for Shadow-aspected Pokémon.
        if (!shadowedhearts$isShadow(this.pokemon)) return;
        if(Minecraft.getInstance().screen instanceof Summary) {
            AuraEmitters.renderInSummaryGUI(context, context.bufferSource(), 1.0F, partialTicks, this.pokemon, ((ModelWidget) (Object) this));
        } else if(Minecraft.getInstance().screen instanceof PCGUI) {
            AuraEmitters.renderInPcGUI(context, context.bufferSource(), 1.0F, partialTicks, this.pokemon, ((ModelWidget) (Object) this));
        }

    }


}
