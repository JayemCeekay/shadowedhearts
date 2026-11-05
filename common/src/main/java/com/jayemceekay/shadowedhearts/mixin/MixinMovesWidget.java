package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveDescriptionScrollList;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import com.jayemceekay.shadowedhearts.util.ShadowMaskingUtil;
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

@Mixin(value = MovesWidget.class, remap = false)
public abstract class MixinMovesWidget {

    @Shadow
    public abstract @Nullable Move getSelectedMove();

    @Shadow private MoveDescriptionScrollList descriptionScrollList;
    @Unique private Move shadowedhearts$describedMove;
    @Unique private boolean shadowedhearts$descriptionMasked;

    @Unique
    private boolean shadowedhearts$shouldMask(Move m, Pokemon pokemon) {
        return ShadowAspectUtil.shouldMaskMove(pokemon, m);
    }

    @Inject(method = "reorderMove", remap = false, at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$disableReorder(
            MoveSlotWidget move, boolean up, CallbackInfo ci
    ) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (ShadowRestrictionPolicy.blocksMoveChanges(pokemon)) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "selectMove", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/moves/MoveDescriptionScrollList;setMoveDescription(Lnet/minecraft/network/chat/MutableComponent;)V"))
    private @NotNull MutableComponent shadowedhearts$maskMoveDescription(
            @NotNull MutableComponent moveDescription
    ) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        shadowedhearts$describedMove = getSelectedMove();
        shadowedhearts$descriptionMasked = shadowedhearts$shouldMask(shadowedhearts$describedMove, pokemon);
        if (shadowedhearts$descriptionMasked) {
            return ShadowMaskingUtil.MASKED_DESC;
        }
        return moveDescription;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void shadowedhearts$refreshDescriptionAfterVisibilityChange(CallbackInfo ci) {
        Move selected = getSelectedMove();
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        boolean masked = shadowedhearts$shouldMask(selected, pokemon);
        if (selected == shadowedhearts$describedMove && masked == shadowedhearts$descriptionMasked) return;

        // Rebuild only when visibility changes so scrolling is preserved during normal rendering.
        descriptionScrollList.setMoveDescription(selected == null ? Component.empty()
                : masked ? ShadowMaskingUtil.MASKED_DESC
                : selected.getDescription());
        shadowedhearts$describedMove = selected;
        shadowedhearts$descriptionMasked = masked;
    }

    @ModifyVariable(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At(value = "STORE"), name = "movePower")
    private MutableComponent shadowedhearts$maskMovePower(MutableComponent value) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (shadowedhearts$shouldMask(getSelectedMove(), pokemon)) {
            return ShadowMaskingUtil.MASKED_VAL;
        }
        var rules = ShadowBattleClientState.INSTANCE.current();
        Move selected = getSelectedMove();
        if (rules != null && rules.colosseumRush() && selected != null && "shadowrush".equalsIgnoreCase(selected.getName())) {
            return Component.literal("90");
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
