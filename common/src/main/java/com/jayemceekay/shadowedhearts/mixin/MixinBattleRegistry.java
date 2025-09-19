package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.jayemceekay.shadowedhearts.SHAspects;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BattleRegistry.class, remap = false)
public class MixinBattleRegistry {

    @ModifyArg(method = "packTeam", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private static Object shadowedhearts$packTeam(Object original, @Local BattlePokemon pokemon) {
        boolean isShadow = false;
        try {
            var pk = pokemon.getEffectedPokemon();       // Kotlin: pokemon.effectedPokemon
            var aspects = pk.getAspects();               // Collection<String>
            isShadow = aspects != null && aspects.contains(SHAspects.SHADOW);
        } catch (Throwable ignored) {
        }
        String line = (String) original;
        // The Kotlin code just wrote the TeraType and a comma.
        // We append our optional 7th misc token now.
        return line + (isShadow ? "true," : "false,");
    }
}
