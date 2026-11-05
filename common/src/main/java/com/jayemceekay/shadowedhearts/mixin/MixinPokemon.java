package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MovesetBuilder;
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.common.aura.AuraStyleProperties;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowMoveUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowFriendshipProgression;
import com.jayemceekay.shadowedhearts.common.shadow.restrictions.ShadowRestrictionPolicy;
import com.jayemceekay.shadowedhearts.network.AuraServerSync;
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
import java.util.Set;

@Mixin(value = Pokemon.class, remap = false, priority = 1001)
public abstract class MixinPokemon implements AuraStyleProperties.ForcedAspectAccess {

    @Shadow
    public abstract boolean isPlayerOwned();

    @Shadow
    private boolean isClient;

    @Shadow
    private Set<String> forcedAspects;

    /** Clear legacy style flags without recursively invoking the native setter. */
    @Override
    public void shadowedhearts$setAuraForcedAspects(Set<String> aspects) {
        forcedAspects = aspects;
    }

    /** Covers native loading/copying and live aspect changes, including wild Pokemon. */
    @WrapMethod(method = "updateAspects")
    private void shadowedhearts$normalizeAuraStyle(Operation<Void> original) {
        Pokemon pokemon = (Pokemon) (Object) this;
        boolean wasShadow = ShadowAspectUtil.hasShadowAspect(pokemon);
        boolean changed = !isClient && AuraStyleProperties.normalize(pokemon);
        original.call();
        if (changed) {
            ShadowAspectUtil.syncProperties(pokemon);
        }
        if (!isClient && wasShadow != ShadowAspectUtil.hasShadowAspect(pokemon)) {
            AuraServerSync.onShadowStateChanged(pokemon, wasShadow);
        }
    }

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
        if (this$0.getAspects().contains(SHAspects.SHADOW) && ShadowMoveUtil.isShadowMove(move)) {
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
        Boolean result = ShadowFriendshipProgression.set((Pokemon) (Object) this, value, coerceSafe);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "incrementFriendship", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventIncrementFriendship(int amount, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = ShadowFriendshipProgression.change((Pokemon) (Object) this, amount, true, coerceSafe);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "decrementFriendship", at = @At("HEAD"), cancellable = true)
    public void shadowedhearts$preventDecrementFriendship(int amount, boolean coerceSafe, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = ShadowFriendshipProgression.change((Pokemon) (Object) this, amount, false, coerceSafe);
        if (result != null) cir.setReturnValue(result);
    }

    @WrapMethod(method = "updateMovesOnFormChange")
    private void shadowedhearts$wrapUpdateMovesOnFormChange(FormData oldForm, Operation<Void> original) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (pokemon.getAspects().contains(SHAspects.SHADOW)) {
            return;
        }
        original.call(oldForm);
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
            if (ShadowMoveUtil.isShadowMove(selfMove)) {
                shadowSlots.add(selfMove.copy());
            } else if (ShadowMoveUtil.isShadowMove(otherMove)) {
                shadowSlots.add(otherMove.copy());
            } else {
                shadowSlots.add(null);
            }
        }

        // Save shadow benched moves from both sources
        List<BenchedMove> shadowBenched = new ArrayList<>();
        for (BenchedMove bm : self.getBenchedMoves()) {
            if (ShadowMoveUtil.isShadowMove(bm.getMoveTemplate())) {
                shadowBenched.add(bm);
            }
        }
        for (BenchedMove bm : other.getBenchedMoves()) {
            if (ShadowMoveUtil.isShadowMove(bm.getMoveTemplate())) {
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

    /** Cobblemon 1.8's default and authored builders bypass the legacy initializer. */
    @WrapMethod(method = "initializeMovesetFrom")
    private void shadowedhearts$preserveShadowMovesFromBuilder(MovesetBuilder builder, Operation<Void> original) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (isClient || !ShadowAspectUtil.hasShadowAspect(pokemon)) {
            original.call(builder);
            return;
        }

        MoveSet moveSet = pokemon.getMoveSet();
        Move[] shadowSlots = new Move[MoveSet.MOVE_COUNT];
        for (int slot = 0; slot < MoveSet.MOVE_COUNT; slot++) {
            Move move = moveSet.get(slot);
            if (ShadowMoveUtil.isShadowMove(move)) shadowSlots[slot] = move.copy();
        }

        original.call(builder);

        // Keep the builder's ordinary moves available for later purification, and retain
        // the assigned Shadow moves' slots and PP instead of rolling a new assignment.
        moveSet.doWithoutEmitting(() -> {
            for (int slot = 0; slot < MoveSet.MOVE_COUNT; slot++) {
                Move shadowMove = shadowSlots[slot];
                if (shadowMove == null) continue;
                Move displaced = moveSet.get(slot);
                // An authored builder may already include this move in a different slot.
                // Swap the displaced move into that slot so the active set stays compact.
                for (int otherSlot = 0; otherSlot < MoveSet.MOVE_COUNT; otherSlot++) {
                    Move other = moveSet.get(otherSlot);
                    if (otherSlot != slot && other != null
                            && other.getTemplate().equals(shadowMove.getTemplate())) {
                        moveSet.setMove(otherSlot, displaced);
                        displaced = null;
                    }
                }
                if (displaced != null && !ShadowMoveUtil.isShadowMove(displaced)) {
                    pokemon.getBenchedMoves().add(new BenchedMove(
                            displaced.getTemplate(), displaced.getRaisedPpStages()));
                }
                moveSet.setMove(slot, shadowMove);
            }
            return Unit.INSTANCE;
        });
        moveSet.update();
    }

}
