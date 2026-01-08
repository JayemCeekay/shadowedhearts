package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.class, remap = false)
public abstract class MixinMovesWidget {

    @Shadow
    public abstract @Nullable Move getSelectedMove();

    @Unique
    private boolean shadowedhearts$shouldMask(Move m, Pokemon pokemon) {
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

    @Inject(method = "reorderMove", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$disableReorder(
            MoveSlotWidget move, boolean up, CallbackInfo ci
    ) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (ShadowGate.isShadowLockedClient(pokemon)) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "selectMove", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/moves/MoveDescriptionScrollList;setMoveDescription(Lnet/minecraft/network/chat/MutableComponent;)V"))
    private @NotNull MutableComponent shadowedhearts$maskMoveDescription(
            @NotNull MutableComponent moveDescription,
            @Local(argsOnly = true) Move move
    ) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(move, pokemon)) {
            return Component.literal("?????");
        }
        return moveDescription;
    }

    @ModifyVariable(method = "renderWidget", at = @At(value = "STORE"), name = "movePower")
    private MutableComponent shadowedhearts$maskMovePower(MutableComponent value) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(getSelectedMove(), pokemon)) {
            return Component.literal("???");
        }
        return value;
    }

    @ModifyVariable(method = "renderWidget", at = @At(value = "STORE"), name = "moveAccuracy")
    private MutableComponent shadowedhearts$maskMoveAccuracy(MutableComponent value) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(getSelectedMove(), pokemon)) {
            return Component.literal("???");
        }
        return value;
    }

    @ModifyVariable(method = "renderWidget", at = @At(value = "STORE"), name = "moveEffect")
    private MutableComponent shadowedhearts$maskMoveEffect(MutableComponent value) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(getSelectedMove(), pokemon)) {
            return Component.literal("???");
        }
        return value;
    }
}