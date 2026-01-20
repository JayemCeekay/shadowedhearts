package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.jayemceekay.shadowedhearts.ShadowGate;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(value = BattleMoveSelection.MoveTile.class, remap = false)
public abstract class CobblemonBattleExtrasMoveTileTooltipMixin {

    @WrapMethod(
            method = "renderTooltipAtPosition"
    )
    private void shadowedhearts$cancelShadowTooltip(
            GuiGraphics context, int tooltipX, int tooltipY, Operation<Void> original
    ) {
        BattleMoveSelection.MoveTile self = (BattleMoveSelection.MoveTile) (Object) this;

        if (shadowedhearts$shouldMaskTooltip(self)) {
            // Cancel the call to prevent the tooltip from rendering
            return;
        }
        original.call(context, tooltipX, tooltipY);
    }

    private static boolean shadowedhearts$shouldMaskTooltip(BattleMoveSelection.MoveTile self) {
        InBattleMove m = self.getMove();
        var pokemon = self.getPokemon();
        if (m == null || pokemon == null) return false;

        return m.getDisabled()
                && ShadowGate.isShadowLockedClient(pokemon)
                && !ShadowGate.isShadowMoveId(m.getId());
    }
}