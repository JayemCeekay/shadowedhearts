package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import name.modid.client.CustomTooltipRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Only for >=1.7.41
// CustomTooltipRenderer renders the attack tooltip when selecting it
@Mixin(CustomTooltipRenderer.class)
public class CobblemonBattleExtrasCustomTooltipRendererMixin {

    // Render shadow icon
    @WrapMethod(method = "renderTypeIcon")
    private static void shadowedhearts$renderTypeIcon(GuiGraphics graphics, int x, int y, ElementalType type, Operation<Void> original) {
        if (type.equals(ElementalTypes.get("shadow"))) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/shadow_type_small.png");
            int ICON_SIZE = 18;
            int RENDER_SIZE = 9;
            graphics.blit(texture, x, y, RENDER_SIZE, RENDER_SIZE, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            return;
        }
        original.call(graphics, x, y, type);
    }

    // Render shadow icon
    @WrapMethod(method = "renderTypeIconLarge")
    private static void shadowedhearts$renderTypeIconLarge(GuiGraphics graphics, int x, int y, ElementalType type, Operation<Void> original) {
        if (type.equals(ElementalTypes.get("shadow"))) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/shadow_type.png");
            int iconSize = 36;
            int renderSize = 18;
            graphics.blit(texture, x, y, renderSize, renderSize, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
            return;
        }
        original.call(graphics, x, y, type);
    }

    // Render shadow icon
    @WrapMethod(method = "renderTypeIconSmall")
    private static void shadowedhearts$renderTypeIconSmall(GuiGraphics graphics, int x, int y, ElementalType type, Operation<Void> original) {
        if (type.equals(ElementalTypes.get("shadow"))) {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("shadowedhearts", "textures/gui/shadow_type_small.png");
            int ICON_SIZE = 18;
            int RENDER_SIZE = 6;
            graphics.blit(texture, x, y, RENDER_SIZE, RENDER_SIZE, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            return;
        }
        original.call(graphics, x, y, type);
    }

    // Move the PP text a bit if the type is Shadow so it doesn't overlap with the bright area of the image
    @ModifyArgs(method = "renderClassicMoveTileForegroundOverlay",
            at = @At(
                    value = "INVOKE",
                    target = "Lname/modid/client/CustomTooltipRenderer;drawScaledTextRightAligned(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIF)V"
            ))
    private static void shadowedhearts$movePPText(Args args, @Local(argsOnly = true) ElementalType type) {
        if (type.equals(ElementalTypes.get("shadow"))) {
            int x = args.get(3);
            args.set(3, x + 10);
        }
    }

}
