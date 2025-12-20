package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSwapScreen;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Masks non-shadow moves in the Summary.MOVE_SWAP side screen entries
 * when the selected Pokemon is Shadow-locked. Hides move name and stats,
 * neutralizes the colored bar, and swaps the type icon to a shadow-locked glyph.
 */
@Mixin(value = MoveSwapScreen.MoveSlot.class, remap = false)
public abstract class MixinMoveSwapScreenMoveSlot {

    @Shadow public abstract MoveSwapScreen getPane();

    @Final
    @Shadow private MoveTemplate move;


    private boolean shadowedhearts$shouldMask() {
        if (move == null) return false;
        MoveSwapScreen pane = getPane();
        MovesWidget mw = pane.getMovesWidget();
        Summary summary = mw.getSummary();
        var pokemon = summary.getSelectedPokemon$common();
        if (pokemon == null) return false;
        String current = move.getName();
        if (ShadowGate.isShadowMoveId(current)) return false;

        int allowed = PokemonAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        int nonShadowIndex = 0;
        for (var mv : pokemon.getAllAccessibleMoves()) {
            if (mv == null) continue;
            String id = mv.getName();
            if (ShadowGate.isShadowMoveId(id)) continue;
            if (id.equalsIgnoreCase(current)) {
                return nonShadowIndex >= allowed;
            }
            nonShadowIndex++;
        }
        return false;
    }

    // Mask move name (ordinal 0)
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 0
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskName(MutableComponent original) {
        if (shadowedhearts$shouldMask()) {

            return Component.literal("????");
        }
        return original;
    }

    // Mask power (ordinal 1)
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 1
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskPower(MutableComponent original) {
        if (shadowedhearts$shouldMask()) {
            return Component.literal("??");
        }
        return original;
    }

    // Mask accuracy (ordinal 2)
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 2
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskAccuracy(MutableComponent original) {
        if (shadowedhearts$shouldMask()) {
            return Component.literal("??");
        }
        return original;
    }

    // Mask effect chance (ordinal 3)
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 3
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskEffectChance(MutableComponent original) {
        if (shadowedhearts$shouldMask()) {
            return Component.literal("??");
        }
        return original;
    }

    // Mask PP info (ordinal 4)
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 4
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskPP(MutableComponent original) {
        if (shadowedhearts$shouldMask()) {
            return Component.literal("PP ??");
        }
        return original;
    }

    // Neutralize tinted base bar when masked
    @WrapOperation(
            method = "render",
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
            int extra,
            Object marker,
            Operation<Void> original
    ) {
        if (shadowedhearts$shouldMask()) {
            red = 0.12F; green = 0.12F; blue = 0.12F; alpha = 1F;
        }
        original.call(poseStack, texture, x, y, height, width, uOffset, vOffset, textureWidth, textureHeight, blitOffset, red, green, blue, alpha, blend, scale, extra, marker);
    }

    // Swap the TypeIcon type to shadow-locked when masked
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/TypeIcon;<init>(Ljava/lang/Number;Ljava/lang/Number;Lcom/cobblemon/mod/common/api/types/ElementalType;Lcom/cobblemon/mod/common/api/types/ElementalType;ZZFFFILkotlin/jvm/internal/DefaultConstructorMarker;)V"
            ),
            index = 2
    )
    private ElementalType shadowedhearts$swapType(ElementalType original) {
        return shadowedhearts$shouldMask() ? ElementalTypes.INSTANCE.get("shadow-locked") : original;
    }
}
