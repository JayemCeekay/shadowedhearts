package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.util.ShadowMaskingUtil;
import com.llamalad7.mixinextras.sugar.Local;
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

@Mixin(value = MovesWidget.class, remap = false)
public abstract class MixinMovesWidget {

    @Shadow
    public abstract @Nullable Move getSelectedMove();

    @Unique
    private boolean shadowedhearts$shouldMask(Move m, Pokemon pokemon) {
        return ShadowAspectUtil.shouldMaskMove(pokemon, m);
    }

    @Inject(method = "reorderMove", remap = false, at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$disableReorder(
            MoveSlotWidget move, boolean up, CallbackInfo ci
    ) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
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
            return ShadowMaskingUtil.MASKED_DESC;
        }
        return moveDescription;
    }

    @ModifyVariable(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "STORE"), name = "movePower")
    private MutableComponent shadowedhearts$maskMovePower(MutableComponent value) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(getSelectedMove(), pokemon)) {
            return ShadowMaskingUtil.MASKED_VAL;
        }
        return value;
    }

    @ModifyVariable(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "STORE"), name = "moveAccuracy")
    private MutableComponent shadowedhearts$maskMoveAccuracy(MutableComponent value) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(getSelectedMove(), pokemon)) {
            return ShadowMaskingUtil.MASKED_VAL;
        }
        return value;
    }

    @ModifyVariable(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "STORE"), name = "moveEffect")
    private MutableComponent shadowedhearts$maskMoveEffect(MutableComponent value) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(getSelectedMove(), pokemon)) {
            return ShadowMaskingUtil.MASKED_VAL;
        }
        return value;
    }
}