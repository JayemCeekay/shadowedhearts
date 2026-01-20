package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = MovesWidget.class, remap = false)
public interface MovesWidgetAccessor {
    @Accessor("moves")
    List<MoveSlotWidget> getMoves();
}
