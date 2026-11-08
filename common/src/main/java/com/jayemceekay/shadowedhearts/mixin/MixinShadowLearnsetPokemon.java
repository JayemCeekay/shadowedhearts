package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowLearnsetMoves;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(value = Pokemon.class, remap = false)
public abstract class MixinShadowLearnsetPokemon {
    /** Both the client picker and the server bench handler use this getter. */
    @ModifyReturnValue(method = "getAllAccessibleMoves", at = @At("RETURN"))
    private Set<MoveTemplate> shadowedhearts$accessibleMoves(Set<MoveTemplate> original) {
        return ShadowLearnsetMoves.accessibleMoves((Pokemon) (Object) this, original);
    }

    // Cobblemon compares old and new level sets. Its free-slot behavior, XP buffering and learn notifications
    // all follow naturally, including gains across multiple levels.
    @WrapOperation(method = {"addExperience", "initializeMoveset"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;getLevelUpMovesUpTo(I)Ljava/util/Set;"))
    private Set<MoveTemplate> shadowedhearts$levelMoves(Learnset learnset, int level,
                                                        Operation<Set<MoveTemplate>> original) {
        return ShadowLearnsetMoves.levelMoves((Pokemon) (Object) this, level, original.call(learnset, level));
    }

    @WrapOperation(method = "validateMoveset$lambda$0$0", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/moves/LearnsetQuery;canLearn(Lcom/cobblemon/mod/common/api/moves/MoveTemplate;Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;)Z"))
    private static boolean shadowedhearts$validateProfileMove(LearnsetQuery query, MoveTemplate move, Learnset learnset,
                                                              Operation<Boolean> original,
                                                              @Local(argsOnly = true) Pokemon pokemon) {
        return ShadowLearnsetMoves.isLegal(pokemon, move, original.call(query, move, learnset));
    }

    @WrapOperation(method = "updateMovesOnFormChange", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/moves/LearnsetQuery;canLearn(Lcom/cobblemon/mod/common/api/moves/MoveTemplate;Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;)Z"))
    private boolean shadowedhearts$validateFormMove(LearnsetQuery query, MoveTemplate move, Learnset learnset,
                                                    Operation<Boolean> original) {
        return ShadowLearnsetMoves.isLegal((Pokemon) (Object) this, move, original.call(query, move, learnset));
    }
}
