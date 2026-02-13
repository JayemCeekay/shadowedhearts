package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.jayemceekay.shadowedhearts.ShadowAspectUtil;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BattleRegistry.class, remap = false)
public class MixinBattleRegistry {

    @ModifyArg(method = "packTeam", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private static Object shadowedhearts$packTeam(Object original, @Local BattlePokemon pokemon) {
        boolean isShadow = false;
        boolean isHyper = false;
        boolean isReverse = false;
        int heartGaugeBars = 0;
        try {
            var pk = pokemon.getEffectedPokemon();       // Kotlin: pokemon.effectedPokemon
            var aspects = pk.getAspects();               // Collection<String>
            isShadow = aspects != null && aspects.contains(SHAspects.SHADOW);
            if (aspects != null) {
                isHyper = aspects.contains(SHAspects.HYPER_MODE);
                isReverse = aspects.contains(SHAspects.REVERSE_MODE);
            }
            // Try to compute heart gauge bars from the live entity if present
            PokemonEntity entity = pokemon.getEntity();
            if (entity != null) {
                heartGaugeBars = ShadowAspectUtil.getBarsRemaining(entity.getPokemon());
            } else {
                // Fallback sensible default: fully closed if shadow, 0 otherwise
                heartGaugeBars = isShadow ? 5 : 0;
            }
        } catch (Throwable ignored) {
            heartGaugeBars = isShadow ? 5 : 0;
        }
        String line = (String) original;
        // The Kotlin code just wrote the TeraType and a comma.
        // We append our optional 7th-10th misc tokens now: isShadow, heartGaugeBars, isHyper, isReverse
        return line
                + (isShadow ? "true," : "false,")
                + heartGaugeBars + ","
                + (isHyper ? "true," : "false,")
                + (isReverse ? "true," : "false,");
    }
}
