package com.jayemceekay.shadowedhearts.mixin.cobblemonbattleextras;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.client.battle.BattleExtrasSwitchMove;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.battle.ShadowBattleClientState;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowUnlockPolicy;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

@Pseudo
@Mixin(targets = "name.modid.client.CustomBattleController", remap = false)
public abstract class MixinBattleExtrasSwitchMoves {
    @ModifyReturnValue(method = "buildSwitchTooltipCacheKey", at = @At("RETURN"))
    private static String shadowedhearts$includeShadowSettings(String key, Object source) {
        if (key == null || !(source instanceof Pokemon pokemon) || !ShadowAspectUtil.hasShadowAspect(pokemon)) return key;
        var rules = ShadowBattleClientState.INSTANCE.current();
        return key + "|shadowMoves=" + ShadowUnlockPolicy.getAllowedVisibleNonShadowMoves(pokemon)
                + "|unlimited=" + (rules != null && rules.unlimitedShadowPP());
    }

    @Inject(method = "getMovesWithPPFromPokemon", at = @At("RETURN"))
    private static void shadowedhearts$prepareSwitchRows(Object source, CallbackInfoReturnable<List<?>> cir) {
        if (source instanceof Pokemon pokemon) {
            for (Object row : cir.getReturnValue()) {
                ((BattleExtrasSwitchMove) row).shadowedhearts$prepareSwitchMove(pokemon);
            }
        }
    }

    @ModifyArgs(method = {"renderClassicSwitchMovesTooltip", "renderSwitchMovesTooltip"},
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"),
            require = 2)
    private static void shadowedhearts$switchTypeIcon(Args args, @Local(name = "moveInfos") List<?> rows,
                                                       @Local(name = {"moveIndex", "i"}) int index) {
        var texture = ((BattleExtrasSwitchMove) rows.get(index)).shadowedhearts$switchIcon();
        if (texture != null) {
            args.set(0, texture);
            args.set(9, 36); // Each Shadow icon occupies one 36-by-36 texture.
        }
    }
}
