package com.jayemceekay.shadowedhearts.mixin.yacl;

import com.jayemceekay.shadowedhearts.client.gui.config.YaclSettingsScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.isxander.yacl3.gui.LowProfileButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Align our topic headings with one another, immediately after the disclosure button. */
@Pseudo
@Mixin(targets = "dev.isxander.yacl3.gui.OptionListWidget$GroupSeparatorEntry", remap = false)
abstract class MixinSettingsGroupHeading {
    @Shadow @Final protected Screen screen;
    @Shadow @Final protected LowProfileButtonWidget expandMinimizeButton;

    @WrapOperation(method = "renderContent", require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/components/MultiLineLabel;renderCentered(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void shadowedhearts$alignHeading(MultiLineLabel label, GuiGraphics graphics, int center, int y,
                                             Operation<Void> original) {
        if (YaclSettingsScreen.isSettingsScreen(screen)) {
            int x = expandMinimizeButton.getX() + expandMinimizeButton.getWidth() + 3;
            label.renderLeftAligned(graphics, x, y, Minecraft.getInstance().font.lineHeight, 0xFFFFFFFF);
        } else {
            original.call(label, graphics, center, y);
        }
    }
}
