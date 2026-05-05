package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = Pokemon.class, remap = false, priority = 1001)
public abstract class MixinPokemon {

    @Shadow
    public abstract boolean isPlayerOwned();

    @WrapMethod(method = "isWild")
    private boolean shadowedhearts$isWild(Operation<Boolean> original) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            if (isPlayerOwned()) {
                return false;
            } else {
                return true;
            }
        }
        return original.call();
    }

    @Inject(method = "validateMoveset$lambda$0$0", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/api/moves/MoveSet;setMove(ILcom/cobblemon/mod/common/api/moves/Move;)V"), cancellable = true)
    private static void shadowedhearts$preventShadowMoveRemoval(Pokemon this$0, LearnsetQuery $query, CallbackInfoReturnable<Unit> cir, @Local(name = "move") Move move) {
        if (this$0.getAspects().contains(SHAspects.SHADOW) && move.getType().equals(ElementalTypes.get("shadow"))) {
            cir.cancel();
        }
    }

    @Inject(method = "validateMoveset", at = @At("HEAD"), cancellable = true)
    private void shadowedhearts$preventShadowMoveValidation(boolean includeLegacy, CallbackInfo ci) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (pokemon.getAspects().contains(SHAspects.SHADOW)) {
            ci.cancel();
        }
    }

    @Inject(method = "setFriendship(IZ)Z", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventSetFriendship(int value, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "incrementFriendship", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventIncrementFriendship(int amount, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "decrementFriendship", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventDecrementFriendship(int amount, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        if (((Pokemon) (Object) this).getAspects().contains(SHAspects.SHADOW)) {
            cir.setReturnValue(false);
        }
    }

    @WrapMethod(method = "updateMovesOnFormChange")
    private void shadowedhearts$wrapUpdateMovesOnFormChange(FormData newForm, Operation<Void> original) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (pokemon.getAspects().contains(SHAspects.SHADOW)) {
            return;
        }
        original.call(newForm);
    }

    @WrapMethod(method = "copyFrom")
    private Pokemon shadowedhearts$wrapCopyFrom(Pokemon other, Operation<Pokemon> original) {
        boolean otherIsShadow = other.getAspects().contains(SHAspects.SHADOW)
                || other.getForcedAspects().contains(SHAspects.SHADOW);
        Pokemon self = (Pokemon) (Object) this;
        boolean selfIsShadow = self.getAspects().contains(SHAspects.SHADOW)
                || self.getForcedAspects().contains(SHAspects.SHADOW);

        if (!otherIsShadow && !selfIsShadow) {
            return original.call(other);
        }

        // Save shadow active slots from BOTH self and other.
        // self (e.g. the mega pokemon in battle) may have a shadow move in an active slot
        // that other (the pre-mega snapshot) does not have.
        List<Move> shadowSlots = new ArrayList<>();
        for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
            Move selfMove = self.getMoveSet().get(i);
            Move otherMove = other.getMoveSet().get(i);
            // Prefer self's active shadow move (current battle state), fall back to other's
            if (selfMove != null && selfMove.getType().equals(ElementalTypes.get("shadow"))) {
                shadowSlots.add(selfMove.copy());
            } else if (otherMove != null && otherMove.getType().equals(ElementalTypes.get("shadow"))) {
                shadowSlots.add(otherMove.copy());
            } else {
                shadowSlots.add(null);
            }
        }

        // Save shadow benched moves from both sources
        List<BenchedMove> shadowBenched = new ArrayList<>();
        for (BenchedMove bm : self.getBenchedMoves()) {
            if (bm.getMoveTemplate().getElementalType().equals(ElementalTypes.get("shadow"))) {
                shadowBenched.add(bm);
            }
        }
        for (BenchedMove bm : other.getBenchedMoves()) {
            if (bm.getMoveTemplate().getElementalType().equals(ElementalTypes.get("shadow"))) {
                boolean present = false;
                for (BenchedMove e : shadowBenched) {
                    if (e.getMoveTemplate() == bm.getMoveTemplate()) {
                        present = true;
                        break;
                    }
                }
                if (!present) shadowBenched.add(bm);
            }
        }

        Pokemon result = original.call(other);

        // Restore shadow moves that may have been stripped
        MoveSet moveSet = self.getMoveSet();
        moveSet.doWithoutEmitting(() -> {
            for (int i = 0; i < MoveSet.MOVE_COUNT; i++) {
                if (shadowSlots.get(i) != null) {
                    moveSet.setMove(i, shadowSlots.get(i));
                }
            }
            return null;
        });

        var benchedMoves = self.getBenchedMoves();
        benchedMoves.doWithoutEmitting(() -> {
            for (BenchedMove bm : shadowBenched) {
                boolean present = false;
                for (BenchedMove e : benchedMoves) {
                    if (e.getMoveTemplate() == bm.getMoveTemplate()) {
                        present = true;
                        break;
                    }
                }
                if (!present) benchedMoves.add(bm);
            }
            return null;
        });

        // Explicit sync with snapshots after all modifications are done
        ShadowAspectUtil.syncMoveSet(self);
        ShadowAspectUtil.syncBenchedMoves(self);

        return result;
    }

    @Inject(method = "initializeMoveset$default", at = @At("HEAD"), cancellable = true)
    private static void shadowedhearts$preventShadowMoveRemovalOnInitializeMoveset(Pokemon pokemon, boolean par2, int par3, Object par4, CallbackInfo ci) {
        if (pokemon.getAspects().contains(SHAspects.SHADOW)) {
            ci.cancel();
        }
    }

}
