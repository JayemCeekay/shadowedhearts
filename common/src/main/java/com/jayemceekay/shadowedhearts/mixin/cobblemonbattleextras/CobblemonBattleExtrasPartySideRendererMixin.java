package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import name.modid.client.PartyInfoSideRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Only for >=1.7.41
// PartyInfoSideRenderer renders the left and right side Pokémon party bars
// The Mixin only changes the details from the tooltip
@Mixin(PartyInfoSideRenderer.class)
public class CobblemonBattleExtrasPartySideRendererMixin {

    // Mask attack name
    @WrapOperation(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lname/modid/client/PartyInfoSideRenderer;safeMoveDisplayName(Lcom/cobblemon/mod/common/api/moves/Move;)Ljava/lang/String;"
            )
    )
    private static String shadowedhearts$maskName(Move move, Operation<String> original, @Local(argsOnly = true) Pokemon pokemon) {
        if (ShadowAspectUtil.shouldMaskMove(pokemon, move)) {
            return "????";
        }
        return original.call(move);
    }

    // Mask attack color. Returning null makes it white
    @WrapOperation(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lname/modid/client/BattleMessageColorizer;getTypeColorByName(Ljava/lang/String;)Ljava/lang/Integer;"
            )
    )
    private static Integer shadowedhearts$maskColorName(String typeName, Operation<Integer> original, @Local(argsOnly = true) Pokemon pokemon, @Local(name = "move") Move move) {
        if (ShadowAspectUtil.shouldMaskMove(pokemon, move)) {
            return null;
        }
        return original.call(typeName);
    }

    // Mask PP text
    // 8th call of drawScaledText
    @ModifyArgs(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lname/modid/client/PartyInfoSideRenderer;drawScaledText(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIFZ)V",
                    ordinal = 7
            )
    )
    private static void shadowedhearts$maskCurrentPP(Args args, @Local(name = "move") Move move, @Local(argsOnly = true) Pokemon pokemon) {
        if (ShadowAspectUtil.shouldMaskMove(pokemon, move)) {
            args.set(2, "??/??");
        }
    }
}
