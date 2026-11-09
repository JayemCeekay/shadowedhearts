package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasMovePresentation;
import com.jayemceekay.shadowedhearts.client.battle.ShadowBattleMoveDetails;
import com.jayemceekay.shadowedhearts.client.gui.ShadowMaskingUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "name.modid.client.CustomBattleController", remap = false)
public abstract class MixinBattleExtrasCustomMoveTiles {
    @ModifyArgs(method = "resolveMoveTileVisualDataUncached", at = @At(value = "INVOKE",
            target = "Lname/modid/client/CustomBattleController$MoveTileVisualData;<init>(Ljava/lang/String;IIZLjava/lang/String;ILjava/lang/String;II)V"))
    private static void shadowedhearts$prepareMoveTile(Args args, @Local(argsOnly = true) Object tile) {
        if (!(tile instanceof BattleMoveSelection.MoveTile move)) return;
        if (ShadowBattleMoveDetails.masked(move)) {
            args.set(0, ShadowMaskingUtil.MASKED_NAME.getString());
            args.set(1, 100); // Battle Extras' neutral PP color for hidden and unlimited values.
            args.set(2, 100);
            args.set(3, false);
            args.set(4, "???");
            args.set(5, 0);
            args.set(6, "shadow-locked");
            args.set(7, 0xFF202020);
        } else if (ShadowBattleMoveDetails.unlimited(move.getMoveTemplate(), move.getPokemon())) {
            args.set(1, 100);
            args.set(2, 100);
        }
    }

    @ModifyExpressionValue(method = "renderCustomMoveTile", at = @At(value = "INVOKE",
            target = "Lname/modid/client/CustomBattleController;formatMovePpText(II)Ljava/lang/String;"))
    private static String shadowedhearts$hideLockedPp(String pp, GuiGraphics graphics, Font font,
                                                       int x, int y, int width, int height, @Coerce Object data) {
        return "shadow-locked".equals(((BattleExtrasMoveTileDataAccessor) data).shadowedhearts$getMoveTypeName())
                ? ShadowMaskingUtil.MASKED_PP.getString() : pp;
    }

    @ModifyArg(method = "resolveMoveCategoryChipStyle", at = @At(value = "INVOKE",
            target = "Lname/modid/client/CustomBattleController$CategoryChipStyle;<init>(Ljava/lang/String;IIIII)V",
            ordinal = 2), index = 0)
    private static String shadowedhearts$hideLockedCategory(String label, @Local(argsOnly = true) String categoryKey) {
        return "???".equals(categoryKey) ? categoryKey : label;
    }

    @Inject(method = "renderMoveTypeBadgeIcon", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$drawTypeIcon(GuiGraphics gui, @Coerce Object data, int x, int y,
                                                     float opacity, CallbackInfo ci) {
        var texture = BattleExtrasMovePresentation.icon(((BattleExtrasMoveTileDataAccessor) data).shadowedhearts$getMoveTypeName(), false);
        if (texture != null) {
            gui.blit(texture, x, y, 12, 12, 0, 0, 36, 36, 36, 36);
            ci.cancel();
        }
    }
}
