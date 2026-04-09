package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import name.modid.client.PartyInfoSideRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Only for >=1.7.41
// PartyInfoSideRenderer renders the left and right side Pokémon party bars
// The Mixin only changes the details from the tooltip
@Mixin(PartyInfoSideRenderer.class)
public class CobblemonBattleExtrasPartySideRendererMixin {

    @Unique
    private static boolean shadowedhearts$shouldMask(Move m, Pokemon pokemon) {
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

    // Mask attack name
    @WrapOperation(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lname/modid/client/PartyInfoSideRenderer;safeMoveDisplayName(Lcom/cobblemon/mod/common/api/moves/Move;)Ljava/lang/String;"
            )
    )
    private static String shadowedhearts$maskName(Move move, Operation<String> original, @Local(argsOnly = true) Pokemon pokemon) {
        if (shadowedhearts$shouldMask(move, pokemon)) {
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
        if (shadowedhearts$shouldMask(move, pokemon)) {
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
        if (shadowedhearts$shouldMask(move, pokemon)) {
            args.set(2, "??/??");
        }
    }
}
