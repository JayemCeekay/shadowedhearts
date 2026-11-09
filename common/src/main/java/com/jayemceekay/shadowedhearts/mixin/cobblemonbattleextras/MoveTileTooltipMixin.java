package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasMovePresentation;
import com.jayemceekay.shadowedhearts.client.battle.ShadowBattleMoveDetails;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleMoveSelection.MoveTile.class, priority = 1100, remap = false)
public abstract class MoveTileTooltipMixin {
    // After the add-on resolves form/tera/extra types, it builds the damage estimate and text for this target.
    @Inject(method = "renderTooltipAtPosition", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lname/modid/client/BattleMessageSubscriber;getTeraType(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
    private void shadowedhearts$targetMatchup(CallbackInfo ci, @Local(name = "effectiveness") LocalFloatRef multiplier,
                                              @Local(name = "battlePokemon") ClientBattlePokemon target) {
        var tile = (BattleMoveSelection.MoveTile) (Object) this;
        if (ShadowMoveUtil.isShadowMove(tile.getMoveTemplate())) {
            multiplier.set(BattleExtrasMovePresentation.effectiveness(target));
        }
    }

    // Battle Extras adds this method to Cobblemon's tile for both mouse and controller tooltips.
    @WrapMethod(method = "renderTooltipAtPosition")
    private void shadowedhearts$hideLockedTooltip(GuiGraphics graphics, int x, int y, int anchorBottomY,
                                                  int[] tileBounds, Operation<Void> original) {
        if (!ShadowBattleMoveDetails.masked((BattleMoveSelection.MoveTile) (Object) this)) {
            original.call(graphics, x, y, anchorBottomY, tileBounds);
        }
    }
}
