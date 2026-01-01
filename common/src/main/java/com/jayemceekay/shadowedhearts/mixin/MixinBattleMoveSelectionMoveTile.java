package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BattleMoveSelection.MoveTile.class, remap = false)
public abstract class MixinBattleMoveSelectionMoveTile {


    private static boolean shadowedhearts$shouldMask(BattleMoveSelection.MoveTile self) {
        InBattleMove m = self.getMove();
        var pokemon = self.getPokemon();
        if (m == null || pokemon == null) return false;
        boolean forcedPlaceholder = (m.getPp() == 100 && m.getMaxpp() == 100); // Thrash/forced turn UI
        return m.getDisabled()
                && !forcedPlaceholder
                && ShadowGate.isShadowLockedClient(pokemon)
                && !ShadowGate.isShadowMoveId(m.getId());
    }

    // Replace the move name text when masked
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    // Kotlin top-level function drawScaledText; compiled owner typically:
                    // Lcom/cobblemon/mod/common/client/render/RenderExtensionsKt;drawScaledText(...)
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 0 // first call: move name
            ),
            index = 2 // argument index of Component in the draw call
    )
    private MutableComponent shadowedhearts$maskName(MutableComponent par3) {
        BattleMoveSelection.MoveTile self = (BattleMoveSelection.MoveTile) (Object) this;
        InBattleMove m = self.getMove();
        var pokemon = self.getPokemon();
        boolean forcedPlaceholder = (m.getPp() == 100 && m.getMaxpp() == 100);
        if (m.getDisabled() && !forcedPlaceholder && pokemon != null
                && ShadowGate.isShadowLockedClient(pokemon)
                && !ShadowGate.isShadowMoveId(m.getId())) {
            return Component.literal("????").copy().withStyle(s -> s.withBold(true));
        }
        return par3;
    }

    // Replace the PP text when masked
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V",
                    ordinal = 1 // second call: PP text
            ),
            index = 2
    )
    private MutableComponent shadowedhearts$maskPP(MutableComponent original) {
        BattleMoveSelection.MoveTile self = (BattleMoveSelection.MoveTile) (Object) this;
        InBattleMove m = self.getMove();
        var pokemon = self.getPokemon();
        if (m.getDisabled() || ShadowGate.isShadowMoveId(m.getId()) && pokemon != null
                && ShadowGate.isShadowLockedClient(pokemon)) {
            return Component.literal("??/??").copy().withStyle(s -> s.withBold(true));
        }
        return original;
    }

    // Wrap the colored base draw, neutralize RGB and reduce alpha when masked
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;blitk$default(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;ZFILjava/lang/Object;)V"
            )
    )
    private void shadowedhearts$neutralizeColor(
            PoseStack poseStack,
            ResourceLocation resourceLocation,
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
            boolean b, float v, int i, Object o, Operation<Void> original
    ) {
        BattleMoveSelection.MoveTile self = (BattleMoveSelection.MoveTile) (Object) this;
        if (shadowedhearts$shouldMask(self)) {
            // Neutral gray/black; pick one. Example: dark gray with lower alpha.
            red = 0.12f; green = 0.12f; blue = 0.12f; // ~#1F1F1F
            alpha = 1F;
        }
        original.call(poseStack, resourceLocation, x, y, height, width, uOffset, vOffset, textureWidth, textureHeight, blitOffset, red, green, blue, alpha, b, v, i, o);
    }

    // Optional: dim the type icon too (purely visual)
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/TypeIcon;render(Lnet/minecraft/client/gui/GuiGraphics;)V"
            )
    )
    private void shadowedhearts$dimTypeIcon(TypeIcon instance, GuiGraphics offsetX, Operation<Void> original) {
        BattleMoveSelection.MoveTile self = (BattleMoveSelection.MoveTile) (Object) this;
        if (shadowedhearts$shouldMask(self)) {
            // Reduce the MoveTile's overall opacity path by editing its field via accessor if desired,
            // or leave as-is — the neutral bar is already a strong cue.
        }
        original.call(instance, offsetX);
    }

    // Replace the ElementalType passed to the TypeIcon constructor for locked moves to our shadow-locked type
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/gui/TypeIcon;<init>(Ljava/lang/Number;Ljava/lang/Number;Lcom/cobblemon/mod/common/api/types/ElementalType;Lcom/cobblemon/mod/common/api/types/ElementalType;ZZFFFILkotlin/jvm/internal/DefaultConstructorMarker;)V"
            ),
            index = 2
    )
    private ElementalType shadowedhearts$swapTypeIconWhenLocked(ElementalType original) {
        BattleMoveSelection.MoveTile self = (BattleMoveSelection.MoveTile) (Object) this;
        return shadowedhearts$shouldMask(self) ? ElementalTypes.INSTANCE.get("shadow-locked") : original;
    }
}