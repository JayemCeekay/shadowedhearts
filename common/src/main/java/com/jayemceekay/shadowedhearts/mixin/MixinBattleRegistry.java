package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeBattleSync;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BattleRegistry.class, remap = false)
public class MixinBattleRegistry {

    @ModifyArg(method = "packTeam", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private static Object shadowedhearts$packTeam(Object original, @Local BattlePokemon pokemon) {
        boolean isShadow = false;
        boolean playerControlled = false;
        boolean isHyper = false;
        boolean isReverse = false;
        int heartGaugeBars = 0;
        double heartGaugeFraction = 0.0;
        try {
            var pk = pokemon.getEffectedPokemon();       // Kotlin: pokemon.effectedPokemon
            var aspects = pk.getAspects();               // Collection<String>
            isShadow = aspects != null && aspects.contains(SHAspects.SHADOW);
            if (aspects != null) {
                isHyper = aspects.contains(SHAspects.HYPER_MODE);
                isReverse = aspects.contains(SHAspects.REVERSE_MODE);
            }
            // Stored state is authoritative for the whole team, including benched members
            // with no live entity. Mode probabilities and damage share the same gauge.
            heartGaugeBars = ShadowHeartGauge.getBarsRemaining(pk);
            heartGaugeFraction = HeartGaugeBattleSync.fraction(pk);
        } catch (Throwable ignored) {
            heartGaugeBars = isShadow ? 5 : 0;
            heartGaugeFraction = isShadow ? 1.0 : 0.0;
        }
        try {
            playerControlled = pokemon.getActor().getType() == com.cobblemon.mod.common.api.battles.model.actor.ActorType.PLAYER;
        } catch (kotlin.UninitializedPropertyAccessException ignored) {
            // Standalone team packing can precede actor assignment. Preserve all other metadata.
        }
        String line = (String) original;
        // The Kotlin code just wrote the TeraType and a comma.
        // Append the 7th-12th misc tokens, retaining the exact gauge separately from display bars.
        return line
                + (isShadow ? "true," : "false,")
                + heartGaugeBars + ","
                + (isHyper ? "true," : "false,")
                + (isReverse ? "true," : "false,")
                + heartGaugeFraction + ","
                + (playerControlled ? "true," : "false,");
    }
}
