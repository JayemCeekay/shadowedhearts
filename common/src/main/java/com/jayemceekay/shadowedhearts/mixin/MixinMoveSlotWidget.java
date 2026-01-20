package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Obfuscate locked moves in the Summary -> Moves screen.
 * When a Pokemon is Shadow-locked, non-Shadow moves have their name and PP masked.
 * Also neutralize the colored move bar tint and swap the TypeIcon to a shadow-locked placeholder.
 */
@Mixin(value = MoveSlotWidget.class, remap = false)
public abstract class MixinMoveSlotWidget {

    @Shadow public abstract Move getMove();

    // Kotlin `private val pokemon: Pokemon` – shadow the backing field by name
    @Final
    @Shadow private com.cobblemon.mod.common.pokemon.Pokemon pokemon;

    @Unique
    private boolean shadowedhearts$shouldMask() {
        Move m = this.getMove();
        if (m == null || pokemon == null) return false;
        if (ShadowGate.isShadowMoveId(m.getName())) return false; // Shadow moves always visible

        // Compute this move's index among non-Shadow moves in move order
        int nonShadowIndex = 0;
        int allowed = PokemonAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        for (var mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null) continue;
            if (ShadowGate.isShadowMoveId(mv.getName())) continue;
            if (mv == m) {
                // If this move's position is at or beyond allowed, mask it
                return nonShadowIndex >= allowed;
            }
            nonShadowIndex++;
        }
        return false;
    }

    // Mask PP text (first drawScaledText call in MoveSlotWidget.renderWidget)
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
        if (shadowedhearts$shouldMask()) {
            return Component.literal("??/??").copy().withStyle(s -> s.withBold(true));
        }
        return original;
    }

    // Mask move name (second drawScaledText call in MoveSlotWidget.renderWidget)
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
        if (shadowedhearts$shouldMask()) {
            return Component.literal("????").copy().withStyle(s -> s.withBold(true));
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
        if (shadowedhearts$shouldMask()) {
            // If the call attempts to tint (rgb != 1), override to a neutral dark gray.
           // float r = red.floatValue();
           // float g = green.floatValue();
           // float b = blue.floatValue();
            //if (!(r == 1F && g == 1F && b == 1F)) {
                red = 0.12F; green = 0.12F; blue = 0.12F; alpha = 1F;
         //   }
        }
        original.call(poseStack, texture, x, y, height, width, uOffset, vOffset, textureWidth, textureHeight, blitOffset, red, green, blue, alpha, blend, scale, something, marker);
    }

    // Swap the type icon to a shadow-locked placeholder when masked
    @ModifyArg(
            method = "renderWidget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/TypeIcon;<init>(Ljava/lang/Number;Ljava/lang/Number;Lcom/cobblemon/mod/common/api/types/ElementalType;Lcom/cobblemon/mod/common/api/types/ElementalType;ZZFFFILkotlin/jvm/internal/DefaultConstructorMarker;)V"
            ),
            index = 2
    )
    private ElementalType shadowedhearts$swapType(ElementalType original) {
        return shadowedhearts$shouldMask() ? ElementalTypes.get("shadow-locked") : original;
    }

   /* @WrapMethod(method = "renderMoveTooltipWithIcons")
    private void shadowedhearts$cancelShadowTooltip(
            GuiGraphics context, int tooltipX, int tooltipY, Operation<Void> original
    ) {
        if (shadowedhearts$shouldMask()) {
            // Cancel the call to prevent the tooltip from rendering
            return;
        }
        original.call(context, tooltipX, tooltipY);
    }*/


}
