package com.jayemceekay.shadowedhearts.mixin.megashowdown;

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.gimmick.MegaGimmick;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MegaGimmick.class, remap = false)
public class MixinMegaGimmick {

    @Inject(method = "megaEvolveInBattle", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockShadowMegaInBattle(Pokemon pokemon, BattlePokemon battlePokemon, CallbackInfo ci) {
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowCanMegaEvolve()) return;
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
            ci.cancel();
        }
    }

    @Inject(method = "canMega", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockShadowCanMega(Pokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowCanMegaEvolve()) return;
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
            cir.setReturnValue(false);
        }
    }
}
