package com.jayemceekay.shadowedhearts.mixin.cobblemonpartyextras;


import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.PokemonAspectUtil;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "party.extras.cobblemon.client.tooltip.NatureTooltipBuilder")
public class MixinCobblemonPartyExtrasNatureTooltipBuilder {

    @Inject(method = "buildNatureTooltip", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$blockNatureTooltip(CallbackInfoReturnable<String> cir, @Local(argsOnly = true) Pokemon pokemon) {
        if(PokemonAspectUtil.hasShadowAspect(pokemon)) {
            cir.cancel();
        }
    }
}
