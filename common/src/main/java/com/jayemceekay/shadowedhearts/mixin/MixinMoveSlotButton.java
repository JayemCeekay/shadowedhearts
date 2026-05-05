package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.gui.interact.moveselect.MoveSlotButton;
import com.jayemceekay.shadowedhearts.util.ShadowMaskingUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Masks locked moves in the MoveSelectGUI (used by Shadow Scale).
 * When a move's template name is "shadow-locked", the name, PP, type icon,
 * and background tint are replaced with shadow-locked placeholders.
 */
@Mixin(value = MoveSlotButton.class, remap = false)
public abstract class MixinMoveSlotButton {

    @Final
    @Shadow
    private MoveTemplate move;

    @Unique
    private boolean shadowedhearts$isMasked() {
        return move != null && move.getName().equalsIgnoreCase("shadow-locked");
    }

    // Mask PP text (first drawScaledText call)
    @ModifyArg(
            method = "renderWidget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 0
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskPP(MutableComponent original) {
        if (shadowedhearts$isMasked()) {
            return ShadowMaskingUtil.MASKED_PP;
        }
        return original;
    }

    // Mask move name (second drawScaledText call)
    @ModifyArg(
            method = "renderWidget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 1
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskName(MutableComponent original) {
        if (shadowedhearts$isMasked()) {
            return ShadowMaskingUtil.MASKED_NAME;
        }
        return original;
    }

    // Neutralize the colored move bar tint when masked
    @WrapOperation(
            method = "renderWidget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;blitk$default(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;ZFILjava/lang/Object;)V"
            )
    )
    private void shadowedhearts$neutralizeTint(
            PoseStack poseStack,
            ResourceLocation texture,
            Number x,
            Number y,
            Number height,
            Number width,
            Number uOffset,
            Number vOffset,
            Number textureWidth,
            Number textureHeight,
            Number blitOffset,
            Number red,
            Number green,
            Number blue,
            Number alpha,
            boolean blend,
            float scale,
            int something,
            Object marker,
            Operation<Void> original
    ) {
        if (shadowedhearts$isMasked()) {
            red = ShadowMaskingUtil.NEUTRAL_TINT[0];
            green = ShadowMaskingUtil.NEUTRAL_TINT[1];
            blue = ShadowMaskingUtil.NEUTRAL_TINT[2];
            alpha = ShadowMaskingUtil.NEUTRAL_TINT[3];
        }
        original.call(poseStack, texture, x, y, height, width, uOffset, vOffset, textureWidth, textureHeight, blitOffset, red, green, blue, alpha, blend, scale, something, marker);
    }

    // Swap the type icon to shadow-locked
    @ModifyArg(
            method = "renderWidget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/TypeIcon;<init>(Ljava/lang/Number;Ljava/lang/Number;Lcom/cobblemon/mod/common/api/types/ElementalType;Lcom/cobblemon/mod/common/api/types/ElementalType;ZZFFFILkotlin/jvm/internal/DefaultConstructorMarker;)V"
            ),
            index = 2
    )
    private ElementalType shadowedhearts$swapType(ElementalType original) {
        if (shadowedhearts$isMasked()) {
            return ShadowMaskingUtil.getLockedType();
        }
        return original;
    }
}
