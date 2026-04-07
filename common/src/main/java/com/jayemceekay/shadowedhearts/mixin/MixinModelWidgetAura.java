package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.jayemceekay.shadowedhearts.client.aura.AuraEmitters;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
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
@Mixin(value = ModelWidget.class)
public abstract class MixinModelWidgetAura {

    @Shadow @Final
    private float baseScale;
    @Shadow
    private RenderablePokemon pokemon;

    @Unique
    private static boolean shadowedhearts$shouldHaveAura(RenderablePokemon rp) {
        return com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil.shouldHaveShadowAura(rp);
    }

    @Inject(method = "renderPKM", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void shadowedhearts$renderAuraAndAxes(GuiGraphics context, float partialTicks, int mouseX, int mouseY, CallbackInfo ci, @Local(name = "matrices") PoseStack matrices) {
        if (this.pokemon == null) return;
        if (!ShadowedHeartsConfigs.getInstance().getClientConfig().enableShadowAura()) return;

        // Render the Shadow aura for Shadow Pokémon or those with a Shadow Shard.
        if (!shadowedhearts$shouldHaveAura(this.pokemon)) return;
        if(Minecraft.getInstance().screen instanceof Summary) {
            AuraEmitters.renderInSummaryGUI(context, context.bufferSource(), 1.0F, partialTicks, this.pokemon, ((ModelWidget) (Object) this));
        } else if(Minecraft.getInstance().screen instanceof PCGUI) {
            AuraEmitters.renderInPcGUI(context, context.bufferSource(), 1.0F, partialTicks, this.pokemon, ((ModelWidget) (Object) this));
        }

    }


}
