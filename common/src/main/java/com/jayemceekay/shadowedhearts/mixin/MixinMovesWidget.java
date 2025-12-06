package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget;
import com.jayemceekay.shadowedhearts.ShadowGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.class, remap = false)
public abstract class MixinMovesWidget {

    @Inject(method = "reorderMove", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$disableReorder(
            MoveSlotWidget move, boolean up, CallbackInfo ci
    ) {
        var pokemon = ((MovesWidget)(Object)this).getSummary().getSelectedPokemon$common();
        if (ShadowGate.isShadowLockedClient(pokemon)) {
            ci.cancel();
        }
    }
}