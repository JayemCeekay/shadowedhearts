package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.client.battle.ShadowBattleMoveDetails;
import com.jayemceekay.shadowedhearts.client.gui.ShadowMaskingUtil;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "name.modid.client.PartyInfoSideRenderer", remap = false)
public abstract class PartyInfoSideRendererMixin {
    @ModifyExpressionValue(method = "renderTooltip", at = @At(value = "INVOKE",
            target = "Lname/modid/client/PartyInfoSideRenderer;safeMoveDisplayName(Lcom/cobblemon/mod/common/api/moves/Move;)Ljava/lang/String;"))
    private static String shadowedhearts$maskMoveName(String name, @Local(argsOnly = true) Pokemon pokemon,
                                                       @Local(name = "move") Move move,
                                                       @Share("lockedMove") LocalBooleanRef locked) {
        locked.set(ShadowUnlockPolicy.shouldMaskMove(pokemon, move));
        return locked.get() ? ShadowMaskingUtil.MASKED_NAME.getString() : name;
    }

    @ModifyExpressionValue(method = "renderTooltip", at = @At(value = "INVOKE",
            target = "Lname/modid/client/BattleMessageColorizer;getTypeColorByName(Ljava/lang/String;)Ljava/lang/Integer;"))
    private static Integer shadowedhearts$maskMoveColor(Integer color, @Share("lockedMove") LocalBooleanRef locked) {
        return locked.get() ? null : color;
    }

    @ModifyExpressionValue(method = "renderTooltip", at = @At(value = "INVOKE",
            target = "Lname/modid/client/PartyInfoSideRenderer;getPPColor(II)I"))
    private static int shadowedhearts$displayPowerPoints(int color, @Local(name = "ppText") LocalRef<String> ppText,
            @Share("lockedMove") LocalBooleanRef locked, @Local(argsOnly = true) Pokemon pokemon, @Local(name = "move") Move move) {
        if (locked.get()) {
            ppText.set(ShadowMaskingUtil.MASKED_PP.getString());
        } else if (ShadowBattleMoveDetails.unlimited(move.getTemplate(), pokemon)) {
            ppText.set(Component.translatable("ui.shadowedhearts.battle_rules.unlimited_pp").getString());
        } else {
            return color;
        }
        return 0xFFFFFF;
    }
}
