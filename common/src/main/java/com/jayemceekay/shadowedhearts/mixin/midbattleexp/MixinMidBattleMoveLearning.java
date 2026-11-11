package com.jayemceekay.shadowedhearts.mixin.midbattleexp;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycle;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowLearnsetMoves;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@Pseudo
@Mixin(targets = "com.shier.midbattleexp.battle.MoveLearning", remap = false)
public abstract class MixinMidBattleMoveLearning {
    // An empty offer makes the add-on advance to the next Pokémon. Its separate free-slot
    // level-up and Showdown synchronization paths still run before this screen is built.
    @ModifyExpressionValue(method = "payloadFor", at = @At(value = "INVOKE",
            target = "Lcom/shier/midbattleexp/battle/MoveLearning$Offer;getMoves()Ljava/util/LinkedHashSet;"))
    private LinkedHashSet<MoveTemplate> shadowedhearts$filterOffer(LinkedHashSet<MoveTemplate> offered,
                                                                  @Local(argsOnly = true) Pokemon pokemon) {
        if (pokemon.getMoveSet().getMoves().stream().allMatch(move ->
                ShadowRestrictionPolicy.blocksMoveReplacement(pokemon, move.getTemplate()))) {
            return new LinkedHashSet<>();
        }
        var allowed = new LinkedHashSet<>(offered);
        allowed.removeIf(move -> !shadowedhearts$canLearn(pokemon, move));
        return allowed;
    }

    // The add-on validates its own packet, then writes each accepted slot directly.
    // Recheck the current party Pokémon here since its restrictions can change after the offer.
    @ModifyVariable(method = "apply", at = @At("HEAD"), argsOnly = true)
    private Map<Integer, MoveTemplate> shadowedhearts$filterReplacements(Map<Integer, MoveTemplate> changes,
                                                                        @Local(argsOnly = true) Pokemon pokemon) {
        var allowed = new LinkedHashMap<>(changes);
        allowed.entrySet().removeIf(change -> {
            Move outgoing = pokemon.getMoveSet().get(change.getKey());
            return outgoing == null || ShadowRestrictionPolicy.blocksMoveReplacement(pokemon, outgoing.getTemplate())
                    || !shadowedhearts$canLearn(pokemon, change.getValue());
        });
        return allowed;
    }

    @Unique
    private static boolean shadowedhearts$canLearn(Pokemon pokemon, MoveTemplate move) {
        return !MoveLifecycle.blocksLearning(pokemon, move) && ShadowLearnsetMoves.isLegal(pokemon, move, true);
    }

    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/moves/MoveSet;setMove(ILcom/cobblemon/mod/common/api/moves/Move;)V"))
    private void shadowedhearts$afterReplacement(MoveSet moves, int slot, Move incoming, Operation<Void> original,
                                                @Local(argsOnly = true) Pokemon pokemon) {
        MoveTemplate outgoing = moves.get(slot).getTemplate();
        original.call(moves, slot, incoming);
        MoveLifecycle.afterExchange(pokemon, outgoing);
    }
}
