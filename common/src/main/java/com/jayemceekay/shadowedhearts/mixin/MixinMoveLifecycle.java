package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.MovesetBuilder;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.MoveLifecycle;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Rules attached to earned moves, without changing native moves on unrelated Pokémon. */
@Mixin(value = Pokemon.class, remap = false)
public abstract class MixinMoveLifecycle {
    @WrapMethod(method = "exchangeMove")
    private boolean shadowedhearts$exchangeLifecycleMove(MoveTemplate oldMove, MoveTemplate newMove, Operation<Boolean> original) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (MoveLifecycle.blocksReplacement(pokemon, oldMove) || MoveLifecycle.blocksLearning(pokemon, newMove)) return false;
        boolean changed = original.call(oldMove, newMove);
        if (changed) MoveLifecycle.afterExchange(pokemon, oldMove);
        return changed;
    }

    @WrapMethod(method = "validateMoveset")
    private void shadowedhearts$preserveValidatedRewards(boolean includeLegacy, Operation<Void> original) {
        MoveLifecycle.preserveEarnedMoves((Pokemon) (Object) this, () -> original.call(includeLegacy));
    }

    @WrapMethod(method = "unlearnMove")
    private void shadowedhearts$unlearnLifecycleMove(MoveTemplate move, Operation<Void> original) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (MoveLifecycle.blocksReplacement(pokemon, move)) return;
        original.call(move);
        MoveLifecycle.afterExchange(pokemon, move);
    }

    @WrapMethod(method = "updateMovesOnFormChange")
    private void shadowedhearts$preserveEvolvedRewards(FormData oldForm, Operation<Void> original) {
        MoveLifecycle.preserveEarnedMoves((Pokemon) (Object) this, () -> original.call(oldForm));
    }

    @WrapMethod(method = "initializeMovesetFrom")
    private void shadowedhearts$preserveRebuiltRewards(MovesetBuilder builder, Operation<Void> original) {
        MoveLifecycle.preserveEarnedMoves((Pokemon) (Object) this, () -> original.call(builder));
    }
}
