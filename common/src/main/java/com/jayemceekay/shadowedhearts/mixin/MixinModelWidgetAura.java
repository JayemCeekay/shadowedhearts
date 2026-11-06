package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraGuiRenderer;
import com.jayemceekay.shadowedhearts.client.aura.shadow.ShadowPokemonAuraSystem;
import com.jayemceekay.shadowedhearts.client.gui.GuiPoseScissor;
import com.jayemceekay.shadowedhearts.client.gui.PurificationChamberGUI;
import com.jayemceekay.shadowedhearts.client.gui.UmbraInfuserScreen;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    @Shadow
    private float baseScale;
    @Shadow
    private RenderablePokemon pokemon;
    @Shadow
    private FloatingState state;

    @Unique
    private ShadowPokemonAuraSystem.PreviewInstance shadowedhearts$previewAura;

    /** These machine screens fit their whole panel, including Cobblemon's nested portrait clip. */
    @WrapOperation(method = "renderPKM", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;enableScissor(IIII)V", remap = true))
    private void shadowedhearts$fitMachinePortraitClip(GuiGraphics graphics, int left, int top, int right, int bottom,
                                                      Operation<Void> original) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof PurificationChamberGUI || screen instanceof UmbraInfuserScreen) {
            GuiPoseScissor.enable(graphics, left, top, right, bottom);
        } else {
            original.call(graphics, left, top, right, bottom);
        }
    }

    @Unique
    private ShadowPokemonAuraSystem.PreviewInstance shadowedhearts$previewAura() {
        if (shadowedhearts$previewAura == null) {
            shadowedhearts$previewAura = new ShadowPokemonAuraSystem.PreviewInstance();
        }
        return shadowedhearts$previewAura;
    }

    @Inject(method = "setPokemon", at = @At("HEAD"))
    private void shadowedhearts$resetPreviewAura(RenderablePokemon pokemon, CallbackInfo ci) {
        if (shadowedhearts$previewAura != null) {
            ShadowPokemonAuraGuiRenderer.deactivate(shadowedhearts$previewAura);
        }
    }

    @Unique
    private static boolean shadowedhearts$shouldHaveAura(RenderablePokemon rp) {
        return com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil.shouldHaveShadowAura(rp);
    }

    @Inject(
            method = "renderPKM",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void shadowedhearts$beginGuiAuraCapture(GuiGraphics context,
                                                     float partialTicks,
                                                     int mouseX,
                                                     int mouseY,
                                                     CallbackInfo ci) {
        if (this.pokemon == null) return;
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()
                || !shadowedhearts$shouldHaveAura(this.pokemon)) {
            if (shadowedhearts$previewAura != null) {
                ShadowPokemonAuraGuiRenderer.deactivate(shadowedhearts$previewAura);
            }
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof Summary || minecraft.screen instanceof PCGUI) {
            ShadowPokemonAuraGuiRenderer.beginCapture(
                    shadowedhearts$previewAura(),
                    this.pokemon,
                    this.state,
                    "model-widget@" + System.identityHashCode(this)
            );
        }
    }

    @Inject(method = "renderPKM", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void shadowedhearts$renderAuraAndAxes(GuiGraphics context, float partialTicks, int mouseX, int mouseY, CallbackInfo ci, @Local(name = "matrices") PoseStack matrices) {
        if (this.pokemon == null) return;
        if (!com.jayemceekay.shadowedhearts.client.ClientEffects.auraVisible()) return;

        // Render the Shadow aura for Shadow Pokémon or those with a Shadow Shard.
        if (!shadowedhearts$shouldHaveAura(this.pokemon)) return;
        if(Minecraft.getInstance().screen instanceof Summary) {
            ShadowPokemonAuraGuiRenderer.renderInSummaryGUI(context, context.bufferSource(), 1.0F, partialTicks, this.pokemon, ((ModelWidget) (Object) this), shadowedhearts$previewAura());
        } else if(Minecraft.getInstance().screen instanceof PCGUI) {
            ShadowPokemonAuraGuiRenderer.renderInPcGUI(context, context.bufferSource(), 1.0F, partialTicks, this.pokemon, ((ModelWidget) (Object) this), shadowedhearts$previewAura());
        }

    }


}
