package com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras;


import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "party.extras.cobblemon.client.tooltip.MoveTooltipBuilder")
public class MixinCobblemonPartyExtrasMoveTooltipBuilder {


    @Unique
    private static boolean shadowedhearts$shouldMask(MoveTemplate m, Pokemon pokemon) {
        if (m == null || pokemon == null) return false;
        if (ShadowGate.isShadowMoveId(m.getName()))
            return false; // Shadow moves always visible

        // Compute this move's index among non-Shadow moves in move order
        int nonShadowIndex = 0;
        int allowed = PokemonAspectUtil.getAllowedVisibleNonShadowMoves(pokemon);
        for (var mv : pokemon.getMoveSet().getMovesWithNulls()) {
            if (mv == null) continue;
            if (ShadowGate.isShadowMoveId(mv.getName())) continue;
            if (mv.getTemplate() == m) {
                // If this move's position is at or beyond allowed, mask it
                return nonShadowIndex >= allowed;
            }
            nonShadowIndex++;
        }
        return false;
    }


    @Inject(method = "buildTooltip", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockMoveTooltip(MoveTemplate moveTemplate, int currentPP, int maxPP, Pokemon pokemon, CallbackInfoReturnable<List<Component>> cir) {
        if (shadowedhearts$shouldMask(moveTemplate, pokemon)) {
            cir.cancel();
        }
    }
}
