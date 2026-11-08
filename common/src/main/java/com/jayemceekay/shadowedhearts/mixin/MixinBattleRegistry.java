package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.heart.HeartGaugeBattleSync;
import com.jayemceekay.shadowedhearts.common.shadow.RetainedShadowMoves;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.data.ShadowLearnsets;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(value = BattleRegistry.class, remap = false)
public class MixinBattleRegistry {

    /** Apply ordinary-state eligibility to both native packed fields (move IDs and their PP). */
    @ModifyExpressionValue(method = "packTeam", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/moves/MoveSet;getMoves()Ljava/util/List;"), require = 2)
    private List<Move> shadowedhearts$battleMoves(List<Move> moves, @Local Pokemon pokemon) {
        if (moves.isEmpty() || ShadowAspectUtil.hasShadowAspect(pokemon)) return moves;
        var profile = ShadowLearnsets.get(pokemon);
        if (profile == null) return moves;
        var allowed = moves.stream().filter(move -> !ShadowMoveUtil.isShadowMove(move)
                || profile.permits(move.getName(), pokemon.getLevel(), false)
                || RetainedShadowMoves.knows(pokemon, move.getName())).toList();
        // An old save or an operator-authored set might consist entirely of ineligible moves.
        return allowed.isEmpty() ? List.of(Moves.INSTANCE.getByName("struggle").create()) : allowed;
    }

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
