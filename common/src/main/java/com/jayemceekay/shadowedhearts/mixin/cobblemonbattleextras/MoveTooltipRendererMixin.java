package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasMovePresentation;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "name.modid.client.CustomTooltipRenderer", remap = false)
public abstract class MoveTooltipRendererMixin {
    @Inject(method = "renderTypeIcon", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$normalIcon(GuiGraphics gui, int x, int y, ElementalType type, CallbackInfo ci) {
        shadowedhearts$drawIcon(gui, x, y, type, 9, 18, ci);
    }

    @Inject(method = "renderTypeIconSmall", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$smallIcon(GuiGraphics gui, int x, int y, ElementalType type, CallbackInfo ci) {
        shadowedhearts$drawIcon(gui, x, y, type, 6, 18, ci);
    }

    @Inject(method = "renderTypeIconLarge", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$largeIcon(GuiGraphics gui, int x, int y, ElementalType type, CallbackInfo ci) {
        shadowedhearts$drawIcon(gui, x, y, type, 18, 36, ci);
    }

    @Unique
    private static void shadowedhearts$drawIcon(GuiGraphics gui, int x, int y, ElementalType type, int size,
                                               int textureSize, CallbackInfo ci) {
        var icon = BattleExtrasMovePresentation.icon(type.getName(), textureSize == 18);
        if (icon != null) {
            gui.blit(icon, x, y, size, size, 0, 0, textureSize, textureSize, textureSize, textureSize);
            ci.cancel();
        }
    }

    @ModifyArg(method = "renderClassicMoveTileForegroundOverlay", index = 3, at = @At(value = "INVOKE",
            target = "Lname/modid/client/CustomTooltipRenderer;drawScaledTextRightAligned"))
    private static int shadowedhearts$spaceShadowPp(int x, @Local(argsOnly = true) ElementalType type) {
        return ShadowMoveUtil.isShadowType(type) ? x + 10 : x;
    }
}
