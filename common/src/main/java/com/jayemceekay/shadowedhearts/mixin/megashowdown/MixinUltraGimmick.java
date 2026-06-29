package com.jayemceekay.shadowedhearts.mixin.megashowdown;

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.gimmick.UltraGimmick;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UltraGimmick.class, remap = false)
public class MixinUltraGimmick {

    @Inject(method = "ultraBurstInBattle", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockShadowUltraBurstInBattle(Pokemon pokemon, BattlePokemon battlePokemon, CallbackInfo ci) {
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowCanUltraBurst()) return;
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
            ci.cancel();
        }
    }

    @Inject(method = "canUltraBurst", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockShadowCanUltraBurst(Pokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        if (ShadowedHeartsConfigs.getInstance().getShadowConfig().shadowCanUltraBurst()) return;
        if (ShadowAspectUtil.hasShadowAspect(pokemon)) {
            cir.setReturnValue(false);
        }
    }
}
