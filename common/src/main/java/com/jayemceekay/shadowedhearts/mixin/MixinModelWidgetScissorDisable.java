package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Temporarily disables scissor clipping in the Cobblemon ModelWidget portrait.
 * Context: Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 */
@Mixin(value = ModelWidget.class, remap = false)
public abstract class MixinModelWidgetScissorDisable {

    @Redirect(
        method = "renderPKM",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;enableScissor(IIII)V"
        )
    )
    private void shadowedhearts$disableEnableScissor(GuiGraphics context, int x1, int y1, int x2, int y2) {
        // no-op to disable scissor region during ModelWidget rendering
    }

    @Redirect(
        method = "renderPKM",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;disableScissor()V"
        )
    )
    private void shadowedhearts$disableDisableScissor(GuiGraphics context) {
        // no-op to keep state unchanged since we never enabled scissor here
    }
}
